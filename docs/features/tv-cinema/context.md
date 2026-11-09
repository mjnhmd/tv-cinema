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
- Private repository created: https://github.com/mjnhmd/tv-cinema, default work branch `fongmi`.
- Rules/scope commit `dfde9bd89` pushed and read back through GitHub API. SSH is authorized; HTTPS OAuth lacks workflow scope for the inherited website workflow, so origin uses SSH without history rewriting.
- ppm requirement: `REQ-0007`, project `github.com/mjnhmd/tv-cinema`.
- ppm exact search for FongMi returned no hits. Registration will use the explicitly created local checkout and actual remote, not directory-name inference.
- No television is connected. An existing API 36 ARM64 emulator is being started for UI smoke tests; it is not Android 9 ARMv7 evidence.

## Decisions and reasons
- Retain FongMi native player/runtime contracts: framework replacement risks both source compatibility and performance on the user's old TV.
- Use one quiet cinema language rather than desktop glass/blur/parallax: long-distance legibility and predictable focus matter more than visual effects.
- Independent package/signing: no overwriting of the installed upstream application or its user data.
- Reference projects are desktop applications, not TV implementations. Borrow information hierarchy and restrained motion, not their mouse/hover/window assumptions.
- Preserve current feature entry points while changing presentation; do not silently remove power-user functions.
- Lightweight mode defaults on: suppress animated wallpaper, focus zoom and looping empty-state artwork; bounded TV-only Glide memory cache (16 MiB) and bitmap pool (8 MiB), no image fade, bounded logo decoding. Wallpaper remains available when the mode is disabled, and wallpaper controls are hidden when ineffective.
- Fork-specific version checking must not offer a FongMi APK signed for a different package. Television version action explains that updates come from this fork's Releases.
- Build inputs and signing material stay ignored; all reproducible source and rules must be tracked despite upstream ignore rules.

## Evidence and gaps
- `adb devices -l`: no connected devices, 2026-11-09.
- Upstream app configuration: leanback/mobile flavors; native splits arm64-v8a and armeabi-v7a; minSdk 24; compile/target SDK 37; AGP 9.4.0; Gradle 9.7.1; Java 21.
- Installed SDK platforms currently reach API 36, Java 21 is bundled with Android Studio.
- Source checkout only includes hook/tvbus AARs. Trial legacy Media3/native AARs were downloaded locally for diagnosis, but they do not match 5.8.7; native verification and full Java compilation fail. They are not accepted release inputs.
- Public Release 5.8.7 includes a leanback ARMv7 APK, but an upstream APK is not this redesign's build evidence.
- Current source tests are excluded by upstream `.gitignore`; regression checks introduced here must be explicitly tracked.
- Targeted XML parsing and `git diff --check` passed after each local increment. Item/chip/round/live normal/focused drawable pairs have identical padding. Android resource linking and catvod Java compilation passed; full application Java compilation remains blocked by media dependencies.
- Evidence and exact verification limits are recorded in `verification.md`.

## Requirement additions
- User added https://github.com/gitzcs/TVBoxAPP as a reference, asking about automatic fetching/merging of sources. Read-only review found background configuration discovery, cached results and source merging with per-site jar rewriting. No code was copied. Presets, not blind background discovery or rule merging, are the current implementation.
- User explicitly allowed preset subscriptions from https://0.12yue.de5.net/.
- Actual site format: `/config.json` describes `list.txt` and `/tvbox/` mirror directories. Root name.json is HTML, not a TV configuration. Each list row is `name.json|date|size|originalUrl`.
- Chosen snapshot: five separately stored VOD subscriptions (饭太硬、南风、心魔、夏夏影视、健康家用) and one dedicated live aggregate. HTTPS links, de-duplicated by URL; third-party plugin code was not executed during HTTP format inspection.
- Do not merge `sites`, `spider`, parsers or headers across configurations: global spider/rules would otherwise conflict. Existing source switch/history handles subscriptions independently.
- Presets are offered only before first setup, behind an in-app disclosure and use/self-add choice. Existing nonempty subscriptions are untouched; a seed marker prevents re-adding entries users delete.
- Public source availability, safety, content rights and playback are not certified by structural JSON inspection.

## Next steps
1. Resolve missing matching 5.8.7 Media3/native AAR inputs, or obtain a user decision before changing the playback/source baseline.
2. A worker identified 5.2.9 / ccce4b5ded0613ea6b532796fd61c5f477c5c2cc / 2026-03-17 as a candidate whose media compilation matched trial public AARs. Its nonmedia plugin inputs still fail; this is not a buildable delivery or a decided migration.
3. Once an honest complete build is possible, run the UI, navigation and source onboarding, then inspect the signed ARMv7 APK and perform real-TV acceptance.

## Handoff status
- Completed in source: TV shell and shared styling, secondary page/control styling, lightweight mode/image budgets, first-run independent subscriptions, explicit fork version behavior, fail-fast build input checker.
- Verified: resource linking, catvod Java compilation, static XML/binding/preset checks.
- Outstanding: complete app compilation, installable APK, runtime/visual acceptance, television performance and playback.
- Blocker: matching custom Media3 Java and unified native dependencies for 5.8.7 not found in the inspected upstream sources/artifacts.
- Current branch keeps upstream 5.8.7 behavior; no silent rollback or engine replacement was performed.

## Not yet verified
- Android 9 installation and hardware decoding on the user's ARMv7 television.
- Real-device frame pacing, heap pressure, remote behavior and long-session playback.
- User acceptance of visual design; screenshots will be supplied when actual UI can run.
