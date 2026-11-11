# Verification record — 2026-11-09

## Windowed playback render path — 2026-11-11
- User photo: detail-page preview is green with audio, fullscreen is normal, for an episode labelled 4K. No affected media, device logs, active player/render preference or real TV is available. Code proves only that windowed playback forcibly selected TextureView while fullscreen used the configured output; it does not prove that the device's green frame was caused by TextureView, clipping, codec or HDR.
- `VideoActivity.applyWindowVideoStyle()` now calls `getPlaybackRender()` instead of forcing TextureView. With EXO's default Surface setting, window and fullscreen retain the same output surface; explicitly selected Texture stays Texture, and MPV stays Texture according to the existing override. The original `clipToOutline` and focus foreground remain unchanged.
- Fail-before: API28 ARM64 regression using default EXO/Surface and a synthetic H.264 fixture failed `Window should use the selected output`; the Texture preference case passed (`build/logs/cinema-window-render-before-runtime.log`). This reproduces the output mismatch, not the user's device-specific green image.
- After: JDK21/pinned Media3, `:app:assembleLeanbackArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebugAndroidTest :app:compileLeanbackArmeabi_v7aDebugJavaWithJavac -PtvApplicationId=com.fongmi.android.tv.sourceprobe --max-workers=2` passes (`build/logs/cinema-window-render-after-build.log`). The final AndroidTest APK rebuild passes (`build/logs/cinema-window-render-av-test-build.log`).
- Android9/API28 ARM64 `emulator-5582`, independent `com.fongmi.android.tv.sourceprobe`: `adb -s emulator-5582 shell am instrument -w -r -e class com.fongmi.android.tv.ui.custom.CinemaWindowRenderAndroidTest com.fongmi.android.tv.sourceprobe.test/androidx.test.runner.AndroidJUnitRunner` passes four cases in 8.661s (`build/logs/cinema-window-render-av-runtime.log`). Tests confirm real EXO/MPV engine identity, selected output and player identity through window/fullscreen/Back, video pixels via PixelCopy or TextureView, progress, and EXO AAC-track selection. Emulator audio output was disabled; audible sound was not tested.
- Test fixture: `build/outputs/cinema-window-render-fixture.mp4`, 120s H.264 320×180 plus AAC, SHA256 `a5c997acddeafda120fc90541c91674f2964c61e69c75c354dc0a113511b1e2f`. Generated from the existing seek fixture using `ffmpeg -i build/outputs/cinema-seek-fixture.mp4 -f lavfi -i 'sine=frequency=440:sample_rate=48000' -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 64k -shortest build/outputs/cinema-window-render-fixture.mp4`. Test surface frame checks reject uniform/green-dominant output for this synthetic clip; they do not diagnose arbitrary content.
- `build/outputs/cinema-window-render-surface.png` is an inspected emulator screenshot with visible video and rounded preview corners. `python3 scripts/cinema-prepare.py`, `python3 scripts/cinema-verify-inputs.py`, 29 `test_cinema_*.py` tool tests (`build/logs/cinema-window-render-tools.log`) and `git diff --check` pass.
- Signed TV build: `TV_VERSION_CODE=217572473 TV_VERSION_NAME=0.1.8+20261111.150113-window-fix bash scripts/cinema-build.sh --abi armeabi-v7a` passes in 2m32s / 901 tasks (`build/logs/cinema-window-render-armv7-build.log`), including stripped-native and Python-asset guards. Copied APK `build/outputs/tv-cinema-armv7-window-fix.apk` is 78,711,921 bytes, SHA256 `94c3c135c28d83e961be8c9a4494ca42961ec4105ac0fe5999446aa40c770b33`. `cinema-ci.py verify-apk` confirms package `com.mjnhmd.tvcinema`, exact version, minSdk24, ARMv7-only and existing signer `d405dcab…bdc5` (`build/logs/cinema-window-render-apk-audit.txt`).
- Publication: commit `051059f65ef7005d4aeeb2b9488286c62164dd85` pushed to `origin/fongmi` with remote SHA verified. `gh workflow run cinema-armv7.yml --ref fongmi -f publish=true -f version_code=217580180 -f version_name=0.1.8+20261111.170940` produced run `43065428038`; both build and publish jobs succeeded. Tag `build-43065428038-1`, title `TV Cinema 0.1.8+20261111.170940`, URL `https://github.com/mjnhmd/tv-cinema/releases/tag/build-43065428038-1`. Redundant push run `43065382091` was cancelled while queued so the publish run could start.
- Anonymous asset readback: downloaded `leanback-armeabi_v7a-coexist.apk` is 78,710,893 bytes / SHA256 `d4047cbd971e4972e61e37e0e410e712ec7514b8ed337e55677db5165ec14986`, matching `leanback-coexist.json` and the GitHub digest. Manifest code `217580180`, name `0.1.8+20261111.170940`, one `armeabi_v7a` asset. `cinema-ci.py verify-apk` passes package `com.mjnhmd.tvcinema`, requested version, minSdk24, non-debuggable ARMv7-only payload and certificate `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`.
- Not tested: the user's Android9 ARMv7 TV, the actual 4K media/codec, device hardware composition, HDMI, perceived audio, long playback, in-app update/install flow and mainland download speed. The published CI APK is a separate build from the local test APK, so its byte hash differs. Preserve the pre-existing unrelated `distribution/overlay/cinema.xml` reorder.

## Authorized 78.5 MB packaging revision — 2026-11-10
- User supersedes the earlier deferral with “等下 改到78.5 然后提交推送发新版”. Old publish42794496346 and automatic42794430961 cancelled before publication; latest remains0.1.7. Pushedbc078403b stays in history; no amend/reset/force push.
- Changes: `sdk-compat.init.gradle` pins NDK29 for all Android projects. `chaquo/build.gradle` appends generated requirements trimmer after project evaluation/ZIP generation and before PythonBuildAssets hashes. `distribution/trim-python-tests.py` excludes exact `test`/`tests` directory entries, validates original ZIPs and all retained bytes, writes temporary archives, then replaces generated outputs. It preserves installed dependency packages/source wheels/native modules/metadata/testing APIs/fonts. No playback or source behavior changes.
- First task hook ran too early and failed with no generated archive; plugin bytecode inspection identified action ordering. `gradle.projectsEvaluated` appends after plugin actions; focused `:chaquo:generateArmeabi_v7aReleasePythonBuildAssets` passes7s. Real generated ARMv7 archive removes498 entries and saves2,078,435B; second run is byte-identical/no-op. Generated build.json SHA-1 matches transformed archives.
- `python3 -m unittest discover -s scripts/tests -p 'test_cinema_*.py' -v`:29 pass (`build/logs/cinema-slim-tool-tests.log`). Four new packaging cases cover exact directory removal/runtime bytes, idempotence, staging/rollback on corrupt later archive, unsafe archive rejection, ELF-debug/Python-test/hash/size failures. `scripts/cinema-packaging.py` is invoked by build wrapper and CI79,000,000-byte publication gate.
- First complete slim Release passes2m55s/901 tasks (78,397,553B, temporary nonpublished code217500100). Final formal command uses `TV_VERSION_CODE=217468795 TV_VERSION_NAME=0.1.8+20261110.101315 bash scripts/cinema-build.sh --abi armeabi-v7a` from isolated root. PASS2m36s/901 tasks (`build/logs/cinema-slim-release-final.log`). No old incomplete-NDK/strip skip is accepted by final guards.
- Final local APK `build/outputs/tv-cinema-armv7-slim.apk`:78,397,589B (78.40MB), SHA256 `a706ac7062a771a46a7f4f42050429600112b488b77e1da4e37d70d96500aa6d`; saves5,530,020B vs83,927,609 fast-update artifact. `cinema-ci.py verify-apk` passes original certificate `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`, exact package/version, minSdk24/non-debuggable and ARMv7-only/native32. `cinema-packaging.py --max-bytes79000000` passes37 stripped libs,2 test-free requirements archives and all Chaquopy asset hashes. Audit `build/logs/cinema-slim-final-apk-audit.{json,txt}`.
- Independent before/after comparison of all37 ARMv7 ELF files confirms each allocated runtime section is byte-identical (`build/logs/cinema-slim-native-sections.json`). Dynamic export checks for the three original large libraries were already recorded in the size review; code/assets beyond debug/test packaging remain unchanged.
- Isolated JDK21/OkHttp5.5 update JVM suite78 pass; ARM64 app/test assemble pass41s/574 tasks (`cinema-slim-tests-build.log`). API28 emulator5566 had stopped; restored existing independent AVD without wiping it or touching active emulator5554. Independent speechprobe app/test installed with data preserved. Runtime command selects `CinemaSlimRuntimeAndroidTest,CinemaSubtitleRemovalAndroidTest`:4 pass3.733s (`build/logs/cinema-slim-api28-runtime.log`). Tests execute NumPy dot/linalg, pywasm WASM returns42, PythonSSL/SQLite/lxml/AES/ujson, FFmpeg/MPV library load, QuickJS native timeout/recovery and real audio/SRT/DSP/pause/seek/resume. ARM64 test APK also passes packaging guard.
- Remaining not_tested: actual user's ARMv7 TV, wider third-party plugin combinations, long sessions and public cloud artifact/OTA. Local Python/native/runtime checks do not prove every external source or targetTV acceptance. Current user authorizes focused commit/push/revised publication; exact cloud size/hash/source will be recorded after readback.

