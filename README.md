# HeckYeah TV

HeckYeah TV is a tvOS-style Fire TV app that turns [FMHY](https://fmhy.net/)
(Free Media Heck Yeah) into a remote-friendly interface: the catalog's
categories become large tiles, each category page becomes shelves of site
tiles based on FMHY's own section headers, and links open in a Safari-styled
in-app browser built for D-pad and mouse-style remote navigation.

## Features

- 12 FMHY categories as large Home tiles; category pages scaffolded by
  FMHY `##`/`###` headers into shelves of applet tiles
- Live catalog refresh from the `fmhy/edit` GitHub repository, with a
  bundled seed catalog as an offline fallback
- Favorites, global search, and a mirror picker for entries with
  multiple links
- Recently Watched shelf for video-category launches, with poster art
  and rating enrichment where available
- Safari-style in-app browser with mouse and D-pad sub-modes, an
  auto-hiding toolbar, and HTML5 fullscreen support (including handling
  for in-page "pseudo-fullscreen" players)
- Host-level ad/tracker and popup blocking, video-aware scroll
  clamping, and fullscreen diagnostics under the `HeckYeahFS` log tag

## Requirements

- Android device, API 25 or later (developed and tested on Fire TV
  with Fire OS 6 / Android 7.1.1)
- To build: JDK 17, Android SDK build-tools 34.0.0 and the android-34
  platform (`android.jar`), plus `bash`, `zip`, and `keytool`

## Build

Signing passwords are provided through the environment; they are never
stored in the repository. If `keystore.jks` does not exist, the build
generates a local development keystore (using a clearly warned default
password when no environment password is set).

```bash
export KEYSTORE_PASS='your-keystore-password'
export KEY_PASS="$KEYSTORE_PASS"   # optional; defaults to KEYSTORE_PASS
./build.sh
```

The signed APK is written to `build/manual/apk/HeckYeah.apk`.

## Install (adb)

```bash
adb connect <device-ip>:5555
adb install -r build/manual/apk/HeckYeah.apk
adb shell monkey -p com.heckyeah.tv -c android.intent.category.LAUNCHER 1
```

## Limits

- Ad blocking is host-level only: known ad/tracker hosts are refused,
  but the API 25 WebView has no extension support or cosmetic
  filtering. This is not uBlock Origin.
- The old WebView cannot play DRM-protected video, and some modern
  sites may refuse or misrender in it.
- Video players embedded in cross-origin iframes cannot be inspected
  by page scripts, so some browser safeguards (such as video-aware
  scroll clamping) may not engage for those players.

## License

MIT — see [LICENSE](LICENSE).

---

# HeckYeah TV — FMHY for Fire TV

A tvOS-style Fire TV app that turns [fmhy.net](https://fmhy.net/)
(freemediaheckyeah) into tile navigation: 12 categories as large
tiles, category pages scaffolded by FMHY's own `##`/`###` headers
into shelves of small applet tiles, and an in-app Safari-styled
browser to open them. Built Oct 26, 2026.

## What it does (Core + A, B, D, E + RW)

- **Home (tvOS-style)** — dark backdrop, "HeckYeah TV" wordmark,
  Search / Settings / nav-mode buttons top-right. 12 large category
  tiles (gradient + glyph + FMHY description + site count); focused
  tile scales up with a white border in D-pad mode.
- **Recently Watched (RW) — first shelf on Home** when non-empty.
  When a site is launched from the Movies/TV (video) category —
  including via Search results sourced from video — one entry per
  launch session tracks the **latest content page**: URL + WebView
  title are maintained via `doUpdateVisitedHistory` (fires for
  SPA in-page navigation too) + `onPageFinished` +
  `onReceivedTitle`, snapshotted once more from
  `WebView.getUrl()/getTitle()` when Done is hit. A site-root URL
  never overwrites a deeper entry for the same host in the same
  session, so the tile quick-links back to the exact episode/movie
  page, not the homepage. On Done, the playing `<video>`'s
  `currentTime` is also captured (best-effort JS) and a reopened
  RW tile tries to seek back to it after load. Cap 12,
  most-recent first, persisted in SharedPreferences. Tap reopens
  the stored exact URL; long-press removes one entry;
  Settings → "Clear Recently Watched" wipes it. Only
  video-category launches are recorded.
- **Category pages** — scaffolded exactly by FMHY headers: each
  shelf header with its applet tiles (name, 2-line description,
  ★ for FMHY-bolded/recommended entries). Shelves over 9 tiles get
  a "Show all →" tile that opens the full shelf grid.
- **A. Favorites** — long-press any tile (or a browser link) to pin
  it; Favorites shelf appears on Home. Long-press detail sheet can
  remove it; Settings can clear all.
- **B. Global Search** — searches name + description + shelf across
  all categories (first 60 hits), results as applet tiles.
- **D. Live Refresh** — Settings → "Update FMHY Data" fetches the
  12 category files from `github.com/fmhy/edit` (`main` branch,
  `docs/*.md`) via
  `https://raw.githubusercontent.com/fmhy/edit/main/docs/<file>`,
  parses the markdown on-device (`##`/`###` shelves, first link in
  a bullet = primary tile, later links = mirrors, `**bold**` =
  recommended), caches to app-private storage, and reloads. Falls
  back to the bundled seed on failure (needs ≥6/12 files and a
  sane entry count before the cache is replaced).
- **E. Mirror Picker** — long-press a tile for its detail sheet:
  primary URL, every mirror URL, Add/Remove Favorite, Copy URL.
- **Safari-style browser** — toolbar with pill buttons, **Dark
  appearance by default** (v1.3, persisted Dark/Light setting in
  the gear sheet + Settings; Dark = `#1C1C1E` toolbar / `#2C2C2E`
  pills / white text, Light = the v1.1 `#F2F2F7` / white-pill look),
  centered pill **URL button** (it is not a text field: clicking it
  opens a URL editor dialog), ‹ › back/forward, reload, mouse/D-pad
  sub-mode toggle, **⚙ gear** (Browser Settings sheet: scale,
  ad blocking, JavaScript, desktop UA, cursor speed, clear
  cache/history), "✕ Done", page title bar underneath, thin
  progress bar, accent `#0A84FF`. JS + DOM storage on, zoom on,
  HTML5 fullscreen video supported. Back = exit fullscreen, then
  browser history back, then Done behavior.
- **Ad blocking + popup blocking** — `shouldInterceptRequest`
  returns an empty 204 for ~100 known ad/tracker/popup-network
  hosts (Fetch's ~50-host list expanded with pop/push/redirect
  networks); `onCreateWindow` popups are refused and multiple
  windows / JS window-open are disabled in WebSettings. Toggle in
  Settings.
- **Navigation modes (v1.1 rework)** — **D-pad/native focus
  everywhere outside the browser.** The mouse cursor exists ONLY
  inside the browser (Fetch-style overlay: D-pad moves, center =
  click, hold center ≈0.5 s = long-press, edge-push scrolls the
  page; Shift = fine movement; cursor speed in Settings/gear).
  The browser keeps two sub-modes — mouse (default) and D-pad —
  toggled by the remote **Menu key** (in the browser), the
  Settings row, or the browser toolbar button; persisted. In
  browser D-pad mode, arrows scroll the page, ▲ at the very top
  moves focus into the toolbar (‹ ›, URL pill, reload, mode, ⚙,
  Done), ▼ returns to the page, and OK taps the page / activates
  the focused button. **All D-pad and media keys are intercepted
  in `dispatchKeyEvent` while the browser is showing and never
  reach the web page**, so site video players can't mistake
  Left/Right for seek or Up/Down for volume.

## Bundled seed catalog

`src/main/assets/catalog.txt`, pipe-delimited:
`categoryId|shelf|name|url|description|recommended|mirrors`
(mirrors comma-separated). **193 entries**: video 29, ai 28,
reading 23, gaming 19, audio 18, linux-macos 16, downloading 15,
educational 15, torrenting 14, misc 14, mobile 1, non-english 1.
Video/AI/reading entries are taken from FMHY pages as rendered on
Oct 26, 2026. The mobile and non-english seed tiles open that
category's fmhy.net page and note that Settings → Update loads the
full list. Live Refresh (D) populates the full ~8–12k-entry
catalog; the seed is a starter + offline fallback.

## Target device / build

- Fire TV Stick, Fire OS 6.7.1.1 = Android 7.1.1, **API 25**:
  `minSdkVersion 25`, `targetSdkVersion 25`, framework-only Java
  (no AndroidX/Leanback), single `MainActivity`.
- LAUNCHER + LEANBACK_LAUNCHER, generated 320×180 TV banner +
  launcher icons (blue/violet gradient, white play glyph).
- Package `com.heckyeah.tv`, version 1.18 (versionCode 19).
  `android:hardwareAccelerated="true"` on app + activity (v1.1).
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE` only.

Manual SDK-tools build (Gradle daemon can't run in the sandbox):
aapt2 → javac (source/target 8, android.jar on `-cp`, NOT
`-bootclasspath`) → d8 `--min-api 25` → zipalign → apksigner.
Keystore: project `keystore.jks` (not tracked). Signing
passwords are supplied through `KEYSTORE_PASS` / `KEY_PASS`;
if the keystore is absent, `build.sh` generates a local
development keystore.

```bash
export KEYSTORE_PASS='change-me'
export KEY_PASS="$KEYSTORE_PASS"
./build.sh   # → build/manual/apk/HeckYeah.apk
```

## v1.18 (Oct 28, 2026) — package rename + public repo prep

- **Package renamed** to `com.heckyeah.tv` (previously a personal
  namespace). Android treats this as a different app: it installs
  fresh, alongside any earlier build, and does not inherit the
  earlier app's data.
- **Personal references scrubbed** from code comments and docs;
  field reports are described in neutral terms throughout.
- **build.sh hardened:** signing passwords come from the
  `KEYSTORE_PASS` / `KEY_PASS` environment variables (no
  credentials in the repository); a missing keystore is generated
  as a local development keystore; JDK and SDK locations come from
  `JAVA_HOME` / `ANDROID_HOME` with local fallbacks.
- **Repo prep:** GitHub front section (top of this file), MIT
  `LICENSE`, and a `.gitignore` that keeps `keystore.jks`, APK
  files, and `build/` out of version control.
- Includes the v1.17 cursor-fade fix unchanged
  (`alive version=1.18 code=19`).

Build verification:

- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **19** /
  versionName **1.18**, sdk 25 / target 25, permissions
  INTERNET + ACCESS_NETWORK_STATE.
- APK: 562,344 bytes, sha256
  `654de1abf1807470459b6cb9172006c2964626d0bcd42b11764bd522127f0f33`.
- `apksigner verify`: OK; signer certificate SHA-256
  `220529f9…b92fab5` (unchanged signing key).
- `unzip -t`: no errors.

## v1.17 (Oct 28, 2026) — cursor fade works in pseudo-fullscreen too

v1.16 CONFIRMED WORKING on a Fire TV Stick (Oct 28): the
problem site's video now fullscreens properly and fills
the screen — via the pseudo-FS path (header force-hide +
scroll freeze; `fsView` stays null the whole time). New
bug from that same session: **in fullscreen the mouse
cursor no longer fades away.**

Root cause: the v1.3 cursor fade was real-FS-only.
`pokeFsCursor()` scheduled `fsFadeRunnable` only
`if (fsView != null && browserMouse)`, the runnable
itself re-checked `fsView != null`, and `pokeFsCursor`
was only ever called from `showFullscreen` /
`handleFullscreenKey`. In pseudo-FS none of that ever
runs, so the regular browser cursor sat at alpha 1 over
the video forever.

Fix — the fade is generalized to "fullscreen-like" state:

- New `fsLikeActive()` = `fsView != null || pseudoFs`
  (raw field: while pseudo is active the ~750ms poll
  keeps it current, every pseudo exit path below cancels
  the fade explicitly, and the runnable re-checks
  `fsLikeActive()` at fire time regardless).
- `fsFadeRunnable` fades when
  `fsLikeActive() && browserMouse && cursorView != null`;
  `pokeFsCursor()` schedules the 10s fade under the same
  condition (restore-alpha-first behavior unchanged).
- Interaction resets in pseudo: `moveCursor()` now calls
  `pokeFsCursor()` on every move (also covers real-FS
  moves, which already poked via `handleFullscreenKey` —
  a duplicate poke just re-arms the timer), and the
  browser mouse-mode CENTER tap path pokes when
  `fsLikeActive()` (that's the tap that toggles play in
  pseudo-FS).
- Arming: the pseudofs ON transition (poll callback,
  next to `hideHeader()`) calls `pokeFsCursor()`, so the
  10s count starts at fullscreen entry even if the user
  never touches a key.
- Cleanup: the pseudofs OFF transition calls
  `cancelFsFade()` (restore alpha, kill the timer), and
  `closeBrowser()` now calls `cancelFsFade()` too — a
  pseudo-FS exit via Done never reaches `hideFullscreen`
  (`fsView` is null), so without it a faded cursor / live
  timer could leak out of the browser. (`hideFullscreen`
  already cancelled for the real-FS path.)
- Regression guard: normal browsing is untouched —
  `fsLikeActive()` is false, `pokeFsCursor()` schedules
  nothing and touches no alpha (its animate-cancel is
  still guarded by `cursorFaded`), so the cursor NEVER
  fades outside fullscreen-like state.
- The `alive` proof line now reads
  `alive version=1.17 code=18`.

## v1.17 verification (Oct 28, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8,
  android.jar on `-cp`; d8 `--min-api 25`; zipalign;
  apksigner).
- Copied to `HeckYeah-1.17.apk`: **562,344 bytes**, sha256
  `a777c23fb2b2b97d6ac305175eb321282598259729a5337e19f398398d59ebe6`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode
  **18** / versionName **1.17**, sdkVersion 25 /
  targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r`
  preserves app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick, NOT uploaded.** On-device check: enter the same
  pseudo-fullscreen as v1.16 and don't touch anything —
  the cursor should fade ~10s after entry; any D-pad
  move or center tap should bring it back instantly and
  restart the 10s count; exiting fullscreen should leave
  the cursor solid; and in normal browsing the cursor
  must never fade.

## v1.16 (Oct 28, 2026) — app-start proof + pseudo-fullscreen detection/handling

Latest field evidence (video, Oct 28): the user taps the player's
fullscreen control; the browser chrome disappears, but the
video renders as a SMALLER CENTERED BOX that does not fill
the TV — and `adb logcat -d -s HeckYeahFS` STILL shows
nothing. Two live hypotheses: (H1) the running build is
pre-v1.14 (install unconfirmed), or the `-s` filter is
failing him (his reliable past workflow was
`logcat -d | grep <tag>`); (H2) this site's "fullscreen"
is PSEUDO-fullscreen inside the page — the player makes its
own container cover the viewport — so
`WebChromeClient.onShowCustomView` never fires: no FS
handler, no guard, no logs, and the page's own sizing
decides the box. v1.16 kills the H1 ambiguity forever and
detects/handles H2. Everything else (v1.15 clamps, real-FS
freeze/guard/logging, scrolllock ON/OFF logs) is unchanged.

**1) App-start proof of life:** `onCreate` now logs
`Log.d("HeckYeahFS", "alive version=1.16 code=17")` as its
first statement after `super.onCreate`. Every launch of
this build leaves exactly one unmistakable line; an empty
dump after launching the app can only mean this build is
not the one running (or the buffer was cleared after the
fact).

**2) Pseudo-FS detection** (the v1.15 ~750 ms poll is
extended; everything it already returned is kept). New
poll-JS fields, top document only for `fe`
(`fullscreenElement` is a top-document concept):

