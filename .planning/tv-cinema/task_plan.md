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

## Verification rules
- Validate before each commit; never exceed 100 changed lines without a targeted check.
- Do not mistake a successful XML parse, compiler, emulator or upstream APK for real-TV acceptance.
- Use existing test patterns; record gaps rather than inventing test passes.

## Errors encountered
| Error | Cause / resolution |
| --- | --- |
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
