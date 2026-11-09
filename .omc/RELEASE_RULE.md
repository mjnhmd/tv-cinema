# Release Rules
<!-- last-analyzed: 2026-11-09 -->

## Version Sources
- Shipping build is `scripts/cinema-build.sh`, not the legacy root application.
- `TV_VERSION_CODE` and `TV_VERSION_NAME` define the APK version; this local release is 10003 / 0.1.3.
- `scripts/cinema-ci.py` defaults to timestamp codes and `0.1.3+YYYYMMDD.HHMMSS`; manual codes must exceed installed versions.
- Existing local signing identity must be reused. Secrets belong only in repository Actions secrets.

## Release Trigger
- Source pushes to `fongmi`: ARMv7 artifact-only build, seven-day retention.
- `workflow_dispatch`, `publish=true`: build, verify and publish a complete draft Release.
- Authorized local release may use immutable `v<version>` tags; the tag must match the verified APK name.
- Every commit/push/secret upload/publication requires current user authorization.

## Test Gate
- CI release-tool unittest and YAML/contract validation.
- Build checks pinned source/patch/native hashes, package, requested version, API24 compatibility, non-debuggable, native ARM32 payloads and existing signer.
- Current local evidence: 98 JVM tests, 10 episode UI cases and 3 manual-source cases. Emulator results are not Android9 ARMv7 television acceptance.

## Registry / Distribution
- Public repository: `mjnhmd/tv-cinema`.
- APK asset: `leanback-armeabi_v7a-coexist.apk`, package `com.mjnhmd.tvcinema`.
- OTA asset: `leanback-coexist.json`; only the verified ARMv7 entry is emitted.
- Publish only after both assets are uploaded; verify anonymous download checksums before handing out links.
- Native files, local properties, signing keys and generated APKs remain ignored.

## Release Notes Strategy
- State source commit, version/ABI, visible feature changes and verification boundaries.
- Explicitly retain the untested target-television and mainland-network limitations.
- `docs/features/tv-cinema/context.md` and `verification.md` hold durable delivery evidence.

## CI Workflow Files
- `.github/workflows/cinema-armv7.yml`
- `scripts/cinema-ci.py`
- `scripts/cinema-build.sh`
- `distribution/baseline.json`

## First-Time Setup Gaps
- Current user authorization is recorded in the feature context.
- Signing-secret installation and first real cloud build/public Release must be read back before marking complete.
