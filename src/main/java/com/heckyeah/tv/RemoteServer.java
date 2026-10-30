package com.heckyeah.tv;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v1.19: Phone Remote server. A hand-rolled HTTP + WebSocket
 * (RFC 6455) server on the LAN port 18765: serves the phone page
 * (assets/remote.html), pairs phones (max 2 sessions), and
 * forwards control commands to the activity through Host.
 * Framework APIs only, no dependencies.
 *
 * Threading: 1 accept thread + a bounded pool of connection
 * threads (max 6); each WebSocket client additionally gets one
 * writer thread fed by a queue. Every Host callback is posted
 * to the activity's UI handler - no UI/WebView work ever happens
 * on a server thread. Everything is exception-guarded: this
 * server must never be able to crash the app.
 *
 * Security model (v1.37, compare-and-pair): LAN-only
 * convenience, OFF by default. There is no typed PIN and no
 * code-guessing surface: pairing starts on the phone
 * (POST /pair/start), which creates ONE pending request with a
 * one-time random 6-digit code shown on BOTH the phone and the
 * TV; the request expires in 60s and only one may be pending at
 * a time. Either side confirms - the phone's Pair tap
 * (POST /pair/confirm, bound to the request id + a per-request
 * client key that only the starting phone ever receives) or
 * the TV's local Pair action - and approval mints the session
 * token. The phone never submits the code, so there is nothing
 * to brute-force; abuse control is a per-source-IP throttle on
 * /pair/start (more than 5 starts within 60s blocks that IP
 * for 60s). Session tokens live in memory only (an app restart
 * or server restart invalidates every phone).
 */
public class RemoteServer {

    public static final int PORT = 18765;
    private static final int MAX_SESSIONS = 2;
    private static final int MAX_CONN_THREADS = 6;
    // v1.37: compare-and-pair constants.
    private static final long PAIR_REQ_TTL_MS = 60000;      // pending request lifetime
    private static final long PAIR_REC_KEEP_MS = 60000;     // finished requests stay queryable this long
    private static final int PAIR_START_LIMIT = 5;          // starts per IP per window
    private static final long PAIR_START_WINDOW_MS = 60000;
    private static final long PAIR_START_BLOCK_MS = 60000;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final float MOVE_GAIN = 1.6f; // trackpad feel

    /** All methods are invoked on the activity's UI thread. */
    public interface Host {
        void remoteMove(float dx, float dy);
        void remoteTap();
        void remoteLongTap();
        void remoteScroll(int dxPx, int dyPx);
        void remoteBack();
        void remoteForward();
        void remoteReload();
        void remoteDone();
        void remotePlayPause();
        /** v1.34: the phone remote's PiP pill. */
        void remotePip();
        void remoteUrl(String u);
        void remoteType(String text);
        void remoteEnterKey();
        /** v1.22: D-pad mode key: up|down|left|right|ok|back|menu. */
        void remoteKey(String k);
        /** v1.39: D-pad direction hold: h:1 = hold start, h:0 = release.
         *  The TV owns the repeat; the phone sends no repeat stream. */
        void remoteKeyHold(String k, boolean down);
        /** v1.39: trackpad touch engagement (d:1 down / heartbeat,
         *  d:0 all fingers up) - keeps edge-push scrolling alive
         *  while a finger is held still against an edge. */
        void remoteTouch(boolean down);
        void remoteVolume(int dir);
        void remoteSeek(int seconds);
        void remoteSessionsChanged();
        /** v1.37: pairing state changed (request started / approved / denied / expired / reset). */
        void remotePairingChanged();
    }

    private final Context ctx;
    private final Handler ui;
    private final Host host;
    private final SecureRandom random = new SecureRandom();

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final AtomicInteger connThreads = new AtomicInteger(0);

    private final Set<String> sessions =
            Collections.synchronizedSet(new HashSet<String>());
    private final List<WsConn> wsClients =
            Collections.synchronizedList(new ArrayList<WsConn>());

    // ------------------------------------------------ v1.37: pairing state
    // ALL pairing state transitions (start, phone confirm, TV
    // approve, deny, cancel, expiry, reset) are guarded by this
    // one lock, so interleavings across connection threads can
    // never mint two sessions for one request or approve a
    // request that already expired/was denied.
    private final Object pairLock = new Object();

