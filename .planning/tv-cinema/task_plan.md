# TV Cinema task plan

## Goal
Ship a native TV redesign and a verified signed ARMv7 APK for an Android 9 television, preserving FongMi playback compatibility.

## Phases
1. [in_progress] Establish repository, rules and build baseline.
2. [pending] Verify cinema shell and shared focus system.
3. [pending] Verify secondary surfaces and preset onboarding.
4. [pending] Verify runtime, APK compatibility and performance evidence.
5. [pending] Publish verified increments and document remaining device acceptance.

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
- Resource and library build passed; UI is source WIP, not runtime-verified delivery.
- Matching current media inputs or a user-approved baseline change are required before a complete APK.
- Historical 5.2.9 candidate rejected after complete assemble exposed 14 remaining custom-media errors; no audited complete-build fallback exists.

## Ownership
- Main: rules/docs, all UI resources, HomeActivity, review, commits/push and ppm.
- Build worker: Gradle/toolchain/signing/scripts only; direct edits in disjoint files.
- Reference worker: completed read-only reference audit; no source modifications.
- Secondary UI worker: completed 27 XML edits; main agent reviewed and owns integration.
- Source worker: preset source class and asset only; main agent owns HomeActivity onboarding.
