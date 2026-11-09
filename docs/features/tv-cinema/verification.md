# Verification record — 2026-11-09

## Current production result

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
