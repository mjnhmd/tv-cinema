# TV Cinema app icon — 2026-11-10

## Goal and scope
- Generate one original app icon reflecting television viewing, film discovery, VOD/live aggregation and a quiet cinema experience.
- User request: a new icon with a technological feel, informed by logosj.com and the linked Canva article.
- Preserve the existing exploration preferences from context.md: rounded interlocking frames / cinema portal; no letters, generic play button, gold, metal reflections or blue-dominant palette.
- User selected the generated jade icon with “可以 就用这个”. Integrate this exact artwork into the production leanback launcher, TV banner, splash and default in-app logo; retain the original prompt and PNG.
- Keep proposals as `icon-proposal-<concept>-v<N>.png` in this existing feature directory. Keep the exact prompt and verification here; do not overwrite previous moss/plum proposals or production artwork. No new asset directory is needed.

## Plan
1. [complete] Read project rules, feature context, existing launcher assets and previous proposals.
2. [complete] Read the user's two design references.
3. [complete] Generate a simplified jade cinema-portal icon with the built-in imagegen tool. User selected it.
4. [complete] Inspect the generated result and original PNG metadata: 1254×1254, opaque RGB, 1,183,090 bytes, SHA256 `5e3d01fa3b016855c66c249f4db7686d8e907bb1cbade8859d3592d5a6036890`.
5. [complete] Package the exact selected PNG through explicit leanback resource overlays, update the production manifest identity references and replace the playback badge's old hard-coded artwork.
6. [complete] Verify resource linking, signed ARMv7 build, final APK icon/banner bindings, unchanged RGB pixels and eight actual API28 resource renders. Target television appearance remains not_tested.

## Integration decisions
- Source: `/Users/mengjingnan/.codex/generated_images/01a1c391-0cfa-76b3-b72c-04252fa677ab/call_0dcc3d90d3074ffcae99c44d3ed784e1.png`, generated with the built-in imagegen tool and accepted in this chat.
- Copy the source byte-for-byte to `distribution/overlay/cinema_icon.png`. Package it as a `drawable-xxxhdpi` resource so Android downsamples for lower-density televisions instead of decoding the full original as `nodpi`.
- Keep generated-image proportions, gradient and central negative space. XML wrappers provide adaptive-icon insets and a centered 16:9 banner; no raster redraw, recoloring, synthetic transparency or distortion.
- Use dedicated `cinema_launcher`, `cinema_banner` and `Cinema.Splash` bindings in the leanback manifest. A leanback-only `ic_logo` wrapper replaces the shared fallback without changing mobile resources or third-party supplied logos.
- Preserve all concurrent source/player/danmaku WIP. After visual selection and verification, the user explicitly authorized committing and pushing this icon increment with “改完提交推送”. Public release publication is outside this authorization.
- The playback-specifications badge draws the old paper logo as hard-coded paths instead of a resource. Replace only that drawing with a cached `ic_logo` drawable so it shares the selected artwork; retain badge layout, timing, text and existing scale/rotation transitions.
- API28 archive-resource rendering caught fixed-dp layer items failing to scale with launcher bounds: the banner left uncovered pixels at 640×360, and the 108dp in-app logo could be oversized in a 32dp playback badge. Switched to a full-bounds background and proportional 21.875% horizontal banner insets; the default-logo wrapper now has zero inset and fills its actual bounds.

## Source findings
- README.md describes native Android TV browsing/search, VOD/live, favorites/history and remote operation. A screen/portal metaphor relates to these functions without adding individual feature pictograms.
- Existing proposals combine a broad rounded ribbon with multiple nested arches. Simplifying the center should retain the concept with fewer details competing at launcher size.
- https://www.logosj.com/ was opened and inspected in the real browser. Its gallery includes compact geometric identities; only general geometry/recognition principles are used.
- https://www.canva.cn/learn/hidden-meanings-behind-50-worlds-recognizable-logos/ currently displays an article titled “Logo设计也疯狂！世界30个著名徽标设计背后的含义”. Its Picasa and FedEx examples explain how negative space can carry a second meaning. Borrow the principle of a meaningful central void; do not reproduce those marks.
- ppm was queried read-only via `/api/search?q=tv-cinema`. Historical records exist, but this checkout is outside the four permitted work roots. No project/item creation or update is in scope.

