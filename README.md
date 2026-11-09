# TV Cinema

基于 FongMi/TV 的原生电视界面改造，目标设备为 **Android 9 / 32 位 ARMv7**。
本项目保留上游 GPL-3.0 许可证与来源；不提供影片内容。

## 当前状态

**开发中，尚未产出可安装的新 APK。**

- 影院风电视界面、遥控焦点、轻量模式与预置订阅流程已落入源码。
- Android 资源链接及 `catvod` Java 编译通过。
- 完整应用构建仍受阻：上游 5.8.7 引用的定制 Media3 类和配套原生 AAR 尚未找到匹配构建输入。
- 不以旧版 AAR、接口桩或重新签名的上游 APK 冒充本项目发布包。
- 当前没有电视真机验证、实际新 UI 截图或性能通过结论。
- 需求、决策、验证与接手信息见 [项目上下文](docs/features/tv-cinema/context.md)。

## 改造方向

- 原生 Android Views / Leanback；保留现有播放与订阅契约。
- 低干扰深色、暖白文字、克制金色焦点；方向键、确定与返回优先。
- 轻量模式默认关闭动态壁纸和焦点缩放；电视图片缓存设上限。
- 点播、直播、搜索、收藏、设置及播放器控件使用统一视觉层次。
- 用户指定网站的订阅独立保存、按 URL 去重；不混合不同插件规则。
- 预置订阅首次使用前说明第三方插件行为；不覆盖已有订阅。
- 独立包名 `com.mjnhmd.tvcinema`、独立签名，和 FongMi 原版共存。

## 构建前提

需要 JDK 21、Android SDK 37 / Build Tools 37、Gradle Wrapper，以及与
FongMi TV 5.8.7 匹配的 `app/libs/lib-*.aar`。普通 Maven Media3 或旧 fork
AAR 缺少本应用使用的接口，不能互换。文件必须来自可信、可追溯的构建输入。

先检查输入；此检查只确认必需类名存在，不替代 Java 编译或原生 ABI 校验：

```sh
python3 scripts/cinema-verify-inputs.py
```

`local.properties` 配置 `sdk.dir`、`storeFile`、`keyAlias`、`storePassword`。
私钥、密码和本地配置不得提交。Gradle 原有配置使用 `storePassword` 作为密钥密码。

```sh
sh scripts/cinema-build.sh
```

脚本只构建宿主电视应用，避免配置无关运行时触发额外 NDK 下载。保留 release
混淆、资源压缩与原生校验。输出按 ABI 拆分，电视交付选
`leanback-armeabi_v7a.apk`；ARM64 产物只用于其他设备或模拟器验证。
发布前仍须核对最终 APK 清单、签名、ELF 架构，并在 Android 9 ARMv7 真机验收。

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
