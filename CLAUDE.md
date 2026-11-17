# TV Cinema

## Goal and boundaries
- Redesign FongMi/TV's native television interface for a 32-bit ARMv7 Android 9 TV.
- Preserve playback, sources, subscriptions, history and remote-control capabilities.
- The 2026-11-13 mobile increment preserves the paired upstream's existing Android touch UI and adds Cinema branding, sources, manual discovery and guarded updates. A phone UI redesign is deferred.
- Keep upstream GPL-3.0 licensing and attribution. Do not embed content providers.
- Use a separate application ID and signing key so the original app stays installed.
- User authorized the initial GitHub creation, earlier commits/pushes and making it public on 2026-11-09. Those historical approvals do not authorize a new commit, push, signing-secret upload or release; obtain current explicit consent under the root AGENTS.md rules.
- Never commit signing keys, local.properties, credentials, generated APKs or dependency caches.

## Structure and naming
- `app/src/leanback/`: native TV activities, presenters, resources and remote navigation.
- `app/src/main/`: existing shared playback and data behavior; change only when necessary.
- `app/src/mobile/`: preserved root baseline; shipping mobile changes belong in the production patch and explicit overlays, like the TV changes.
- `docs/features/tv-cinema/context.md`: requirements, decisions, acceptance and handoff.
- `docs/features/tv-cinema/performance.md`: 2026-11-16 responsiveness, loading and playback investigation, before/after evidence and remaining target-TV checks. Disposable profiles and baseline source snapshots go under ignored `build/diagnostics/performance/`; logs under `build/logs/cinema-performance-*`. Use local controlled fixtures and a task-owned emulator/package; leave connected personal devices and unrelated emulators untouched.
- `docs/features/tv-cinema/episode-layout.md`: 2026-11-17 episode ordering and TV detail layout decisions, independent review objections, acceptance and evidence. Use `build/logs/cinema-episodes-*` and `build/outputs/cinema-episodes-*` for disposable logs/screenshots/fixtures. Keep source/range controls outside the episode scroll viewport; preserve playback identity and fullscreen renderer. Scope is TV only, with shared ordering enabled explicitly by its TV host; do not silently change phone playback order.
- `docs/features/tv-cinema/mobile-context.md` and `mobile-verification.md`: mobile scope, decisions, validation and handoff; keep TV evidence separate.
- `distribution/cinema-mobile-test.init.gradle` selects only the explicitly copied `app/src/cinemaMobileTest/` runtime tests for the disposable `com.mjnhmd.tvcinema.mobile.probe` emulator app. Shared TV tests stay untouched. Generated fixtures/screenshots stay under ignored `build/`.
- `build/android-avd/cinema-mobile-api36-arm64*`: task-owned phone emulator using the already-installed API36 ARM64 image; keep existing AVDs/data intact. Stop the task-owned emulator after verification; delete its files only with explicit cleanup authorization.
- `.planning/tv-cinema/`: task_plan.md, findings.md and progress.md for this task.
- `scripts/`: reproducible build, artifact verification and existing runtime utilities.
- `scripts/tests/`: focused stdlib Python tests for Cinema CI setup, metadata and artifact staging; `test_cinema_*.py` only, no generated APKs or credentials.
- `.github/workflows/cinema-armv7.yml`: builds the TV ARMv7 and phone ARM64 editions (file name kept for history). One Release carries both APKs and both manifests, because GitHub `latest` is repository-wide and splitting editions would break the other side's OTA URL. Keep signing material in GitHub Actions secrets; do not publish on every source push. Ordinary CI artifacts must not be described as public download links or tested device releases.
- `.omc/RELEASE_RULE.md`: project release-rule cache derived from the CI recipe; refresh when workflow/version rules change. No credentials or generated APKs. Feature context and verification records remain the delivery truth.
- `app/build/outputs/`: disposable build products; never source assets.
- `build/reference/`: ignored, pinned third-party checkouts for isolated baseline verification; do not mix their source into the application without an integration decision and provenance.
- `build/logs/`: ignored command output; durable outcomes and limitations belong in feature verification.md.
- `build/diagnostics/speech-removal/`: disposable frozen production recipe and isolated TV/Media3 sources for this removal's builds/tests. Copy only pinned tracked inputs plus declared patches/overlays; use existing Git metadata read-only, separate generated outputs and no shared-source edits. Store the before/after audit in `build/logs/`; retain until explicit cleanup.
- `build/diagnostics/apk-size-audit/`: disposable extracted APK entries and size-only compression/ELF stripping experiments. Never replace source assets, canonical APKs, runtime dependencies or signing material from this directory; record measured opportunities and compatibility limits in feature docs.
- `build/diagnostics/update-routing/`: frozen production TV/Media3 inputs and independent outputs for update-route tests/builds. Keep Git metadata read-only; no shared-source builds or external mirror traffic in regression tests. Local loopback fixtures only; durable evidence stays in feature docs.
- The update-routing isolated checkout may be reused sequentially for the authorized 78.5 MB build after freezing its new inputs. Package-only changes use NDK29 and trim `test`/`tests` directories from generated Chaquopy requirements archives before its build manifest is hashed; never edit installed packages, source wheels, runtime modules, fonts or signing identity.
- `build/ci/release/`: ignored, replaceable ARMv7 release staging using fixed APK/manifest filenames; no growing version history and no signing keys. CI keeps downloadable artifacts for seven days; public Releases are a separate explicitly authorized operation.
- `distribution/`: production baseline lock, reviewed patch set and integration metadata. Build scripts materialize the pinned paired upstream in `build/reference/`, apply only the tracked patch and add the explicitly listed Cinema sources/assets.
- Mobile builds use `scripts/cinema-build.sh --mode mobile --abi arm64-v8a` (ARMv7 optional), package `com.mjnhmd.tvcinema.mobile`, the existing private signer and separate output filenames. Default mode/package/output remain the TV edition. Never overwrite a TV APK with a mobile build.
- Shared Cinema configuration classes/assets and shared caller strings are declared for both flavors in `baseline.json`; keep TV-specific UI/profile classes in leanback.
- `distribution/baseline.json` `removals`: explicit upstream-relative source, test, resource and dependency files removed from production. Pin each original SHA-256; preparation must validate every remaining original before deleting any, reject path escapes or edited files, and tolerate already-absent entries. Verification requires all listed targets to be absent. Do not delete user data or downloaded language models on devices.
- The root `app/` is the preserved first UI experiment against FongMi's unmatched 5.8.7 interfaces. The shipping entry point is `scripts/cinema-build.sh`, not the legacy root Gradle application.
- Use `cinema_` prefixes for new TV design resources. The user selected the generated jade interlocking-screen icon on 2026-11-10; preserve its original artwork and colors for Cinema launcher, banner, splash and default in-app branding.
- Keep the selected icon and its XML wrappers in `distribution/overlay/cinema_*`; explicitly declare flavor resource targets in `distribution/baseline.json`. Retain exploration images/prompts under the existing feature docs. Original generated artwork is the master; do not redraw or recolor it during packaging.
- Keep evidence inside the feature directory; logs and downloaded build inputs stay ignored.
- Do not delete user data, caches or unrelated files without permission.