- `fe` — `document.fullscreenElement ||
  document.webkitFullscreenElement` ? its `tagName` : `""`.
- `vl/vt/vr/vb` — the winning (largest playing) video's
  rect in CSS VIEWPORT coords, i.e. the composed rect
  BEFORE the page-scroll offset is added (the v1.15
  `l/t/r/b` page rect is still returned too).
- `ps` — 1 when that viewport rect covers >= 90% of the
  viewport width AND >= 90% of the viewport height.

Java side: `pseudoFs = videoFound && fsView == null &&
ps == 1`, re-evaluated every poll, with `pseudoFsAt`
refreshed while it holds (fresh = < 2 s). Transitions log
under HeckYeahFS: `pseudofs ON rect=[l,t,r,b] fe=<tag|none>`
(rect = CSS viewport coords) and `pseudofs OFF`.
Separately, if `fe` is non-empty while `fsView == null`,
the poll logs ONCE per page (flag reset in
`onPageStarted`): `inpage-fs fe=<tag> (no custom view
handed to app)` — the smoking gun for a fullscreen the app
never received. `clearVideoData()` (navigation / real-FS
entry / Done) resets the pseudo state silently, mirroring
how it already resets `videoFound`.

**3) Pseudo-FS handling** while `pseudoFs` is active and
fresh:

- `doScrollBy` is a no-op — the same hard freeze as real
  fullscreen, sharing the early-return block at its head:
  the video already covers the viewport, so any page
  scroll can only shift the page under/around it.
- On the ON transition only, the browser header is forced
  out of the way through the existing header-hide path
  (`hideHeader()`), so the WebView gets the full window
  height — the pseudo overlay can only fill the WebView's
  own viewport, and a visible header shrinks exactly that.
  (Codebase note: `showHeader(false)` RESTORES/shows the
  bar in this code; `hideHeader()` is the hide path.) On
  OFF, nothing special — the v1.4 auto-hide logic resumes
  naturally.
- Deliberately NOT in this build: JS `requestFullscreen`
  promotion of the pseudo overlay into a real fullscreen.

**Expected HeckYeahFS lines with v1.16 installed** (this
is the H1 kill-shot — every case MUST produce lines):

- **App launch:** `alive version=1.16 code=17` (exactly
  once per launch).
- **Plain inline play:** `scrolllock ON rect=[l,t,r,b]
  viewport=<w>x<h> doc=<w>x<h>` when the poll first finds
  the playing video; `scrolllock OFF` when a later poll
  sees it stop (pause/end) while staying in the browser.
- **Real fullscreen tap:** the v1.14/v1.15 lines —
  `[attach] check … fixed=<n>` (plus any correction-detail
  line), `enter viewClass=… container=[…] view=[…]
  real=<WxH> window=<WxH> orientation=landscape
  scroll=<x>,<y>`, `[post]/[global]/[layout]/[focus]/
  [delay150]/[delay400]/[delay900] check …` summaries
  (identical clean `global` repeats are suppressed),
  `[jsprobe enter] …`, `[jsprobe delay400] …`,
  `[jsprobe delay900] …`, and `exit container=[…]
  view=[…]` on leaving fullscreen. No `pseudofs` lines:
  the poll does not run during a real FS session.
- **Pseudo fullscreen tap:** NO `enter`/`exit`/enforcer
  lines (no custom view is ever handed to the app).
  Instead: `pseudofs ON rect=[l,t,r,b] fe=<tag|none>` on
  the transition (`scrolllock ON …` too, if that same poll
  is also the first to find the video playing), plus
  `inpage-fs fe=<tag> (no custom view handed to app)` if
  the page actually set `fullscreenElement`; then
  `pseudofs OFF` (and `scrolllock OFF` if playback also
  stopped) when the overlay/video shrinks or stops.

## v1.16 verification (Oct 28, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8,
  android.jar on `-cp`; d8 `--min-api 25`; zipalign;
  apksigner).
