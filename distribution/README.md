# Cinema production integration

This directory defines the production input pair and our reviewed changes.
The upstream repositories retain their GPL/Apache/third-party notices.

- `baseline.json` pins the TV commit, matching Media3 commit and dependency hashes.
- `cinema.patch` is the complete reviewed modification to the pinned TV source.
- `overlay/` contains Cinema-only source or resources added after patch verification.
- `baseline.json` also pins exclusive upstream files in `removals` by SHA-256. Preparation validates the complete list before deleting any original, refuses changed files/path escapes/symlinks, and is safe to rerun. Verification requires these retired files to be absent.
- Existing Cinema preset class/assets under root `app/src/leanback/` are copied only through the explicit build manifest.
- Disposable checkouts, local signing configuration, downloads, logs and APKs stay under ignored `build/`.
- Never reset a changed checkout. A mismatched revision or unexpected patch state must fail with an actionable error.
- `scripts/cinema-build.sh` is the production build entry point; the root application is retained as an earlier design experiment.
- `sdk-compat.init.gradle` pins installed NDK29 so release native symbols are stripped. `trim-python-tests.py` transforms generated dependency archives only, before Chaquopy's build manifest hashes them; runtime/wheel/source inputs are preserved. Final `scripts/cinema-packaging.py` verifies stripped ELF payloads, test-free requirements and asset hashes; CI caps the signed ARMv7 APK at79MB for this release.

The paired upstream keeps its mpv/ASS/danmaku implementations. This integration
does not claim that the separate, unpublished FongMi 5.8.7 API is binary-compatible.
Cinema removes the unused real-time speech subtitle/translation feature, its model
downloader, PCM/lookahead pipeline and Sherpa Java/native dependencies. Ordinary
embedded/external/ASS subtitles and Android system voice search remain available.
Cinema adds its own identity, quiet amber appearance, manual hero rotation,
reduced motion, bounded image memory and independently stored preset subscriptions.