## Tool and access notes
- Built-in imagegen is the generation path; no external image API or API key is needed.
- Kimi WebBridge is used only to inspect the two supplied pages. The task's tabs are grouped under “TV 图标设计参考”.
- Initial sandboxed localhost requests were denied/unreachable. Starting WebBridge also encountered a sandbox log-rotation permission error; approved access reached the already-running daemon. No restart or upgrade was performed.

## Prompt
```text
Use case: logo-brand.
Asset type: one finished square app icon for TV Cinema, an Android television app for discovering films, watching VOD and live TV, combining multiple viewing sources, and returning to a personal cinema library.
Primary request: create an original, sophisticated technology-feel icon using a cinema portal and interlocking screen-frame concept. One memorable mark, meaningful negative space, immediately readable from across a room. This is a new design, not a copy of a known logo.
Scene/backdrop: full-bleed smooth near-black forest-charcoal square, opaque edge to edge, approximately #0A1110. No outer rounded app tile drawn inside the canvas; the operating system supplies its own mask.
Subject: two broad, continuous, softly rounded geometric bands interlock into a compact cinema portal. Their shared dark central opening clearly suggests a single horizontal widescreen cinema screen, approximately 16:9. The offset overlapping frame suggests moving into a world of films and different sources meeting at one screen. Keep the shape unified and bold, using at most two interlocking layers. The central negative-space screen is generous, quiet and completely uncluttered. Not a triangle, letter or literal camera lens. Softer corners, confident proportions, no thin lines.
Style/medium: premium minimal digital brand icon with precise vector-like silhouette and restrained shallow dimensionality. Satin-matte digital color planes, clean smooth curves, crisp deliberate negative spaces. Technological precision and cinematic calm.
Composition/framing: perfectly front-facing, one large optically centered emblem occupying roughly 68 percent of the square width and 58 percent of the height. Balanced clear safe margins on all sides. The whole mark fits safely inside a circular launcher crop. Design must stay recognizable at 48 pixels. Render at high resolution, square 1:1, ideally 1024x1024 or larger.
Lighting/mood and palette: cold jade / mint green against deep forest charcoal. Pale jade on the leading band transitioning gently to rich emerald on the rear band. Controlled tonal depth at the overlap, no bright cream center. Clean contrast and elegant, quiet technology. No blue, purple or gold.
Text: none.
Constraints: a single usable app-icon asset, opaque background, no typography, no initials, no labels, no presentation sheet, no mockup, no phone, no captions, no watermark, no border. No generic play button. No concentric tunnel arches. No chrome, gold, shiny metal, specular reflections, glass, plastic, neon bloom, lens flare, particles, stars, circuitry, tiny decorations, fuzzy shadows or strong 3D perspective. Avoid excessive softness and gummy inflated shapes. Make the geometry and silhouette do the work.
```

