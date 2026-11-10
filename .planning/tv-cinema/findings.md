# Findings

## Adaptive update downloads — 2026-11-10
- User approved implementation after the route-speed inspection. `Updater.onConfirm()` now selects `downloadAdaptive`; metadata checks keep the original light ordered lookup. No probe at startup/background check and no new mirror.
- Three parallel prefix requests: 256KiB consumed per route, 2.5s call cap plus bounded join allowance; validate APK magic/content type/size and exact zero-based Content-Range for 206. Ignore Range safely by cancelling before closing/draining. Stable descending measured rate includes connection/body time; failed probes stay as fallback candidates. Cancellation owns all active calls and interrupts worker/probes.
- Transfer meter reports monotonic smoothed speed every >=500ms and ETA when total is known. Two low 4s windows plus >=12s route age, under 64KiB/s, >1MiB remaining and beneficial restart/unknown alternative permit a bounded switch. Avoid discarding most bytes for unknown routes. Every dropped slow original can be retried once without a low-speed cutoff if all alternatives fail; no partial-byte merging. Final/working transfer has no whole-call deadline; 6s connect, 12s idle-read and lifecycle cancellation remain.
- Separate localized status TextView shows testing/route/percent/rate/ETA/verifying; buttons retain compact labels and cancellation. Shared updater callbacks have defaults for compatibility; minimal mobile dialog/API/layout changes included. Ordinary integrity, package/version/signer/ABI and cache ownership remain.
- Actual project OkHttp5.5 Gradle update suite passes 78 tests; all existing 26 downloader checks pass. Earlier standalone compatibility run's two IPv6 failures do not reproduce with actual project dependencies. API28 ARM64 `emulator-5566`, independent updateprobe package, passes all three instrumentation methods in 8.489s, covering actual dialog geometry/D-pad/Back plus 9 invoked real-loopback probe/download scenarios. Screenshot inspected, no clipped telemetry.
- User-TV/mainland throughput, live mirror Range behavior, real installer/OTA and long-duration downloads remain untested. New adaptive behavior cannot improve an already installed old updater until the new APK is installed; no public release or Git delivery authorized.

## Update download route inspection — 2026-11-10
- User defers further APK optimization and reports update route 1 downloads successfully but slowly. Production `UpdateSources.mirrors()` fixes order to `gh-proxy.com`, `ghfast.top`, original GitHub; `UpdateSourcesTest` asserts exactly that order. No TV throughput probe/ranking exists.
- `UpdateDownloader` switches only after transport/content/checksum failure, with 6s connect, 12s idle-read and 600s whole-call timeouts per route. Continuous slow data avoids idle-read failure, so route 1 may remain active up to the whole-call limit. Current fallback discards `.part` and restarts; no HTTP Range/resume or slow-speed trigger. Dialog shows route number/percent only, no speed/ETA or route-choice action.
- Existing tests cover stalled responses, failure fallback, hash/validator and cancellation, but not sustained low-throughput switching. No current measurement ranks mainland ISPs or the user's TV. Recommend real APK prefix throughput measurement on TV, bounded low-speed fallback and speed/ETA; any Range support must validate exact bytes/version/hash and server range contracts. This is a recommendation, not implemented behavior.