- Copied to `HeckYeah-1.16.apk`: **562,344 bytes**, sha256
  `9c37b48ee519bfa79e04bcd4aa07ae2eeaab028aa71226c97e8afa90708b9ea5`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode
  **17** / versionName **1.16**, sdkVersion 25 /
  targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r`
  preserves app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick, NOT uploaded.** On-device checks: (1) just launch
  the app and dump `HeckYeahFS` — the `alive` line must be
  there; if not, this build is not running, full stop.
  (2) Play a video inline: expect `scrolllock ON …`.
  (3) Tap the problem site's fullscreen control: if the
  dump gains `pseudofs ON …`, it was H2 (pseudo-FS) — the
  header should now hide and scrolling should freeze for
  as long as the video covers the viewport; if instead the
  `enter …` + check lines appear, it was a real fullscreen
  all along and the guard data applies.

## v1.15 (Oct 28, 2026) — video-bounded scroll area + fullscreen scroll freeze

On-device report on v1.13/v1.14: (1) `adb logcat -d -s
HeckYeahFS` STILL returned nothing (install/buffer-order question; this build's
logs make sessions provable either way, see D); (2) inline, a
playing video could still be scrolled around and moved
before fullscreen — v1.13's poll only queried the TOP
document, so players living in same-origin iframes were
never detected, and where detection DID fire the binary lock
was the wrong tool anyway; (3) in fullscreen the video
appeared to shift toward the lower left. His words: "The
browser when video is playing needs to better determine the
max scrollable area and have the full screen not move to the
lower left."

**A) Video-bounded scroll area** (replaces the v1.13 binary
lock's internals; the `video_scroll_lock` setting and the
once-per-page Toast concept carry over):

- `VIDEO_POLL_JS` now returns a JSON object (via
  `JSON.stringify`; parsed in Java with `org.json` after
  unwrapping the quoted string `evaluateJavascript` hands
  back). It scans the top document AND same-origin iframes
  recursively — per-frame try/catch, cross-origin frames
  skipped — accumulating iframe element rect offsets so the
  video rect is in TOP-page CSS px. A video counts as
  playing when `!paused && !ended && (readyState >= 2 ||
  currentTime > 0)`; size bar `width>=160 && height>=90`
  with a substantial viewport intersection (visible area
  >= 50% of the video's area, or >= 50% of the viewport);
  the LARGEST such video wins. The payload always carries
  `iw/ih` (viewport CSS size), `dw/dh` (document scroll
  size, max of `documentElement`/`scrollingElement`), and
  `sx/sy` (current scroll); with a video, its PAGE rect
  (client rect + window scroll at poll time).
- All scrolling is centralized in `doScrollBy(int, int)`.
  Every former `gateScroll()` + `webView.scrollBy/pageUp/
  pageDown` site (mouse keys, D-pad keys, PgUp/PgDn/FF/Rew,
  mouse edge transit, the v1.11 top-edge rule) now calls it;
  page keys use `doScrollBy(0, ∓(webView.getHeight() -
  dp(48)))` via `pageScrollStep()`. Grep proof: the only
  direct `webView.scrollBy` calls left in the file are the
  two inside `doScrollBy` itself, and the only direct
  `webView.scrollTo` is the fullscreen restore in (B).
  `gateScroll()`/`videoScrollLocked()` are gone.
- Clamp math (physical px deltas converted with
  `webView.getScale()`, targets computed in CSS px):
  document clamp ALWAYS (when poll data is < 2 s old) —
  target in `[0, max(0, docW-innerW)] x [0, max(0,
  docH-innerH)]` CSS px, i.e. the page's true max scrollable
  area rather than WebView overscroll slack. Video clamp
  ADDITIONALLY (video found + setting ON) — target CSS
  scroll must stay in `[vidR-innerW, vidL] x [vidB-innerH,
  vidT]` with endpoints normalized: for a video smaller
  than the viewport that is exactly the range keeping it
  fully in view; for one larger, the range where it still
  covers the viewport; the result is intersected with the
  document range. The first VIDEO-clamp truncation per page
  shows the v1.13 hint Toast once (`videoHintShown`, reset
  in `onPageStarted`). Cursor movement itself is untouched.
  One refinement in `moveCursor`: the top-transit scroll
  only marks `transitScrolledUp` if the page's scrollY
  actually changed, so a clamped-to-zero transit no longer
  suppresses the v1.11 top-edge rule on the same move.
- Stale/absent poll data: `doScrollBy` falls back to a
  plain unclamped `scrollBy` — pre-v1.15 behavior. Browsing
  never freezes because detection failed.

**B) Fullscreen scroll freeze + anchoring:**

- `showFullscreen` snapshots `fsSavedScrollX/Y` from the
  WebView BEFORE attaching anything; the v1.14 `enter` log
  line gains `scroll=<x>,<y>`.
- `enforceFullscreenGeometry` restores that scroll whenever
  it finds drift: `webView.scrollTo(saved, saved)`, counted
  as a correction (`fsPassFixCount++`) with detail
  `webscroll [x,y] -> [sx,sy]; ` in the HeckYeahFS line. A
  page scrolled under the fullscreen video (player JS is
  the usual suspect) is also a prime suspect for the video
  LOOKING shifted toward the lower left — the page moves,
  the surface compositing follows it.
- While `fsView != null`, `doScrollBy` is a no-op:
  fullscreen never scrolls the page through app input.
- Every place the enforcer (re)adds `fsContainer` to the
  decorView or resets its `LayoutParams` now uses
  `FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)`
  with `gravity = Gravity.FILL`, and the params check flags
  non-FILL gravity as bad. (The custom view keeps its
  CENTER gravity; FILL is container-only.) The initial
  attach in `onCreate` uses FILL too, so the first enforcer
  pass doesn't spend a correction on it.
- `fsJsProbe("enter")` fires once right after attach, in
  addition to delay400/900 — the fullscreen element's rect
  at t=0 is now captured.
- `hideFullscreen` leaves the page scroll at the saved
  values (it never moved).

**C) Setting semantics unchanged:** `video_scroll_lock`
default ON; OFF disables ONLY the video clamp — the
document clamp still applies (that's just correct bounds).
Settings labels untouched.

**D) Provable logging:** poll detection flips log under
HeckYeahFS — `scrolllock ON rect=[l,t,r,b] viewport=<w>x<h>
doc=<w>x<h>` on false->true, `scrolllock OFF` on
true->false. Together with the v1.14 FS enter/check/probe/
exit lines, an empty dump from a session that browsed a
playing video and went fullscreen now unambiguously means
this build is not the one running.

## v1.15 verification (Oct 28, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8,
  android.jar on `-cp`; d8 `--min-api 25`; zipalign;
  apksigner).
- Copied to `HeckYeah-1.15.apk`: **562,344 bytes**, sha256
  `9c0a83fdee65db367087f2fa5d3d4c08da345d3f24732c2936d09d3c6bd69998`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode
  **16** / versionName **1.15**, sdkVersion 25 /
  targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r`
  preserves app data).
- `unzip -t`: no errors.
- Scroll routing grep-verified (see above); zero leftover
  references to `videoLockActive`/`gateScroll`/
  `videoScrollLocked`.
- Build-verified only — **NOT yet tested on the physical
  Stick, NOT uploaded.** On-device checks: (1) play a video
  inline (ideally an iframe-embedded player) and push the
  mouse/D-pad — the page should stop at the exact point the
  video would start leaving the screen, in all four
  directions; pause it and full document scrolling returns
  within ~1 s. (2) `adb logcat -d -s HeckYeahFS` should now
  show `scrolllock ON …` for that session. (3) Go
  fullscreen: the video should stay anchored; the dump's
  `enter` line shows `scroll=<x>,<y>`, and any
  `webscroll [x,y] -> [sx,sy]` correction line names a
  drift the guard caught.

## v1.14 (Oct 28, 2026) — DIAGNOSTIC: HeckYeahFS speaks at every fullscreen checkpoint

A field test ran `adb logcat -d -s HeckYeahFS` after a fullscreen
session and got NOTHING. Code inspection confirmed why: the
whole app had exactly ONE `Log` call (inside
`enforceFullscreenGeometry`), fired ONLY when a geometry
deviation was actually corrected. Silence was therefore
ambiguous — guard satisfied vs guard never ran vs log buffer
rotated — so the v1.8 diagnostic design was too quiet. v1.14
changes NO behavior except logging; all of it is
`Log.d("HeckYeahFS", …)`:

1. **FS ENTER** (`showFullscreen`, right after the "attach"
   enforcer pass): `enter viewClass=<fqcn> container=[l,t,r,b]
   view=[l,t,r,b] real=<WxH> window=<WxH> orientation=landscape`
   — custom view class name, container/view laid bounds, real
   display metrics (`getRealMetrics`), decorView size, and the
   requested orientation. Bounds are as-laid at attach time;
   the later passes show the settled values.
2. **Every enforcer pass** now logs a summary line even when
   clean: `[<trigger>] check container=[l,t,r,b] view=[l,t,r,b]
   surface=<class|none> holderFixed=<WxH|none> fixed=<n>`,
   where `fixed` counts the corrections applied in that pass
   (new per-pass counter incremented at all 7 correction sites:
   3 container + 4 in `enforceFsTree`). When corrections WERE
   made, the pre-existing detail line (`[<trigger>] <view>
   bounds=[...] -> [0,0,W,H] full-bleed; ...`) still logs
   first, unchanged. Volume control: triggers are only the
   ones that already existed (attach, post, global, layout,
   focus, delay150/400/900) — no polling loop added. The
   chatty "global" trigger suppresses a CLEAN summary when it
   is identical to the previous global clean summary (the key
   resets on any correction, any other trigger's summary, and
   each new fullscreen session); correction lines always log.
3. **FS EXIT** (`hideFullscreen`, just before the custom view
   is detached): `exit container=[l,t,r,b] view=[l,t,r,b]`.
4. **JS VIDEO PROBE** at the +400 ms and +900 ms checkpoints
   (piggybacked on the existing `fsDelay400`/`fsDelay900`
   runnables — no new timers): `[jsprobe delay400] inner=<W>x<H>
   fs=<tag|none> rect=[l,t,r,b] video=<videoWidth>x<videoHeight>`
   — the largest `<video>` element's `getBoundingClientRect()`
   (CSS px), its intrinsic size, whether
   `document.fullscreenElement` exists and its tag, and
   `window.innerWidth/innerHeight`. `video=none` when the page
   has no `<video>`. Fully guarded try/catch; a failed or
   empty eval logs nothing.

Helpers added (all logging-only): `fsBounds(View)`,
`findFsSurface(View)`, `fsJsProbe(String)`, `FS_PROBE_JS`.
The ONLY behavioral delta vs v1.13 is the logging above —
geometry enforcement, scroll lock, and everything else are
unchanged in behavior.

## v1.14 verification (Oct 28, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.14.apk`: **562,344 bytes**, sha256
  `97bc605911af62b609e89d610df3995204f5fdbe04c8f54960dc30c5526c2c7f`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **15** /
  versionName **1.14**, sdkVersion 25 / targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device check: `adb logcat -c`, enter fullscreen
  on any video, exit, then `adb logcat -d -s HeckYeahFS`
  should now show the `enter` line, one `check` summary per
  checkpoint (plus detail lines for any corrections), the
  two `[jsprobe …]` lines, and the `exit` line — even for a
  perfectly behaved session.

## v1.13 (Oct 26, 2026) — scroll lock while a video plays inline

