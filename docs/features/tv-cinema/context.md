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
- First preserved UI experiment: FongMi/TV `4512b4c9f82534ff66acfde06ccdd68f35b074f0`, version 5.8.7, under root `app/`.
- Production build now uses the locked paired fork defined in `distribution/baseline.json`; `scripts/cinema-build.sh` is the shipping entry point. Do not build the legacy root application to produce a release.
- Upstream GPL-3.0 remains in force. Source history is preserved.
- Private repository created: https://github.com/mjnhmd/tv-cinema, default work branch `fongmi`.
- Rules/scope commit `dfde9bd89` pushed and read back through GitHub API. SSH is authorized; HTTPS OAuth lacks workflow scope for the inherited website workflow, so origin uses SSH without history rewriting.
- ppm requirement: `REQ-0007`, project `github.com/mjnhmd/tv-cinema`.
- ppm exact search for FongMi returned no hits. Registration will use the explicitly created local checkout and actual remote, not directory-name inference.
- No television is connected. Our paired-source optimized ARM64 build was exercised in an API36 emulator; this is not Android9 ARMv7 evidence.

## Decisions and reasons
- Superseded decision: keep the unbuildable current FongMi private Media3 contracts unchanged. User selected the wobuhui reference; use its internally consistent playback/source pair rather than mixing the two APIs.
- Keep the paired fork's native Views/Leanback and existing Compose controls. Cinema applies a small reviewed patch, not a framework rewrite.
- Use one quiet cinema language rather than desktop glass/blur/parallax: long-distance legibility and predictable focus matter more than visual effects.
- Independent package/signing: no overwriting of the installed upstream application or its user data.
- Reference projects are desktop applications, not TV implementations. Borrow information hierarchy and restrained motion, not their mouse/hover/window assumptions.
- Preserve current feature entry points while changing presentation; do not silently remove power-user functions.
- Production lightweight profile defaults on: no View focus zoom/panel fades, manual hero rotation, bounded Glide memory cache (16MiB) and bitmap pool (8MiB), no image fade, bounded logo/artwork decoding. Existing Compose focus controls and optional wallpaper are preserved; this is not a claim that every animation is disabled.
- Fork-specific version checking must not offer a FongMi APK signed for a different package. Television version action explains that updates come from this fork's Releases.
- Build inputs and signing material stay ignored; all reproducible source and rules must be tracked despite upstream ignore rules.

## Evidence and gaps
- `adb devices -l`: no connected devices, 2026-11-09.
- Upstream app configuration: leanback/mobile flavors; native splits arm64-v8a and armeabi-v7a; minSdk 24; compile/target SDK 37; AGP 9.4.0; Gradle 9.7.1; Java 21.
- SDK37.0 and Java21 available; missing NDKr29 installation was repaired using official sdkmanager.
- Source checkout only includes hook/tvbus AARs. Trial legacy Media3/native AARs were downloaded locally for diagnosis, but they do not match 5.8.7; native verification and full Java compilation fail. They are not accepted release inputs.
- Public Release 5.8.7 includes a leanback ARMv7 APK, but an upstream APK is not this redesign's build evidence.
- Current source tests are excluded by upstream `.gitignore`; regression checks introduced here must be explicitly tracked.
- Historical root-application resource checks passed while its full build remained blocked. Production paired-source full builds now pass for both ARMv7 and ARM64 with minification/resource shrinking; current verification evidence supersedes those historical failures for delivery.
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
1. Current delivery is the validated paired source plus Cinema integration. Install `build/outputs/tv-cinema-armv7.apk` on the user's Android9 television and validate decoder/remote/performance behavior.
2. Historical 5.2.9 / ccce4b5ded0613ea6b532796fd61c5f477c5c2cc / 2026-03-17 also failed complete assembly after filling legacy plugin inputs. Earlier absence of media diagnostics was caused by other missing classes, not compatibility. The candidate is rejected: it still needs custom SubtitleView/MediaItem decode and matching nextlib APIs.
3. Complete provider/live/advanced-feature testing separately; do not infer it from local fixture playback or library presence.