## Search-title focused marquee — 2026-11-10
- Search uses `JetStreamStrongLabelTextView`, intentionally without marquee. Existing native `PosterTitleMarquee` already measures original wrapped lines and starts only for truncated focused titles, with rebind/detach cleanup. Its switch to a single line changes wrap-content height, so search needs an opt-in lock to its measured two-line height during marquee.
- Keep default helper behavior unchanged for home/history/list users. Search retains its existing typography and metadata; bind the helper once in the holder after XML inflation. Pixel-level Android tests must distinguish actual text movement from parent focus animation.
- Added actual-card tests and verified the original search holder fails waiting for marquee on API28. Full raw title, measured title/card heights and actual drawn pixels are asserted. Shared build was interrupted by concurrent Sherpa-dependency removal, so isolated validation uses the committed pinned recipe with only search changes. Initial `git apply` in an exported directory was skipped by Git's enclosing-worktree boundary; switching to `patch -p1` plus exact modified Git-blob verification materialized every declared hunk before compilation.
- Pixel and screenshot inspection exposed an API28 `TextWatcher` side effect: binding the existing helper before text assignment forces Editable/DynamicLayout, where two-line end ellipsis is lost. Search's new `bindWithFixedHeight` path skips that watcher and exposes `refresh()` for the holder after each bind. Other helper users retain their original behavior. The original helper independently reproduces both existing single-line poster pixel failures on API28 (2/3 fail); this is not a regression introduced by the opt-in search path.
- Final seven search tests pass with strict blur ellipsis, real Chinese/English pixel motion, fitting/blurred stillness, fixed heights, rebind/detach and existing full search integration. ARMv7 Debug compile succeeds in the isolated pinned recipe; the current production helper/holder are byte-identical to tested inputs. Shared full build, ARMv7 TV, Release installation and the unrelated original poster failures remain separate verification gaps.

## APK size audit — 2026-11-10
- Current local ARMv7 Release is 91,915,944 bytes (87.658 MiB), SHA256 `a4a9ed0b2392def3ef728e2c4c0d997115ec2d4bf63532e8fbf41a7c84889fd9`; the retained published 0.1.7 copy is 91,125,793 bytes (86.904 MiB). Keep these artifacts distinct.
- Actual ZIP payload (local): native libraries 41,075,059 compressed bytes; assets 31,136,759; DEX 10,456,737; resources 7,674,267; other 1,370,757. APK contains only armeabi-v7a and production already enables R8/resource shrinking and compressed native packaging.
- Largest entries include Python requirements archive 14.466 MiB, sherpa-onnx JNI 7.530 MiB, subtitle font 7.220 MiB, FFmpeg avcodec 6.593 MiB and MPV 6.091 MiB (all APK-stored sizes). Feature ownership and safe savings still need verification; no runtime/source change during the audit.
- Initial source search excluded every `build/` ancestor and returned incomplete results; rerun from the materialized TV checkout with relative source paths. ppm service is unavailable and unrelated approximate history was not adopted.

## Further APK size review — 2026-11-10
- No additional feature removal or production-code change requested. Measurements use disposable copies of the verified 83,922,421-byte APK.
- Three ARMv7 libraries retain non-runtime debug/symbol sections: libc++ (6.459 MB), FFmpeg JNI (3.393 MB), QuickJS budget JNI (1.763 MB). `llvm-strip --strip-unneeded` on copies saves 3,432,366 compressed bytes using identical deflate settings. All allocated runtime sections and dynamic symbol tables remain byte-identical. Native runtime execution after stripping is not yet tested.
- Build logs explicitly say `Unable to strip ... packaging them as they are`; R8/resource shrinking do not fix native stripping. Determine the selected NDK/tool path before implementation, rather than claiming native libraries were already minimized.
- Python archive has about 1.96 MB compressed test/testing payload plus header/source extras. NumPy is required by pinned pywasm 1.0.8 and the tested yangshipin key-generation path; removing NumPy/Python wholesale can break sources.
- MPV subtitle font is Go Noto Current (60,628 glyphs / 32,388 mapped codepoints), 7.571 MB in APK; three MiSans UI fonts already contain only ~7,140 mapped codepoints each (3.552 MB combined). Dropping font hints alone measures only ~0.50 MB total and can affect rasterization; larger savings need charset/weight choices and rendering tests.
- Initial nested-ZIP repack reused mutable ZipInfo objects and invalidated offsets for the second source read, producing a CRC error in the experiment. Repack must clone entry metadata; the original APK and extracted archive remain untouched.
- Corrected archive experiment passes source/remaining-entry CRC and byte comparisons. Removing only `test`/`tests` (514 entries) saves 1,976,199 bytes with identical ZIP settings; preserve runtime `testing` helpers and native dependencies until imports/source compatibility are tested.
- Native log identifies incomplete default NDK28.2 (`source.properties` missing) before stripping is skipped; installed NDK29 works on copies. First two low-impact opportunities total 5,408,565 bytes, yielding an estimated 78.51 MB target, not a rebuilt artifact.
- Deliberate Latin/Cyrillic/CJK/kana subtitle-font subset saves 3,033,391 same-compressor bytes but reduces mapped characters 32,388→13,881; system fallback/old-TV languages untested. Keeping one MiSans weight can save ~2.37 MB with visible weight changes. Full review: `docs/features/tv-cinema/apk-size-review.md`.
- Python footprint is ~23.81 MB; wholesale removal breaks source contracts. MPV exclusive native footprint ~6.40 MB; shared FFmpeg ~10.83 MB is also needed by EXO. No duplicate top-level native libraries found; cannot sum both as MPV-removal savings. No production implementation or experimental runtime execution in this review.