On-device report: with a video playing INLINE (before
the user presses the player's fullscreen button), the page is still
scrollable — moving the mouse toward the player's controls
(e.g. down toward the fullscreen button) edge-scrolls the
page and the video slides away; D-pad scrolling does the
same. On this device EVERY page scroll is app-driven (remote
only — no touch, no wheel events ever reach the page), so
the app can simply refuse to scroll while a video is playing.

**Detection:** while the browser is the current screen and
NOT in HTML5 fullscreen, a Handler loop polls every ~750 ms
via `evaluateJavascript` (kicked from `showScreen()` when the
browser pane shows; the loop self-terminates at its next
cycle once another screen is current, so Done never leaks
it). The poll is skipped while a browser dialog is open
(`browserDialogDepth > 0`) or the browser is suspended, and
fullscreen forces the state off. The script returns "1" if
ANY `<video>` is `!paused && !ended && readyState > 2` with
a `getBoundingClientRect()` of at least 200×100 substantially
inside the viewport (the size bar means small/hidden
autoplay widgets don't count; a big visible playing ad DOES
count, briefly, by design — pausing/ending unlocks within
one poll cycle). State resets in `onPageStarted`, on Done,
and on entering fullscreen.

**Gating:** every app-initiated page scroll passes one
helper, `gateScroll()`: locked = setting ON (prefs
`video_scroll_lock`, default ON) AND `videoLockActive` fresh
(< 2 s). Gated call sites (grep-verified complete — these
are ALL the `webView.scrollBy/pageUp/pageDown` sites in the
file): mouse-mode edge scrolling in `moveCursor()` (all
four edges incl. the v1.11 top-edge rule), mouse-mode
PAGE_UP/PAGE_DOWN + FF/REW keys, D-pad-mode arrow scrolling
(all four directions), and D-pad-mode PAGE_UP/PAGE_DOWN +
FF/REW keys. Cursor MOVEMENT is never gated — only scroll
actions. A refused scroll shows a Toast — "Video playing —
scrolling locked (pause to scroll)" — at most once per
navigation (the shown flag resets in `onPageStarted`);
further refusals are silent. Non-goals kept: the page's own
JS/internal scrolling is untouched, and nothing is gated in
HTML5 fullscreen.

**Setting:** "Video scroll lock: ON/OFF" in the gear sheet
(⚙ Browser Settings, below Cursor speed) AND "Lock
scrolling while a video plays (browser)" in the main
Settings browser section — both write the same prefs key
and take effect immediately, no reload.

## v1.13 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.13.apk`: **558,248 bytes**, sha256
  `24a507e527ac39e41f0d08bc6cd9c900ada348ba95a199cca2e5551355c84733`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **14** /
  versionName **1.13**, sdkVersion 25 / targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device check: play a video inline (not
  fullscreen) and try to scroll with mouse edges and D-pad —
  the page should hold still and show the hint once; pause
  the video → scrolling frees within ~a second; go
  fullscreen → no lock at all. Toggle it off in ⚙ Browser
  Settings → scrolling is never gated.

## v1.12 (Oct 26, 2026) — RW screenshots use the resume mechanism; stale pre-v1.10 shots purged

Micro-build from a user question: "Can the screenshots of the
Recently watched be doing the same screenshot mechanism as the
resume preview?"

**Part 1 — parity (verified in source, no change needed):**
both screenshot consumers already capture exclusively through
the shared strict helper from v1.10 — `captureResumeShot()`
and `captureRwShot()` each call `captureViewportBitmap()`
(bitmap at the WebView's exact on-screen size,
`webView.draw()` on an untransformed canvas, downscale only
afterwards) and neither carries any draw/scale logic of its
own. A grep for draw/scale/canvas operations across the file
finds exactly one `webView.draw(...)`, inside the helper
itself. So RW tile screenshots and the resume preview have
been the same mechanism since v1.10 — identical framing, the
exact visible viewport at the current scroll position.

**Part 2 — purge the stale pre-v1.10 shots:** the old
(squeezed-capture) image files persist in app storage across
`adb install -r` updates, so tiles would keep DISPLAYING old
bad shots even though every new capture is correct. New
one-time migration `migrateShotsIfNeeded()` (modeled on the
v1.8 `enr_ver` migration, called in `onCreate` right after
`loadRw()` / `migrateEnrichmentIfNeeded()`): a new prefs key
`shot_ver` (current **2**); when below 2 it deletes every
file in `files/rw_shots/`, deletes `files/resume_shot.jpg`,
clears both in-memory decoded-shot caches (`shotCache`, and
the resume preview's stamp cache via `deleteResumeShot()`),
and stamps `shot_ver = 2`. RW records keep their shot
filename field — the files are simply gone, and both loaders
already treat a missing file as "no screenshot" (audited:
the RW tile loader's `decodeFile` returns null → shot view
stays GONE, poster shows instead when present; the resume
preview loader gates on `isFile()` and the expansion only
runs when a bitmap exists). No poster/enrichment data is
touched. Fresh shots re-capture by themselves through the
existing triggers (RW: next Done / debounced page capture;
resume: next Done).

## v1.12 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.12.apk`: **558,248 bytes**, sha256
  `9e2001c3e63b6548831d2f48a143fec973b841c5c955c5b77d49358ef6d94431`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **13** /
  versionName **1.12**, sdkVersion 25 / targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device check: after installing, existing RW
  tiles should show poster-or-nothing (old screenshots gone);
  watch something and hit Done → the RW tile and the resume
  preview should show fresh viewport-true shots.

## v1.11 (Oct 26, 2026) — top-edge push reveals the bar AND keeps scrolling

Single-issue fix from the v1.10 on-device test, in the
browser's **mouse mode** with the auto-hiding header: pushing
the cursor to the top of the screen revealed the nav bar (the
v1.4 behavior) but the page **stopped scrolling up** — "it
reveals the browser bar but does not scroll to the top of the
site when I am hitting the top of the screen. It needs to
scroll as well."

**Cause:** mouse-mode edge scrolling is driven per key-repeat
inside `moveCursor()`: holding UP fires repeated moves, and
the scroll-up only happens inside a check that requires the
cursor to be **inside the WebView's window rect** while it
crosses the WebView's top edge (`cursorY >= loc[1]` and
`cursorY + dy < loc[1]`). With the header hidden, the WebView
fills the screen, its top edge is y=0, and scrolling works —
until the cursor pins at y=0 and the v1.4 reveal block calls
`showHeader()`. The header taking its space back drops the
WebView's top edge to header height, so the cursor — still
parked at screen y=0, now over the bar — fails the
inside-the-page test on every subsequent repeat, and no
scroll ever fires again. The reveal didn't cancel a timer
(there is none — scrolling is per-repeat-event);
`showHeader()`/`hideHeader()` never touched the scroll
path. The reveal simply invalidated the geometry the
scroll gate depended on. The bottom edge never had this
problem because the WebView reaches the screen bottom in
both header states; the top is the only edge whose page
boundary moves when the header toggles.

**Fix (`moveCursor()` only):** after the cursor is clamped,
an UP push on the browser now also scrolls when the cursor
is **pinned to the screen top** (`cursorY <= 0`) or **parked
above the page's top edge** (over the revealed bar), unless
the in-page crossing check already scrolled on that same
move (no double scroll on the crossing step). The reveal
itself is unchanged and fires first in the same move, so
one continuous hold now does both in sequence: pin at the
top → bar reveals → page keeps scrolling up on every
further repeat until `scrollY == 0` (`scrollBy` no-ops at
the top). Because the new rule is keyed to the cursor
rather than page geometry, the header's show animation /
layout change cannot disarm it mid-hold, and it works
identically when the header was already visible. Stop
conditions mirror the existing edge-scroll exactly — it is
the same `moveCursor()` entry point: leaving the edge,
leaving mouse mode, a browser dialog opening, Done, and
fullscreen (`fsView != null`) all skip it as before. The
auto-hide hysteresis is untouched — scrolling up via the
edge naturally keeps the bar shown. Bottom/left/right
edge-push behavior and the D-pad sub-mode are unchanged.

## v1.11 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.11.apk`: **558,248 bytes** (same byte
  count as v1.9/v1.10 by coincidence; contents differ), sha256
  `1ef8bdb90df07c89fcb0e4d3f1666e3c2ef05ad3f19eeed89d68c62acb4c8804`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **12** /
  versionName **1.11**, sdkVersion 25 / targetSdkVersion 25,
  LAUNCHER + LEANBACK_LAUNCHER, banner present.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device check: in mouse mode, scroll down a long
  page until the bar auto-hides, then push the cursor to the
  top edge and keep holding — the bar should reappear and
  the page should keep scrolling up to the very top without
  lifting off or re-pushing; repeat with the bar already
  visible (park the cursor over the bar and hold UP) — same
  continuous scroll to the top.

## v1.10 (Oct 26, 2026) — viewport-true screenshots + Clear card to the far right

Two corrections from the v1.9 on-device test (with TV photo
of the resume preview). Everything else from v1.9 is unchanged.

### 1. Screenshots now capture the exact visible viewport

The resume hover preview showed a capture that was "a little
bugged": content from well past the visible area squeezed into
the frame — it did not match what was on screen. **Cause:**
both capture paths (the resume shot in `captureResumeShot()`
and the RW shots in `captureRwShot()`) created the bitmap at
the already-shrunk storage size and called `cv.scale()` on the
canvas *before* `webView.draw()`, so the WebView rasterized
its document through that shrink transform instead of the app
copying the visible viewport at native size — a re-render
squeezed through the transform, not a faithful copy of the
screen, framing included.

**Fix:** one shared strict helper, `captureViewportBitmap()`:

- The bitmap is the WebView's **exact on-screen pixel size**
  (`getWidth()` × `getHeight()` at capture time); a zero-size
  view skips the capture (the resume path keeps its
  delete-the-shot behavior for that case).
- `webView.draw()` runs on an **untransformed canvas** — no
  scale, no translate, no content-height sizing, no scrolling
  the page, no measure/layout tricks — so the bitmap is the
  visible region at its current scroll position, centered
  exactly as the user saw it.
- Only *after* capture is the finished bitmap resampled
  (`createScaledBitmap` via `downscaleToWidth()`, aspect
  preserved, never upscaled) to the storage widths — 640px
  for the resume shot, 480px for RW shots — where a resample
  cannot change the framing.

Capture timing is unchanged (resume shot at the top of
`closeBrowser()` while the browser is still visible; RW shots
at their usual Done/debounced points), and the honest limit is
unchanged: a last screen of protected/fullscreen video can
still capture black, because those surfaces never composite
into a view draw at any size.

### 2. Clear Recently Watched card moved to the far right

Correction to the v1.9 placement: the ✕ Clear card
sits at the **end** of the RW shelf, not the start. In
`buildHome()` the card is now added to the shelf row **after**
the entry loop, so the order is **[entry1 … entryN, Clear]**.
Nothing else about the card changes — same look, same
confirmation dialog, same factored `clearAllRw()` + Home
rebuild on confirm.

Focus consequences, verified in code: `firstRwTile` is still
captured from the entry loop, so Search DOWN still lands on
the first (leftmost) content entry (the v1.6 contract); a
native RIGHT-walk now traverses all entries and reaches Clear
**last**; the card keeps its `setNextFocusUpId(bSearch)`
symmetry; nothing else in the app referenced its old position.

## v1.10 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.10.apk`: **558,248 bytes** (same byte
  count as v1.9 by coincidence; contents differ), sha256
  `cc0fe7bfb743c1310c31c0ed45633b46a5903d04d4b604d4d15ff82ce71da7a0`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **11** /
  versionName **1.10**, sdkVersion 25 / targetSdkVersion 25,
  LAUNCHER + LEANBACK_LAUNCHER, banner present.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device checks: browse partway down a page, hit
  Done, hover the Back-to bar — the preview should be a
  faithful miniature of exactly what was on screen (same
  scroll position, nothing squeezed in from further down);
  the RW shelf should read entries first with the ✕ Clear
  card last on the right, reached by walking RIGHT past the
  final entry.

## v1.9 (Oct 26, 2026) — RW Clear card + resume hover preview

Two requested items; everything else from v1.8 is
unchanged.

### 1. "Clear" card at the left of the Recently Watched shelf

