# Findings

## 饭太硬 candidate endpoints — 2026-11-10
- Built-in default is not 饭太硬's own domain: `app/src/leanback/assets/cinema/sources.json` points at the 12yue re-host `https://0.12yue.de5.net/tvbox/饭太硬.json`, which resolves to Cloudflare `104.21.47.215`. Domestic mobile-broadband latency is the expected weak point; content is a 48-site copy identical to the official one.
- Official `/tv` serves a JPEG followed by `**` + base64 JSON, not plain JSON. The app's `com.fongmi.android.tv.api.Decoder.getJson` handles that envelope, so the official endpoint is usable as-is.
- Live today (HTTP 200, 19,951 bytes, 48 sites / 6 lives, Last-Modified 2026-11-08): `http://www.饭太硬.net/tv` (BlogCDN), `http://www.饭太硬.cc/tv` (nginx), `https://www.饭太硬.cc/tv` (valid TLS, same ETag `6aefd459-4def`).
- Dead: `http://饭太硬.top/tv` and `http://fty.xxooo.cf/tv` (no response).
- Third-party mirrors live but are older copies: `https://jijietvbox.lol/tv/fty.json` (17,343B, 50 sites), plus the current 12yue re-host (17,765B, 48 sites).
- `https://www.饭太硬.net/tv` fails TLS (`tlsv1 alert internal error`), so `.net` is HTTP-only and `CinemaSources.seeds()` would drop it. Only `https://www.饭太硬.cc/tv` is currently shippable without relaxing the HTTPS-only filter.
- Host probes prove endpoint liveness and content only. No device is connected, so no TV-network ranking exists yet.
- Implementation: 饭太硬 is now a three-endpoint group (`https://www.饭太硬.cc/tv`, `http://www.饭太硬.net/tv` flagged insecure, `https://jijietvbox.lol/tv/fty.json`) activated as `cinema://fantai`. `CinemaDiscovery` times the endpoints on the TV, caches the winner for 24h and re-probes on failure or expiry; `CinemaSourceProbe` holds the timing/validation logic and is JVM-tested.
- `https://` stays mandatory for every endpoint without `"insecure": true`, so the HTTP official host is the only plaintext exception and it is declared in the asset, not hard-coded.
- Official `.cc`/`.net` payloads include whole-line `//` comments that strict JSON rejects; both the probe and the discovery decode path strip those lines. Inline `https://` inside string values must survive, which a line-start-anchored regex guarantees.

## MPV Android 9 surface crash — 2026-11-10
- Switching EXO→MPV during playback crashed on API28 inside `CCodecBufferChannel::setSurface` with SIGSEGV, not a Java exception. `mediacodec-copy` avoids passing a Surface and completes playback; `MpvHwDecPolicy` limits that path to SDK<=28 so newer devices keep surface-first decoding.
- The copy path may fall back to software decode; it fixes the crash, not necessarily MPV performance.

## Navigation and playback preload — 2026-11-10
- `HomeActivity.onNavClick` already handled `home_setting`; moving the entry required only a nav item plus removing the My-card bindings/layout. API28 UI confirms the new order and the absence of the My settings card.
- `preload_next` defaults to true in the upstream player and shares a 64MB next-item preload buffer with foreground playback. CinemaProfile now writes false only when the key was never set, using a separate migration marker so existing 0.1.x installs receive it.
- This mitigation is unmeasured on the user's TV; it can reduce network/CPU contention but cannot be called the confirmed stutter root cause.

## Android 9 source-selection crash — 2026-11-10
- TV screenshot identifies 0.1.3 / Android9/API28 and `SearchRelevance$Query` initialization. The nested cause is not visible in the photo.
- API28 ARM64 emulator reproduces that exact click-path failure and reports `PatternSyntaxException` for `\p{IsHan}`. The same failing expression and R8 ID exist in the downloaded public 0.1.3 APK, and its actual DEX fails to initialize on API28. The 0.1.4 source contains the same expression.
- `\p{script=Han}` compiles on API28 and host JDK. One-line fix passes the existing matching tests, 8 Android9 integration cases, and API28 release-Dex initialization. User TV and ARMv7 runtime are not connected.

## Homepage recovery implementation — 2026-11-09
- Cache-first uses a five-item, per-category snapshot with a seven-day age limit and HTTPS-only artwork; transient empty feeds preserve the visible hero instead of clearing it.
- Auto rotation now waits for the successor image and swaps title/indicator/artwork atomically; only the next image is prefetched at view size. Manual switching remains immediate.
- Snapshot writing compares the item body before replacing it, so repeated renders do not rewrite unchanged JSON; freshness is a separate timestamp key.

## Homepage inspection and delivery readback — 2026-11-09
- Published BuildConfig has empty TMDB key; direct TMDB list requests are skipped. RexNow feed can still supply TMDB identity/artwork; editorial label is not list-origin evidence.
- Feed caches are process-local; existing Glide caching remains enabled. No explicit next-slide preload; 7s auto-advance cancels pending artwork although its call timeout is 20s. This is a conditional weak-network risk, not a proven TV root cause.
- Explicit curl no-HTTP-proxy same-image test: proxy HTTP200 JPEG50,599B/1.76s; official HTTP200 same size/2.97s. Host-only samples. Direct Douban poster succeeds with the app's existing Referer; headerless418 is not app evidence.
- Public 0.1.3 and successful cloud run42624579403 verified. Downloaded cloud/local release signer is identical; version codes/hashes differ. No implementation or new remote write during this consultation; bounded cached-first/ready-image rotation is only a recommendation.

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
