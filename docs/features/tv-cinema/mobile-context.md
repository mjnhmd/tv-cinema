# Cinema Android mobile — 2026-11-13

## Goal and agreed scope
- User requests a phone version of this project and chooses the existing touch UI with Cinema capabilities, rather than redesigning the phone homepage/detail/player.
- Deliver a signed, installable Android ARM64 mobile APK; support an explicit ARMv7 build through the same production entry point.
- Preserve existing touch navigation, search, detail/episodes, fullscreen playback, history, favorites and ordinary subtitles. Keep the shipped TV edition's interaction unchanged.
- No new commit, push, release or signing-secret operation is authorized by this development request.

## Acceptance
- Reproducible mobile build from the pinned TV/Media3 pair and reviewed patch/copy manifest; the preserved root application is not the shipping source.
- Cinema launcher icon, splash and label. The package is now `com.mjnhmd.tvcinema`, identical to the television edition, so one Bugly product accepts both builds; the trade-off is that the phone and TV APKs can no longer coexist on one device, and reinstall replaces user data of the other edition.
- Same preset catalog/default rules as TV, without replacing an existing user configuration. Explicit source selection and manual discovery are reachable from phone settings.
- Discovery only starts after the user taps Start; fetching does not execute plugins or automatically activate a merged candidate; failure preserves the old candidate/current source.
- Update checks/downloads use this project's mobile manifest and immutable mobile assets, with package/flavor/ABI/size/hash/signer checks retained. Reject TV assets and upstream-edition metadata.
- Online wallpapers use the same six reviewed providers as TV: the refresh action re-fetches one reachable provider, replaces the cached snapshot atomically and keeps the current wallpaper when every provider fails. Startup reuses an existing cached provider snapshot instead of re-downloading.
- Cinema defaults (amber accent, danmaku on by default, next-episode preload off) apply to the phone too, without changing the existing phone layout.
- Assemble and inspect the real signed APK; run phone UI and controlled playback/source/update checks on an ARM64 emulator when available. Distinguish emulator evidence from physical-phone acceptance.
- TV production preparation and focused update/source tests still pass; mobile output never replaces the TV APK.

## Current state and findings
- Delivered local signed ARM64/ARMv7 APKs, `0.1.0+20261113.103519` /217729319, 75,213,759B /72,404,975B. Original signer/package/minSdk/ABI/packaging guards pass; exact hashes and commands are in `mobile-verification.md`.
- Final controlled API36 ARM64 suite passes8 tests in30.279s (wallpaper included); the 6-test core run with actual system Back passes24.658s: settings/catalog/explicit activation, manual fetch/failure/refetch preservation, update dialog cancellation and real H.264/AAC play/pause/seek/fullscreen/Back. A focused real-system-Back test also passes9.312s; prior immediate synthetic Back reruns were intermittent, so no broader playback claim follows from them. Debug probe runtime remains separate from signed Release inspection.
- Initial production mobile compile passes (37s / 229 tasks); 29 build-tool tests and 110 shared update/source tests pass with zero failures/errors.
- Wired mobile preset installation, Cinema icon/splash/shortcut and settings catalog/discovery/installer permission into the production patch; update routing accepts only matching Cinema mobile assets for ARM64/ARMv7.
- Bugly monitoring is shared, not mobile-specific: the same `App.onCreate()` call, Gradle dependency and proguard rules cover `mobile`, and `CinemaMonitoring` tags the report channel with `BuildConfig.FLAVOR_mode`. Verified by rebuilding the mobile ARM64 Release (`cinema-mobile-arm64.apk`), the packaged `libBugly_Native.so`, the dex Bugly classes and the compiled `setAppChannel`/`initCrashReport` calls.
- The package was unified to `com.mjnhmd.tvcinema` on 2026-11-14 so the existing `TVCinema` Bugly product accepts phone reports without a second product. `UpdateSources.CINEMA_MOBILE_PACKAGE` now aliases `CINEMA_PACKAGE`; OTA separation relies on the `mobile-*`/`leanback-*` manifest and asset names plus ABI, not on the package. `CinemaUpdateTest` was updated accordingly and still passes. Bugly console receipt is still not verified.
- Existing shared Android test sources contain TV-only tests, so mobile runtime tests need an explicitly isolated source set. Do not modify/remove existing TV tests.
- Branch `fongmi`, initial HEAD `e219421023ca6a3b7b7c1e833ba45c72adcd8637`; root worktree clean at start.
- Final work remains uncommitted on the same branch/HEAD. Mobile source fingerprint `800edeb54205126c29a2e580978c3a5ca0c5e2c1243f27c414e64fbd5f7460b8`; source list/hash evidence is `build/logs/cinema-mobile-final-inputs.sha256`.
- Actual signed ARM64 APK installed, cold-started in1854ms and exposed both Cinema settings rows in an offline emulator smoke. Task-owned emulator stopped; no existing AVD data removed.
- Paired production sources under `build/reference/wobuhui-tv` differ from the preserved root `app/`; edit the production patch/overlays, not the root mobile classes.
- Before this increment, shared `VodConfig`, `WallConfig` and playback code referenced Cinema classes/strings copied only to leanback. Explicit mobile copies fix that compilation failure without changing TV UI.
- The production wrapper now builds either mode with distinct outputs. The public CI recipe still builds TV only; mobile public publication remains a separate authorized operation.
- ppm read-only search succeeded; historical source snapshots date to 2026-11-09. This checkout is outside the four managed project roots, so no project/item writes.
- No Android device connected at start. Physical-phone testing remains `not_tested`.

## Decisions and reasons
- Use the existing touch screens; the user selected a usable first release without a UI redesign.
- Keep separate package/output identities to avoid replacing the TV app or mixing OTA artifacts.
- Reuse the same reviewed source catalog and icon bytes; do not copy the TV-only animation/profile/navigation code into the phone.
- Cinema wallpaper providers, cache/snapshot handling and Cinema defaults are shared behavior, not TV-only UI, so they are enabled on mobile. Leanback-only pieces stay on TV: cached hero rotation (`CinemaHeroSnapshot`/`DiscoverHeroPresenter`), remote-focused search cards (`CinemaSearchCard`), the natural-width episode grid (`JetStreamEpisodeGrid`) and D-pad motion tuning.

## Next steps
1. Install the matching local APK on the user's phone and verify real subscriptions/playback/gestures.
2. If requested, authorize Git delivery and public mobile distribution; keep both flavor manifests available.
3. Real phone/ARMv7 runtime/public OTA remain `not_tested`. Optional inherited ARM32 `mitv:` vendor support is not proven on ARM64-only phones.