When the RW shelf is built on Home (rw list non-empty), the
FIRST tile in the shelf row — before the most-recent entry —
is a utility card: a large **✕** glyph (the text glyph in the
app's Inter fonts, not an emoji) in iOS red over a two-line
"Clear Recently Watched" label. It carries the same tile
chrome and focus behavior as an RW tile (focus border,
`focusIntoView()` scroll-into-view, translationZ lift, 1.03
scale) but a dim red-tinted fill (`#241417`, red-tinted
border) so it reads as a utility action, not content. It is
narrower than a content tile (170dp vs 300dp) with a 96dp
minimum height and vertical centering in the row to sit level
with the entries.

- **Click → confirmation dialog** ("Clear Recently Watched?" /
  "This removes all N entries, their screenshots and poster
  art." / **Clear** | **Cancel**), built with the app's dialog
  pattern (`trackDialog`). On confirm it runs the exact same
  wipe as Settings → "Clear Recently Watched": that lambda and
  this card now both call one factored `clearAllRw()` (entries
  + `rw_shots` files + poster files + enrichment sidecars +
  caches), then Home rebuilds — the shelf and the card vanish.
- **Focus wiring (the v1.6 contract survives):** the Search
  button's `nextFocusDownId` still points at the first
  `rwTile()` — the first content *entry*, NOT the Clear card —
  because `firstRwTile` is captured from the entry loop exactly
  as before. The card joins native Left/Right inside the row:
  LEFT from the first entry reaches it, RIGHT from it reaches
  the first entry; it also gets `setNextFocusUpId(bSearch)`
  for symmetry with the first entry. After a clear, the shelf
  is empty, so Search DOWN falls back to the first category
  tile, as wired in v1.6.

### 2. Resume ("‹ Back to …") hover preview

When the Back-to bar at the top of Home / category / search
gains focus, the **last screenshot of the page it will resume
to** expands open underneath it; when focus leaves, it
collapses. Poster art is deliberately NOT used here — the
request was for the last screen itself.

- **Capture (any browser session, not just video):** resume is
  browser-wide while RW shots are video-category-only, so the
  preview has its own file, `files/resume_shot.jpg`. At the
  top of `closeBrowser()` — the same Done moment whose
  finalize saves the new resume target via `updateResume()`,
  and while the browser is still on screen (the finalized
  path hides the pane, and an invisible WebView may draw
  blank) — the current WebView viewport is drawn with the
  same machinery as the RW shots, downscaled to ~640px wide,
  JPEG 75, overwriting the file every time. No current page
  (blank WebView) = no capture; the previous shot still pairs
  with the unchanged target. **Stale-pairing guard:** whenever
  `openEntry` / `openRw` replaces the resume target with a
  *different* URL, `resume_shot.jpg` is deleted on the spot —
  the fresh shot arrives at that session's Done — and
  Settings → "Clear saved browser page" deletes it too.
- **Display + animation:** `resumeBar()` now returns a
  vertical block **[bar] + [hint] + [preview area]**. The
  preview is an `ImageView` (480×270dp max, CENTER_CROP)
  starting GONE at height 0, loading `resume_shot.jpg` when it
  exists (decoded once, cached by file stamp). The bar's
  existing focus listener (focusIntoView + restyle + scale —
  extended, not replaced) drives it: focus gain → VISIBLE and
  a `ValueAnimator` grows the preview's `LayoutParams.height`
  0→270dp over ~200ms with a DecelerateInterpolator, alpha
  tracking the height fraction; focus loss → the reverse over
  ~150ms, then GONE. **Why height, not scaleY:** a
  scale/alpha-only reveal on a VISIBLE view leaves a
  full-height hole in the scroll content (rows below never
  move), while a GONE→VISIBLE height animation makes the
  content below genuinely shift down and back up — the
  expand/collapse feel — and composes cleanly with the parent
  ScrollViews. Rapid focus in/out cannot stick it: each
  transition bumps a generation counter, cancels the running
  animator, starts from the current height, and stale
  animators' end-actions check the generation before touching
  final state.
- **Scroll interaction:** none needed, by design. The bar is
  pinned at the very top of every screen it appears on and
  `focusIntoView()` on the bar runs at the same focus gain, so
  the preview unfolds downward into already-visible space and
  pushes the content below it down; the preview block's own
  container joins the v1.4 unclip chain so nothing clips the
  growing image. When focus moves on (collapsing), the target
  view was already chosen by the focus system, and its own
  focusIntoView scrolls it into place after the layout
  settles.
- **No shot = v1.8 behavior:** a resume target saved before
  v1.9, or a failed capture, simply never expands. Same
  honest limit as the RW shots: if the last screen was
  fullscreen video, the preview may be black.

## v1.9 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.9.apk`: **558,248 bytes**, sha256
  `309ba72f7c9e2d3bddaf511ad633be2feceb7db5b147cce9193a48fc1ff309bd`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **10** /
  versionName **1.9**, sdkVersion 25 / targetSdkVersion 25,
  LAUNCHER + LEANBACK_LAUNCHER, banner present.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device checks: the RW shelf opens with the ✕
  Clear card at its left edge; Search → DOWN still lands on
  the first RW *entry* (LEFT then reaches Clear); Clear asks
  for confirmation and empties the shelf; after any browser
  session + Done, hovering the Back-to bar expands the last
  screen's screenshot and moving focus away collapses it.

## v1.8 (Oct 26, 2026) — scrollable Settings, poster matching v2, fullscreen geometry guard

Three items from the v1.6/v1.7 on-device tests.

### 1. Settings page is scrollable (bug fix)

In v1.7 the Settings screen was a fixed `LinearLayout` pane: the
rows (browser mode, scale, appearance, ad blocking, cursor
speed, OMDb key + note, Update, status, Clear RW / saved page /
Favorites / cache, data-source note) ran past the bottom of the
TV and the lower rows could not be reached or selected. The
Settings column now lives inside a vertical `ScrollView` built
exactly like the other menu screens — an `unclip()`'ed
`ScrollView` wrapping a `pageColumn()` (same 40dp side / 18dp
top / 28dp bottom page padding, clipChildren/clipToPadding off
on the chain) — with the header (‹ Home + "Settings") inside the
scroll area, matching the Home/Category pattern. Every row is a
`safariBtn` using the shared focus styler, so focus gain runs
`focusIntoView()` and scrolls the row fully into view, down to
the last row. Entering Settings posts a focus request to the
first row's control (browser control mode); the header is one UP
away; Back exits to Home as before.

### 2. Poster matching v2 (chain rework)

v1.7's fuzzy iTunes/TVmaze scoring produced a false positive:
the "Clayface" RW entry (an upcoming movie with thin catalog
coverage) got artwork from an animated Batman series, because a
candidate whose name merely *contained* or *started with* the
title could win. The chain is now, in order:

1. **OMDb (key only) — unchanged.** Exact `&t=`/`&y=` lookups
   plus the `&s=` + `&i=` search fallback; still the only source
   of IMDb/RT scores.
2. **Wikipedia page image (new, no key).**
   `GET en.wikipedia.org/w/api.php?action=query&generator=search&gsrnamespace=0&gsrlimit=6&prop=pageimages&piprop=original|thumbnail&pithumbsize=600&format=json&gsrsearch=<q>`.
   Primary query: `intitle:"<T>" (film|series) <YEAR?>`; if it
   yields no accepted page, fallbacks `<T> film <YEAR>` and
   `<T> TV series`. A page is accepted ONLY when its title is
   exactly the cleaned title, `<T> (film)`, `<T> (<YEAR> film)`,
   `<T> (TV series)`, or `<T> (<...> TV series)`
   (normalize-compared); the first accepted page (by search
   rank) with a page image wins — `original.source`, else the
   thumbnail. Wikipedia film/series article images are the
   official poster in the overwhelming majority of cases.
3. **DuckDuckGo image search (new, no key; requested
   approach).** Unofficial two-step token flow, desktop browser
   User-Agent on every request: (1)
   `GET duckduckgo.com/?q=<query>&iax=images&ia=images`, vqd
   token extracted from the HTML by regex `vqd[="':]+([0-9-]+)`;
   (2) `GET i.duckduckgo.com/i.js?l=us-en&o=json&q=<query>&vqd=<token>&f=,,,&p=1`
   → JSON `results[]` with `image`, `width`, `height`, `title`.
   Queries in order: `"<T>" <YEAR> movie poster`, `<T> <YEAR>
   movie poster`, `<T> official poster`, `<T> poster`. A
   candidate must pass a declared-dimension pre-filter (portrait
   w/h 0.55–0.85, width ≥ 250) and its result title
   (normalized) must contain the cleaned title; candidates are
   then downloaded (max 6 across all queries) and must pass on
   their ACTUAL decoded dimensions (portrait 0.55–0.85, width
   ≥ 250). First passing image wins. Best-effort only: DDG can
   bot-challenge or change this endpoint at any time; every
   failure degrades silently to the next step.
4. **iTunes + TVmaze — demoted to last resort, STRICT.**
   Exact normalized-title equality only (the v1.7 contains /
   startsWith wins are gone — that scoring is what let the
   Batman series in); a known year off by more than 1 is a
   reject; iTunes `kind == "tv-episode"` results are rejects.
   Nothing passing = `miss` → the tile shows its screenshot.

- **Migration (`enr_ver` = 2):** posters fetched by the v1
  matcher may be wrong on devices already running v1.7, so the
  first launch under v1.8 clears every stored poster file +
  lookup state (poster/state/ts) and each current RW entry is
  re-looked-up with the new chain. Stored imdb/rt values are
  kept — they only ever came from OMDb exact lookups.
- **Manual correction:** the RW tile long-press dialog is now a
  choice list — **↻ Re-fetch poster** (deletes the entry's
  poster + sidecar and kicks a fresh lookup; Home rebuilds when
  it lands, per the existing completion rules), **✕ Remove
  poster art** (deletes the poster file and sets sidecar
  `poster_blocked=1`; auto-lookup — including the
  OMDb-key-saved forced pass — skips blocked entries until a
  Re-fetch clears the flag; the tile falls back to its
  screenshot), **Remove entry** and **Cancel**, as before.

*Clayface walkthrough (no year extracted, upcoming film):*
OMDb is skipped without a key (with a key, its exact lookups
either find the real film or nothing). Wikipedia actually HAS a
"Clayface (film)" article and it passes the title rule — but as
of this build the article exposes no page image through the
API, so the chain moves on. DDG then searches `"Clayface" movie
poster`: only results whose titles name Clayface and whose
downloaded bytes are true portrait posters qualify — an
animated-series key art titled "Batman: The Animated Series"
fails the title-contains test, and series posters naming
Clayface fail portrait/title checks in practice; if DDG is
challenged or nothing qualifies, strict iTunes/TVmaze require
the candidate name to be exactly "Clayface", so the Batman
artwork that won under v1.7's loose scoring is now a reject by
construction — worst case is `miss` → screenshot, never the
wrong poster. And if a wrong image ever does slip through,
Re-fetch / Remove poster art fix it from the tile itself.

### 3. Fullscreen geometry guard (continuous, not one-shot)

Fullscreen video still occasionally shifted off-center after
v1.4's fix. The one-shot pass could be undone when Chromium (or
the player page) re-laid-out the custom view / reset the
surface afterward. v1.8 enforces the geometry continuously
while fullscreen is active; every trigger funnels into ONE
idempotent `enforceFullscreenGeometry()` (a re-entrancy flag
prevents listener loops; it touches nothing when geometry
already conforms):

- **Triggers:** entry ("attach" + a posted "post"), an
  `OnGlobalLayoutListener` on the fullscreen container
  ("global"), an `OnLayoutChangeListener` on the custom view
  itself ("layout"), window-focus regain ("focus"), and
  scheduled re-asserts at +150/+400/+900 ms after entry
  ("delay150/400/900") for slow immersive transitions.
  Listeners and pending runnables are removed on exit — nothing
  leaks into the next session.
- **Each pass asserts:** the container is a MATCH_PARENT child
  of the decorView above the app content with no margins or
  transforms (the cursor overlay stays above it); the custom
  view has fresh `FrameLayout.LayoutParams(MATCH_PARENT,
  MATCH_PARENT, gravity CENTER)`, zero margins/padding and
  reset translation/rotation/scale, and — if its laid bounds
  still differ from the container's — is laid out directly at
  (0, 0, containerW, containerH). RECURSIVELY over all
  descendants: ViewGroup children get MATCH_PARENT params (of
  their own params' runtime type, gravity CENTER for
  FrameLayout params) with zero margins; every SurfaceView
  additionally gets `getHolder().setFixedSize(realW, realH)`
  from `getRealMetrics` (re-asserted only when the view/size
  changed, so the surface is not churned); every TextureView
  gets MATCH_PARENT.
- **Orientation is locked** to landscape on entry
  (`SCREEN_ORIENTATION_LANDSCAPE`) and the previous requested
  orientation is restored on exit — portrait-frame layout races
  are gone.
- **Order fix:** the window flags + immersive system UI are now
  applied BEFORE the custom view is attached, so its first
  layout pass happens at the final window size (v1.4 attached
  first, flagged after).
- **Diagnostics:** a pass that actually CORRECTS a deviation
  logs `Log.d("HeckYeahFS", "[<trigger>] <view> bounds=[...]
  params=... -> [0,0,W,H] full-bleed; ...")` — nothing is logged
  when geometry is already correct. If a shifted screenshot
  ever appears again, pair it with `adb logcat -s HeckYeahFS`.

## v1.8 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.8.apk`: **554,152 bytes**, sha256
  `27604996b71050b6d64db9c7bbfd39e933acba82d613551443929b28cc6eca29`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **9** /
  versionName **1.8**, sdkVersion 25 / targetSdkVersion 25,
  LAUNCHER + LEANBACK_LAUNCHER, banner present.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Endpoint checks from the build sandbox: the Wikipedia
  generator=search query returns "Clayface (film)" as an
  accepted title (it currently exposes no page image, so the
  chain falls through as designed); the DDG html page yields a
  vqd token with the shipped regex (the sandbox's datacenter IP
  gets a bot challenge on the i.js step — the flow is
  best-effort and degrades silently; a residential Stick
  connection is the real test). The Wikipedia acceptance rule
  and the vqd regex were additionally unit-checked on a desktop
  JVM against the shipped logic (10 title cases + token
  extraction — all as designed).
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device checks: Settings scrolls to the last row
  and every row is selectable; the Clayface RW tile re-fetches
  under chain v2 on first Home build (wrong Batman art is wiped
  by the migration); fullscreen stays centered, and any
  correction shows in `adb logcat -s HeckYeahFS`.

## v1.7 (Oct 26, 2026) — RW posters + IMDb/RT scores replace the (usually black) screenshots

Field report on v1.3–v1.6: the Recently Watched screenshot is black most
of the time (the known protected-surface limit), so detect the
movie/show from the RW title, pull a real poster from an online
source, and show IMDb + Rotten Tomatoes scores when possible.
The screenshot stays as the fallback when no poster is found.

- **Title cleaning** — `cleanTitle(pageTitle, url)`: cut at the
  first of ` | ` / ` – ` / ` — ` / ` - `; strip leading junk words
  repeatedly (`Watch`, `Watch Free`, `Stream`, `Watch Online`);
  remove junk phrases anywhere (`Full Movie`, `Full Episode`,
  `Online Free`, `Watch Online`, `Free Online`, `HD`,
  `Movie Online`); extract a year (first `(YYYY)` group, else
  first bare `YYYY` token — extracted and removed); cut at
  episode markers (`Season N`, `S01E01`-style, `Episode N`). If
  <2 chars remain, no lookup. Examples, verified on the JVM
  against the shipped code: `"Project Hail Mary | Movy"` →
  title `Project Hail Mary`, year null;
  `"Watch Project Hail Mary (2026) Full Movie Online Free |
  Fmovies"` → `Project Hail Mary`, 2026;
  `"The Pitt Season 2 Episode 5 | Movy"` → `The Pitt`.
- **Sidecar state (RW record format unchanged)** — prefs keys
  `enr_<urlhash>_{poster,imdb,rt,state,ts}` where `<urlhash>` is
  the same hex hash used for `rw_shots` filenames; poster bytes
  at `files/posters/<urlhash>.jpg`. Entry removal (tile
  long-press), Clear Recently Watched, and cap-12 eviction
  delete the poster file + sidecar keys, next to the existing
  screenshot deletion; if a live session's URL changes to a
  different page, the old URL's enrichment is deleted so a
  poster can never follow the session to the wrong page.
- **Lookup chain** (background thread, one in flight per entry,
  8 s connect/read timeouts, every failure degrades silently):
  1. **OMDb — only when a key is configured:**
     `GET omdbapi.com/?apikey=<key>&t=<title>[&y=<year>]`; on
     `Response=False` retry without the year, then `&s=<title>`
     search — take the result whose Title normalizes closest
     (strip non-alnum, case-insensitive; exact > startsWith >
     contains) and fetch the full record by `&i=<imdbID>`
     (search hits carry no ratings). From a hit: `Poster` (skip
     `N/A`), `imdbRating`, and the `Ratings[]` entry with
     Source `Rotten Tomatoes`.
  2. **iTunes Search API (no key):**
     `itunes.apple.com/search?term=<title>&media=movie&limit=8`,
     then `&media=tvShow&limit=8`. Candidates scored by the same
     normalized title match, preferring release year within 1
     of the extracted year; `artworkUrl100` is upgraded
     `100x100bb.jpg` → `600x600bb.jpg` and downloaded.
  3. **TVmaze (no key, shows):**
     `api.tvmaze.com/search/shows?q=<title>` — best name match
     with `show.image != null`, prefer `image.original` over
     `image.medium`; downloaded.
  A downloaded poster must decode as a real image or it is
  rejected. If OMDb produced ratings but no poster, the ratings
  are still stored and the poster chain continues. End state is
  `ok` when a poster and/or any score was found, else `miss`
  (timestamped; a `miss` retries only after 24 h, or immediately
  when a key is newly saved).
- **Triggers** — (i) the Done snapshot path (entry created /
  updated: URL + title are final there), and (ii) every Home
  build, for entries with no state or a stale miss — throttled
  by the in-flight set so a build kicks at most the missing
  ones, once. When a lookup finishes and Home is showing, Home
  rebuilds so the poster appears — unless focus is currently
  inside the RW shelf, in which case the rebuild is skipped
  (the focused tile reads the poster file on its next focus
  gain; nothing is yanked out from under the user).
- **Display (focused RW tile)** — with a poster on disk, the
  focus expansion is a horizontal row instead of the
  screenshot: poster (92×138dp, CENTER_CROP) at left; right
  column keeps the header title/link lines above it and adds
  `IMDb ★ <rating>` and `Rotten Tomatoes 🍅 <value>` lines
  under it, each omitted when absent. Without a poster, the
  v1.3 screenshot expansion is byte-for-byte the old behavior,
  and screenshots keep being captured exactly as before.
- **Settings** — new row "OMDb API key — posters + IMDb/RT
  scores", button reads **Not set** / **Key saved**. Tapping
  opens a text-input dialog (prefilled with the current key,
  blank removes it) whose hint/subtext reads: "Free key at
  omdbapi.com (1,000 lookups/day). Without a key, posters still
  load; scores need the key." Saving persists the key (prefs
  `omdb_key`), clears all `miss` sidecars, and kicks lookups
  for all current RW entries (a forced kick still skips an
  entry that already has a poster and, when a key is set, a
  stored IMDb score).