## Real-time speech subtitle removal — 2026-11-10
- User explicitly requested removing this feature and its code/dependencies. It generates subtitles from program audio, with optional translation; Android system voice search is independent. Models were downloaded separately and never bundled in the audited APK.
- Removed 40 exclusive upstream source/test/resource/JAR/native files using exact SHA-256 removal entries, plus all caller/UI/resource/keep-rule references. Five guard tests prove idempotence, validation before deletion, rejection of edits/invalid paths/symlinks/duplicates and required absence. CI now runs all `test_cinema_*.py`; ignore exceptions retain the new tests as reviewable source.
- Audio sink retains ordinary/effects/software/passthrough branches; removed PCM recognizer tap, 10-second lookahead override, ASR sessions and model-download/translation/secret UI. Ordinary subtitle code/font, DSP, Python and system microphone classes remain in the signed APK.
- Frozen isolated baseline builds avoid concurrent source mutation. Final recipe and declared copies match the current working tree, including the other session's search helper. Final signed ARMv7 APK `build/outputs/tv-cinema-armv7-no-speech.apk`, 83,922,421 bytes, SHA256 `98634969fa7bb0488eaced3f29e91f1eab8b69c840088e7dbaea552936a0122e`. Saves 7,993,523 bytes (8.697%) vs the preserved 91,915,944-byte input. 308 speech/Sherpa DEX classes and one native library disappear (38→37).
- 25 tool tests and 54 focused JVM tests pass. API28 ARM64 `emulator-5566`, isolated `speechprobe`, passes real SRT/audio playback with/without DSP, paused seek and resume, even with an old speech-enabled preference (2 cases / 2.748s). ARMv7 Release rebuild passes 900 tasks / 3m11s and exact package/version/minSdk/signature/native audit.
- Optional mobile compile fails six pre-existing TV overlay references (`cinema_debug_exo_only`, `cinema_playlist_invalid`, `CinemaDiscovery`, `CinemaWallpaper`); no removed feature symbol fails. Do not broaden this TV task to repair mobile packaging. Target TV/runtime/performance and OTA remain untested.

## Held seek release — 2026-11-10
- Production playback is `VideoActivity`/`CastActivity` → `CustomKeyDownVod` → `SeekKeySequence` + `PendingSeek`, not the preserved root `SeekInput` experiment.
- Accepted DOWN/repeats cancel any scheduled commit and update the preview. A commit is posted only on UP matching key code, device and original downTime. No idle fallback exists; missing/rerouted/unmatched UP leaves `PendingSeek` and the preview active until another short press posts it.
- `SeekKeySequence` deliberately rejects foreign or stale sequences, including held events after media replacement. Retain that boundary; use a bounded inactivity fallback for VOD rather than accepting arbitrary UP events.
- Existing Android input tests already cover canceled/foreign release, reset/fullscreen exit, navigation takeover, real replacement callbacks and accelerated steps. Extend these contracts with missing/mismatched-release and held-repeat cases. Actual television event loss/identity has not been captured, so this is a reproduced code failure path, not a measured firmware diagnosis.
- API28 ARM64 fail-before: both missing-UP and mismatched-UP cases fail with zero commits (`cinema-seek-before-runtime.log`). After: seven added regressions plus seven existing ownership tests pass (14 total, 28.841s); three existing JVM suites pass (12 tests). Production input compile and pinned-input verification pass.
- VOD now refreshes a 1s idle commit after accepted DOWN/repeats; matching UP retains 250ms coalescing. Both successful/zero-offset completions clear key ownership; delayed callbacks recheck seekability before sending a seek. No Live/Compose seek behavior was changed.
- Actual production `VideoActivity.dispatchKeyEvent` + EXO/MediaController test passes with locally generated 120s H.264: missing UP commits the accumulated +60s, closes the preview, keeps playback, and ignores a late UP. Runtime is 13.902s (`cinema-seek-playback-runtime.log`). No third-party provider is needed. Production prepare/verify and Cinema-package ARMv7 Debug compile pass; no new Release APK or target-TV acceptance.

