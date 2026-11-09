# TV Cinema task plan

## Goal
Ship a native TV redesign and a verified signed ARMv7 APK for an Android 9 television, preserving FongMi playback compatibility.

## Phases
1. [in_progress] Establish repository, rules and build baseline.
2. [pending] Implement cinema shell and shared focus system.
3. [pending] Redesign browse, search, settings and playback surfaces.
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

## Ownership
- Main: rules/docs, all UI resources, HomeActivity, review, commits/push and ppm.
- Build worker: to be assigned Gradle/toolchain/signing/scripts only.
- Reference worker: completed read-only reference audit; no source modifications.