## v1.7 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.7.apk`: **550,056 bytes**, sha256
  `e7b49cae7508f0a792393bee413d3b66ca1570a93dcdc2cfbaa92dd4009ed159`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **8** /
  versionName **1.7**, sdkVersion 25 / targetSdkVersion 25,
  LAUNCHER + LEANBACK_LAUNCHER, banner present.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- `cleanTitle` additionally unit-checked on a desktop JVM with
  the exact shipped source (10 representative page titles:
  separators, junk words/phrases, `(YYYY)` + bare-year
  extraction, `Season N` / `SxxExx` cuts, give-up cases — all
  as designed). The network chain itself is exercised only
  on-device.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device check: watch something in the browser,
  hit Done, return Home and focus its RW tile — a poster
  should appear (may take a few seconds on the first build);
  add an OMDb key in Settings to also get the IMDb/RT lines.

## v1.6 (Oct 26, 2026) — fix: entering Recently Watched from above lands on the right side (from the v1.5 test)

Single-issue focus fix; everything else from v1.4/v1.5 is
unchanged.

- **Home focus path resume → DOWN → Search → DOWN → Recently
  Watched landed on a right-side RW tile instead of the first
  (leftmost) one.** Two non-deterministic mechanisms were in
  play: Android's focus memory (a ViewGroup restores its last
  focused descendant when re-entered) and geometry-based
  FocusFinder choosing a tile from the wide header row. Fix is
  deterministic explicit directional focus, wired in
  `buildHome()` on every build (Home is rebuilt on every show):
  - `bSearch.setId(View.generateViewId())` — Home's Search
    button is the only focusable search control above the shelf
    (the `searchInput` EditText lives on the separate Search
    screen, which is untouched). `safariBtn` views otherwise
    carry no id.
  - The first `rwTile()` created in the RW row is captured; if
    it has `View.NO_ID` it gets `View.generateViewId()`. Then
    `bSearch.setNextFocusDownId(firstRwTileId)` and
    `firstRwTile.setNextFocusUpId(bSearchId)`. An explicit
    next-focus id is consulted before FocusFinder geometry and
    overrides the shelf's remembered focused descendant, so
    DOWN from Search ALWAYS starts on the leftmost RW entry and
    UP from that entry returns to Search.
  - **Empty-RW fallback, decided in code by `firstRwTile ==
    null`:** the first category tile (leftmost tile of the
    first category row, id assigned the same way) is captured
    during the grid build, and the wiring target is
    `downTarget = firstRwTile != null ? firstRwTile :
    firstCatTile`; `bSearch.setNextFocusDownId(downTarget)`.
    Ids are generated fresh and wired in the same build pass,
    so no nextFocus id can point at a view from a previous
    build.
  - Belt-and-braces: the RW `HorizontalScrollView` gets a
    posted `scrollTo(0, 0)` after layout on every build, so the
    shelf is entered at its left edge.
  - No ids are wired among the RW tiles themselves — Left/Right
    inside the shelf stays native sequential — and no other
    screen's focus behavior changed. No `bringToFront()` was
    added in any menu path (the v1.5 rule stands; the only call
    left is the cursor overlay over the fullscreen container).

## v1.6 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.6.apk`: **545,960 bytes**, sha256
  `57c31647a9bc976260328f9f25001c9981e80f5c1b99888db4be87fab6c450a1`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **7** /
  versionName **1.6**, sdkVersion 25 / targetSdkVersion 25,
  LAUNCHER + LEANBACK_LAUNCHER, banner present.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.** On-device check: on Home, focus the resume bar →
  DOWN to Search → DOWN: selection must land on the leftmost
  Recently Watched tile every time; RIGHT moves to the second
  tile; UP from the first tile returns to Search. With RW
  cleared, Search → DOWN must land on the first category tile.