## Search-result metadata and titles — 2026-11-10
- Ordinary search uses `CollectFragment` → `VodPresenter` → `VodRectHolder`. The holder hides its below-title metadata row on blur; `adapter_vod_rect.xml` limits titles to one 26dp line.
- `VodRectHolder` is reused outside search. Use a search-specific presenter/holder/layout for this request, preserving other card surfaces.
- At the start, production `Vod` had certification (`getContentRating`), which explicitly is not a review score, but no review-score field. Added optional explicit ten-point scores independently; never interpret certification or infer a rating from title/update text.
- Existing native title tests attach widgets in the disposable `com.fongmi.android.tv.sourceprobe` app. Existing card roots/posters supply outline and focus styling; reuse those rather than introducing a new UI framework.
- Initial sandboxed `adb devices -l` failed because it could not bind its localhost listener. Runtime verification needs approved local Android-tool access; this is not evidence of an empty device list.
- User rejected simultaneous bottom-left score/bottom-right update text on small posters. Revised to stacked full-width bottom lines over a gradient; source remains top-right. Approved ADB check confirms no connected devices; existing isolated API28 ARM64 AVD is available, with a stale pre-move image path to correct before starting.
- Concurrent held-seek/icon work is modifying disjoint production files and shared recipe/docs. Preserve their entries when appending this task's patch/copies. Another session used the sourceprobe package on emulator-5554 during our first instrumentation, yielding `Process crashed`; logs show its seek tests replaced the active run. Same-AVD read-only startup was refused, so final verification uses independent `cinema-search-api28-arm64` at emulator-5562 with frozen copies of this task's app/test APKs. Do not stop emulator-5554.
- Final: 10 JVM metadata tests, production ARMv7 Debug Java/resource compile, 5 API28 ARM64 attached card/Parcel checks and 1 full search integration pass. Actual bitmap-background screenshots at 94/110dp widths show no score/update overlap; source/update ellipsize independently and titles keep two lines across focus. Empty/recycled/grouped-source fields refresh without focus. Real TV and new ARMv7 Release remain untested.

## App icon exploration — 2026-11-10
- Final package retains identical source RGB pixels after aapt2 compression. Actual API28 renders confirm proper proportional banner scaling and legibility in 48px launcher / 32px playback masks. Build and resource checks prove packaging, not target-TV acceptance.
- User accepted the generated jade image; byte-exact master is now `distribution/overlay/cinema_icon.png` (1254² RGB, SHA256 `5e3d01fa…36890`). Leanback-specific launcher/banner/splash/default logo bindings preserve mobile and network logos.
- Actual API28 archive-resource rendering caught non-scaling fixed-dp banner/logo layers; relative 21.875% horizontal banner insets and a zero-inset default-logo wrapper correct the geometry. Playback badge switches its hard-coded paper artwork to a cached shared drawable.
- Current request is an original technological app icon informed by logosj.com and the supplied Canva article. Prior project context prefers rounded cinema/interlocking frames, a non-blue palette and no metallic reflections or generic play/letter mark.
- Real-browser inspection completed on both supplied URLs. Canva's current article presents 30 examples and explicitly explains secondary meanings in negative spaces (Picasa/FedEx). Use that general principle in a widescreen-shaped central opening, not a copied mark.
- Exact selected artwork/prompt/verification live in `docs/features/tv-cinema/icon-design.md`; retain previous moss/plum designs. Icon production resources are included; concurrent seek/source/danmaku changes are outside this task.