## Adaptive update download routes — 2026-11-10
- User approved throughput-based choice, slow-route recovery and speed/ETA display after reporting fixed route 1 was usable but slow. Preserved existing three mirrors, metadata behavior, TLS trust, checksum/package/version/signature/ABI/cache/install validation. No external mirror benchmark or user-TV speed claim.
- Implementation: `Updater.onConfirm()` invokes `downloadAdaptive`. At most three concurrent 256KiB prefixes, 2.5s per call / bounded join allowance; body transfer rate includes startup. Validate content type/APK header/expected size and zero-based Content-Range; cancelled calls prevent ignored Range responses from draining full APKs. Failed measurements stay as fallback routes, source numbers keep original route identities. No metadata/startup probing.
- Continuous low-speed rule: at least 12s on route, two low 4s windows below 64KiB/s, >1MiB remaining and beneficial fresh restart / early unknown alternative. No partial reuse between mirrors. A deferred known-working route gets one final attempt without speed cutoff if all others fail; attempts are bounded. Full transfer has no fixed whole-call deadline, with existing 6s connect / 12s idle-read / lifecycle cancellation. Cancel propagates to every probe and active download and prevents validation/completion.
- TV dialog adds a dedicated three-line status view above compact buttons: testing, route/percent, smoothed speed and estimated remaining time, retry and verifying. Retries clear stale telemetry; Cancel/D-pad/Back remain reachable. Matching localized resources in English/simplified/traditional Chinese and minimal mobile API/layout compatibility are included; full mobile product packaging remains outside this TV task.
- Preflight: `python3 scripts/cinema-prepare.py` and `python3 scripts/cinema-verify-inputs.py` PASS for both workspace and isolated frozen `build/diagnostics/update-routing/` recipe. `python3 -m unittest discover -s scripts/tests -p 'test_cinema_*.py' -v`: 25 pass (`build/logs/cinema-update-adaptive-tool-tests.log`). Source patch/hash/overlay identity and XML/scoped diff checks pass; no `.git` refs/index mutation.
- Project JDK21/Python3.10/OkHttp5.5 Gradle command from isolated TV: `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests 'com.fongmi.android.tv.update.*' :app:assembleLeanbackArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebugAndroidTest -PtvApplicationId=com.fongmi.android.tv.updateprobe --max-workers=2 --console=plain`, with pinned Media3 and SDK compat init. PASS, final 34s / 574 tasks. All 78 JVM tests, zero failures/errors, including all 26 original downloader tests (`cinema-update-adaptive-final-tests-build.log`).
- Added controlled regressions distinguish headers from payload, correct/ignored/invalid Range, blocked/HTML mirrors, concurrent cancellation, timestamped speed/ETA windows, continuous slow failover, full-file integrity, original route numbering, final slow-route usability, recovery of deferred slow routes and no inherited whole-call deadline. Initial standalone cached-dependency classpath failures and two OkHttp4.9/localhost-IPv6 fallback failures are tooling-only; neither reproduces in authoritative OkHttp5.5 Gradle suite.
- API28 ARM64 `emulator-5566`, independent `com.fongmi.android.tv.updateprobe` app/test pair: `adb -s emulator-5566 shell am instrument -w -r -e class com.fongmi.android.tv.update.UpdateDownloadAndroidTest com.fongmi.android.tv.updateprobe.test/androidx.test.runner.AndroidJUnitRunner`. PASS, 3 methods / 8.489s (`build/logs/cinema-update-adaptive-api28-runtime.log`), including nine invoked local network scenarios plus real status layout/D-pad cancel/Back. Screenshot `build/outputs/update-download-status.png` inspected: route37%, 1.5MB/s and estimated2:05 fit above buttons, no clipping. Synthetic local traffic only, no installer invoked.
- Final signed Release command from isolated root: `TV_VERSION_CODE=217447202 TV_VERSION_NAME=0.1.8-fast-update-local bash scripts/cinema-build.sh --abi armeabi-v7a`. PASS, 2m50s / 900 tasks (`cinema-update-adaptive-armv7-release-final.log`); initial complete build 3m37s. Actual current and frozen patch/manifest/copied inputs match.
- Local APK `build/outputs/tv-cinema-armv7-fast-update.apk`: 83,927,609 bytes, SHA256 `8e3bd9383f3096ec638e505a44b036f3bccb459b9830599399421fba4ae23664`, code217447202/name0.1.8-fast-update-local. `cinema-ci.py verify-apk` PASS for Cinema package, minSdk24, non-debuggable, ARMv7-only/native32 and original certificate `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`. Actual Release DEX contains probe/meter/formatter; R8 mapping retains/inlines adaptive and UI paths. Audits `build/logs/cinema-update-adaptive-apk-audit.{json,txt}`. Main HEAD `980d18b85a7d0e018f3ed859c12c11cc5233aa09`, dirty work; production patch SHA256 `78673dc698872fb25c58358a88f9dc51253fefb6997b9bfbb4cb3e6ac72b089e`.
- Remaining `not_tested`: real user's Android9 ARMv7 TV, actual mainland/mirror throughput/Range behavior, installer/OTA end-to-end and long sessions. New updater takes effect only after installing the new APK; existing installed releases retain old download behavior. No new size optimization, feature removal beyond earlier speech work, commit/push or public release. Suggested commit: `feat: select faster update routes and recover slow downloads`.