    /** The single in-flight pairing request (null when none). */
    private static final class PairReq {
        String id;       // UUID, public handle of the request
        String key;      // UUID, secret: returned ONLY to the starting phone
        String code;     // 6-digit compare code shown on phone + TV
        String ip;       // source IP that started the request
        long expiresAt;  // start + PAIR_REQ_TTL_MS
    }

    /** A finished request, kept briefly so /pair/status can still
     *  report (and, for approvals, re-deliver) the outcome. */
    private static final class PairRec {
        static final int APPROVED = 1, DENIED = 2, EXPIRED = 3;
        String key;
        int state;
        String token;    // APPROVED only
        long until;      // record dropped after this
    }

    private PairReq pending; // guarded by pairLock
    private final java.util.HashMap<String, PairRec> pairRecent =
            new java.util.HashMap<>(); // guarded by pairLock
    // Per-source-IP /pair/start throttle, guarded by pairLock.
    private final java.util.HashMap<String, java.util.ArrayList<Long>> pairStartTimes =
            new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> pairStartBlockedUntil =
            new java.util.HashMap<>();

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener nsdListener;

    private String pageHtmlCache;

    public RemoteServer(Context ctx, Handler ui, Host host) {
        this.ctx = ctx.getApplicationContext();
        this.ui = ui;
        this.host = host;
    }

    // ---------------------------------------------------------- lifecycle

