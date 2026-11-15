# Cinema Android mobile verification — 2026-11-13

## Delivered local artifacts

Both APKs use package `com.mjnhmd.tvcinema` (unified with the television edition on 2026-11-14 so the
existing `TVCinema` Bugly product accepts phone reports), label `TV Cinema`, minimum SDK24, target SDK37,
version `0.1.0+20261113.103519` / code217729319. They are signed, optimized Release builds with their own
`mobile-*` OTA identity; because the package is shared they cannot be installed next to the TV APK.

| ABI | Local output | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| arm64-v8a | `build/outputs/cinema-mobile-arm64.apk` | 75,405,096 | `538f1044c32084fe3e77d9d9b9659a830234987c2543a8f47a463e863511ab2f` |
| armeabi-v7a | `build/outputs/cinema-mobile-armv7.apk` | 72,605,106 | `0946cc087f15f1b3e9a416f95560bbfba6617592dfe6df6a5ab58747b34d90cd` |

- Original Cinema signer SHA-256: `d405dcabe3e9507a7e33eb3fdf2d61de03e27b6e4bc80099b5b1d054f0d8bdc5`.
- ARM64: 34 stripped `lib/arm64-v8a/` libraries including Bugly's `libBugly_Native.so`; native runtime/Python audit finds164 ARM64 ELF payloads.
- ARMv7: 37 stripped `lib/armeabi-v7a/` libraries including Bugly's `libBugly_Native.so`; full native/assets/Python audit finds168 ARM32 ELF payloads.
- Both: two dependency archives with third-party test directories excluded and Chaquopy asset hashes matching.
- The inherited Forcetech `assets/libmitv.so` remains ARM32 in the ARM64 APK. It belongs to optional `mitv:` P2P handling, not the EXO H.264/AAC smoke path. This protocol is not validated for ARM64-only phones; no vendor binary was replaced or removed.
- AAPT re-encodes the icon PNGs. The opaque icon retains identical pixels; the transparent mark renders identically on both black and white after encoding. Fully transparent RGB normalization changes raw byte identity without changing visible artwork.

## Build and shared regression evidence

Environment: macOS Apple Silicon, Android Studio JBR21, Python3.10, SDK37 and NDK29,
the existing local Cinema signing configuration, pinned TV17abcd745 / Media3342f6590.
No key/password values were printed or added to source.

| Check | Command / log | Result |
| --- | --- | --- |
| Initial mobile Java/resources | `:app:compileMobileArm64_v8aDebugJavaWithJavac`; `build/logs/cinema-mobile-initial-compile.log` | PASS,37s /229 tasks |
| Build-tool regressions | `python3 -m unittest discover -s scripts/tests -p 'test_cinema_*.py'`; `cinema-mobile-tools.log` | PASS,29 tests |
| Shared update/source regressions | `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests 'com.fongmi.android.tv.update.*' --tests 'com.fongmi.android.tv.api.config.Cinema*Test'`; `cinema-mobile-shared-tests.log` | PASS,110 tests, zero failures/errors |
| Final ARM64 Release | `bash scripts/cinema-build.sh --mode mobile`; `cinema-mobile-wallpaper-arm64.log` | PASS,900 tasks |
| Final ARMv7 Release | `bash scripts/cinema-build.sh --mode mobile --abi armeabi-v7a`; `cinema-mobile-wallpaper-armv7.log` | PASS,900 tasks |
| Manifest/signer/payload | `aapt2 dump badging`, `apksigner verify --print-certs`, `cinema-packaging.py` plus recursive ELF audit | PASS, exact identity/ABI/version/signer and packaging evidence above |
| Actual signed ARM64 Release smoke | `adb install -r`, offline `am start -W`, native layout dump and Settings tap; `cinema-mobile-release-start.log` | PASS, cold start1854ms, correct Cinema logo/default source, both source rows reachable, no AndroidRuntime fatal output |
| Shared-package rebuild with Bugly (2026-11-14) | `bash scripts/cinema-build.sh --mode mobile` and `--abi armeabi-v7a` | PASS, package `com.mjnhmd.tvcinema`, 34/37 stripped libs incl. `libBugly_Native.so`, signer and ABI checks pass |
| Update routing after package unification | `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests 'com.fongmi.android.tv.update.*'`; `CinemaUpdateTest` 3 tests | PASS, `mobile-*` manifests/assets still accepted only for the `mobile` mode |
| Build-tool regressions after package unification | `python3 -m unittest discover -s scripts/tests -p 'test_cinema_*.py'` | PASS,29 tests |
| Production inputs | `python3 scripts/cinema-verify-inputs.py` | PASS,81 unique overlays, including18 mobile overlays |