## Real-time speech subtitle removal — 2026-11-10
- Scope: user requested deleting real-time speech subtitles/translation and related code/dependencies. Removed 40 exclusive upstream files through SHA-256-pinned `baseline.json` removals, all settings/player bindings, locale strings, recognizer/translators/model downloader/secret UI, PCM tap/lookahead/session hooks, Sherpa JAR/JNI and its keep rule. Ordinary embedded/external/ASS subtitles, audio DSP/passthrough and Android voice search remain.
- `python3 scripts/cinema-prepare.py` / `python3 scripts/cinema-verify-inputs.py`: PASS; fresh isolated snapshot removes exactly 40 files, repeated prepare removes zero. Preparation validates every target before any deletion and rejects edits, paths outside `app/`, traversal, invalid hashes, symlinks and duplicates. New tests are included via `test_cinema_*.py` in CI and narrow `.gitignore` exceptions.
- `python3 -m unittest discover -s scripts/tests -p 'test_cinema_*.py' -v`: 25 pass, including 5 removal-safety cases. Log `build/logs/cinema-speech-removal-tool-tests.log`. Python compilation and scoped diff checks pass.
- Build isolation: `build/diagnostics/speech-removal/` contains frozen pinned TV `17abcd7453a0ecbdbc9ed40a22f3fd3632158495`, Media3 `342f6590e02ac390e0bd6b4488b8a1bff55747c1` and declared Cinema inputs, with independent generated outputs. Existing Git metadata is linked for read-only baseline/blob checks; no refs/index/history writes. Refreshed only the concurrently completed search-title helper and declared copies, then confirmed final recipe and all copied inputs match current workspace.
- Final command, from that isolated root: `TV_VERSION_CODE=217447201 TV_VERSION_NAME=0.1.8-no-speech-local bash scripts/cinema-build.sh --abi armeabi-v7a`. PASS, 3m11s / 900 tasks; earlier complete build 4m14s. Logs `build/logs/cinema-speech-removal-armv7-release{,-final}.log`. Initial sandbox-only Gradle wrapper lock refusal was resolved using approved existing cache/signing access.
- Final APK `build/outputs/tv-cinema-armv7-no-speech.apk` (also canonical ARMv7 output): 83,922,421 bytes; SHA256 `98634969fa7bb0488eaced3f29e91f1eab8b69c840088e7dbaea552936a0122e`. Preserved before APK `build/outputs/tv-cinema-armv7-before-speech-removal.apk`: 91,915,944 bytes, SHA256 `a4a9ed0b2392def3ef728e2c4c0d997115ec2d4bf63532e8fbf41a7c84889fd9`. Saves 7,993,523 bytes (8.697%). This is local validation, not a public release.
- Existing `cinema-ci.py verify-apk` passes package `com.mjnhmd.tvcinema`, requested code/name, minSdk24, non-debuggable, only ARMv7 native payloads and certificate `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`. Log `cinema-speech-removal-armv7-audit.txt`. Actual manifest has no speech settings Activity; DEX definitions remove 308 `ai/subtitle`/Sherpa classes, native `lib/` count 38→37 and no Sherpa/model entry remains. Ordinary subtitle controller, EXO engine, DSP and `CustomMic` definitions plus subtitle font/Python assets remain. Audit `build/logs/cinema-speech-removal-apk-audit.json`, manifest `cinema-speech-removal-manifest.txt`.
- JVM command with JDK21/Python3.10/pinned Media3: `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests 'com.fongmi.android.tv.player.audio.*' --tests 'com.fongmi.android.tv.player.subtitle.*' --tests 'com.fongmi.android.tv.player.exo.ExoTunnelFallbackTest' -PtvApplicationId=com.fongmi.android.tv.speechprobe`. All 54 pass, zero failures/errors. ARM64 `assembleLeanbackArm64_v8aDebug` / `assembleLeanbackArm64_v8aDebugAndroidTest` also pass. Combined log `cinema-speech-removal-tests-build.log` later ends failed only because optional mobile compilation encounters six existing TV-overlay references (`cinema_debug_exo_only`, `cinema_playlist_invalid`, `CinemaDiscovery`, `CinemaWallpaper`); do not describe that whole command as passed.
- API28 ARM64 `emulator-5566`: installed independent `com.fongmi.android.tv.speechprobe` app/test pair. `adb -s emulator-5566 shell am instrument -w -r -e class com.fongmi.android.tv.player.exo.CinemaSubtitleRemovalAndroidTest com.fongmi.android.tv.speechprobe.test/androidx.test.runner.AndroidJUnitRunner`: 2 pass / 2.748s (`build/logs/cinema-speech-removal-api28-runtime.log`). Real generated WAV advances, external SRT produces cues, pause/seek/resume pass, actual DSP is absent/active as selected; stale speech-enabled preferences cannot re-enable deleted ASR. No model/network recognition is invoked. Existing Cinema/other test packages and user data are untouched.
- Remaining not_tested: user's Android9 ARMv7 television, MPV/ASS runtime on that television, perceptual audio/visual acceptance, long sessions and public OTA. No commit, push, public release or device language-model deletion performed. Suggested commit: `refactor: remove real-time speech subtitles and dependencies`.

## Search-title focused marquee — 2026-11-10
- Pre-commit: the title-only production snapshot applies to pristine pinned files with every original/modified Git blob verified; helper, holder and test bytes match the actual isolated validation build. Existing manifest entries stay unchanged except the patch checksum. All 20 release-tool tests pass (`cinema-search-marquee-precommit-tools.log`).
- Scope: opt-in `PosterTitleMarquee.bindWithFixedHeight()` plus `CinemaSearchCardHolder` binding/refresh, production patch/checksum and focused tests. Overflow after the original two-line layout switches to native single-line scrolling inside its unchanged two-line measured height; blur restores wrapping/end ellipsis. Existing default helper callers are unchanged.
- Reproduced before: `CinemaSearchCardAndroidTest#overflowingTitleScrollsOnlyOnFocusAndKeepsItsTwoLineFootprint` fails waiting for focused marquee on API28 (8.727s, `build/logs/cinema-search-marquee-before-runtime.log`).
- Verification isolation: shared checkout build initially failed during concurrent Sherpa dependency removal. `build/reference/cinema-search-title-tv` uses committed Cinema recipe `c7a3feaf1a5bea54c25ea122c0643b649af14bac` over pinned TV `17abcd7453a0ecbdbc9ed40a22f3fd3632158495` / Media3 `342f6590e02ac390e0bd6b4488b8a1bff55747c1`, plus only this search increment. Both full patches were applied and every resulting target checked against its declared Git blob. Tested helper/holder bytes match the current production files. This does not validate concurrent subtitle-removal work.
- JDK21, `MEDIA3_SOURCE_DIR` set to pinned media, `:app:compileLeanbackArmeabi_v7aDebugJavaWithJavac :app:assembleLeanbackArm64_v8aDebug :app:assembleLeanbackArm64_v8aDebugAndroidTest -PtvApplicationId=com.fongmi.android.tv.sourceprobe` with the reviewed absolute init script: `BUILD SUCCESSFUL in 1m 28s`, 554 tasks (`cinema-search-marquee-ellipsis-build.log`). No new ARMv7 Release APK.
- Android9/API28 ARM64 / 1280×720, dedicated `emulator-5562`, `am instrument -w -r -e class com.fongmi.android.tv.ui.CinemaSearchCardAndroidTest,com.fongmi.android.tv.test.SearchRelevanceIntegrationTest -e native_ui_evidence true com.fongmi.android.tv.sourceprobe.test/androidx.test.runner.AndroidJUnitRunner`: all seven pass, 27.987s (`cinema-search-marquee-ellipsis-runtime.log`). Covers Chinese/English overflow actual text pixels moving, fitting/blurred text pixels staying still, full raw text, equal measured title/card heights, proper two-line blur ellipsis, focused short/long rebinding, detach/reattach, metadata, D-pad/OK/long-click, empty/recycled/grouped source fields, Parcel data and complete search filtering/Back focus.
- Screenshot review caught the existing helper's TextWatcher forcing Editable/DynamicLayout and clipping two-line text without ellipsis on API28. Search now uses explicit holder refresh instead; strict line-count and ellipsis assertions plus final screenshots `search-title-marquee-focused.png` / `search-title-marquee-blurred.png` pass. Earlier intermediate screenshots are superseded.
- Broader helper suite: one history-title case passes, two pre-existing single-line poster pixel tests fail on API28 with both modified and original unchanged helper (original control: 21.377s, `cinema-search-marquee-original-helper-runtime.log`). Do not describe the complete original poster suite as passing or attribute those failures to this opt-in search path.
- Production prepare/input verification and scoped whitespace checks pass after integrating this search change. The user explicitly authorized commit/push for this increment on 2026-11-10; exclude concurrent subtitle-removal changes. No ppm write, public release, user-TV/ARMv7 runtime/performance or installation acceptance by this follow-up.

## Selected jade app icon — 2026-11-10
- Accepted artwork: original 1254×1254 RGB PNG at `distribution/overlay/cinema_icon.png`, SHA256 `5e3d01fa3b016855c66c249f4db7686d8e907bb1cbade8859d3592d5a6036890`. Generated through built-in imagegen; exact prompt and reference findings in `icon-design.md`.
- Production changes: eight explicit leanback brand overlays, leanback manifest icon/roundIcon/banner/splash references, and playback-info badge artwork using a cached shared drawable. Original shapes/colors, playback behavior, mobile resources and provider logos are preserved.
- `python3 scripts/cinema-prepare.py`, `python3 scripts/cinema-verify-inputs.py`, XML parse, byte-match overlay checks and scoped `git diff --check` pass. `aapt2` verifies `cinema_launcher`, `cinema_banner` and `Cinema.Splash` bindings in the actual final package.
- Final command `TV_VERSION_CODE=217447200 TV_VERSION_NAME=0.1.8-icon-local bash scripts/cinema-build.sh --abi armeabi-v7a`: `BUILD SUCCESSFUL in 3m 37s`, 900 tasks. Local `build/outputs/tv-cinema-armv7.apk`: 91,915,944 bytes, SHA256 `a4a9ed0b2392def3ef728e2c4c0d997115ec2d4bf63532e8fbf41a7c84889fd9`. Existing `cinema-ci.py verify-apk` passes original certificate, package, version, minSdk24 and ARMv7-only payload. Logs: `build/logs/cinema-icon-armv7-final-build.log`, `cinema-icon-final-apk-audit.json`, `cinema-icon-final-{manifest,resources}.txt`.
- Packaging recompresses PNG bytes; a stdlib PNG decoder compared every source/packaged RGB pixel and found equality. Pixel SHA256 `3fc98667b6914d08a32a5fde7f82be8c2e15b0158d222f5e36ece149d525d07f`, record `build/logs/cinema-icon-pixel-audit.json`. The final package contains that exact audited image entry.
- Android9/API28 ARM64 `emulator-5566`: temporary `CinemaIconRenderProbe` loads the ARMv7 APK using `AssetManager` and draws resources on Android `Canvas`. No installation, app startup, data modification or UI interaction. Eight outputs pass and were visually inspected: 320px square, 80px launcher, 48px circle, 640×360 / 320×180 TV cards, 88px home logo, 32px playback logo and 288px splash foreground. Images `build/outputs/cinema-icon-render/`, log `build/logs/cinema-icon-api28-render.log`.
- Fail-before visual check caught fixed-dp layer sizes leaving banner pixels uncovered at larger bounds and risking clipping in the small playback badge. Relative 21.875% horizontal banner insets and a zero-inset logo wrapper fix both; final renders confirm proportional geometry and full background coverage.
- Pre-delivery fingerprint: `fongmi`, observed HEAD `f2158adf164426f235693f364bb7b4410d16b330`. User authorized icon-only commit/push after verification; its staged patch/copy checksum must exclude the unrelated held-seek increment and match all included source assets. Shared worktree also contains unrelated changes, so the existing local validation APK is not an icon-only public release. No ppm mutation or public release publication. Actual Android9 ARMv7 television appearance and install/OTA remain not_tested.
- Icon-only pre-commit checks pass: pristine pinned sources + selected production patch yield all 26 declared modified blobs; icon entries match the tested source; every copy dependency is committed or included. `python3 -m unittest discover -s scripts/tests -p test_cinema_ci.py -v` passes all 20 tests (`build/logs/cinema-icon-precommit-tools.log`). Unrelated seek changes and reordered strings are excluded from the commit.