## 饭太硬 candidate endpoints — 2026-11-10
- Built-in default is not 饭太硬's own domain: `app/src/leanback/assets/cinema/sources.json` points at the 12yue re-host `https://0.12yue.de5.net/tvbox/饭太硬.json`, which resolves to Cloudflare `104.21.47.215`. Domestic mobile-broadband latency is the expected weak point; content is a 48-site copy identical to the official one.
- Official `/tv` serves a JPEG followed by `**` + base64 JSON, not plain JSON. The app's `com.fongmi.android.tv.api.Decoder.getJson` handles that envelope, so the official endpoint is usable as-is.
- Live today (HTTP 200, 19,951 bytes, 48 sites / 6 lives, Last-Modified 2026-11-08): `http://www.饭太硬.net/tv` (BlogCDN), `http://www.饭太硬.cc/tv` (nginx), `https://www.饭太硬.cc/tv` (valid TLS, same ETag `6aefd459-4def`).
- Dead: `http://饭太硬.top/tv` and `http://fty.xxooo.cf/tv` (no response).
- Third-party mirrors live but are older copies: `https://jijietvbox.lol/tv/fty.json` (17,343B, 50 sites), plus the current 12yue re-host (17,765B, 48 sites).
- `https://www.饭太硬.net/tv` fails TLS (`tlsv1 alert internal error`), so `.net` is HTTP-only and `CinemaSources.seeds()` would drop it. Only `https://www.饭太硬.cc/tv` is currently shippable without relaxing the HTTPS-only filter.
- Host probes prove endpoint liveness and content only. No device is connected, so no TV-network ranking exists yet.
- Implementation: 饭太硬 is now a three-endpoint group (`https://www.饭太硬.cc/tv`, `http://www.饭太硬.net/tv` flagged insecure, `https://jijietvbox.lol/tv/fty.json`) activated as `cinema://fantai`. `CinemaDiscovery` times the endpoints on the TV, caches the winner for 24h and re-probes on failure or expiry; `CinemaSourceProbe` holds the timing/validation logic and is JVM-tested.
- `https://` stays mandatory for every endpoint without `"insecure": true`, so the HTTP official host is the only plaintext exception and it is declared in the asset, not hard-coded.
- Official `.cc`/`.net` payloads include whole-line `//` comments that strict JSON rejects; both the probe and the discovery decode path strip those lines. Inline `https://` inside string values must survive, which a line-start-anchored regex guarantees.

