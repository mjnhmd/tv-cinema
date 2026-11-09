# Cinema production integration

This directory defines the production input pair and our reviewed changes.
The upstream repositories retain their GPL/Apache/third-party notices.

- `baseline.json` pins the TV commit, matching Media3 commit and dependency hashes.
- `cinema.patch` is the complete reviewed modification to the pinned TV source.
- `overlay/` contains Cinema-only source or resources added after patch verification.
- Existing Cinema preset class/assets under root `app/src/leanback/` are copied only through the explicit build manifest.
- Disposable checkouts, local signing configuration, downloads, logs and APKs stay under ignored `build/`.
- Never reset a changed checkout. A mismatched revision or unexpected patch state must fail with an actionable error.
- `scripts/cinema-build.sh` is the production build entry point; the root application is retained as an earlier design experiment.

The paired upstream keeps its mpv/ASS/danmaku implementations. This integration
does not claim that the separate, unpublished FongMi 5.8.7 API is binary-compatible.
Cinema adds its own identity, quiet amber appearance, manual hero rotation,
reduced motion, bounded image memory and independently stored preset subscriptions.
