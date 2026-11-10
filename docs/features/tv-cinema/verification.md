# Verification record — 2026-11-09

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

## 饭太硬 multi-endpoint source — 2026-11-10
- Endpoint liveness (host `curl --noproxy '*'`, no browser UA proxy tricks): `http://www.饭太硬.net/tv`, `http://www.饭太硬.cc/tv` and `https://www.饭太硬.cc/tv` all return HTTP 200 / 19,951 bytes / ETag `6aefd459-4def` / Last-Modified 2026-11-08. `https://www.饭太硬.net/tv` fails with `tlsv1 alert internal error`. `http://饭太硬.top/tv` and `http://fty.xxooo.cf/tv` do not respond.
- Payload shape: JPEG + `**` + base64; decoding yields 48 `sites`, 6 `lives`, and the same spider `https://xiuxiu-pro-new.meitudata.com/posters/…;md5;…` as the previous 12yue copy, so the transport, not the content, was the change.
- These are host-side liveness checks only. They do not rank endpoints for the user's China Mobile television, which is why the ranking is done at runtime on the device.
- `CinemaSourceProbeTest` (new): picks fastest valid endpoint, skips empty/invalid payloads, reports total failure, stops early at a good-enough latency, times out a stalled endpoint and falls through, accepts official payloads containing `//` comment lines, and keeps `https://` inside string values when stripping comments.
- Focused JVM: `:app:testLeanbackArmeabi_v7aDebugUnitTest --tests 'com.fongmi.android.tv.api.config.*'` → 31 tests, 0 failures.
- Local ARMv7 `TV_VERSION_CODE=217435000 TV_VERSION_NAME=0.1.8` Release: `BUILD SUCCESSFUL in 3m 44s`, 900 tasks; `verify-apk` reports package `com.mjnhmd.tvcinema`, code `217435000`, minSdk24, `armeabi-v7a` only, signer `d405dcab…bdc5`; 91,134,761 bytes, SHA256 `164aadbcb588cdce5d3a68e0b251452a5debd926be45ca058a174ed02545bf97`. Packaged `assets/cinema/sources.json` carries the three endpoints.
- Not tested: install on the 32-bit television, which endpoint wins on its network, latency of the spider/plugin download, and playback through the chosen endpoint. No Git or release operation was performed.

## EXO debug panel wiring — 2026-11-10
- Change: `PlaybackActivity.toggleDebugView()` / `hideDebugView()` / `isDebugViewVisible()` now delegate to `PlayerView` instead of an Activity-only flag; MPV shows the `cinema_debug_exo_only` hint.
- `python3 scripts/cinema-verify-inputs.py`: `PASS: pinned checkouts, native hashes, patches, overlay copies and signing inputs are ready.`
- `:app:compileLeanbackArmeabi_v7aDebugJavaWithJavac` on the patched pinned checkout: `BUILD SUCCESSFUL in 35s`, 230 actionable tasks (9 executed). Resource merge resolved the new string in values / values-zh-rCN / values-zh-rTW.
- Not tested: `adb devices -l` returned no attached device, so panel rendering, R8 release/reflection behavior, engine switching, Back handling and the Android9 ARMv7 television remain not_tested.

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