Builds use the version/code above through `TV_VERSION_NAME` / `TV_VERSION_CODE`.
TV remains the wrapper's default mode with its original package/output. No new TV Release was required or produced.
Production patch SHA-256: `0a4b2005bda256e5be77048bcda7d5ba1c530708bc62f6a92d7a86e57b09f88e`.

## Controlled mobile runtime

- Device: project-owned `cinema-mobile-api36-arm64`, emulator5556, Android16/API36, ARM64,
  phone display1080×2280. This is neither ARMv7 nor a physical-phone acceptance result.
- Package: `com.mjnhmd.tvcinema.mobile.probe`, a same-source Debug app isolated from the signed delivery.
- Test sources: only the explicitly copied `app/src/cinemaMobileTest/` files, selected by
  `distribution/cinema-mobile-test.init.gradle`. Existing shared TV tests stay intact.
- Build with `:app:assembleMobileArm64_v8aDebug :app:assembleMobileArm64_v8aDebugAndroidTest`,
  both init scripts and `-PtvApplicationId=com.mjnhmd.tvcinema.mobile.probe`.
- Run with `adb -s emulator-5556 shell am instrument -w -r -e class
  com.fongmi.android.tv.api.config.CinemaMobileUiAndroidTest,com.fongmi.android.tv.api.config.CinemaMobileDiscoveryAndroidTest,com.fongmi.android.tv.api.config.CinemaMobilePlaybackAndroidTest
  com.mjnhmd.tvcinema.mobile.probe.test/androidx.test.runner.AndroidJUnitRunner`.
- Full phone suite including wallpaper on the committed-source state: PASS,8 tests /33.518s; `build/logs/cinema-mobile-commit-runtime.log` (earlier run 30.279s). `build/logs/cinema-mobile-profile-runtime.log` (6 navigation/discovery/update/playback + 2 wallpaper).
- Wallpaper-focused offline run: PASS,2 tests /5.248s; `build/logs/cinema-mobile-wallpaper-runtime.log`.
- Focused actual system Back verification: PASS,1 test /9.312s; `build/logs/cinema-mobile-real-back-runtime.log`. The current test uses UIAutomator Back with UI settling, asserts the detail Activity remains open and playback continues, and records playWhenReady reasons.
- Final complete suite with this actual-system-Back test: PASS,6 tests /24.658s; `build/logs/cinema-mobile-release-runtime.log`.

Verified behaviors:
- Actual phone navigation/settings/catalog, Cinema label and cancelling the discovery confirmation without changing the current subscription/candidate; seeding preserves existing user configuration.
- Controlled manual fetch success/failure, bounded candidate storage, no automatic activation and no plugin JAR download.
- Two providers retain their distinct relative rules. Explicitly tapping the merged catalog entry activates it; refetch uses the other slot and preserves the active content.
- Native mobile update dialog renders route/progress/rate/ETA; Back invokes cancellation.
- Local120s/320×180 H.264 +48kHz AAC fixture decodes real nonuniform frames; audio track is selected.
  Native phone play/pause actions, relative seek, fullscreen landscape geometry and Back preserve playback/player identity.
  Player actions use the production callbacks; catalog/navigation/system Back use UIAutomator. Emulator audio output is disabled.