## FongMi upstream comparison — 2026-11-10
- Production uses TV `17abcd7453a0ecbdbc9ed40a22f3fd3632158495` plus media `342f6590e02ac390e0bd6b4488b8a1bff55747c1`, not the preserved root `app/` experiment.
- Existing evidence distinguishes API28 ARM64 MPV `CCodecBufferChannel::setSurface` SIGSEGV from the user's unclassified EXO stutter. `mediacodec-copy` is a crash workaround with potential decoding-performance cost, not a stutter diagnosis.
- GitHub metadata: FongMi/media default branch `release`, pushed 2026-10-24; FongMi/mpv-android default branch `fongmi`, pushed 2026-11-09 UTC. Pin exact revisions before recommending changes.
- Existing user edit changes only the checkout path in feature context; preserve it. No ppm write is authorized for this checkout outside managed roots.
- Read-only direct ppm API succeeds outside the sandbox: historical `BUG-0038` is pending_test and records the API28 native crash plus copy-mode workaround; `REQ-0009` still leaves stutter attribution open. The recorded AndroidProjects checkout no longer exists. Do not mutate historical records from this excluded checkout.
- FongMi/media has a separate `release-1.11.0-fongmi` branch at `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`; default `release` at `2bc207851df311340767e913931ca7b28cab1794` is not the full customization surface. Existing production fork is shallow, so use explicit upstream diffs rather than assuming Git ancestry is locally available.
- Production native bundle already comes from FongMi/mpv-android `99a60ad214` with FFmpeg9, but its JNI bridge is rebuilt from Aryan447/mpvlibAndroid `5d658fe7` to preserve 23 methods. Replacing just an AAR/so can break both Java JNI contracts and FFmpeg symbol versions.
- Existing production Exo configuration already enables decoder fallback, hardware/software track preferences and optional tunneling with effect exclusions; these are not new discoveries to present as missing work. Check the whole activity/dialog/library path before declaring diagnosis UI absent.
- GitHub cross-fork compare proves production media `342f6590` is ahead of current FongMi `3c2cbe8a` by two commits, behind by zero, with only `DefaultExtractorsFactory.java` changed (Matroska probing before MMT/TLV). Thus FongMi's current EXO codec, H.264, HLS and seek fixes are already inherited; do not recommend a blanket update as new remediation.
- Inspected actual HLS edge-case diff: it tolerates inaccurate codec declarations and suppresses segment `IOException` as a gap. This is format/network tolerance, not higher decoding throughput, and may hide skipped content. The seek fix signals EOS for an audio track ending before the requested seek time; not generic playback stutter.
- Reference worker's preliminary lifecycle-integration claim confused preserved root AAR with production bridge. Requested correction against actual `mpv/build.gradle` and rebuilt bridge; no conclusion adopted yet. No user report says 0.1.7 still crashes.
- Direct file comparison confirms FongMi and production `MediaCodecVideoRenderer.java` and expanded `DebugTextViewHelper.java` are byte-identical. The inherited renderer already contains API<=28 MiTV/MiBox device-specific Surface workarounds and pre-API31 frame-rate-change codec recreation. Device codename must be measured, not inferred from MiTV-MFTP0 marketing model.
- Upstream Media3 vsync callback deduplication (`fd8a6b2c`) reduces redundant main-thread wakeups on display changes; already in production. The vendor-level detached-Surface change (`bb3d9921`) is a separate capability workaround, not evidence of an MPV API28 crash fix.
- Existing Media3 `PlayerView.toggleDebugView()` and `DebugTextViewHelper` expose decoder identity, buffered seconds, dropped frames before/after decoding and frame processing offset. Full-path review found the TV app's existing `PlayerEngineDialog.selectDebug()` calls `PlaybackActivity.toggleDebugView()`, but that implementation only flips `debugViewVisible`; neither show nor hide calls the actual PlayerView. This is a concrete incomplete wiring path, not a missing button. The library includes keep rules; runtime/R8 availability still requires validation.
- Actual production `mpv/build.gradle` excludes runtime ZIP `libplayer.so` and installs the rebuilt extended bridge. Its checked `render.cpp` directly calls `mpv_set_option(wid)` and deletes the JNI global ref, while `main.cpp` synchronously joins the event thread during native destroy. New upstream queued lifecycle semantics are not automatically inherited through the runtime ZIP.
- FongMi/mpv-android `fongmi` pinned to `c3ceb8af5be34cee57c043ef2b4b845259ded0d1`; Nov 9 commit `8da68c3a283e5aea2b5e68b8c5ff8c7577567681` adds native-handle locking, completion-aware Surface replacement/detach, explicit errors and request-correlated asynchronous commands. Its `destroy()` now enqueues shutdown and returns before destruction; our Java native-generation/cache fence currently treats the synchronous bridge return as completion. A drop-in library replacement would invalidate that fence and can allow premature recreation/cache release.
- MPV new lifecycle work addresses a separate race class. No examined source/commit establishes a direct fix for our API28 `CCodecBufferChannel::setSurface` configure crash. Keep the existing SDK<=28 copy-mode mitigation pending device proof; a hard native signal cannot be caught by Java or automatically tried on the next hwdec entry.
- MPV default `profile=fast`, GLES path, audiotrack/opensles and 64MiB forward/back demux limits are already used by production. `display-fps-override` is an upstream-only difference worth measuring, not a proven stutter fix. Do not enable interpolation/gpu-next/Vulkan or lower quality blindly on a 32-bit low-end TV.
- Verified 13 relevant production files against GitHub Git blob hashes: render, vsync, debug text, track selector, sample queue, H.264, HLS, audio capabilities, load control, MPV Surface target/default options and PlayerView/debug bridge all match. Production does not actually use the inherited Media3 MPV implementation; it constructs a self-hosted `com.fongmi.android.tv.player.mpv.MpvPlayer`.
- Unrelated source/default/danmaku WIP appeared during this read-only audit. No such file was modified by this investigation; do not prepare/build/reset the shared materialized checkout while another task may be working there.

