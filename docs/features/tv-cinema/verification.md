# Verification record — 2026-11-09

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

## Not tested
- Full redesigned application launch and actual appearance.
- D-pad / OK / Back behavior, playback, source plugins, live channels, database lifecycle in an installed app.
- Android 9 ARMv7 installation, final APK signature and native payload.
- Cold start, memory, frame timing, long-running playback or hardware decoder behavior on the television.
- Visual design acceptance.
