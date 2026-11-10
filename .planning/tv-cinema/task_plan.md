# TV Cinema task plan

## Goal
Ship a native TV redesign and a verified signed ARMv7 APK for an Android 9 television, preserving FongMi playback compatibility.

## Phases
1. [completed] Verify pinned wobuhui paired baseline.
2. [completed] Build Cinema overlay and optimized release.
3. [completed] Verify secondary surfaces and preset onboarding.
4. [completed] Verify runtime and APK compatibility boundaries.
5. [completed] Publish evidence and target-device handoff.
6. [pending] Audit and stabilize source/wallpaper WIP.
7. [completed] Expose manual discovery in source catalog.
8. [pending] Implement trusted, reachable TV update flow.
9. [pending] Verify emulator, ARMv7 and handoff.
10. [pending] Diagnose category-as-episode and implement TV grid.
11. [completed] Validate natural-width titles and focus safety.
12. [completed] Add reproducible ARMv7 CI workflow.
13. [completed] Publish authorized ARMv7 and verify cloud.
14. [completed] Inspect slow homepage hero and recommend recovery.
15. [completed] Implement cached-first hero, ready-image rotation and publish 0.1.4.
16. [completed] Reproduce Android9 source-selection initialization crash.
17. [completed] Fix regex and test on API28.
18. [completed] Hand off verified ARMv7 fix; await Git authorization.
19. [completed] Publish 0.1.5 Android 9 crash fix.
20. [in_progress] Move settings to home nav and add playback preload mitigation.
21. [completed] Fix MPV engine-switch SIGSEGV on Android 9.
22. [completed] Publish 0.1.7 MPV crash fix.
23. [completed] Keep search-result metadata visible on posters and allow two-line titles.
24. [completed] Generate jade interlocking-screen icon; user selected it.
25. [completed] Integrate selected artwork and verify actual APK resources on API28.
26. [completed] Fix held seek previews remaining pending after button release.
27. [completed] Scroll truncated search titles on focus while retaining the two-line card footprint.

## Search-title focused marquee — 2026-11-10
- [completed] Read search holder/layout/tests, native marquee helper and existing focus/rebind test patterns.
- [completed] Add actual-pixel overflow/fit/blur/rebind/detach regressions, reproduce failure and implement an opt-in fixed-height native marquee.
- [completed] Compile ARMv7 source/resources from isolated pinned production recipe; run API28 ARM64 UI checks and full search integration; update evidence and handoff.
- Acceptance: only a title ellipsized after wrapping to two lines scrolls; raw title remains intact; losing focus restores two lines; card/title height never shrinks; metadata and D-pad/OK/long-click behavior remain unchanged. The user explicitly authorized committing/pushing this increment on 2026-11-10; stage only its helper, holder, tests, recipe and evidence.
- Result: seven search tests pass, 27.987s, final ARMv7 Debug compile succeeds and screenshots inspected. Two unrelated original single-line poster pixel failures also reproduce with unchanged helper on API28; recorded separately. Shared full build and true ARMv7 TV acceptance remain outside these isolated checks.

## Held seek release — 2026-11-10
- Scope: production VOD remote seek input and focused regression tests; preserve unrelated work. The user explicitly authorized this fix's commit and push with “提交推送” on 2026-11-10; branch changes and public release are outside this authorization.
- Acceptance: short presses retain the existing seek step and 250ms burst coalescing; long presses accumulate the existing accelerated preview and commit once after release. Missing or unmatched release must not leave the preview stuck. Repeated key-down events must keep the preview pending while held. Back/navigation, fullscreen exit and media replacement must still cancel old seeks.
- [completed] Read production input, key ownership policy, pending callback scheduler, activity dispatch/cancellation, related JVM/Android tests and live-input pattern.
- [completed] Reproduce lost/unmatched release, implement a bounded fallback without relaxing key/device ownership, and verify cancellation.
- [completed] Run focused JVM/Android input verification and production compile; record target-TV acceptance limits.
- [completed] Verify actual player position and seek-preview dismissal through the production Activity, then close out evidence.
- Outcome: 12 JVM + 15 Android tests pass; actual H.264 video seeks +60s without another press and closes the popup. Production ARMv7 Debug compile and recipe checks pass. User-TV/ARMv7 Release acceptance pending.
- [in_progress] Stage only the seek change and evidence, commit `fix: commit held seeks when remote release is lost`, push `origin/fongmi` and read back the exact remote SHA.
- ppm context was queried read-only; service was unavailable in the sandbox and returned unrelated approximate history. This excluded checkout is not registered or written to ppm.

## App icon delivery — 2026-11-10
- User selected the generated jade artwork and explicitly authorized committing/pushing this increment with “改完提交推送”. Public release is separate.
- Scope: original PNG, seven leanback brand wrappers, manifest identity bindings, shared playback badge artwork, matching production recipe/rules and focused icon documentation. Preserve unrelated seek/source work.
- [completed] Signed local ARMv7 build, original-signer/package/minSdk/ABI checks, unchanged RGB pixel audit and eight actual API28 offscreen renders.
- [in_progress] Review and stage icon-only changes, commit `feat: apply jade cinema app identity`, push `origin/fongmi` and verify the exact remote SHA.
- Target television appearance and install/OTA remain not_tested.