- Online wallpapers use the same six providers as TV: refresh re-fetches one reachable provider, replaces the cached snapshot atomically, and preserves the current wallpaper/snapshot when all providers fail. Offline test asserts no fake success and unchanged config/cache.
- Cinema defaults apply on phone: amber accent (visible on the selected tab in `cinema-mobile-wallpaper-settings.png`), danmaku on by default and next-episode preload off. The existing phone layout is otherwise unchanged.
- Settings/update screenshots were inspected: `build/outputs/cinema-mobile-settings.png` and `cinema-mobile-update.png`.
- The actual signed ARM64 delivery APK also installed and started with networking disabled on the task-owned emulator.
  Home showed the Cinema logo and seeded 饭太硬 source; tapping Settings exposed both `内置片源` and `手动抓取片源`.
  Evidence: `cinema-mobile-release-layout.json`, `cinema-mobile-release-settings-layout.json` and
  `cinema-mobile-release-crash-check.log`. This smoke does not verify real third-party streams.
- Playback screenshot initially caught a rotation transition, so the test now waits for actual landscape/full-display geometry.
  The OS first-use fullscreen tutorial was confirmed on the task-owned emulator with
  `immersive_mode_confirmations=confirmed`. This changes the disposable test environment, not the app.

## TV-only features intentionally not on phone
- `CinemaHeroSnapshot`/`DiscoverHeroPresenter` cached hero rotation, `CinemaSearchCard`/`CinemaSearchPresenter` remote-focused result cards,
  `JetStreamEpisodeGrid` measured natural-width episode rows and the Leanback D-pad motion tuning depend on Leanback/remote UI.
  The phone keeps its own touch card, search and episode components; porting them would be a UI redesign, which was deferred by the user.
- Everything shared by both flavors is active on phone: source catalog/discovery/probing, playlist guard, watch history/favorites,
  adaptive update download with mobile OTA routing, EXO debug hint, online wallpapers and Cinema defaults.

## Failed attempts and remaining boundaries

- Existing Pixel_9_Pro AVD starts but APK installation fails for insufficient internal storage.
  No existing data/packages were removed; the project-owned phone AVD uses the installed image.
- Initial UI test fixture failed because inherited DoH refuses `localhost`; changing the fixture to numeric127.0.0.1
  then exposed MockWebServer reverse DNS on the main thread. Building fixture URLs from the assigned port fixes both.
  These were fixture errors; application DNS behavior was not changed.
- Immediate synthetic Back-dispatch reruns intermittently failed the continuing-playback assertion, including one run after
  confirming the tutorial. The tutorial alone is not a proven cause. The current test uses real system Back and waits for UI
  settling; that path passes. No production playback code was changed on the basis of the intermittent synthetic-input result.
- Review found no P1. A reported overlay/generated-test mismatch was from an intermediate snapshot; final prepare/verify and
  byte comparison reconcile the current test source. All added files are visible as local untracked source until a future authorized commit.
- Physical phone/ARMv7 runtime, real third-party VOD/live/protocol compatibility, subjective phone UX and long playback: `not_tested`.
- Actual public mobile OTA download/Android installer round trip: `not_tested`; no mobile manifest/APK has been published yet.
  The workflow now builds/stages/publishes TV and phone in one Release; a dressed-rehearsal `stage` with the real TV and
  phone APKs produced both manifests (`leanback` + `mobile`) under one tag, and 23 CI helper tests pass.
- No commit, push, branch, signing-secret change or public release was performed. All task changes remain local.
- Task-owned emulator5556 was stopped after verification. Existing AVD data and the new AVD files remain intact.
- Final mobile source fingerprint: `800edeb54205126c29a2e580978c3a5ca0c5e2c1243f27c414e64fbd5f7460b8`,
  derived from the sorted source hashes in `build/logs/cinema-mobile-final-inputs.sha256`. Branch/HEAD remain
  `fongmi` / `e219421023ca6a3b7b7c1e833ba45c72adcd8637`.