### Transparent toolbar mark — 2026-11-11
- User reported the top-left app logo still carried the artwork's near-black plate against the `#101216` home background. `distribution/overlay/cinema_icon_mark.png` is a 1254×1254 RGBA derivative of the accepted artwork with the uniform plate keyed to alpha 0 (border-ring median `(8,14,14)`, 4–14 RGB-distance smoothstep); band pixels and their colors are unchanged. `cinema_icon.png`, launcher/banner/splash bindings and mobile resources are untouched.
- Production wiring: `distribution/overlay/cinema_logo.xml` bitmap source → `@drawable/cinema_icon_mark`; new copy entry in `distribution/baseline.json` → `app/src/leanback/res/drawable-xxxhdpi/cinema_icon_mark.png`.
- `TV_VERSION_CODE=217447200 TV_VERSION_NAME=0.1.8-icon-local bash scripts/cinema-build.sh --abi armeabi-v7a`: `BUILD SUCCESSFUL in 3m 26s`, 901 tasks, signed ARMv7 APK `build/outputs/tv-cinema-armv7.apk` (`build/logs/cinema-icon-mark-build.log`).
- Package audit: `aapt2` lists `drawable/cinema_icon_mark` as `res/Fk.png`; `drawable/ic_logo` (`res/Ze.xml`) points to `@0x7f080086` = `cinema_icon_mark`. Decoded packaged image mode RGBA, alpha histogram identical to the source (0: 1,228,932; 1–3: 590; 4–15: 750; 16–254: 4,322; 255: 337,922). aapt2 zeroed RGB only where alpha = 0; composited pixels over `#101216` match the source exactly.
- `python3 scripts/cinema-prepare.py`, `python3 scripts/cinema-verify-inputs.py` and all 29 `scripts/tests/test_cinema_*.py` tests pass (`build/logs/cinema-icon-mark-tools.log`). Actual television appearance remains not_tested; no commit or push performed.

## Held seek release — 2026-11-10
- Environment: macOS/JDK21, pinned production TV `17abcd7453a0ecbdbc9ed40a22f3fd3632158495` and Media3 `342f6590e02ac390e0bd6b4488b8a1bff55747c1`; isolated `com.fongmi.android.tv.sourceprobe`, `emulator-5554` Android9/API28 ARM64. This is not the user's ARMv7 TV.
- Fail-before: `VodSeekReleaseAndroidTest` missing-UP and mismatched-UP cases both fail with `expected:<1> but was:<0>` after 1.35s, proving no seek completion (`build/logs/cinema-seek-before-runtime.log`; 2 tests, 2 failures). Original app/test APK assembly passed in 14s (`cinema-seek-before-build.log`).
- Fix: reviewed `CustomKeyDownVod` hunk in `distribution/cinema.patch`; accepted DOWN/repeats refresh a 1s idle commit, matching UP keeps 250ms. Final commit rechecks `canSeek` and both nonzero/zero-delta completion clear key ownership. Changes are VOD-only; foreign/stale key rejection, short-press/acceleration and existing reset/navigation/media ownership remain intact.
- Focused JVM: `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests com.fongmi.android.tv.ui.custom.PendingSeekTest --tests com.fongmi.android.tv.ui.custom.SeekKeySequenceTest --tests com.fongmi.android.tv.ui.custom.SeekConfirmKeySequenceTest`: 12 cases, 0 failures/errors. Combined ARM64 app/test assembly and JVM invocation passes in 47s (`build/logs/cinema-seek-after-build.log`).
- Android command: `adb -s emulator-5554 shell am instrument -w -r -e class com.fongmi.android.tv.ui.custom.VodSeekReleaseAndroidTest,com.fongmi.android.tv.ui.custom.SeekKeyOwnershipAndroidTest com.fongmi.android.tv.sourceprobe.test/androidx.test.runner.AndroidJUnitRunner`: 14 pass, 28.841s (`build/logs/cinema-seek-after-runtime.log`). Covers missing/mismatched/late release, sustained repeats at 600ms without early commit, accelerated forward/rewind, short-press combination, canceled/unseekable playback, Back/fullscreen/media reset, zero-offset cancellation, foreign sequences, touch swipes and production replacement callbacks.
- Actual playback fixture generated locally: `ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=320x180:rate=10' -t 120 -an -c:v libx264 -preset ultrafast -crf 32 -g 10 -pix_fmt yuv420p -movflags +faststart build/outputs/cinema-seek-fixture.mp4`; 2,987,039 bytes. Copied only to `/sdcard/Android/data/com.fongmi.android.tv.sourceprobe/files/cinema-seek-fixture.mp4`. Fixture is ignored, disposable build output, not a production asset.
- `VodSeekPlaybackAndroidTest` exercises actual `VideoActivity.dispatchKeyEvent`, EXO, MediaController and production preview state without a third-party source: sends an accumulated +60s held sequence without UP, asserts video position moves by >=58s and reaches READY, preview closes and playback continues; sends late UP and verifies no second seek. Runtime passes 1 test / 13.902s (`build/logs/cinema-seek-playback-runtime.log`); test assembly passes in 5s (`cinema-seek-playback-build.log`).
- Production recipe: `TV_VERSION_CODE=217435000 TV_VERSION_NAME=0.1.8 python3 scripts/cinema-prepare.py` and `python3 scripts/cinema-verify-inputs.py` both PASS (`build/logs/cinema-seek-prepare.log`). Actual Cinema package compile `:app:compileLeanbackArmeabi_v7aDebugJavaWithJavac -PtvApplicationId=com.mjnhmd.tvcinema` passes in 6s / 230 tasks (`cinema-seek-production-compile.log`). `git diff --check` passes. Two test files are explicitly listed in the build manifest and match their copied targets.
- Worktree evidence at 2026-11-10 12:33 +08:00: branch `fongmi`, HEAD `d4068e0f027a`, dirty seek patch/manifest and new tests; source blob `CustomKeyDownVod.java` = `c38d60aaa52ecb125510f40d7e2e73520b61e6fd`. New test SHA256: input `97305cfae00d9b5fa1f485130e858d161feec32ba2e8963ef3e693d42afa92fa`; playback `3d29d28a3645db0e9b41f6bf50406f1fd724df55ae6e486c4919d0be4095f3e0`. Concurrent icon/search/source/danmaku changes are not part of this fix.
- Pre-commit recheck: tested VOD source blob and both test SHA256 values still match the verified inputs above; recipe verifier passes. The user explicitly authorized commit and push with “提交推送” on 2026-11-10. Stage only the seek hunk, two test overlays, corresponding manifest entries/checksum and seek evidence; preserve other work. Commit message: `fix: commit held seeks when remote release is lost`.
- Delivery receipt: committed and pushed `c7a3feaf1a5bea54c25ea122c0643b649af14bac` to `origin/fongmi`; exact remote SHA read back. Selected commit applies cleanly to pristine pinned files and yields all 27 expected source blobs. Every overlay dependency is committed or included; staged patch checksum `d94d1587382c56df992cb36ecd4edfe91430d1ccb48d79c7cea1cc1f574121bb` matches the staged manifest. All 20 CI-helper tests pass (`build/logs/cinema-seek-precommit-tools.log`), index is empty, post-commit working-tree recipe verifier passes and unrelated changes remain local. No new release publication.
- Not tested: user's TV/remote event identities and repeat gaps, ARMv7 runtime/MPV playback, long-session behavior, full Release packaging/install/OTA for this fix. Public release, branch changes and cleanup are outside this authorization.