## v1.5 (Oct 26, 2026) — hotfix: menu navigation regression (from the v1.4 test)

Single-issue fix; everything else from v1.4 is unchanged.

- **Pressing LEFT in the menus swapped tile positions and focus
  stayed pinned on the right side of the screen.** Root cause:
  v1.4's focus-clipping fix called `View.bringToFront()` whenever
  a tile gained focus (inside the shared `focusIntoView()` helper,
  used by the shared focus styler, the RW tile listener, and the
  resume bar). `bringToFront()` is a draw-order API, but in a
  LinearLayout it physically REORDERS the child to the end of the
  parent's child list — so each focus gain moved the focused tile
  to the end of its row, tiles visibly swapped places, and focus
  search geometry broke. Fix: the `bringToFront()` call is gone.
  `focusIntoView()` now raises the focused tile with
  `setTranslationZ(dp(8))` instead — draw order only, layout
  order and focus order are never touched (API 21+, fine on 25) —
  and each of the three focus listeners resets
  `setTranslationZ(0)` on focus loss. The rest of the v1.4
  clipping fix is untouched: clipChildren=false /
  clipToPadding=false chain, ≥10dp shelf padding, and the
  10dp-expanded requestRectangleOnScreen scroll-into-view (none
  of which reorder anything). Audit: no removeView/addView or
  setLayoutParams swaps happen on focus gain anywhere. The one
  remaining `bringToFront()` in the file is the mouse-cursor
  overlay re-asserting itself over the fullscreen container in
  the window DecorView (a FrameLayout); the cursor is
  non-focusable and not in any navigation row, so it cannot
  affect menu navigation.

## v1.5 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.5.apk`: **541,864 bytes** (same byte size
  as v1.3/v1.4 by coincidence; contents differ), sha256
  `65a0aef92bd3c685a931b4cc425ea56a3cb5bfcdd89b8ea74c0745a7e06e8d8d`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **6** /
  versionName **1.5**, sdkVersion 25 / targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.**

## v1.4 (Oct 26, 2026) — browser menu, auto-hiding bar, three fixes (from the v1.3 test + TV photos)

1. **Long-press Browser Menu** — holding CENTER/OK for ~0.5 s in
   the browser (not fullscreen) opens a "Browser Menu" dialog
   replicating every toolbar action in toolbar order: ‹ Back,
   › Forward, Edit URL…, ⟳ Reload, Mouse/D-pad mode toggle
   (label shows the current mode; choosing it updates the toolbar
   Mode button), ⚙ Browser Settings…, ★ Add/Remove current page
   to/from Favorites (built from the live page title+URL when no
   catalog entry matches), ✕ Done. Reachable three ways: mouse
   sub-mode hold (a hold ON a link still gets the link menu; the
   nav menu is the fallback when no link menu appeared), D-pad
   sub-mode hold with the page block focused, and the remote
   MENU key (which no longer toggles the sub-mode outside
   fullscreen — the toggle lives in this menu, the toolbar, and
   the gear sheet). D-pad hold detection: key-down arms a 500 ms
   runnable; if it fires, a flag suppresses the tap that the
   following key-up would otherwise dispatch, so short presses
   are unchanged. The dialog uses the current browser
   appearance (dark default / light) and the trackDialog()
   depth mechanism.
2. **Auto-hiding nav bar (Safari-style)** — the whole header
   block (toolbar + page-title strip + progress bar) hides while
   scrolling down and returns on scroll-up, at the page top, or
   when a new page starts loading. Hiding animates
   translationY/alpha (~180 ms) then sets the block GONE so the
   WebView expands to full height. Scroll deltas come from a
   WebView subclass overriding onScrollChanged (covers native
   D-pad scrolling, mouse edge/wheel scrolling, and the page's
   own JS scrolling), with direction-aware hysteresis: hide only
   past 150dp with >40dp accumulated downward delta, show on
   >20dp accumulated upward delta or scrollY = 0. Extra reveals:
   mouse pushed against the top screen edge; D-pad UP while
   hidden (shows the bar and moves toolbar focus into it).
   Hiding resets toolbar focus; the bar is force-restored
   (no animation) on Done and on fullscreen enter/exit, and it
   never hides while a browser dialog is open.
3. **Focus-outline clipping fix (all menu screens)** — a focused
   tile scales to 1.03 and its white border was sliced wherever
   the tile touched its container. Approach per container: the
   clip-flags route everywhere — clipChildren=false +
   clipToPadding=false on the full ancestor chain (tile rows,
   HorizontalScrollViews, vertical ScrollViews, page columns,
   content frame, window root), ≥10dp padding on all sides
   inside the Home shelves, bringToFront + requestRectangleOnScreen
   with a 10dp-expanded rect on focus gain so the focused tile
   draws over neighbors and scrolls FULLY into view. The
   no-scale fallback was NOT needed anywhere: the 40dp page
   padding plus ≥10dp shelf padding exceeds the ~8dp maximum
   scale growth on every screen, so a complete border is
   guaranteed with the scale kept.
4. **"‹ Back to" resume button stuck on the first page** —
   root cause: the resume fields had exactly ONE writer,
   closeBrowser()'s finalize, and its fallback wrote back
   sessUrl — a field the live-resume path itself seeds FROM the
   saved resume values, so a resumed session could re-save its
   own starting page forever; nothing updated the target during
   browsing or when a new entry opened. Fix: a single
   updateResume() that always overwrites + persists immediately
   (no write-once guards), called at openEntry/openRw,
   onPageFinished, doUpdateVisitedHistory (SPA pushState),
   onReceivedTitle, and in the Done snapshot, which now takes
   webView.getUrl()/getTitle() authoritatively (fallback: last
   tracked navigation, never sessUrl). The bar re-reads
   SharedPreferences each time a screen is built, and resume
   reuses the live session only when the WebView is actually on
   the button's URL — otherwise it loads that URL fresh.
   Traced: [open A → Done → open B → Done] leaves the button on
   B loading B; [open A → in-page navigate to episode → Done]
   leaves it on the episode page.
5. **Fullscreen video sometimes shifted off-center** — cause:
   Chromium hands the custom view over with its in-page geometry
   still attached (the view could arrive already parented, with
   baked-in layout params/offsets), and the view was laid out
   once before the immersive-flag transition finished resizing
   the window. Fix: on fullscreen entry the view is detached
   from any previous parent, all transforms zeroed (x/y,
   translation, rotation, scale, padding), and added with a
   FRESH MATCH_PARENT LayoutParams with gravity CENTER;
   every SurfaceView/TextureView in its hierarchy is forced to
   MATCH_PARENT and each SurfaceView holder gets
   setFixedSize(realDisplayWidth, realDisplayHeight) from
   getRealMetrics; a post-attach layout pass re-runs the
   centering, and onWindowFocusChanged re-runs it again while
   fullscreen so the late immersive resize can't leave stale
   geometry. The surface keeps the video's own aspect handling —
   black bars now come only from the video's aspect ratio and
   are symmetric.

## v1.4 verification (Oct 26, 2026)

- `bash build.sh`: BUILD OK (javac source/target 8, android.jar
  on `-cp`; d8 `--min-api 25`; zipalign; apksigner).
- Copied to `HeckYeah-1.4.apk`: **541,864 bytes**, sha256
  `78939d936f0cae8bf597846274a7d505537fc91e67a02b608c39a15d40ec915b`.
- `aapt2 dump badging`: `com.heckyeah.tv` versionCode **5** /
  versionName **1.4**, sdkVersion 25 / targetSdkVersion 25.
- `apksigner verify`: Verifies, 1 signer, cert SHA-256
  `220529f9…b92fab5` (unchanged — `adb install -r` preserves
  app data).
- `unzip -t`: no errors.
- Build-verified only — **NOT yet tested on the physical
  Stick.**

## v1.3 (Oct 26, 2026) — polish release (from the v1.2 test + toolbar photo)

1. **Fullscreen cursor auto-fade** — in fullscreen + mouse
   sub-mode, the cursor fades out (~300 ms alpha) after 10 s with
   no remote interaction; any handled key makes it reappear and
   restarts the timer. Timer cancels on fullscreen exit or a
   switch to the D-pad sub-mode. Non-fullscreen cursor unchanged.
2. **Recently Watched screenshots** — at snapshot time (Done, plus
   a debounced refresh on page-finished for video sessions) the
   WebView viewport is captured (max 480 px wide, JPEG ~70) to
   `files/rw_shots/<url-hash>.jpg` and the filename stored as a
   new trailing field in the RW record (older records still
   load). On Home, a focused RW tile expands to show the
   screenshot below its title/link info (16:9-ish, center-crop);
   unfocused or no-shot tiles show nothing extra. Entry removal,
   Clear RW, and cap-12 eviction delete the screenshot file.
   **Honest limit:** if the last screen was fullscreen video, the
   capture may be black — Android does not composite
   protected/hardware video surfaces into view screenshots.
3. **Home edge overflow** — focus scale is now 1.03 max
   everywhere, page side padding is 40dp, applet/RW tiles have
   12dp gutters (`tileLp()`), and shelf rows have side padding
   with `clipToPadding=false`, so edge tiles grow inside the
   display instead of past it. Category grid: 12dp gaps between
   tiles and between rows (no more touching tiles).
4. **Browser Dark appearance (default)** — persisted
   "Browser appearance" setting (gear sheet + main Settings)
   restyles the full browser chrome without a reload: toolbar,
   pills, URL pill, progress tint, dialog theme, and the backdrop
   behind pages while loading. **Limit:** API-25 WebView has no
   force-dark (API 29+) — this themes the browser UI only,
   websites render as they design themselves; no CSS hacks.
5. **Toolbar clipping (device photo: Done + outlines cut at the
   right edge)** — toolbar side padding is 16dp, top/bottom 8dp,
   buttons are compact (~38dp, short labels — "Mouse"/"D-pad"),
   the URL pill keeps its flexible weight so it absorbs the
   squeeze, and toolbar buttons **no longer scale on focus**
   (blue ring only), so strokes can never be pushed outside the
   bar. Applies in both appearances.
6. **Inter app-wide** — Inter Regular + SemiBold + Bold
   (copied from the AirPlay TV project's local font assets; the
   closest open-source match to SF Pro) are bundled in
   `assets/fonts/` and applied through the text/button factory
   helpers to Home, categories, shelves, RW tiles, Settings, the
   browser toolbar, and dialogs. APK grows by the font files
   (~540 KB total build).

## v1.3 verification (Oct 26, 2026)

- APK: `build/manual/apk/HeckYeah.apk`, copied to
  `HeckYeah-1.3.apk` — **541,864 bytes**,
  sha256 `82fa880e4b276c7d2b447289eb9518092a021982a2db8a31d481d38168b6a71a`
- `aapt2 dump badging`: package `com.heckyeah.tv`
  **versionCode 4 / versionName 1.3**, sdk 25 / target 25,
  label "HeckYeah TV", launchable + leanback-launchable
  MainActivity, banner present.