## Design and implementation
- Use the paired native Android/Leanback implementation, including its existing Compose controls. Do not introduce a framework rewrite; reduce motion, background work and image memory for the target TV.
- D-pad and Back must reach every action without touch, hover or a mouse.
- Visible focus must not depend on color alone or change measured layout size.
- Prefer quiet near-black surfaces, warm white typography and one restrained accent.
- Avoid blur, parallax, autoplay heroes, animated wallpapers and continuous decoration.
- Keep posters bounded to display size and retain recycling/cancellation.
- Do not silently replace the upstream player, runtime contracts or stored-data schema.
- Real-time speech recognition subtitles and subtitle translation are removed by the user's 2026-11-10 request, including their UI, model downloader, audio taps and Sherpa dependencies. Preserve normal embedded/external/ASS subtitles and Android system voice search.
- Update speed selection runs only after the user confirms a download. Bound concurrent APK-prefix probes and traffic; cancel every probe/download on exit. Rank actual transfer samples, retain fallback routes, and keep all byte-count/hash/package/version/signer/ABI checks. Show speed/ETA as estimates; slow-route retry must remain bounded and preserve a working final route. Mobile updates must use this project's mobile assets and reject television/upstream packages.
- Read relevant source, interfaces, related tests and a similar implementation before edits.
- No unrelated refactors, formatting or new features.

## Verification and evidence
- Verify each independently useful increment before its commit/push.
- Run focused checks before changing more than 100 lines without verification.
- TV release artifacts must be leanback, contain only armeabi-v7a native code and support API 28. Mobile artifacts must use the mobile launcher/package, one declared ARM ABI and the pinned minimum SDK. `lib/` and Python payload ELF class/machine must match the variant; inherited vendor assets outside `lib/` may be either supported ARM class.
- Inspect the final manifest, signature and native payload rather than trusting the filename.
- Build success does not prove playback, navigation or performance on the user's TV.
- Record emulator API/ABI separately; never describe an ARM64 emulator as ARMv7 acceptance.
- Record actual commands, outcomes, gaps and next steps in context.md and the task ledger.
- Use ppm for project/item deduplication and explicit progress/verification writeback.
- Do not create TV performance guarantees without measurements on that television.

## Parallel ownership
- Main agent owns UI resources, HomeActivity and project documentation.
- Build worker may own Gradle/toolchain/signing/build scripts only after assignment.
- Other workers must have explicit disjoint write sets and must not commit or push.
- Main agent reviews, integrates, validates and publishes all changes.