## Search-result card readability — 2026-11-10
- Scope: ordinary search (`CollectFragment`, including compact results) uses a dedicated native presenter/holder/layout. Source is at poster top-right; bottom score/year and update/source count use stacked full-width lines over a gradient. Two title lines remain outside the image in both focus states. Other poster holders are unchanged.
- Production checks: `python3 scripts/cinema-prepare.py`, `python3 scripts/cinema-verify-inputs.py`, XML parse and `git diff --check` pass. Full patch blob IDs/checksum and overlay copies match the locked production recipe.
- Pre-commit scope verification: the search-only patch applies to pristine pinned source blobs and produces every declared modified Git blob; its checksum and seven overlay entries match. All 20 release-tool contract tests pass (`cinema-search-precommit-ci-tools.log`). Concurrent icon/seek/danmaku changes are excluded from the commit.
- JDK21 / pinned TV+Media3, `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests com.fongmi.android.tv.bean.VodReviewScoreTest --tests com.fongmi.android.tv.bean.VodContentRatingTest -PtvApplicationId=com.mjnhmd.tvcinema`: 10 tests, zero failures (4 new review-score / 6 certification), `BUILD SUCCESSFUL in 17s`. Log: `build/logs/cinema-search-score-tests.log`.
- `:app:compileLeanbackArmeabi_v7aDebugJavaWithJavac -PtvApplicationId=com.mjnhmd.tvcinema`: `BUILD SUCCESSFUL in 33s`, 230 tasks. Log: `build/logs/cinema-search-card-compile.log`. This is source/resource compilation, not a release APK.
- ARM64 disposable app/test APK assembly passes (`cinema-search-instrumentation-build.log`, 2m31s / 530 tasks). Frozen copies under ignored `build/tv-cinema/search-card-validation/` prevent other sessions' builds from changing the installed fixture mid-run. Initial shared-emulator run was interrupted by another session using sourceprobe; the shared emulator was left running. A same-AVD read-only instance was refused by the emulator, so final checks use independent `cinema-search-api28-arm64`, serial `emulator-5562`, Android9/API28 ARM64 / 1280×720.
- Final runtime command: `adb -s emulator-5562 shell am instrument -w -r -e class com.fongmi.android.tv.ui.CinemaSearchCardAndroidTest,com.fongmi.android.tv.bean.VodContentRatingParcelTest -e native_ui_evidence true com.fongmi.android.tv.sourceprobe.test/androidx.test.runner.AndroidJUnitRunner`: all 5 pass, 3.241s (`cinema-search-card-artwork-runtime.log`). Exercises 94/110dp card widths, source badge bounds, stacked metadata, two-line title and stable height across actual D-pad focus, OK/long-click, grouped-source payload refresh, empty/recycled fields, review score/certification/following Parcel fields and nested source candidates.
- Existing full search integration `SearchRelevanceIntegrationTest`: 1 pass, 3.189s (`cinema-search-results-integration.log`). Real loopback HTTP results render through the new search presenter; all three filtering modes reuse cached responses without additional search requests; D-pad/filter/Back focus is restored.
- Final screenshots `build/outputs/search-cards-unfocused.png` and `search-cards-focused.png` inspected. These use actual attached production card widgets and synthetic local bitmap artwork, not a generated UI mockup. The initial placeholder screenshot was replaced after fixing the test fixture to use supported inline bitmap loading; a BitmapDrawable assertion now ensures artwork loaded before capture.
- Not tested: user's Android9 ARMv7 television, real provider score coverage, full ARMv7 Release packaging/installation for this UI, or TV performance. User authorized commit/push for this search increment on 2026-11-10; public release and ppm writes are outside this delivery.

## EXO debug panel wiring — 2026-11-10
- Change: `PlaybackActivity.toggleDebugView()` / `hideDebugView()` / `isDebugViewVisible()` now delegate to `PlayerView` instead of an Activity-only flag; MPV shows the `cinema_debug_exo_only` hint.
- `python3 scripts/cinema-verify-inputs.py`: `PASS: pinned checkouts, native hashes, patches, overlay copies and signing inputs are ready.`
- `:app:compileLeanbackArmeabi_v7aDebugJavaWithJavac` on the patched pinned checkout: `BUILD SUCCESSFUL in 35s`, 230 actionable tasks (9 executed). Resource merge resolved the new string in values / values-zh-rCN / values-zh-rTW.
- Not tested: `adb devices -l` returned no attached device, so panel rendering, R8 release/reflection behavior, engine switching, Back handling and the Android9 ARMv7 television remain not_tested.

## 饭太硬 multi-endpoint source — 2026-11-10
- Endpoint liveness (host `curl --noproxy '*'`, no browser UA proxy tricks): `http://www.饭太硬.net/tv`, `http://www.饭太硬.cc/tv` and `https://www.饭太硬.cc/tv` all return HTTP 200 / 19,951 bytes / ETag `6aefd459-4def` / Last-Modified 2026-11-08. `https://www.饭太硬.net/tv` fails with `tlsv1 alert internal error`. `http://饭太硬.top/tv` and `http://fty.xxooo.cf/tv` do not respond.
- Payload shape: JPEG + `**` + base64; decoding yields 48 `sites`, 6 `lives`, and the same spider `https://xiuxiu-pro-new.meitudata.com/posters/…;md5;…` as the previous 12yue copy, so the transport, not the content, was the change.
- These are host-side liveness checks only. They do not rank endpoints for the user's China Mobile television, which is why the ranking is done at runtime on the device.
- `CinemaSourceProbeTest` (new): picks fastest valid endpoint, skips empty/invalid payloads, reports total failure, stops early at a good-enough latency, times out a stalled endpoint and falls through, accepts official payloads containing `//` comment lines, and keeps `https://` inside string values when stripping comments.
- Focused JVM: `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests 'com.fongmi.android.tv.api.config.*'` → 31 tests, 0 failures.
- Local ARMv7 `TV_VERSION_CODE=217435000 TV_VERSION_NAME=0.1.8` Release: `BUILD SUCCESSFUL in 3m 44s`, 900 tasks; `verify-apk` reports package `com.mjnhmd.tvcinema`, code `217435000`, minSdk24, `armeabi-v7a` only, signer `d405dcab…bdc5`; 91,134,761 bytes, SHA256 `164aadbcb588cdce5d3a68e0b251452a5debd926be45ca058a174ed02545bf97`. Packaged `assets/cinema/sources.json` carries the three endpoints.
- Not tested: install on the 32-bit television, which endpoint wins on its network, latency of the spider/plugin download, and playback through the chosen endpoint. No Git or release operation was performed.

## MPV engine switch crash — 0.1.7
- API28 ARM64, loopback 720p H.264 fixture, `VideoActivity` playing on EXO then `PlayerManager.setEngine(MPV)`:
  - Before: `Fatal signal 11 (SIGSEGV), code 2` in `CodecLooper`; backtrace `CCodecBufferChannel::setSurface` → `CCodec::configure` (`build/logs/cinema-mpv-playback-probe3.txt`).
  - After `MpvHwDecPolicy` selects `mediacodec-copy` on SDK<=28: instrumented switch passes, `event: file-loaded`, position >1s, no SIGSEGV (`cinema-mpv-policy-probe2.txt`).
- JVM `MpvHwDecPolicyTest`: 1 test, 0 failures.
- Local ARMv7 `TV_VERSION_CODE=217433204 TV_VERSION_NAME=0.1.7` signed Release passes package/minSdk/ARMv7/original-signer checks; 91,128,921 bytes, SHA256 `742c45d1398c73214be2672a60506d174ce960a215893589382ee1e5c566a457`.
- Published cloud release `build-42727947198-1`: `0.1.7+20261110.004454` / code `217434694`; downloaded APK 91,125,793 bytes, SHA256 `75276eafeba1305c40eaebcda3b06b2f43559f067db207ba22a35619b121baa0`, matching manifest; `verify-apk` passes package/minSdk24/ARMv7/original signer; latest manifest works through `gh-proxy.com`.
- Not tested: user's 32-bit television, Amlogic hardware decode path, sustained MPV frame rate/memory, and actual in-app update on the TV.