- `apksigner verify`: **Verifies** (v2 + v3 true), 1 signer,
  cert SHA-256 `220529f9…b92fab5` (same keystore as v1.0–1.2,
  so it installs over them with `-r`).
- `unzip -t`: no errors. Zip contents include
  `assets/catalog.txt`, `assets/fonts/inter_regular.ttf`
  (324,820 B), `assets/fonts/inter_semibold.ttf` (326,048 B),
  `assets/fonts/inter_bold.ttf` (326,468 B), `classes.dex`.
- Verified by badging/apksigner/zip only — **NOT yet tested on
  the physical Stick.**

## v1.2 (Oct 26, 2026) — fullscreen + rendering fix release

Fixes from the v1.1 test on a Fire TV Stick:

1. **Fullscreen video now fills the display — root cause (v1.1):**
   the fullscreen container was a child of the browser pane's root
   layout (itself nested inside `contentFrame`/`windowRoot`), and
   `onShowCustomView` set no window flags, so the custom view never
   escaped the browser layout and kept its in-page size. **Fix:**
   the container is now attached directly to the window's
   **decorView** (top level, above the browser pane) and the custom
   view is added at MATCH_PARENT × MATCH_PARENT with
   `requestLayout()`; entering fullscreen saves the current system
   UI visibility + window flags, then sets `FLAG_FULLSCREEN` and
   `FLAG_KEEP_SCREEN_ON` and immersive system UI
   (`FULLSCREEN | HIDE_NAVIGATION | IMMERSIVE_STICKY |
   LAYOUT_FULLSCREEN | LAYOUT_STABLE`). Exiting (BACK /
   `onHideCustomView`) removes the container, restores the browser,
   restores the saved flags/visibility, and invokes the stored
   `CustomViewCallback`.
2. **D-pad + mouse work in fullscreen — root cause (v1.1):** the
   fullscreen branch inside `handleBrowserKey` ran before the
   mouse/D-pad handling and simply returned (only CENTER toggled
   play via JS), so the cursor never moved and arrows did nothing;
   the custom view could also take focus, and the cursor overlay
   sat inside `windowRoot`, below any window-level fullscreen
   view. **Fix:** the custom view and its container are made
   non-focusable; fullscreen keys are handled **first** in
   `dispatchKeyEvent` (dialogs exempt, and none can be open in
   fullscreen); the cursor overlay now lives at decor level and is
   brought to the front over the video. In mouse sub-mode the
   D-pad moves the cursor over the video and CENTER dispatches a
   simulated tap at the cursor position **directly to the
   fullscreen container** (players show controls / toggle play).
   In D-pad sub-mode CENTER taps screen center, arrows are
   consumed and tap to surface the player's controls,
   `MEDIA_PLAY_PAUSE` center-taps. Keys are still never forwarded
   into the WebView/custom view, so no accidental seeking; BACK
   exits fullscreen.
3. **Transparent page elements rendered white — root cause
   (v1.1):** the browser pane root was explicitly
   `setBackgroundColor(Color.WHITE)` and the WebView never got a
   background, leaving its default opaque white — transparent /
   rgba page elements composited over white. **Fix:**
   `webView.setBackgroundColor(0x00000000)` (transparent), the
   browser pane root and column are BLACK, the theme window
   background is `#000000`, and the fullscreen container + custom
   view are black so letterboxing is black, not white. (Audit: no
   injected CSS/JS in the app sets page background colors — the
   only JS is pause/seek — so there was nothing to remove there.)

## v1.2 verification (Oct 26, 2026)

- APK: `build/manual/apk/HeckYeah.apk`, copied to
  `HeckYeah-1.2.apk` — **66,502 bytes** (same byte count as v1.1
  by coincidence; contents differ — the new fullscreen methods
  are present in `classes.dex`),
  sha256 `315c4c0c2a723bf9e4220b97ece8b1f73943ecd02b404ee650300f0b62a27520`
- `aapt2 dump badging`: package `com.heckyeah.tv`
  **versionCode 3 / versionName 1.2**, sdk 25 / target 25,
  label "HeckYeah TV", launchable + leanback-launchable
  MainActivity, banner present.
- `apksigner verify`: **Verifies** (v2 + v3 true), 1 signer,
  cert SHA-256 `220529f9…b92fab5` (same keystore as v1.0/v1.1,
  so it installs over them with `-r`).
- `unzip -t`: no errors.
- Verified by badging/apksigner/zip only — **NOT yet tested on
  the physical Stick.**

## v1.1 (Oct 26, 2026) — on-device bug-fix release

Fixes from the v1.0 test on a Fire TV Stick:

1. **Video playback** — `setMediaPlaybackRequiresUserGesture(false)`,
   mixed content always allowed, third-party cookies accepted
   (players/logins need them), plugin state on, hardware
   acceleration on. **`WebChromeClient.onShowCustomView` /
   `onHideCustomView` implemented**: HTML5 fullscreen video now
   goes truly fullscreen (black fullscreen container, toolbar +
   page hidden, immersive system UI); Back exits fullscreen first.
2. **Scaling** — pages were rendering zoomed-in. Now
   `setUseWideViewPort(true)` + `setLoadWithOverviewMode(true)` +
   `TEXT_AUTOSIZING` so a page fits the TV width. New persisted
   **Browser scale** setting (50/75/100/125/150%, **default
   100%**) in Settings and the browser gear sheet: applied via
   `setInitialScale()` before every load and `setTextZoom()` live
   (with reload) when changed, shared by all browser sessions.
3. **Done pauses + suspends** — Done now runs JS pausing every
   `<video>`/`<audio>`, calls `WebView.onPause()` +
   `pauseTimers()`, and hides the browser **without destroying
   the WebView or blanking the page**, so nothing keeps playing
   or running in the background. App-backgrounding while browsing
   also pauses the WebView. The page you just left is saved
   (URL/title/applet/category, SharedPreferences), and Home,
   category, shelf and search screens show a prominent accent
   **"‹ Back to <title>"** button pinned at the very top that
   resumes the same session (media stays paused until you press
   play) — or reloads the saved URL fresh after an app restart.
4. **Padding + button polish** — ≥12dp gaps between all browser
   toolbar buttons, consistent ~40dp pill buttons with white
   backgrounds, `#0A84FF` focus ring + slight scale in D-pad
   mode; menu buttons got larger padding (16×9dp, min height
   40dp) and 10–12dp spacing in headers, setting rows and stacks.
5. **⚙ Browser Settings in the browser** — gear button in the
   toolbar opens a sheet with Scale, Ad blocking, JavaScript,
   Desktop user-agent, Cursor speed, Clear cache, Clear history.
   All persisted; JS/UA/scale changes reload the page.
6. **Navigation rework** — mouse cursor ONLY in the browser;
   Home/categories/search/settings/dialogs are pure D-pad.
   Browser D-pad mode: the URL is a **button** (one focus stop)
   that opens a full URL editor dialog — no more
   character-by-character traversal of an inline EditText.
7. **D-pad keys never reach the page** — while the browser is
   showing, `dispatchKeyEvent` consumes DPAD arrows, CENTER/
   ENTER, PAGE_UP/DOWN and media keys (FF/Rewind/Play-Pause) at
   the activity level: mouse mode drives the cursor, D-pad mode
   scrolls/taps/activates the toolbar. Site players can no
   longer read Left/Right as seek ±10s. This also holds during
   HTML5 fullscreen (Back still exits fullscreen). Browser
   dialogs (URL editor, gear sheet, link menu) are exempt so
   they keep native key handling.
8. **Recently Watched homepage bug — root cause (v1.0):** RW
   was written ONLY from `onPageFinished`, one entry per finished
   page, deduped by exact URL. Modern streaming aggregators are
   SPAs: navigating from the site's homepage to an episode page
   happens via `history.pushState`, which never fires
   `onPageFinished` — so the only URL ever recorded for those
   sites was the applet's homepage URL loaded by `openEntry`
   (plus root/trailing-slash finishes, which were inserted at
   the front and flooded the cap-12 list). Tiles faithfully
   opened the stored URL — the homepage. **Fix:** one RW entry
   per launch session, continuously updated via
   `doUpdateVisitedHistory` + `onPageFinished` +
   `onReceivedTitle`, with a root-URL guard (a `/` URL never
   overwrites a deeper same-host entry), a final
   `getUrl()`/`getTitle()` snapshot on Done, and best-effort
   video-position capture/restore (guarded JS, ignored on any
   failure).

## v1.1 verification (Oct 26, 2026)

- APK: `build/manual/apk/HeckYeah.apk`, copied to
  `HeckYeah-1.1.apk` — **66,502 bytes**,
  sha256 `3a846e116309e69419053e184e6885dab18fb5a50f348233cf9468cc1d138834`
- `aapt2 dump badging`: package `com.heckyeah.tv`
  **versionCode 2 / versionName 1.1**, sdk 25 / target 25,
  label "HeckYeah TV", launchable + leanback-launchable
  MainActivity, banner present.
- `apksigner verify`: **Verifies** (v2 + v3 true), 1 signer,
  cert SHA-256 `220529f9…b92fab5` (same keystore as v1.0).
- `unzip -t`: no errors.
- Verified by badging/apksigner/zip only — **NOT yet tested on
  the physical Stick.**

## v1.0 verification (Oct 26, 2026)

- APK: `build/manual/apk/HeckYeah.apk` — **58,310 bytes**,
  sha256 `8453350e789ad481b3bf81be75846ea6b91b0e26d5dd20c8d44580df7d332d7e`
- `aapt2 dump badging`: package `com.heckyeah.tv` 1.0 (1),
  sdk 25 / target 25, label "HeckYeah TV", launchable +
  leanback-launchable MainActivity, banner present, icons at all
  densities, only the two permissions above.
- `apksigner verify`: **Verifies** (v2 + v3 true), 1 signer,
  cert SHA-256 `220529f9…b92fab5`.
- `unzip -t`: no errors; APK contains `classes.dex` and
  `assets/catalog.txt` (194 lines = header + 193 entries).
- Verified by badging/apksigner/zip only — **NOT yet tested on
  the physical Stick.**

## Honest limits

- **Ad blocking is host-level only.** The API-25 WebView has no
  extension support, so this is not uBlock Origin: no cosmetic /
  element hiding, and first-party or same-host ads and some
  redirect tricks will still get through. Popup windows are
  blocked, which removes the worst streaming-site behavior.
- **The old WebView cannot do DRM.** Widevine-in-WebView playback
  on Fire OS 6 is not available; some modern/streaming sites will
  refuse the browser or fail to play video. Tiles open the sites —
  playback is each site's own player doing what the Stick's
  WebView allows.
- Live Refresh depends on the `fmhy/edit` repo layout
  (`docs/*.md` on `main`, verified Oct 26, 2026). If upstream
  renames files, refresh reports failure and keeps the last good
  catalog / bundled seed.
- Seed entries for gaming/audio/downloading/torrenting/
  educational/linux-macos/misc are starter lists (15–19 each);
  only video/ai/reading seeds were transcribed from full page
  renders. Refresh fills in the rest.
