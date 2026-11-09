# Findings

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
