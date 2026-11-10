# Release Rules
<!-- last-analyzed: 2026-11-10 -->

## Version Sources
- Shipping build is `scripts/cinema-build.sh`, not the legacy root application.
- `TV_VERSION_CODE` and `TV_VERSION_NAME` define the APK version; `scripts/cinema-ci.py` `BASE_VERSION_NAME` is the shipping version base, currently `0.1.8`.
- CI defaults to seconds since 2020-01-01 UTC plus 1,000,000 and `0.1.8+YYYYMMDD.HHMMSS`; manual codes must exceed installed versions. Tests pin the clock conversion and matching base version.
- Existing local signing identity must be reused. Secrets belong only in repository Actions secrets.

## Release Trigger
- Source pushes to `fongmi`: ARMv7 artifact-only build, seven-day retention.
- `workflow_dispatch`, `publish=true`: build, verify and publish a complete draft Release.
- Authorized local release may use immutable `v<version>` tags; the tag must match the verified APK name.
- Every commit/push/secret upload/publication requires current user authorization.

## Test Gate
- CI release-tool unittest and YAML/contract validation.
- Run all `scripts/tests/test_cinema_*.py`, including guarded upstream-removal checks. Production preparation removes the checksum-pinned speech-subtitle files; input verification requires their absence. Final APK must exclude Sherpa JNI and speech-subtitle classes/activities while retaining ordinary subtitle dependencies.
- Adaptive updater gate: `com.fongmi.android.tv.update.*` tests cover prefix ranking, low-speed fallback/recovery, integrity and cancellation; verify R8 APK retains probe/meter/status entry points. API28 emulator evidence and real TV/mirror throughput are separate acceptance scopes.
- Build checks pinned source/patch/native hashes, package, requested version, API24 compatibility, non-debuggable, native ARM32 payloads and existing signer.
- Current increment evidence: 78 update JVM tests, 25 CI/preparation tests, 54 retained audio/subtitle JVM tests, three API28 update dialog/network cases and two audio/SRT cases. Earlier episode/search evidence is recorded separately. Emulator results are not Android9 ARMv7 television acceptance.

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
- Existing five signing-secret names and historical successful cloud/public release are verified. Do not upload or replace secrets during ordinary releases.
- 2026-11-10 user explicitly requested “提交推送发布新版”; this authorizes the current focused commit/push and 0.1.8 publication. Record exact run/source/asset verification in the feature context; historical authorizations remain scoped to their deliveries.