## Navigation and playback preload — 0.1.6
- Focused JVM run covering `source.*`, `ui.home.*` and `model.*`: 18 suites / 216 tests / 0 failures (`cinema-016-compile.log`).
- ARM64 debug build and API28 ARM64 sourceprobe on default Android9/API28 emulator: home nav renders `Home Library Live Search My TV Setting` in one row; tapping `My TV` shows `Keep`, `Watch history`, push, `Cast to this TV` and `Local files`, with no settings card. Screenshots `build/outputs/cinema-016-home-nav.png`, `cinema-016-my-page.png`.
- Fresh isolated install persisted `preload_next=false`, `cinema_low_end_preload_v1=true` and `cinema_profile_initialized=true`; this proves the one-time default, not playback smoothness.
- Local ARMv7 `TV_VERSION_CODE=217408805 TV_VERSION_NAME=0.1.6`: signed Release passes package/minSdk/ARMv7/169-native-payload and original-signer checks; 91,128,333 bytes, SHA256 `f1ea29b76f271771c69b1144834c0ce40b89c65a585e9bf52149d556ee67e953`; exact APK DEX `RELEASE INITIALIZER OK` on API28 (`cinema-api28-release016-initializer.log`).
- Not tested: 32-bit television install/upgrade, actual stutter improvement, codec/bitrate-specific playback, long-session memory/frame pacing, and public OTA for 0.1.6.

## Published Android 9 source crash fix — 0.1.5
- Actions run `42663020367` build and publish succeeded; public latest release `build-42663020367-1` contains `0.1.5+20261109.172651` / code `217408411`, larger than `0.1.4`'s `217397053`.
- Downloaded cloud APK is 91,126,917 bytes, SHA256 `4beac57b89934e3ed416756d2d1d9b92a1cde0f83ba281bd6f06e910f2289fcc`, matching its OTA manifest and release digest; `verify-apk` passes package/minSdk24/ARMv7/original signer, and the exact cloud DEX initializes `SearchRelevance` on API28 (`cinema-api28-cloud015-initializer.log`). User television update/selection/playback remains untested.

## Android 9 source selection crash — 2026-11-10
- User photo: actual TV `MiTV-MFTP0`, Android9/API28, app `0.1.3`, top stack `ExceptionInInitializerError` in `SearchRelevance$Query.<init>` → `PosterSourcesController`; no nested cause visible in screenshot.
- Fail-before: isolated default Android9/API28 ARM64 emulator; installed same-source local `0.1.4`/`10004`, enabled detail sources in that emulator's own data, navigated from home hero into detail and pressed D-pad center on `选择来源`. App entered `CrashActivity`; `build/logs/cinema-api28-source-before.log` records `Caused by: java.util.regex.PatternSyntaxException: U_ILLEGAL_ARGUMENT_ERROR` at `(?:(?<=[\p{IsHan}])|(?<=\s))...` in `SearchRelevance.<clinit>`. Exact released 0.1.3 APK DEX initialized through `app_process` on the same API28 runtime failed identically (`cinema-api28-release013-initializer.log`).
- API28 regex probe (`cinema-api28-regex-probe.txt`) rejected original `\p{IsHan}`, accepted replacement `\p{script=Han}` and exercised Chinese/Latin, standalone numerals and numbered-title cases. Host JDK and API28 matching retain the expected behavior for these bounded cases.
- Fix: only the Unicode script spelling in `distribution/cinema.patch`, with its reviewed blob/hash updated in `distribution/baseline.json`; added the explicit `SearchRelevanceAndroidCompatTest` overlay.
- Focused JVM Gradle tests: `SearchRelevanceTest` 13, `PosterSourceResultsTest` 31, `DiscoverSourceSearchTest` 5 and `SearchResultFilterTest` 8; 57 total, zero failures (`cinema-api28-regex-jvm.log`).
- API28 ARM64 isolated `com.fongmi.android.tv.sourceprobe`: `SearchRelevanceAndroidCompatTest` 1, `detailSourcePanelIsOptionalAndWorksWithoutMetadataOrConfiguredSources` 1, `PosterSourceSearchIntegrationTest` 5, `posterPriorityEntryRestartsSearchAndKeepsFocusStableUntilLeavingResults` 1; all 8 passed. Logs `cinema-api28-regex-instrumentation.log`, `cinema-api28-source-empty-test.log`, `cinema-api28-source-search-fixtures.log`, `cinema-api28-source-priority-test.log`. In a separate interactive sourceprobe run, title `特立独行` produced a visible source panel and 21 candidates; screenshot `build/outputs/cinema-api28-source-fixed.png`. Search availability is not playback proof.
- Final local ARMv7 `TV_VERSION_CODE=217404967 TV_VERSION_NAME=0.1.5 bash scripts/cinema-build.sh --abi armeabi-v7a`: `BUILD SUCCESSFUL in 2m 31s`, 900 tasks; log `cinema-armv7-015-clock-version.log`. `cinema-ci.py verify-apk` reports package `com.mjnhmd.tvcinema`, minSdk24, ARMv7 only, expected version and original signer SHA256 `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`; 169 ELF32/EM_ARM native payloads, 91,129,249 bytes, SHA256 `5005734f53c8e19b67b6731c1dd7c7934c9fcf2ad45137bc119bdb81000856f9`. Exact final release APK DEX initialized on API28 via `app_process`: `RELEASE INITIALIZER OK` (`cinema-api28-release015-final-initializer.log`). The prior local build code `217500000` was superseded with the CI clock formula to avoid blocking subsequent time-derived updates.
- No commit, push or new release in this follow-up; the downloaded API28 system image and isolated AVD remain in ignored `build/`. The test device is API28 ARM64, not the user's 32-bit ARM television. Local APK build, DEX initialization and UI fixtures do not prove device installation/upgrade, third-party playback, sustained performance or OTA download on the user's television.

## Homepage cache and ready-image rotation — 0.1.4
- New JVM `CinemaHeroSnapshotTest`: round-trip fields, HTTPS-only artwork, duplicate-id removal, five-item bound, seven-day expiry and malformed JSON rejection. Focused run also included `PosterHomeCatalogTest`, `HomeSourceStateTest`, `RexHomeCatalogTest`, `RexFeedDateTest`, `BrowseExperienceSettingsTest`, `PreloadBudgetTest`, `MpvVideoOutputRecoveryTest`, `AssHistoryTest` and `HistoryRequestStateTest`; `BUILD SUCCESSFUL`.
- Full `testLeanbackArmeabi_v7aDebugUnitTest`: 1042 tests, 3 failures in `LocalSubtitleScannerTest` (`Episode.srt` expected but `Episode.SRT` enumerated on the case-insensitive macOS test filesystem). This class is untouched by the Cinema patch; the failures are environmental and unrelated to the homepage change.
- Local ARMv7 Release `0.1.4` / `10004`: 91,129,129 bytes, SHA256 `420d447f6e3cc632bc8ded969af40dbcf5f70d2daab2578b6fe40ae05850a953`; `cinema-ci.py verify-apk` passes package, minSdk24, ARMv7-only payload and signer `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`.
- Emulator smoke (API36 ARM64, not the target TV): upgraded from installed `0.1.2`/`10002` to `0.1.4`/`10004` with the existing app data. Online launch showed a five-item hero (`build/outputs/cinema-0.1.4-hero-online.png`). After enabling airplane mode, force-stopping and relaunching, the hero and artwork still appeared from the persisted snapshot/Glide cache (`build/outputs/cinema-0.1.4-hero-offline.png`); no `AndroidRuntime` fatal entry was observed.
- Runtime television acceptance for the 7s slow-image path and Android9 ARMv7 behavior still requires the connected TV. The emulator does not prove hardware decoding, memory pressure or remote-control behavior.
- Public release: Actions run `42631667857` (head `d57dce1ba`) build and publish jobs succeeded. Release `build-42631667857-1` is public, non-draft and marked latest: https://github.com/mjnhmd/tv-cinema/releases/tag/build-42631667857-1
- OTA manifest `latest/download/leanback-coexist.json` returned version `0.1.4+20261109.141733`, code `217397053` (greater than installed `10003` and cloud `217393652`), APK size 91,127,513 and SHA256 `fe3d7c041687d6b7f4bdfc011b02ea43c57da03afae877fc789ff62bd14c1c19` through both direct GitHub and `gh-proxy.com`.
- Downloaded release APK matches that checksum; `verify-apk` passes package `com.mjnhmd.tvcinema`, code `217397053`, minSdk24, ARMv7-only payload and signer `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`. No new version was installed on the user's television in this run.