## EXO debug panel wiring — 2026-11-10
- `PlayerView.isDebugViewVisible()` is the only honest source of button state; the Activity flag drifted whenever the view detached or the engine changed.
- The Media3 overlay requires an `ExoPlayer` instance; the self-hosted `com.fongmi.android.tv.player.mpv.MpvPlayer` does not implement `toggleGeneralStats`, so the panel would stay permanently empty on MPV.
- Patch blob IDs were generated from a scratch Git repo so `cinema-prepare.py` accepts full 40-character hashes; `cinema-verify-inputs.py` PASS; incremental `compileLeanbackArmeabi_v7aDebugJavaWithJavac` BUILD SUCCESSFUL in 35s.
- No device attached (`adb devices -l` empty). Panel rendering, R8 reflection keep rules, engine switching and TV behavior remain not_tested.

## MPV Android 9 surface crash — 2026-11-10
- Switching EXO→MPV during playback crashed on API28 inside `CCodecBufferChannel::setSurface` with SIGSEGV, not a Java exception. `mediacodec-copy` avoids passing a Surface and completes playback; `MpvHwDecPolicy` limits that path to SDK<=28 so newer devices keep surface-first decoding.
- The copy path may fall back to software decode; it fixes the crash, not necessarily MPV performance.

## Navigation and playback preload — 2026-11-10
- `HomeActivity.onNavClick` already handled `home_setting`; moving the entry required only a nav item plus removing the My-card bindings/layout. API28 UI confirms the new order and the absence of the My settings card.
- `preload_next` defaults to true in the upstream player and shares a 64MB next-item preload buffer with foreground playback. CinemaProfile now writes false only when the key was never set, using a separate migration marker so existing 0.1.x installs receive it.
- This mitigation is unmeasured on the user's TV; it can reduce network/CPU contention but cannot be called the confirmed stutter root cause.

## Android 9 source-selection crash — 2026-11-10
- TV screenshot identifies 0.1.3 / Android9/API28 and `SearchRelevance$Query` initialization. The nested cause is not visible in the photo.
- API28 ARM64 emulator reproduces that exact click-path failure and reports `PatternSyntaxException` for `\p{IsHan}`. The same failing expression and R8 ID exist in the downloaded public 0.1.3 APK, and its actual DEX fails to initialize on API28. The 0.1.4 source contains the same expression.
- `\p{script=Han}` compiles on API28 and host JDK. One-line fix passes the existing matching tests, 8 Android9 integration cases, and API28 release-Dex initialization. User TV and ARMv7 runtime are not connected.

## Homepage recovery implementation — 2026-11-09
- Cache-first uses a five-item, per-category snapshot with a seven-day age limit and HTTPS-only artwork; transient empty feeds preserve the visible hero instead of clearing it.
- Auto rotation now waits for the successor image and swaps title/indicator/artwork atomically; only the next image is prefetched at view size. Manual switching remains immediate.
- Snapshot writing compares the item body before replacing it, so repeated renders do not rewrite unchanged JSON; freshness is a separate timestamp key.

## Homepage inspection and delivery readback — 2026-11-09
- Published BuildConfig has empty TMDB key; direct TMDB list requests are skipped. RexNow feed can still supply TMDB identity/artwork; editorial label is not list-origin evidence.
- Feed caches are process-local; existing Glide caching remains enabled. No explicit next-slide preload; 7s auto-advance cancels pending artwork although its call timeout is 20s. This is a conditional weak-network risk, not a proven TV root cause.
- Explicit curl no-HTTP-proxy same-image test: proxy HTTP200 JPEG50,599B/1.76s; official HTTP200 same size/2.97s. Host-only samples. Direct Douban poster succeeds with the app's existing Referer; headerless418 is not app evidence.
- Public 0.1.3 and successful cloud run42624579403 verified. Downloaded cloud/local release signer is identical; version codes/hashes differ. No implementation or new remote write during this consultation; bounded cached-first/ready-image rotation is only a recommendation.

