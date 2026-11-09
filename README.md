# TV Cinema

基于 FongMi / wobuhui666 生态的原生电视界面改造，目标设备为 **Android 9 / 32 位 ARMv7**。
生产构建使用锁定的 `wobuhui666/TV` + `wobuhui666/media` 配对源码，以及本项目的影院风与轻量补丁。
本项目保留上游 GPL-3.0、Media3 与第三方许可证及来源；不提供影片内容。

## 当前状态

**已生成独立签名的 TV Cinema 0.1.0 ARMv7 Release；旧电视真机验收尚未执行。**

- 完整 Kotlin/Java、R8 混淆、资源压缩与 Release 签名构建通过。
- 已核对 minSdk 24、仅 `armeabi-v7a`、38 个 ELF32 ARM 原生库及签名。
- 生产入口不再依赖未找到的 FongMi 5.8.7 私有 AAR。根 `app/` 保留为最初 UI 实验，不能直接用根 Gradle 出成品。
- 不用桩类、重新签名的上游 APK 或参考包冒充本项目构建。
- Android 9 ARMv7 电视的安装、解码、帧率、内存与长时间播放仍需真机验证。
- 需求、决策、验证与接手信息见 [项目上下文](docs/features/tv-cinema/context.md)。

## 改造方向

- 原生 Android / Leanback 与配对底座已有的 Compose 控件；不做框架重写。
- 低干扰深色、暖白文字、克制金色焦点；方向键、确定与返回优先。
- 轻量模式默认减弱界面动画；海报手动切换、电视图片缓存与大图解码设上限。
- 点播、直播、搜索、收藏、设置及播放器控件使用统一视觉层次。
- 用户指定网站的订阅独立保存、按 URL 去重；不混合不同插件规则。
- 预置订阅首次使用前说明第三方插件行为；不覆盖已有订阅。
- 独立包名 `com.mjnhmd.tvcinema`、独立签名，和 FongMi 原版共存。

## 构建前提

需要 JDK 21、Python 3.10、Android SDK 37.0 / Build Tools 37、NDK r29
与 CMake 3.22.1。脚本只使用已安装的工具，不自动安装全局组件。

`distribution/baseline.json` 固定 TV 与 Media3 的提交、原生包哈希、Cinema 补丁哈希及复制清单。
准备器只初始化缺失的依赖检出，不 reset / clean 已有改动；未知修改会明确失败。

```sh
python3 scripts/cinema-prepare.py
python3 scripts/cinema-verify-inputs.py
```

`local.properties` 配置 `sdk.dir`、`storeFile`、`keyAlias`、`storePassword`。
`storeFile` 相对根 `app/` 解析，`keyPassword` 可选，默认与库密码相同。
私钥、密码和本地配置不得提交。

```sh
bash scripts/cinema-build.sh
bash scripts/cinema-build.sh --abi arm64-v8a
```

默认输出 `build/outputs/tv-cinema-armv7.apk`；ARM64 包仅用于其他设备或模拟器验证。
默认版本 `0.1.0` / code `10000`，可通过 `TV_VERSION_NAME` / `TV_VERSION_CODE` 显式更新。
脚本核对最终 APK 包名、minSdk、单一 ABI、非 debuggable 与签名；设备验收另行记录。

## 配对底座

- TV：`wobuhui666/TV`，`17abcd7453a0ecbdbc9ed40a22f3fd3632158495`。
- Media3：`wobuhui666/media`，`342f6590e02ac390e0bd6b4488b8a1bff55747c1`。
- 保留这套实现的 Exo、MPV、ASS、双字幕与弹幕；不宣称与 FongMi 未公开的所有私有扩展等价。
- 详细原生依赖来源、许可和桥接说明保留在锁定上游检出的 `third_party/`。

## 上游：影視TV

Android TV、手機、平板都能用，支援本地檔案與網址播放。

[使用指南](https://fongmi.github.io/TV) · [討論群組](https://t.me/fongmi_official)

## 主要功能

- **瀏覽**：分類篩選、跨站搜尋、收藏、播放記錄與無痕模式。
- **播放**：倍速、字幕、彈幕、片段跳過、畫中畫與背景音訊。
- **直播**：分組選台、節目表、頻道收藏與線路切換。
- **操作**：電視遙控器、手機手勢、DLNA 投放與接收。
- **互通**：網頁遙控、檔案傳輸、記錄同步與 Android Auto。

爬蟲開發說明與 Java、Python、QuickJS 範例見 [CatVodSpider](https://github.com/FongMi/CatVodSpider)。

## Star History

<a href="https://www.star-history.com/?repos=FongMi%2FTV&amp;type=date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=FongMi/TV&amp;type=date&amp;theme=dark" />
    <img alt="FongMi/TV Star History" src="https://api.star-history.com/chart?repos=FongMi/TV&amp;type=date" />
  </picture>
</a>