## Homepage network inspection — investigation only
- Reviewed the materialized production `RexFeedLoader`, `RexNowClient`, `RexTmdbClient`, `PosterHomeController`, `DiscoverHeroPresenter`, `DiscoverApi`, `TmdbNetwork` and `OkGlideModule`. No runtime/source change in this investigation.
- Generated ARMv7 Release BuildConfig has an empty TMDB key; the TMDB list client skips requests without it. Feed identity/label and artwork host must not be conflated with the actual list transport.
- Conditional slow-network hazard established from code: automatic rotation is 7,000ms; changing slides clears pending artwork, while the image transport's call timeout is 20s. This is not evidence that the user's television actually reaches either duration. Existing Glide caching is retained; feed caches are memory-only.
- Host-only curl samples: RexNow trending HTTP200 /31,219B /~0.19s; direct Douban list HTTP200 /5,647B /~0.24s. An initial Douban poster request without Referer returned HTTP418; adding the same Referer/User-Agent used by the app returned HTTP200 /19,137B /~0.03s. Do not treat the headerless failure as app incompatibility.
- Explicit no-HTTP-proxy sample (`curl -q --noproxy '*'`, 12s total/5s connect limit): `/kTp2i00tjARpsI6wTkU4Q8ArGaX.jpg` at w780 returned HTTP200, image/jpeg and JPEG magic from both routes; proxy 50,599B /TTFB1.713s /total1.763s; official host 50,599B /TTFB2.591s /total2.967s. Results: `build/logs/hero-probe-direct-images.json`. One host sample per route, no TV or mainland-ISP acceptance, no exclusion of system-level tunnelling.
- Proposed but not implemented: persistent bounded feed snapshot/cached-first display; wait for ready artwork instead of timed cancellation; next-slide-only prefetch and bounded independent list/image recovery. Android9 ARMv7 behavior remains not_tested.

## Authorized delivery follow-through — cloud and public release
- GitHub readback: public, non-draft, non-prerelease `v0.1.3` contains `leanback-armeabi_v7a-coexist.apk` (91,125,389B; digest matches `1cf4e59cc01f90cf3f8ef19e01a681c4fe058ca156dbb70c06fa709c4f22153f`) and `leanback-coexist.json` (627B).
- Read-only build-worker inspection: Actions run `42624579403`, head `5dcbb7137bb64e61ec467597089efcc0c3397a3f`, build success, publish skipped as configured. Actual artifact downloaded to `build/ci/cloud-run-42624579403/leanback-armeabi_v7a-coexist.apk`; 91,124,333B; SHA256 `688514095ae54cf3639a1548a2662e1be5ed602ff65a1d9d4b5453f935946041`.
- Actual cloud APK `apksigner verify` and `cinema-ci.py verify-apk` pass: package `com.mjnhmd.tvcinema`, minSdk24, targetSdk37, non-debuggable, ARMv7-only native payload, v2 signature. Signer SHA256 `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5` equals the published local APK. Same signer is not byte-for-byte reproducibility: cloud name/code `0.1.3+20261109.132052`/`217393652` differ from public local `0.1.3`/`10003`.
- Target-TV installation/upgrade, playback/performance, and mainland download/in-app-update end-to-end are still not_tested. No new Git or remote mutation in this inspection.

## Current follow-up — 0.1.3

- Production command: `TV_VERSION_CODE=10003 TV_VERSION_NAME=0.1.3 bash scripts/cinema-build.sh --abi armeabi-v7a`.
- Final corrected build: `BUILD SUCCESSFUL in 2m 29s`, 900 tasks; log `build/logs/cinema-armv7-013-corrected.log`.
- Signed Release: `com.mjnhmd.tvcinema`, version `0.1.3` / code `10003`, minSdk24, targetSdk37, non-debuggable.
- ARMv7 only: 38 `.so` files under `lib/armeabi-v7a/`; CI helper reads each ELF header and requires ELF32, little-endian and EM_ARM (40).
- Full native audit also covers bootstrap assets, `assets/libmitv.so` and Python `.imy` archives: 169 native payloads total, all ELF32/little-endian/EM_ARM, zero foreign-ABI payloads (`cinema-armv7-full-native-audit.json`). The same nested-asset checks are included in the CI helper.
- APK size: 91,125,389 bytes; SHA256 `1cf4e59cc01f90cf3f8ef19e01a681c4fe058ca156dbb70c06fa709c4f22153f`.
- Canonical output: `build/outputs/tv-cinema-armv7.apk`; downloadable local staging: `build/ci/release/leanback-armeabi_v7a-coexist.apk`.
- V2 signature verifies; certificate SHA256 `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`, matching the existing ARM64 Cinema release key.
- Real-APK helper `verify-apk` and `stage` pass; SDK37 `V2 Signer:` output is supported and any additional differing signer certificate is rejected. Logs: `cinema-armv7-013-final-{stage,signature,badging}.log`, `cinema-armv7-013-final.sha256`.
- Latest focused JVM suites: 98 tests, zero failures/errors (`cinema-jvm-final-identity.log`, 18s); covers row packing, navigation-list guard, source merge, wallpaper parsing and updater checks. The relative-rule collision regression first failed, then passed after resolving ext before dedupe identity.
- CI helper: 19 unittest cases pass (`cinema-ci-unit-final.log`), including foreign-ABI rejection inside native assets and Python archives, plus workflow YAML/contract review. This is not a GitHub runner execution.
- API36 ARM64 sourceprobe: ten episode UI cases and the then-current two source cases pass together (12 tests, 78.912s, `cinema-final-focus-safe-runtime.log`). Latest discovery-only run passes three cases (7.071s, `cinema-discovery-final-contract-runtime.log`), including actual settings/catalog selection, preservation on failure/refetch and two separate provider-relative rules without fetching plugin code.
- UI evidence: `build/outputs/episode-flow-{mixed,overflow-idle,overflow-focused}.png`, `source-settings-manual-action.png`, `source-catalog-merged-option.png`. These are Android screenshots of production widgets in an isolated test package, not mockup images.
- Emulator first-focus observations for 100/300/1000 titles: 265/256/467ms (`cinema-episode-metrics.log`); do not treat these as ARMv7 timing guarantees.
- No user television is connected: Android9 ARMv7 installation/playback/memory/frame performance remains `not_tested`.
- User authorized this commit/push/signing/publication batch. Five signing secrets are configured with name-only readback; v0.1.3 release metadata is staged, not yet publicly delivered. First cloud run, public Release/mirror downloads and actual update installation remain pending. Version-tag/draft-publication contracts pass 20 tool tests before the authorized commit.

## Historical production result — 0.1.0

The earlier root-application failures below are preserved as historical evidence.
Production now uses `distribution/baseline.json` and `scripts/cinema-build.sh`,
not the root application's missing private Media3 AARs.

### Pinned paired build and artifact
- TV `17abcd7453a0ecbdbc9ed40a22f3fd3632158495`, Media3 `342f6590e02ac390e0bd6b4488b8a1bff55747c1`.
- Official SDK NDK r29 installation repaired; source build used JDK21, Python3.10, SDK37.0 and Gradle9.1.0.
- Paired ARMv7 Debug baseline: complete assemble passed, 498 tasks / 5m21s.
- Cinema overlay Kotlin/Java increments passed before full packaging.
- `bash scripts/cinema-build.sh`: signed ARMv7 Release passed with R8 and resource shrinking enabled.
- A missing real Pine dependency was resolved by `org.lsposed.hiddenapibypass:hiddenapibypass:6.2`; no missing-class suppression or player stubs were added.
- Final wrapper run: `BUILD SUCCESSFUL in 11s`, 900 tasks, package/version/ABI/minSdk/signature verification passed.
- `bash scripts/cinema-build.sh --abi arm64-v8a`: same-source optimized ARM64 Release passed, 900 tasks / 7m49s; separate runtime-verification artifact only.
- Both variants: `com.mjnhmd.tvcinema`, version `0.1.0` / code `10000`, minSdk24, targetSdk37, non-debuggable, v2 signature verified.
- ARMv7: 91,085,901 bytes, exactly 38 native libraries, each ELF32 / EM_ARM, only `lib/armeabi-v7a/`.
- ARMv7 SHA-256: `e0289fb10b76b6c27e4def2eb2aea4dce8e1dfde473722e454178548dfedffdd`.
- ARM64: 94,760,109 bytes; SHA-256 `d1d129a14d7c53f9a3df5a35e15a5887e3a10ad18bc824394fac3ba35aafc67b`.
- Delivery: `build/outputs/tv-cinema-armv7.apk`; native assets and all six source preset entries inspected.
- Logs: `build/logs/cinema-armv7-release.log`, `cinema-recipe-armv7-final.log`, `cinema-recipe-arm64.log`.