## Verification and handoff
- Final command: `TV_VERSION_CODE=217447200 TV_VERSION_NAME=0.1.8-icon-local bash scripts/cinema-build.sh --abi armeabi-v7a`. `BUILD SUCCESSFUL in 3m 37s`, 900 tasks. Production prepare/input checks and scoped `git diff --check` also pass.
- Local validation APK: `build/outputs/tv-cinema-armv7.apk`, 91,915,944 bytes, SHA256 `a4a9ed0b2392def3ef728e2c4c0d997115ec2d4bf63532e8fbf41a7c84889fd9`. `cinema-ci.py verify-apk` passes package `com.mjnhmd.tvcinema`, expected local version/code, minSdk24, ARMv7-only native payload and original signer `d405dcab…bdc5`. This is a local validation build from the shared working tree, not an icon-only public release.
- `aapt2` confirms application icon/roundIcon → `mipmap/cinema_launcher`, banner → `mipmap/cinema_banner`, and `Cinema.Splash` → `drawable/cinema_icon_foreground`. The build recompresses the PNG; decoded source and packaged RGB pixels are identical (pixel SHA256 `3fc98667b6914d08a32a5fde7f82be8c2e15b0158d222f5e36ece149d525d07f`).
- Android9/API28 ARM64 resource rendering uses the actual ARMv7 APK as an archive via `AssetManager` and Android `Canvas`; no APK is installed, no application is started, and no app data is modified. Generated images are under `build/outputs/cinema-icon-render/`.
- Eight inspected renders pass: square 320px, standard 80px, circular 48px, TV cards 640×360 and 320×180, default home logo 88px, playback logo 32px and splash foreground 288px. After the relative-sizing correction, banner backgrounds fill their bounds and small logos remain inside circular masks. Runtime log: `build/logs/cinema-icon-api28-render.log`; build/audit/pixel logs use the `cinema-icon-*` prefix under `build/logs/`.
- Pre-delivery worktree evidence: branch `fongmi`, observed HEAD `f2158adf164426f235693f364bb7b4410d16b330`. The authorized delivery includes only icon assets, its two production patch entries, matching build copies/checksum, rules and focused documentation. Unrelated seek/source edits remain in the working tree. No ppm mutation because the checkout is outside permitted roots.
- An initial probe attempted `ActivityThread.systemMain()` and was terminated before rendering; the successful revised probe loads archive resources directly. Its temporary source and DEX ZIP are under ignored `build/tv-cinema/`.
- Initial sandboxed Gradle failed to write its existing wrapper lock; approved local build access succeeded. A host probe initially used a nonexistent `android-37/android.jar` spelling; compiling against the actual installed API28 platform succeeded. No new SDK/library installation.
- Actual television launcher/splash/home/playback appearance remains not_tested. No public release was requested.
- Authorized commit message: `feat: apply jade cinema app identity`.
- Pre-commit verification: the icon-only production patch applies to pristine pinned source files and reproduces all 26 declared modified blobs; all overlay dependencies are present in the selected commit. The shared `cinema.xml` difference only reorders identical strings and is excluded. All 20 existing release-tool tests pass (`build/logs/cinema-icon-precommit-tools.log`). Remote `origin/fongmi` matches the pre-delivery HEAD above.
- Delivery completed: commit `587601fbcb9604ad0117166d43f57d6682add1a7` (`feat: apply jade cinema app identity`) pushed to `origin/fongmi`; `git ls-remote origin refs/heads/fongmi` returned the identical full SHA. Seventeen icon-only files were committed. Index is empty and production input checks still pass; unrelated held-seek/code/document work remains local. This post-push receipt is local handoff evidence; no public release was created.

## Transparent in-app mark — 2026-11-11
- User feedback: the toolbar logo at the top-left showed the artwork's near-black square plate against `cinema_background` `#101216`; the hard edge looked harsh. Requested a transparent background for that icon.
- Added `distribution/overlay/cinema_icon_mark.png`, derived 1:1 from the accepted PNG: same 1254×1254 geometry, band shapes and colors. Alpha is keyed from the uniform plate color (border-ring median `(8,14,14)`) with a 4–14 RGB-distance smoothstep, so the plate and the portal opening are fully transparent while the bands stay opaque. The original `cinema_icon.png` is unchanged and remains the artwork master.
- Leanback `ic_logo` (`cinema_logo.xml`) now references `@drawable/cinema_icon_mark`; launcher, banner, splash and mobile resources keep the opaque artwork. The mark keeps the original margins, so Glide's 48dp `circleCrop` in `ImgUtil.logo` does not clip it (farthest opaque pixel 511px vs 627px circle radius).
- Verified: `cinema-prepare.py` and `cinema-verify-inputs.py` PASS; full ARMv7 build `BUILD SUCCESSFUL in 3m 26s`, 901 tasks (`build/logs/cinema-icon-mark-build.log`). `aapt2` resolves `cinema_icon_mark` and `ic_logo` → `@0x7f080086`; the packaged APK entry keeps the source alpha histogram exactly (aapt2 only zeroes RGB in fully transparent pixels), and composited pixels over `#101216` are identical. All 29 `scripts/tests/test_cinema_*.py` tests pass (`build/logs/cinema-icon-mark-tools.log`).
- Not verified: appearance on the user's Android9 ARMv7 television.