## Follow-up — 2026-11-09
- Actual Android catalog dialog clips after four visible entries on the test TV-sized display; placing the new merged entry seventh made it appear missing. The latest candidate is now first while six presets remain unchanged below it. MockWebServer instrumentation proves no request before starting, no plugin/JAR execution during discovery, explicit activation only, failure preservation and active-candidate slot preservation.
- Latest episode layout uses actual MiSans text metrics and max of normal/selected font widths plus padding, not a character-count threshold or fixed columns. Width measurement is remembered separately from row packing and focus, so only viewport changes repack rows. LazyColumn composes visible rows; only the actual focused overflow chip installs marquee. The shared TV button's focus-scale is disabled here to keep wide buttons and outlines inside the clipped viewport.
- Screenshot source `6v电影` maps to 心魔's `csp_XBPQ` config; fetched rule text has empty 播放数组/播放列表 and generic selectors. Its website home contains the same category sequence shown under 选集. The exact detail response was not captured; the generic-selector explanation is evidence-supported, not yet individually proven.
- Upstream update logic validates downloaded APK package, exact version, APK signer and ABI, but points to `wobuhui666/TV`. The user authorized changing `mjnhmd/tv-cinema` from private to public on 2026-11-09; authenticated readback reports `PUBLIC` and anonymous repo GET 200. No release published yet. Never embed tokens. This host fetched a reference manifest via direct GitHub and two proxies, but domestic-TV availability is unverified.
- User-provided wallpaper endpoints have different protocols: xxapi returns JSON `data` URL; Peapix returns JSON array with `fullUrl`; upx8 redirects to HTTP JPEG; mxin serves JPEG; xsot redirects to Bing JPEG. bing-img redirected but its target timed out from the host. Service availability can change.
- Actual source snapshots had different global spider jars, parser lists and rules (饭太硬 48 sites, 南风 91, 心魔 151, 夏夏 43, 健康家用 64). Discovery must not execute jars/JS/Python, and a merge cannot pretend all global semantics are compatible.
- Source settings' `内置片源` is only a catalog; user-requested manual discovery/merge is not implemented.

## Baseline
- Upstream 4512b4c, GPL-3.0, native Android Views/Leanback.
- App source expects untracked custom media AARs, SDK 37, AGP 9.4.0, Gradle 9.7.1 and Java 21.
- No connected TV. API 36 ARM64 emulator available only.
- Upstream .gitignore excludes CLAUDE.md, docs, tests and properties; task-owned source must be force-added or specifically unignored.

## Reference review
- recent.design: use typography hierarchy, spacing and restraint, not its desktop masonry layout. Reviewed actual page and interface filter.
- xiugou-tv: Windows Flutter/libmpv. Useful reference paths: `lib/ui/desktop/widgets/focus_ring.dart`, `lib/ui/theme/app_tokens.dart`, `lib/ui/desktop/player/episode_drawer.dart`, `lib/ui/desktop/player/playback_preparing_overlay.dart`.
- CineFlow: Electron desktop. Useful separation of progressive loading/error presentation; avoid its blur, card tilt, pointer parallax and hover scroll.
- No reference implementation was executed; do not treat reference comments or worker inferences as measured TV facts.
- Remote navigation needs visible focus distinct from current playback/selection. Directional movement must not start a different episode before OK.
- Android TV documentation search returned memory optimization and focus-system guides through the installed android CLI.

## Design
- Near-black canvas, warm white text, restrained amber accent, consistent quiet rounded surfaces.
- Focus outline + luminance change, not layout-changing borders or constant scaling.
- No animated wallpapers, autoplay hero, blur or framework migration.

## Implemented, pending runtime verification
- Native header/typographic introduction, compact six-action navigation, restrained cards and stable focus outlines.
- Lightweight toggle preserves optional wallpaper without a hidden running video player by default.
- Search/settings/detail/live/player controls use consistent tokens; original binding IDs and player surface order retained.
- TV image memory cache/pool capped; logo no longer decodes original dimensions.
- First-run subscription consent; six independent subscriptions, URL dedupe and no blind spider merge.
- Fork version action no longer offers upstream APKs.