## Search-result cards — 2026-11-10
- Scope: ordinary search results only; preserve other poster cards, source selection, playback and existing dirty source/debug work. The user explicitly authorized committing and pushing this search increment on 2026-11-10; unrelated concurrent changes are excluded.
- Acceptance: source at poster top-right; poster bottom stacks full-width score/year and update/source-count lines, avoiding the side-by-side crowding the user rejected. Title reserves two lines and keeps its dimensions across focus changes. Missing metadata leaves no empty badges; recycled/grouped results refresh correctly; D-pad and OK/long-press behavior remain reachable.
- [completed] Read production card/controller, model, related title/rating tests and native layout patterns.
- [completed] Add an isolated search card and explicit optional review-score support.
- [completed] Compile production resources/source and verify attached cards, long titles, focus and recycled metadata; record actual runtime limits.
- Verification: 10 JVM metadata tests, production ARMv7 Debug Java/resource compilation, 5 API28 ARM64 card/Parcel tests and 1 complete search integration pass. Final native screenshots inspected. No new ARMv7 Release or user-TV acceptance. Commit message: `feat: keep search metadata visible and show two-line titles`.

## Current investigation — FongMi player upstreams — 2026-11-10
- Scope: read-only code/history comparison of FongMi/media and FongMi/mpv-android with the actual pinned production pair; no player replacement, dependency update, build, commit or push.
- [completed] Pin upstream revisions and compare production playback paths.
- [completed] Separate already-integrated changes from transferable fixes and incompatible changes.
- [completed] Record prioritized recommendations, evidence and device-verification gaps.
- Current checkout is `/Users/mengjingnan/code/tv-cinema`, outside the user's four ppm project roots. Query historical records only; do not create or update ledger projects/items.
- Initial ppm query reports server unavailable and non-project-filtered approximate history; those hits are not evidence for this project.
- Initial sandboxed GitHub request failed; approved read-only GitHub API access succeeds.
- Direct read-only ppm API succeeded after approval. Historical records remain untouched because this checkout is outside managed roots.
- Validation: cross-fork compare plus Git blob checks on 13 playback/diagnostic files; no player behavior changes or runtime benchmark in this audit.
- Outcome: wire the existing EXO debug action to real statistics first; separately evaluate MPV JNI Surface completion/lifecycle hardening, preserving the Android9 copy-mode workaround and existing extended JNI/cache ownership contracts. Full report is in the feature directory; key handoff facts also persist in tracked findings.

## Current implementation — EXO debug panel wiring — 2026-11-10
- [completed] Patch `PlaybackActivity` to drive the real `PlayerView` debug overlay.
- [completed] Add the MPV-unsupported hint string in all three value folders.
- [completed] Update `cinemaPatchSha256` and revalidate inputs.
- [completed] Compile the patched pinned checkout.
- [pending] Device/release verification when a television or emulator is attached.
- The shared checkout already carried the earlier reviewed patch, so the new hunk was applied there deliberately to keep the state fully applied; `cinema-verify-inputs.py` passes.

## Verification rules
- Validate before each commit; never exceed 100 changed lines without a targeted check.
- Do not mistake a successful XML parse, compiler, emulator or upstream APK for real-TV acceptance.
- Use existing test patterns; record gaps rather than inventing test passes.

## Errors encountered
| Error | Cause / resolution |
| --- | --- |
| Icon-reference localhost access blocked in sandbox | Approved read-only access reached the running WebBridge/ppm services; no service restart or ledger mutation. |
| Gradle wrapper lock blocked by sandbox | Approved local build access uses the installed cache and signing inputs; no Git or release operation. |
| Icon probe used nonexistent android-37 platform path | Read installed platforms and compile against android-28/android.jar. |
| Icon probe process terminated before rendering | Replaced system-context initialization with direct AssetManager archive loading; six API28 resource renders passed. |
| Fixed-dp brand layers did not scale with banner/logo bounds | API28 image inspection caught uncovered banner pixels / small-logo clipping; use relative banner insets and a zero-inset logo wrapper. |
| Raw README requested from leanback branch returned 404 | Actual default branch is fongmi; read repository metadata first. |
| Anonymous GitHub metadata returned 403 | Use authenticated gh API for metadata. |
| zsh commands disappeared inside loop | `path` is a special zsh variable; use descriptive loop names. No state changed. |
| gh jq invalid escape | Avoid backslash escape in jq pattern; use literal path fragment. |
| JDK glob failed | No matching downloaded Gradle JDK; Android Studio bundles Java 21. |
| HTTPS initial push rejected inherited workflow | Existing SSH authentication for mjnhmd succeeds; pushed without dropping or rewriting history. |
| Multi-file image patch had wrong home XML context | apply_patch rejected that hunk; reapplied with existing context, then XML validation passed. |
| Dotted Cinema styles inferred missing parent | Explicit parent="" fixed the resource link error. |
| TV label conflicted with main manifest | Flavor tools:replace marker fixed the merge; final resource linking passed. |
| Full release native validation failed | Trial AAR has split FFmpeg while current host requires unified FFmpeg; validation preserved. |
| Full Java compilation failed | Current custom Media3 API is absent from trial AARs; no stubs or feature removal. |

