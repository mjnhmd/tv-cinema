# Findings

## Follow-up — 2026-11-09
- Actual Android catalog dialog clips after four visible entries on the test TV-sized display; placing the new merged entry seventh made it appear missing. The latest candidate is now first while six presets remain unchanged below it. MockWebServer instrumentation proves no request before starting, no plugin/JAR execution during discovery, explicit activation only, failure preservation and active-candidate slot preservation.
- Latest episode layout uses actual MiSans text metrics and max of normal/selected font widths plus padding, not a character-count threshold or fixed columns. Width measurement is remembered separately from row packing and focus, so only viewport changes repack rows. LazyColumn composes visible rows; only the actual focused overflow chip installs marquee. The shared TV button's focus-scale is disabled here to keep wide buttons and outlines inside the clipped viewport.
- Screenshot source `6v电影` maps to 心魔's `csp_XBPQ` config; fetched rule text has empty 播放数组/播放列表 and generic selectors. Its website home contains the same category sequence shown under 选集. The exact detail response was not captured; the generic-selector explanation is evidence-supported, not yet individually proven.
- Upstream update logic validates downloaded APK package, exact version, APK signer and ABI, but points to `wobuhui666/TV`. The user authorized changing `mjnhmd/tv-cinema` from private to public on 2026-11-09; authenticated readback reports `PUBLIC` and anonymous repo GET 200. No release published yet. Never embed tokens. This host fetched a reference manifest via direct GitHub and two proxies, but domestic-TV availability is unverified.
- User-provided wallpaper endpoints have different protocols: xxapi returns JSON `data` URL; Peapix returns JSON array with `fullUrl`; upx8 redirects to HTTP JPEG; mxin serves JPEG; xsot redirects to Bing JPEG. bing-img redirected but its target timed out from the host. Service availability can change.
- Actual source snapshots had different global spider jars, parser lists and rules (饭太硬 48 sites, 南风 91, 心魔 151, 夏夏 43, 健康家用 64). Discovery must not execute jars/JS/Python, and a merge cannot pretend all global semantics are compatible.
- Source settings' `内置片源` is only a catalog; user-requested manual discovery/merge is not implemented.

## Baseline
- Upstream 4512b4c, GPL-3.0, native Android Views/Leanback.
- App source expects untracked custom media AARs, SDK 37, AGP 9.4.0, Gradle 9.7.1 and Java 21.
- No connected TV. API 36 ARM64 emulator available only.
- Upstream .gitignore excludes CLAUDE.md, docs, tests and properties; task-owned source must be force-added or specifically unignored.

## Reference review
- recent.design: use typography hierarchy, spacing and restraint, not its desktop masonry layout. Reviewed actual page and interface filter.
- xiugou-tv: Windows Flutter/libmpv. Useful reference paths: `lib/ui/desktop/widgets/focus_ring.dart`, `lib/ui/theme/app_tokens.dart`, `lib/ui/desktop/player/episode_drawer.dart`, `lib/ui/desktop/player/playback_preparing_overlay.dart`.
- CineFlow: Electron desktop. Useful separation of progressive loading/error presentation; avoid its blur, card tilt, pointer parallax and hover scroll.
- No reference implementation was executed; do not treat reference comments or worker inferences as measured TV facts.
- Remote navigation needs visible focus distinct from current playback/selection. Directional movement must not start a different episode before OK.
- Android TV documentation search returned memory optimization and focus-system guides through the installed android CLI.

## Design
- Near-black canvas, warm white text, restrained amber accent, consistent quiet rounded surfaces.
- Focus outline + luminance change, not layout-changing borders or constant scaling.
- No animated wallpapers, autoplay hero, blur or framework migration.

## Implemented, pending runtime verification
- Native header/typographic introduction, compact six-action navigation, restrained cards and stable focus outlines.
- Lightweight toggle preserves optional wallpaper without a hidden running video player by default.
- Search/settings/detail/live/player controls use consistent tokens; original binding IDs and player surface order retained.
- TV image memory cache/pool capped; logo no longer decodes original dimensions.
- First-run subscription consent; six independent subscriptions, URL dedupe and no blind spider merge.
- Fork version action no longer offers upstream APKs.