## Handoff status
- Completed: locked TV/media source recipe, Cinema native appearance/profile, lightweight image/motion defaults, first-run independent subscriptions, fork update behavior, signed optimized APKs.
- Verified: both complete release builds, minSdk/package/version/signature/ABI, 38 ARMv7 ELF headers, 9 patch tests, 26 focused JVM tests, controlled local playback/pause/resume/Back and settings in API36 ARM64 emulator.
- Outstanding: real television performance/playback and visual acceptance; external preset/live/advanced formats not comprehensively tested.
- Historical missing-AAR blocker resolved for delivery by the explicitly selected paired source, not by reconstructing or supplying FongMi private AARs.
- Original source experiment remains preserved; active distribution uses the explicitly selected paired reference and tracks its source revisions and reviewed patches.
- UI/source WIP commit `00a70e5266a5a3b5b5c788f4fe2d6ced7a21f35b` pushed and read back through GitHub API; working tree was clean after publication.
- Actual fallback build `/tmp/tv-529-build`, command `assembleLeanbackArmeabi_v7aRelease`, failed with 14 errors; log `/tmp/tv-529-assemble-3.log` inspected by the main agent. No fallback APK exists.
- At the end of the earlier investigation no baseline had completed a full build; the user-selected wobuhui paired baseline subsequently passed and is now the production source.
- The temporary API36 emulator's display overrides were reset and the task-owned emulator stopped.

## Paired fork baseline investigation
- Reference TV commit: `17abcd7453a0ecbdbc9ed40a22f3fd3632158495`, branch `ui/apple-tv-redesign`, GPL-3.0.
- Its release workflow pins `wobuhui666/media` commit `342f6590e02ac390e0bd6b4488b8a1bff55747c1` rather than a floating version. `MEDIA3_SOURCE_DIR` enables composite source dependency substitution and a checked patch step.
- The fork has `mpv`, `subtitle-ass`, `media3-av3a` modules and a `media3-ui-danmaku` dependency. The earlier claim that adopting it necessarily means removing all mpv/ASS/danmaku functionality was overbroad and is retracted; actual feature limits need code and runtime checks.
- Its release configuration disables minification/resource shrinking and includes Compose; these choices must not be copied blindly to the low-end-TV design.
- Local baseline verification and a concrete dependency/feature map are required before changing the application source.
- Complete paired ARMv7 Debug baseline built successfully: 498 tasks, 5m21s. Its generated APK declares `com.mjnhmd.tvcinema.reference`, minSdk 24, and only armeabi-v7a; debug signing verified.
- The first run failed because installed NDK r29 contained only `.installer`, not a usable toolchain. Repaired using official sdkmanager; no version substitution.
- Media3 patch guard tests passed (9 tests). Both MPV bridges retained 23 JNI methods and resolved 47 media imports; archive SHA-256 matched the upstream recorded hashes.
- Production integration uses `distribution/cinema.patch` plus explicit Cinema sources/assets. Source-matched external release was installed separately only for reference inspection; it is not presented as our build.
- Cinema changes compiled against the paired source: both Kotlin and Java passed. Manual hero rotation, amber defaults, reduced View animation, 16 MiB Glide cache / 8 MiB pool, bounded logo/artwork loading, preset onboarding and fork-specific update behavior are integrated.
- Signed delivery: `0.1.0` / code `10000`, package `com.mjnhmd.tvcinema`; ARMv7 Release91,085,901bytes, SHA256 `e0289fb10b76b6c27e4def2eb2aea4dce8e1dfde473722e454178548dfedffdd`.
- R8 initially exposed missing Pine `HiddenApiBypass`; adding the official6.2 dependency fixed the actual class path. R8/resource shrinking stay enabled; no suppression or fake interfaces.
- Same-source ARM64 Release installed and exercised in API36 emulator; first-run disclosure, D-pad Settings/Back and controlled local H264/AAC playback/pause/resume passed. External subscriptions remained unexecuted during controlled playback testing.
- `verification.md` records exact commands, results, artifacts and remaining scopes.
- No current evidence establishes all feature equivalence with unpublished FongMi custom APIs. The reference has its own mpv, ASS, dual-subtitle, danmaku and decoding implementation; ISO/BD-J and latest source/auth/runtime differences remain explicit integration boundaries.

## Not yet verified
- Android 9 installation and hardware decoding on the user's ARMv7 television.
- Real-device frame pacing, heap pressure, remote behavior and long-session playback.
- User acceptance of visual design; actual running screenshots are available under `build/outputs/`.