    public synchronized void start() {
        if (running) return;
        synchronized (pairLock) { // v1.37: fresh pairing state per server start
            pending = null;
            pairRecent.clear();
            pairStartTimes.clear();
            pairStartBlockedUntil.clear();
        }
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (Exception e) {
            Log.d("HeckYeahFS", "remote start FAILED: " + e);
            return;
        }
        running = true;
        acceptThread = new Thread(this::acceptLoop, "hy-remote-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        registerNsd();
        Log.d("HeckYeahFS", "remote server start port=" + PORT);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        unregisterNsd();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        serverSocket = null;
        synchronized (wsClients) {
            for (WsConn c : new ArrayList<>(wsClients)) c.closeQuietly();
            wsClients.clear();
        }
        sessions.clear();
        synchronized (pairLock) { // v1.37: stop() cancels any pending request
            pending = null;
            pairRecent.clear();
        }
        Log.d("HeckYeahFS", "remote server stop");
    }

    public boolean isRunning() { return running; }
    public int sessionCount() { return sessions.size(); }

    // ------------------------------------------------ v1.37: TV-side pairing API

    /**
     * The pending request's 6-digit compare code, or null when
     * there is no live pending request. Lazily expires the
     * request (expiry is a state transition like any other and
     * notifies the Host when it happens).
     */
    public String pendingPairCode() {
        boolean changed;
        String code;
        synchronized (pairLock) {
            changed = expirePendingLocked(System.currentTimeMillis());
            code = pending != null ? pending.code : null;
        }
        if (changed) notifyPairingChanged();
        return code;
    }

    /** Milliseconds until the pending request expires (0 when none). */
    public long pendingPairExpiresInMs() {
        boolean changed;
        long left;
        synchronized (pairLock) {
            long now = System.currentTimeMillis();
            changed = expirePendingLocked(now);
            left = pending != null ? Math.max(0, pending.expiresAt - now) : 0;
        }
        if (changed) notifyPairingChanged();
        return left;
    }

    /** TV Pair action: approve the pending request locally. The
     *  session token waits for the phone's next /pair/status poll. */
    public boolean approvePendingFromTv() {
        String token;
        synchronized (pairLock) {
            expirePendingLocked(System.currentTimeMillis());
            if (pending == null) return false;
            if (sessions.size() >= MAX_SESSIONS) return false;
            token = approveLocked(pending);
        }
        Log.d("HeckYeahFS", "remote paired via=tv n=" + sessions.size());
        notifySessionsChanged();
        notifyPairingChanged();
        return token != null;
    }

    /** TV Deny action: reject the pending request. */
    public boolean denyPendingFromTv() {
        boolean denied;
        synchronized (pairLock) {
            expirePendingLocked(System.currentTimeMillis());
            denied = pending != null && finishPendingLocked(PairRec.DENIED, null);
        }
        if (denied) {
            Log.d("HeckYeahFS", "remote pairing denied via=tv");
            notifyPairingChanged();
        }
        return denied;
    }

    /** Panel "Reset pairing": drop every session, cancel any
     *  pending request, and clear the start throttle. */
    public void resetPairingAndDropSessions() {
        synchronized (pairLock) {
            if (pending != null) finishPendingLocked(PairRec.DENIED, null);
            pairStartTimes.clear();
            pairStartBlockedUntil.clear();
        }
        dropAllSessions(); // notifies sessions-changed itself
        notifyPairingChanged();
    }

    public void dropAllSessions() {
        sessions.clear();
        synchronized (wsClients) {
            for (WsConn c : new ArrayList<>(wsClients)) c.closeQuietly();
            wsClients.clear();
        }
        notifySessionsChanged();
    }

    private void notifySessionsChanged() {
        ui.post(() -> {
            try { host.remoteSessionsChanged(); } catch (Exception ignored) {}
        });
    }

    private void notifyPairingChanged() {
        ui.post(() -> {
            try { host.remotePairingChanged(); } catch (Exception ignored) {}
        });
    }

    // ------------------------------------------------ v1.37: pairing core
    // Every method in this block runs with pairLock held.

    /** Lazy expiry: a pending request past its TTL becomes an
     *  EXPIRED record. Returns true when a transition happened. */
    private boolean expirePendingLocked(long now) {
        if (pending != null && now >= pending.expiresAt) {
            finishPendingLocked(PairRec.EXPIRED, null);
            return true;
        }
        return false;
    }

    /** Move the pending request into the recent-records map with
     *  the given outcome. Returns false when nothing is pending. */
    private boolean finishPendingLocked(int state, String token) {
        if (pending == null) return false;
        PairRec rec = new PairRec();
        rec.key = pending.key;
        rec.state = state;
        rec.token = token;
        rec.until = System.currentTimeMillis() + PAIR_REC_KEEP_MS;
        pairRecent.put(pending.id, rec);
        pending = null;
        return true;
    }

    /** The one approval path, shared by phone confirm and TV
     *  approve: mint the token, add the session, retire the
     *  request as APPROVED (token stored for status re-delivery,
     *  so a lost confirm response can't strand the phone, and a
     *  double approval returns the SAME token - never two
     *  sessions for one request). Caller checks the slot first. */
    private String approveLocked(PairReq req) {
        String token = UUID.randomUUID().toString();
        sessions.add(token);
        if (req == pending) finishPendingLocked(PairRec.APPROVED, token);
        return token;
    }

    /** Drop finished records past their keep window. */
    private void pruneRecentLocked(long now) {
        java.util.Iterator<java.util.Map.Entry<String, PairRec>> it =
                pairRecent.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue().until) it.remove();
        }
    }

    /** Sends a state JSON text frame to every connected phone. */
    public void broadcast(String msg) {
        if (!running) return;
        byte[] frame = buildTextFrame(msg);
        synchronized (wsClients) {
            for (WsConn c : wsClients) c.enqueue(frame);
        }
    }

    // ---------------------------------------------------------- mDNS (extra B)

    private void registerNsd() {
        try {
            nsdManager = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
            if (nsdManager == null) return;
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName("HeckYeah TV");
            info.setServiceType("_http._tcp.");
            info.setPort(PORT);
            nsdListener = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo si) {
                    Log.d("HeckYeahFS", "remote nsd registered: " + si.getServiceName());
                }
                @Override public void onRegistrationFailed(NsdServiceInfo si, int err) {
                    Log.d("HeckYeahFS", "remote nsd registration failed: " + err);
                }
                @Override public void onServiceUnregistered(NsdServiceInfo si) {}
                @Override public void onUnregistrationFailed(NsdServiceInfo si, int err) {}
            };
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener);
        } catch (Exception e) {
            Log.d("HeckYeahFS", "remote nsd unavailable: " + e);
            nsdManager = null;
            nsdListener = null;
        }
    }

    private void unregisterNsd() {
        try {
            if (nsdManager != null && nsdListener != null)
                nsdManager.unregisterService(nsdListener);
        } catch (Exception ignored) {}
        nsdManager = null;
        nsdListener = null;
    }

    // ---------------------------------------------------------- IP helper

    /** WiFi IPv4 of this device (for the pairing panel). */
    public static String localIpAddress(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                            + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
                }
            }
        } catch (Exception ignored) {}
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try { if (!ni.isUp() || ni.isLoopback()) continue; }
                catch (Exception e) { continue; }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address)
                        return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    // ---------------------------------------------------------- accept + HTTP

    private void acceptLoop() {
        while (running) {
            final Socket s;
            try {
                s = serverSocket.accept();
            } catch (Exception e) {
                if (running) Log.d("HeckYeahFS", "remote accept error: " + e);
                break;
            }
            if (connThreads.incrementAndGet() > MAX_CONN_THREADS) {
                connThreads.decrementAndGet();
                try { s.close(); } catch (Exception ignored) {}
                continue;
            }
            Thread t = new Thread(() -> {
                try { handleConnection(s); }
                catch (Exception ignored) {}
                finally {
                    connThreads.decrementAndGet();
                    try { s.close(); } catch (Exception ignored) {}
                }
            }, "hy-remote-conn");
            t.setDaemon(true);
            t.start();
        }
    }

    private void handleConnection(Socket s) throws Exception {
        BufferedInputStream in = new BufferedInputStream(s.getInputStream());
        OutputStream out = s.getOutputStream();
        byte[] headBytes = readUntilHeaderEnd(in);
        if (headBytes == null) return;
        String head = new String(headBytes, StandardCharsets.ISO_8859_1);
        String[] lines = head.split("\r\n");
        if (lines.length == 0) return;
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 2) return;
        String method = requestLine[0];
        String path = requestLine[1];
        String query = "";
        int qi = path.indexOf('?');
        if (qi >= 0) { query = path.substring(qi + 1); path = path.substring(0, qi); }
        String clientIp = "";
        try {
            if (s.getInetAddress() != null) clientIp = s.getInetAddress().getHostAddress();
        } catch (Exception ignored) {}
        TreeMap<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) headers.put(lines[i].substring(0, c).trim(),
                    lines[i].substring(c + 1).trim());
        }

        if ("GET".equals(method) && "/ws".equals(path)) {
            handleWebSocket(s, in, out, headers);
            return;
        }
        if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
            byte[] body = pageHtml().getBytes(StandardCharsets.UTF_8);
            writeResponse(out, 200, "OK", "text/html; charset=utf-8", body, null);
            return;
        }
        if ("POST".equals(method) && path.startsWith("/pair/")) {
            int len = 0;
            try { len = Integer.parseInt(headers.getOrDefault("Content-Length", "0")); }
            catch (Exception ignored) {}
            byte[] body = readFully(in, Math.min(Math.max(len, 0), 4096));
            if ("/pair/start".equals(path)) { handlePairStart(out, clientIp); return; }
            if ("/pair/confirm".equals(path)) { handlePairConfirm(out, body); return; }
            if ("/pair/cancel".equals(path)) { handlePairCancel(out, body); return; }
        }
        if ("GET".equals(method) && "/pair/status".equals(path)) {
            handlePairStatus(out, query);
            return;
        }
        if ("GET".equals(method) && "/pair/session".equals(path)) {
            handlePairSession(out, headers);
            return;
        }
        writeResponse(out, 404, "Not Found", "text/plain", "not found".getBytes(StandardCharsets.UTF_8), null);
    }

    // ------------------------------------------------ v1.37: pairing endpoints

    private static String sessionCookie(String token) {
        return "Set-Cookie: hy=" + token + "; Path=/; Max-Age=31536000\r\n";
    }

    /** POST /pair/start: create the single pending request. The
     *  response carries the code AND the secret client key to the
     *  starting phone only; the TV learns the code through
     *  pendingPairCode() (local), never over the wire. */
    private void handlePairStart(OutputStream out, String ip) throws Exception {
        long now = System.currentTimeMillis();
        String id, code, key;
        int err = 0;
        String errName = "";
        synchronized (pairLock) {
            // Per-IP throttle: >5 starts within 60s blocks the IP
            // for 60s. Attempts are counted before any other check.
            Long blocked = pairStartBlockedUntil.get(ip);
            if (blocked != null && now < blocked) {
                err = 429; errName = "locked";
            } else {
                java.util.ArrayList<Long> times = pairStartTimes.get(ip);
                if (times == null) {
                    times = new java.util.ArrayList<>();
                    pairStartTimes.put(ip, times);
                }
                times.add(now);
                while (!times.isEmpty() && now - times.get(0) > PAIR_START_WINDOW_MS)
                    times.remove(0);
                if (times.size() > PAIR_START_LIMIT) {
                    pairStartBlockedUntil.put(ip, now + PAIR_START_BLOCK_MS);
                    times.clear();
                    Log.d("HeckYeahFS", "remote pairing locked 60s ip=" + ip);
                    err = 429; errName = "locked";
                }
            }
            if (err == 0) {
                expirePendingLocked(now);
                pruneRecentLocked(now);
                if (sessions.size() >= MAX_SESSIONS) { err = 403; errName = "full"; }
                else if (pending != null) { err = 409; errName = "busy"; }
            }
            if (err == 0) {
                PairReq req = new PairReq();
                req.id = UUID.randomUUID().toString();
                req.key = UUID.randomUUID().toString();
                req.code = String.format("%06d", random.nextInt(1000000));
                req.ip = ip;
                req.expiresAt = now + PAIR_REQ_TTL_MS;
                pending = req;
                id = req.id; code = req.code; key = req.key;
            } else {
                id = code = key = null;
            }
        }
        if (err != 0) {
            writeJson(out, err, "{\"ok\":false,\"err\":\"" + errName + "\"}", null);
            return;
        }
        Log.d("HeckYeahFS", "remote pairing started ip=" + ip);
        writeJson(out, 200, "{\"ok\":true,\"id\":\"" + id + "\",\"code\":\"" + code
                + "\",\"key\":\"" + key + "\",\"expiresIn\":"
                + (PAIR_REQ_TTL_MS / 1000) + "}", null);
        notifyPairingChanged();
    }

    /** Query params of /pair/status: id + key, both plain UUIDs. */
    private static String queryParam(String query, String name) {
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name))
                return part.substring(eq + 1);
        }
        return "";
    }

    /** GET /pair/session (v1.38): is the caller's hy cookie a
     *  live session? Parses the cookie exactly like the WS
     *  upgrade. 200 {"ok":true,"paired":true} when it is, 401
     *  otherwise. Read-only: the phone page asks this after a
     *  WebSocket dies before opening, so a transient socket
     *  failure is never mistaken for "unpaired" (the v1.38
     *  double-pairing fix) — the overlay may only appear on a
     *  proven 401 from here. */
    private void handlePairSession(OutputStream out,
                                   TreeMap<String, String> headers) throws Exception {
        String cookie = headers.getOrDefault("Cookie", "");
        String token = null;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("hy=")) token = p.substring(3).trim();
        }
        if (token != null && sessions.contains(token)) {
            writeJson(out, 200, "{\"ok\":true,\"paired\":true}", null);
        } else {
            writeJson(out, 401, "{\"ok\":false}", null);
        }
    }

    /** GET /pair/status?id&key: the phone's poll. A TV approval
     *  reaches the phone here (with the session cookie). */
    private void handlePairStatus(OutputStream out, String query) throws Exception {
        String id = queryParam(query, "id");
        String key = queryParam(query, "key");
        long now = System.currentTimeMillis();
        int status;
        String json;
        String cookie = null;
        boolean changed;
        synchronized (pairLock) {
            changed = expirePendingLocked(now);
            pruneRecentLocked(now);
            if (pending != null && pending.id.equals(id)) {
                if (!pending.key.equals(key)) {
                    status = 401; json = "{\"ok\":false,\"err\":\"key\"}";
                } else {
                    long left = Math.max(0, (pending.expiresAt - now + 999) / 1000);
                    status = 200;
                    json = "{\"ok\":true,\"state\":\"pending\",\"expiresIn\":" + left + "}";
                }
            } else {
                PairRec rec = pairRecent.get(id);
                if (rec == null || !rec.key.equals(key)) {
                    status = 401; json = "{\"ok\":false,\"err\":\"key\"}";
                } else if (rec.state == PairRec.APPROVED) {
                    status = 200;
                    json = "{\"ok\":true,\"state\":\"approved\"}";
                    cookie = sessionCookie(rec.token);
                } else if (rec.state == PairRec.DENIED) {
                    status = 200;
                    json = "{\"ok\":true,\"state\":\"denied\"}";
                } else {
                    status = 410;
                    json = "{\"ok\":false,\"state\":\"expired\",\"err\":\"expired\"}";
                }
            }
        }
        if (changed) notifyPairingChanged();
        writeJson(out, status, json, cookie);
    }

    /** Parse a {id,key} JSON body; returns [id, key] ("" when absent). */
    private static String[] pairBodyIds(byte[] body) {
        String id = "", key = "";
        try {
            JSONObject o = new JSONObject(new String(body, StandardCharsets.UTF_8));
            id = o.optString("id", "");
            key = o.optString("key", "");
        } catch (Exception ignored) {}
        return new String[]{id, key};
    }

    /** POST /pair/confirm {id,key}: the phone's Pair tap.
     *  Idempotent: re-confirming an approved request re-delivers
     *  the same token instead of minting a second session. */
    private void handlePairConfirm(OutputStream out, byte[] body) throws Exception {
        String[] ids = pairBodyIds(body);
        String id = ids[0], key = ids[1];
        long now = System.currentTimeMillis();
        int status;
        String json;
        String cookie = null;
        boolean approvedNow = false, changed;
        synchronized (pairLock) {
            changed = expirePendingLocked(now);
            pruneRecentLocked(now);
            PairRec rec = pairRecent.get(id);
            if (rec != null && rec.key.equals(key)) {
                if (rec.state == PairRec.APPROVED) {
                    status = 200; json = "{\"ok\":true,\"state\":\"approved\"}";
                    cookie = sessionCookie(rec.token);
                } else if (rec.state == PairRec.DENIED) {
                    status = 410; json = "{\"ok\":false,\"err\":\"denied\"}";
                } else {
                    status = 410; json = "{\"ok\":false,\"err\":\"expired\"}";
                }
            } else if (pending == null || !pending.id.equals(id)) {
                status = (rec != null) ? 401 : 404;
                json = "{\"ok\":false,\"err\":\"" + (status == 401 ? "key" : "gone") + "\"}";
            } else if (!pending.key.equals(key)) {
                status = 401; json = "{\"ok\":false,\"err\":\"key\"}";
            } else if (sessions.size() >= MAX_SESSIONS) {
                status = 403; json = "{\"ok\":false,\"err\":\"full\"}";
            } else {
                String token = approveLocked(pending);
                status = 200; json = "{\"ok\":true,\"state\":\"approved\"}";
                cookie = sessionCookie(token);
                approvedNow = true;
            }
        }
        if (changed) notifyPairingChanged();
        if (approvedNow) {
            Log.d("HeckYeahFS", "remote paired via=phone n=" + sessions.size());
            notifySessionsChanged();
            notifyPairingChanged();
        }
        writeJson(out, status, json, cookie);
    }

    /** POST /pair/cancel {id,key}: the phone gives up its request. */
    private void handlePairCancel(OutputStream out, byte[] body) throws Exception {
        String[] ids = pairBodyIds(body);
        boolean cancelled = false;
        synchronized (pairLock) {
            expirePendingLocked(System.currentTimeMillis());
            if (pending != null && pending.id.equals(ids[0]) && pending.key.equals(ids[1])) {
                cancelled = finishPendingLocked(PairRec.DENIED, null);
            }
        }
        if (cancelled) {
            Log.d("HeckYeahFS", "remote pairing cancelled by phone");
            notifyPairingChanged();
        }
        writeJson(out, 200, "{\"ok\":true}", null);
    }

    private String pageHtml() {
        if (pageHtmlCache != null) return pageHtmlCache;
        try {
            InputStream is = ctx.getAssets().open("remote.html");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) > 0) buf.write(chunk, 0, n);
            is.close();
            pageHtmlCache = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            pageHtmlCache = "<html><body>remote page missing</body></html>";
        }
        return pageHtmlCache;
    }

    private static void writeResponse(OutputStream out, int code, String reason,
                                      String contentType, byte[] body,
                                      String extraHeaders) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        // v1.32: no-store + must-revalidate. The pairing URL is
        // versioned (?v=<versionCode>) so a phone tab restored
        // from a snapshot at the OLD address cannot silently keep
        // running a stale copy of the remote page.
        sb.append("Cache-Control: no-store, must-revalidate\r\n");
        sb.append("Connection: close\r\n");
        if (extraHeaders != null) sb.append(extraHeaders);
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static void writeJson(OutputStream out, int code, String json,
                                  String extraHeaders) throws Exception {
        String reason = code == 200 ? "OK" : code == 401 ? "Unauthorized"
                : code == 403 ? "Forbidden" : code == 404 ? "Not Found"
                : code == 409 ? "Conflict" : code == 410 ? "Gone"
                : code == 429 ? "Too Many Requests" : "Error";
        writeResponse(out, code, reason, "application/json",
                json.getBytes(StandardCharsets.UTF_8), extraHeaders);
    }

    /** Reads until the CRLFCRLF header terminator (16 KB cap). */
    private static byte[] readUntilHeaderEnd(BufferedInputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0; // bytes of the \r\n\r\n terminator matched so far
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (buf.size() > 16384) return null;
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = (b == '\r') ? 1 : 0;
            if (state == 4) return buf.toByteArray();
        }
        return buf.size() > 0 ? buf.toByteArray() : null;
    }

    private static byte[] readFully(InputStream in, int len) throws Exception {
        byte[] out = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(out, off, len - off);
            if (n < 0) break;
            off += n;
        }
        return out;
    }

    // ---------------------------------------------------------- WebSocket (RFC 6455)

    private void handleWebSocket(Socket s, BufferedInputStream in, OutputStream out,
                                 TreeMap<String, String> headers) throws Exception {
        String cookie = headers.getOrDefault("Cookie", "");
        String token = null;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("hy=")) token = p.substring(3).trim();
        }
        if (token == null || !sessions.contains(token)) {
            writeResponse(out, 401, "Unauthorized", "text/plain",
                    "pair first".getBytes(StandardCharsets.UTF_8), null);
            return;
        }
        String key = headers.getOrDefault("Sec-WebSocket-Key", "");
        String accept = wsAccept(key);
        String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(resp.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();

        WsConn conn = new WsConn(s, out);
        wsClients.add(conn);
        conn.startWriter();
        notifySessionsChanged();
        try {
            wsReadLoop(conn, in);
        } finally {
            wsClients.remove(conn);
            conn.closeQuietly();
            notifySessionsChanged();
        }
    }

    private static String wsAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + WS_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private void wsReadLoop(WsConn conn, BufferedInputStream in) throws Exception {
        ByteArrayOutputStream frag = new ByteArrayOutputStream();
        boolean inFrag = false;
        while (running) {
            int b0 = in.read();
            if (b0 < 0) return;
            int b1 = in.read();
            if (b1 < 0) return;
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = ((long) readByte(in) << 8) | readByte(in);
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByte(in);
            }
            if (len > 65536) return; // absurd frame: drop the connection
            byte[] maskKey = masked ? readFully(in, 4) : null;
            byte[] payload = readFully(in, (int) len);
            if (masked && maskKey != null) {
                for (int i = 0; i < payload.length; i++)
                    payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }
            switch (opcode) {
                case 0x8: // close
                    conn.enqueue(buildFrame(0x8, new byte[0]));
                    return;
                case 0x9: // ping -> pong
                    conn.enqueue(buildFrame(0xA, payload));
                    break;
                case 0xA: // pong: ignore
                    break;
                case 0x1: // text
                    if (fin) {
                        dispatchCommand(new String(payload, StandardCharsets.UTF_8));
                    } else {
                        frag.reset();
                        frag.write(payload);
                        inFrag = true;
                    }
                    break;
                case 0x0: // continuation
                    if (inFrag) {
                        frag.write(payload);
                        if (fin) {
                            dispatchCommand(new String(frag.toByteArray(), StandardCharsets.UTF_8));
                            inFrag = false;
                            frag.reset();
                        }
                    }
                    break;
                default: // binary and others: ignore payload
                    break;
            }
        }
    }

    private static int readByte(InputStream in) throws Exception {
        int b = in.read();
        if (b < 0) throw new java.io.EOFException();
        return b;
    }

    private static byte[] buildFrame(int opcode, byte[] payload) {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(0x80 | opcode);
        int len = payload.length;
        if (len < 126) {
            f.write(len);
        } else if (len <= 0xFFFF) {
            f.write(126);
            f.write((len >>> 8) & 0xFF);
            f.write(len & 0xFF);
        } else {
            f.write(127);
            for (int i = 7; i >= 0; i--) f.write((int) ((len >>> (8 * i)) & 0xFF));
        }
        f.write(payload, 0, payload.length);
        return f.toByteArray();
    }

    private static byte[] buildTextFrame(String msg) {
        return buildFrame(0x1, msg.getBytes(StandardCharsets.UTF_8));
    }

    private final class WsConn {
        final Socket socket;
        final OutputStream out;
        final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        volatile boolean open = true;

        WsConn(Socket socket, OutputStream out) {
            this.socket = socket;
            this.out = out;
        }

        void startWriter() {
            Thread t = new Thread(() -> {
                try {
                    while (open) {
                        byte[] frame = queue.take();
                        synchronized (out) {
                            out.write(frame);
                            out.flush();
                        }
                    }
                } catch (Exception ignored) {}
                closeQuietly();
            }, "hy-remote-ws-write");
            t.setDaemon(true);
            t.start();
        }

        void enqueue(byte[] frame) {
            if (open) queue.offer(frame);
        }

        void closeQuietly() {
            open = false;
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    // ---------------------------------------------------------- commands

    private void dispatchCommand(final String msg) {
        final JSONObject o;
        try {
            o = new JSONObject(msg);
        } catch (Exception e) {
            return;
        }
        final String t = o.optString("t", "");
        try {
            switch (t) {
                case "move": {
                    final float dx = (float) o.optDouble("dx", 0) * MOVE_GAIN;
                    final float dy = (float) o.optDouble("dy", 0) * MOVE_GAIN;
                    ui.post(() -> host.remoteMove(dx, dy));
                    break;
                }
                case "tap":
                    ui.post(() -> host.remoteTap());
                    break;
                case "longtap":
                    ui.post(() -> host.remoteLongTap());
                    break;
                case "scroll": {
                    // v1.33: dx joins dy (horizontal trackpad pan).
                    // Old dy-only messages still parse: dx defaults 0.
                    final double dx = o.optDouble("dx", 0);
                    final double dy = o.optDouble("dy", 0);
                    ui.post(() -> host.remoteScroll((int) Math.round(dx),
                            (int) Math.round(dy)));
                    break;
                }
                case "back":
                    ui.post(() -> host.remoteBack());
                    break;
                case "fwd":
                    ui.post(() -> host.remoteForward());
                    break;
                case "reload":
                    ui.post(() -> host.remoteReload());
                    break;
                case "done":
                    ui.post(() -> host.remoteDone());
                    break;
                case "playpause":
                    ui.post(() -> host.remotePlayPause());
                    break;
                case "pip":
                    ui.post(() -> host.remotePip());
                    break;
                case "url": {
                    final String u = o.optString("u", "");
                    ui.post(() -> host.remoteUrl(u));
                    break;
                }
                case "type": {
                    final String text = o.optString("text", "");
                    ui.post(() -> host.remoteType(text));
                    break;
                }
                case "key": {
                    // "enter" is the v1.19 type-field Enter (page JS);
                    // v1.22 adds physical-remote keys for D-pad mode.
                    // v1.39: a direction key carrying h (1 = hold
                    // start, 0 = release) routes to remoteKeyHold -
                    // the TV repeats it. No h = today's single fire.
                    final String k = o.optString("k", "");
                    final boolean isDir = "up".equals(k) || "down".equals(k)
                            || "left".equals(k) || "right".equals(k);
                    if (isDir && o.has("h")) {
                        final boolean down = o.optInt("h", 0) != 0;
                        ui.post(() -> host.remoteKeyHold(k, down));
                    } else if ("enter".equals(k)) {
                        ui.post(() -> host.remoteEnterKey());
                    } else if (isDir || "ok".equals(k) || "back".equals(k)
                            || "menu".equals(k)) {
                        ui.post(() -> host.remoteKey(k));
                    }
                    break;
                }
                case "touch": {
                    // v1.39: trackpad engagement heartbeat (see Host).
                    final boolean down = o.optInt("d", 0) != 0;
                    ui.post(() -> host.remoteTouch(down));
                    break;
                }
                case "vol": {
                    final int d = o.optInt("d", 0);
                    ui.post(() -> host.remoteVolume(d));
                    break;
                }
                case "seek": {
                    final int sec = o.optInt("s", 0);
                    ui.post(() -> host.remoteSeek(sec));
                    break;
                }
                default:
                    break;
            }
        } catch (Exception ignored) {}
    }
}