## Current handoff
- 2026-11-10 MPV: API28 reproduced SIGSEGV in `CCodecBufferChannel::setSurface`; `MpvHwDecPolicy` now uses `mediacodec-copy` on SDK<=28. Probe reaches file-loaded and advances >1s. Published 0.1.7 as `build-42727947198-1`, code `217434694`, cloud APK signer/ABI/manifest verified. TV pending.
- 2026-11-10 latest: published `0.1.5` crash fix as release `build-42663020367-1`; cloud APK signer/ABI/manifest and API28 initializer verified. Implemented 0.1.6 settings nav beside My, removed the My settings card, and disabled default next-episode preload with a migration marker for existing installs. 216 JVM tests, API28 UI screenshots, local ARMv7 `0.1.6`/`217408805` (sha `f1ea29b7...`) verified. 0.1.6 is uncommitted/unpublished; stutter root cause still unproven and awaits TV codec/source evidence.
- Latest state 2026-11-10: `fongmi` HEAD `1e3e67fcb`, dirty production patch/overlay and task documentation. User TV reports 0.1.3/API28 crash; API28 ARM64 reproduced the exact failing `\p{IsHan}` initializer, including actual public 0.1.3 DEX. Fixed to `\p{script=Han}`; 57 JVM + 8 API28 integration cases pass, with live UI source search. Signed local ARMv7 `0.1.5`/`217404967` verifies and its exact DEX initializer passes API28. True ARMv7 TV/OTA remains untested. No current commit/push authorization.
- Ignored Android9 system image/AVD consume about 3.6G under build; no cleanup authorized. Await explicit current commit and push approval before publishing the fix.
- Latest superseding state: `fongmi` HEAD `5dcbb7137`, production source clean before this documentation-only inspection. Public `v0.1.3` and successful cloud run `42624579403` verified; downloaded cloud and published local APKs have the same signer, different version metadata/hashes. Prior delivery authorization consumed. TV/mainland OTA acceptance remains open.
- Homepage slow-load consultation completed without code changes. Verified 7s rotation can clear in-flight artwork before its 20s timeout; feed and artwork are distinct paths, and published TMDB key is empty. Recommend bounded persistent cached-first feed, ready-image rotation, next-image-only preload and independent bounded fallback. Host curl results are not TV measurements. Details in feature context/verification.
- Remaining handoff bullets below describe the pre-publication snapshot; publication/cloud status is superseded by the two bullets above.
- `fongmi` HEAD `d3c919693` remains dirty; preserve all source/wallpaper/update WIP. Production is the pinned paired `distribution/` recipe, not legacy root Gradle.
- ARMv7 `0.1.3` / `10003` signed Release rebuilt after resolved-rule dedupe fix: 2m29s/900 tasks, 38 ELF32/EM_ARM libraries, minSdk24, non-debuggable, v2 signature, same existing certificate. Canonical `build/outputs/tv-cinema-armv7.apk`; staged `build/ci/release/leanback-armeabi_v7a-coexist.apk`; SHA256 `1cf4e59cc01f90cf3f8ef19e01a681c4fe058ca156dbb70c06fa709c4f22153f`.
- Natural-width episode chips, focused overflow marquee and separate remote/playback selection are implemented. 10 episode UI cases passed; actual manual-source settings/catalog/failure/refetch/two-provider rules pass 3 cases. Latest JVM suites: 98 passed; CI helper: 19 passed, including native assets/Python archive ABI checks.
- New merged candidate is first in the six-preset catalog and is not automatically activated. Candidate files are bounded to two slots, preserving the active one on refetch.
- Current user explicitly authorized commit/push, existing signing-secret setup and publication. Five signing secrets are configured. Initial push/cloud run/public Release remain in progress; only return links after verifying the actual downloadable assets. CI uses build-ID tags; local 0.1.3 may use matching immutable v0.1.3.
- API36 ARM64 emulator evidence is not Android9 ARMv7 acceptance. User-TV install/playback/performance and mainland download/update routes remain untested. No disk cleanup is authorized.

## Ownership
- Main: rules/docs, all UI resources, HomeActivity, review, commits/push and ppm.
- Build worker: Gradle/toolchain/signing/scripts only; direct edits in disjoint files.
- Reference worker: completed read-only reference audit; no source modifications.
- Secondary UI worker: completed 27 XML edits; main agent reviewed and owns integration.
- Source worker: preset source class and asset only; main agent owns HomeActivity onboarding.