### Tests and input safeguards
- Media3 patch protection: 9 Python tests passed.
- Five focused upstream suites: 26 tests, zero failures/errors. Covers preload budget, MPV video-output recovery, ASS history, history request state and home source state.
- Command: paired Gradle `:app:testLeanbackArmeabi_v7aDebugUnitTest` with those five `--tests` filters; `BUILD SUCCESSFUL in 29s`.
- Both MPV native bridges retained all 23 JNI methods and resolved 47 media symbols against the recorded FFmpeg9 runtime; native ZIP hashes matched pinned values.
- Production prepare and verify passed; idempotent settings handling and refusal of unexpected settings edits / extra TV paths / changed patch-target blobs were exercised.
- Artifact selection reads Gradle `output-metadata.json` and requires the requested version, package and one declared output rather than picking an arbitrary stale APK.
- APK tools' `minSdkVersion` spelling is explicitly handled; the initial wrapper-only parsing failure was fixed and final wrapper rerun passed.
- Local signing configuration files are mode0600; no signing values, native archives or APKs are tracked in Git.

### Actual runtime smoke — API36 ARM64 emulator, not target TV
- Installed our source-built R8 ARM64 release, independently from the separately downloaded reference release.
- Cold launch completed (`am start -W`, 2443ms observed once); this emulator number is not a television startup benchmark.
- Notification prompt handled; Cinema first-run source disclosure visible with both choices. Selected self-add for controlled testing; preset provider plugins were not used as playback proof.
- Captured home, onboarding, settings and playback screenshots from the running app.
- D-pad navigation reached My TV and Settings; focus visible, Back returned from playback to home/history.
- Controlled H264/AAC fixtures generated locally with ffmpeg and served only on loopback: 12-second 720p clip reached the end, and a 90-second 360p clip reached PLAYING, PAUSED at65064ms, then resumed PLAYING at65088ms with `error=null`.
- No uncaught exception appeared in the crash buffer during the observed flows.
- One emulator memory snapshot: PSS161955KiB / RSS289948KiB; not a low-end-TV heap/performance pass.
- Evidence files: `build/logs/cinema-runtime-media.txt`, `cinema-paused-state.txt`, `cinema-resumed-state.txt`, `cinema-runtime-memory.txt`, `reference-probe-http.log`; screenshots under `build/outputs/`.

### Still not tested
- Android9 ARMv7 television installation, hardware decoding, remote focus quirks, frame pacing, memory pressure and long sessions.
- All public preset plugins, provider availability, external live channels, DRM, ISO/BD-J or complete feature equivalence with FongMi's unpublished APIs.
- Visual acceptance by the user and full nonlocal media/source end-to-end coverage.

## Earlier root application investigation

## Environment
- macOS host; JDK 21.0.10 bundled with Android Studio.
- Gradle wrapper 9.7.1, AGP 9.4.0, Android SDK / Build Tools 37.
- Upstream FongMi/TV 4512b4c9f82534ff66acfde06ccdd68f35b074f0, version 5.8.7.
- No connected Android 9 ARMv7 television.
- Existing API 36 ARM64 Pixel emulator booted and its API/ABI queried. No redesigned APK was installed; this is not a UI smoke pass.

## Passed

### Resource linking and crawler library compilation

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew -PcinemaHostOnly=true \
  :app:processLeanbackReleaseResources \
  :catvod:compileReleaseJavaWithJavac --console=plain
```

Final result: `BUILD SUCCESSFUL in 3s`, 26 actionable tasks.
Local complete log: `build/logs/cinema-resource-link.log` (ignored).

- Final merged manifest: `com.mjnhmd.tvcinema`, minSdk 24, targetSdk 37.
- Application label is TV Cinema; touchscreen and leanback remain optional hardware declarations.
- minSdk 24 includes API 28 as a declared installation target, not tested installation evidence.
- The generated ARMv7 and ARM64 resource packages are resource outputs, not installable application APKs.

### Static UI and preset checks
- All 207 television resource XML files parsed successfully.
- Original view binding IDs/types preserved across the modified layouts; additions explicitly named.
- Normal and focused item/chip/round/live selectors have identical padding.
- Six preset subscription URLs parse as HTTPS and have no duplicates.
- Shell build script and Python input verifier parse successfully.
- `git diff --check` passed.
- Source initialization reviewed: nonempty existing configurations prevent seeding, inserts are transactional, marker is written after success, deleted presets are not automatically recreated.

## Failed / blocked

### Complete release build

```sh
./gradlew -PcinemaHostOnly=true :app:assembleLeanbackRelease
```

`verifyLeanbackReleaseMpvNative` rejects the trial legacy native AARs:
`libmpv.so must use unified libffmpeg.so for armeabi-v7a`.
Log: `build/logs/probe-host-only.log`.
The temporary diagnostic bypass was removed; release native verification is enforced.

### Application Java compilation

```sh
./gradlew -I build/cinema-diagnostics.gradle -PcinemaHostOnly=true \
  :app:compileLeanbackReleaseJavaWithJavac --console=plain
```

Fails on missing/incompatible custom Media3 types and methods, including
`DecoderMode`, `DiscMenuController`, `LibassConfiguration`,
`LibassSubtitleController`, `IsoDiscPreparation`, `SecondaryTextOutput`
and video/danmaku controllers. Local log: `build/logs/cinema-java-diagnostics.log`.
An absent diagnostic for a changed file does not establish a successful application build.

### Input gate

`python3 scripts/cinema-verify-inputs.py` exits 2 with the missing class list,
as expected for the currently supplied legacy AARs. It prevents presenting them as valid release inputs.

## Dependency research boundary
- Current FongMi/media `release` and `release-1.11.0-fongmi` branches do not supply the app's full current custom API surface.
- Official media Actions artifact 9131565941 (`media3-libass-aar`, run 31571172760, commit 9dae39e9689ee305c83f94bd11cf3bcecb9d6171) was downloaded and inspected as an archive. It contains an older LibassPlaybackSession but lacks the required Configuration/Controller interfaces.
- FongMi/mpv-android, FFmpeg, bdj-runtime, python-android, quickjs-wrapper, quickjs and nodejs-mobile were inspected for matching inputs. Runtime/native components do not substitute for missing Media3 Java classes.
- FongMi/Release currently offers APKs and separate runtime APKs, not the full matching AAR input set discovered by this audit.
- Legacy AARs used for diagnosis came from a public fork and remain ignored; they are not approved final dependencies.
- No upstream APK was re-signed or presented as this redesign, no fake player interfaces were introduced, and no playback features were removed to force compilation.

### Historical baseline cross-check

- Isolated 5.2.9 checkout: ccce4b5ded0613ea6b532796fd61c5f477c5c2cc, `/tmp/tv-529-build`.
- After supplying legacy plugin inputs, `assembleLeanbackArmeabi_v7aRelease` failed with 14 Java errors.
- Remaining missing APIs include `SubtitleView` text-size/position helpers, `MediaItem.Builder.setDecode(int)` and `NextRenderersFactory.setAudioPrefer(boolean)`.
- Log `/tmp/tv-529-assemble-3.log` was inspected; no APK was generated.
- The earlier "no media diagnostics" partial probe is retracted. Incomplete diagnostics masked additional media incompatibility.
- Official media repository history was also searched for the affected SubtitleView/MediaItem helpers, with no matching implementation found in the fetched public refs.
- A separate 5.5.6 probe also failed on media APIs. Neither older version is an approved or verified fallback.

## Earlier baseline not tested
- Full redesigned application launch and actual appearance.
- D-pad / OK / Back behavior, playback, source plugins, live channels, database lifecycle in an installed app.
- Android 9 ARMv7 installation, final APK signature and native payload.
- Cold start, memory, frame timing, long-running playback or hardware decoder behavior on the television.
- Visual design acceptance.
