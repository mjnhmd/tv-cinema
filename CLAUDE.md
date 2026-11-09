# TV Cinema

## Goal and boundaries
- Redesign FongMi/TV's native television interface for a 32-bit ARMv7 Android 9 TV.
- Preserve playback, sources, subscriptions, history and remote-control capabilities.
- Keep upstream GPL-3.0 licensing and attribution. Do not embed content providers.
- Use a separate application ID and signing key so the original app stays installed.
- User authorized creating a private GitHub repository and committing/pushing validated increments in this task.
- Never commit signing keys, local.properties, credentials, generated APKs or dependency caches.

## Structure and naming
- `app/src/leanback/`: native TV activities, presenters, resources and remote navigation.
- `app/src/main/`: existing shared playback and data behavior; change only when necessary.
- `app/src/mobile/`: outside this redesign's scope.
- `docs/features/tv-cinema/context.md`: requirements, decisions, acceptance and handoff.
- `.planning/tv-cinema/`: task_plan.md, findings.md and progress.md for this task.
- `scripts/`: reproducible build, artifact verification and existing runtime utilities.
- `app/build/outputs/`: disposable build products; never source assets.
- `build/reference/`: ignored, pinned third-party checkouts for isolated baseline verification; do not mix their source into the application without an integration decision and provenance.
- `build/logs/`: ignored command output; durable outcomes and limitations belong in feature verification.md.
- `distribution/`: production baseline lock, reviewed patch set and integration metadata. Build scripts materialize the pinned paired upstream in `build/reference/`, apply only the tracked patch and add the explicitly listed Cinema sources/assets.
- The root `app/` is the preserved first UI experiment against FongMi's unmatched 5.8.7 interfaces. The shipping entry point is `scripts/cinema-build.sh`, not the legacy root Gradle application.
- Use `cinema_` prefixes for new TV design resources. Reuse existing icon artwork.
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
- Read relevant source, interfaces, related tests and a similar implementation before edits.
- No unrelated refactors, formatting or new features.

## Verification and evidence
- Verify each independently useful increment before its commit/push.
- Run focused checks before changing more than 100 lines without verification.
- Release artifact must be leanback, contain only armeabi-v7a native code and support API 28.
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
