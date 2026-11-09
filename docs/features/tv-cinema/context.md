# TV Cinema: television redesign

## User goal and original request
- Replace the unattractive FongMi/TV television UI and reconsider its interaction model.
- Use recent.design, qfw66/xiugou-tv and incldue/CineFlow as references.
- Deliver an installable APK for a 32-bit ARMv7 Android 9 television, with old-device performance as a first-class requirement.
- On 2026-11-09 the user explicitly authorized creating a GitHub project and committing/pushing changes as work proceeds.

## Scope and acceptance
- Preserve native playback, source configuration, live TV, search, history, favorites and settings.
- Redesign the television shell, browse cards, search, detail/episode interface, player chrome, live channel navigation and shared dialogs.
- Use native Views/Leanback, system fonts, restrained near-black surfaces and clearly differentiated focus/selected states.
- All primary actions must be reachable with D-pad, OK and Back; focus must not shift measured layout.
- Build a separate signed app; inspect APK ABI, min SDK, manifest and signature.
- Exercise launch, empty state, settings, browse/search/detail, focus and Back with the available runtime.
- Measure available startup/memory/frame evidence; television playback and ARMv7 performance remain not_tested until the actual device is connected.
- Never bundle unauthorized media sources or copy proprietary reference assets.

## Current state
- Local checkout: `/Users/mengjingnan/AndroidProjects/tv-cinema`.
- Upstream: https://github.com/FongMi/TV, branch `fongmi`, commit `4512b4c9f82534ff66acfde06ccdd68f35b074f0`, version 5.8.7.
- Upstream GPL-3.0 remains in force. Source history is preserved.
- GitHub authenticated owner is `mjnhmd`; intended private repository is `mjnhmd/tv-cinema`.
- ppm exact search for FongMi returned no hits. Registration will use the explicitly created local checkout and actual remote, not directory-name inference.
- No Android device is currently connected. Only an API 36 ARM64 emulator image is installed; it is not Android 9 ARMv7 evidence.

## Decisions and reasons
- Retain FongMi native player/runtime contracts: framework replacement risks both source compatibility and performance on the user's old TV.
- Use one quiet cinema language rather than desktop glass/blur/parallax: long-distance legibility and predictable focus matter more than visual effects.
- Independent package/signing: no overwriting of the installed upstream application or its user data.
- Reference projects are desktop applications, not TV implementations. Borrow information hierarchy and restrained motion, not their mouse/hover/window assumptions.
- Preserve current feature entry points while changing presentation; do not silently remove power-user functions.
- Build inputs and signing material stay ignored; all reproducible source and rules must be tracked despite upstream ignore rules.

## Evidence and gaps
- `adb devices -l`: no connected devices, 2026-11-09.
- Upstream app configuration: leanback/mobile flavors; native splits arm64-v8a and armeabi-v7a; minSdk 24; compile/target SDK 37; AGP 9.4.0; Gradle 9.7.1; Java 21.
- Installed SDK platforms currently reach API 36, Java 21 is bundled with Android Studio.
- Source checkout only includes hook/tvbus AARs. Media3/native player AAR inputs are absent and require investigation before claiming a successful build.
- Public Release 5.8.7 includes a leanback ARMv7 APK, but an upstream APK is not this redesign's build evidence.
- Current source tests are excluded by upstream `.gitignore`; regression checks introduced here must be explicitly tracked.

## Next steps
1. Create the authorized private GitHub repository and ppm requirement.
2. Establish a reproducible baseline build with the actual upstream media dependencies.
3. Implement native TV design tokens and shell, verify, then continue through feature surfaces.
4. Validate release artifact and runtime navigation, publish source commits and the installable APK if successful.

## Not yet verified
- Android 9 installation and hardware decoding on the user's ARMv7 television.
- Real-device frame pacing, heap pressure, remote behavior and long-session playback.
- User acceptance of visual design; screenshots will be supplied when actual UI can run.
