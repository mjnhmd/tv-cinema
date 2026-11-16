# Gugaart 对 Cinema 的适用性 — 2026-11-16

## 用户目标与范围

用户提供 https://github.com/camille3344520/gugaart，询问能否用在自己的项目、有哪些可借鉴点。本次按当前工作区 TV Cinema 评估，涵盖 Android 9 ARMv7 电视和 Android 手机；仅调研，不实施功能或更换底座。

结论：适合作为交互和少量策略代码的参考，不适合作为可直接接入的完整底座。Cinema 保持现有生产构建、片源与播放器；后续功能优先级是建议，尚未成为开发需求。

## 核实范围

- Cinema：根 HEAD `a925babf7023274906590a34cdb60332a1e6a26c`，开始时工作区干净；读取 `CLAUDE.md`、feature/mobile context、`distribution/baseline.json`、生产 patch/overlay 和物化的配对生产源码。
- Gugaart：公开 main 固定为 `60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5`；源码归档放在忽略目录 `build/reference/gugaart-60f3cff782f2/`，未执行其脚本。当前 GitHub 最新 Release 为 v1.2.5。
- 读取 README、Gradle 配置、字幕/搜索/备份/Trakt/TV UI 代码，查看 README 提供的 TV 首页/播放器及手机首页图片。README 说明截图内影视数据和画面为虚构示例。
- ppm 搜索 `gugaart` 无命中；该 checkout 不在四个工作根目录下，没有创建或更新 ppm 项目、事项。
- 未构建 Gugaart、未运行其 APK、未验证真实 Emby 服务器或用户电视；所有性能与安装结论均保留此限制。公开树未找到测试源码，代码注释里的“有单测”不作为本次验证证据。

## 为什么不能整套接入

1. README 第 185 行明确说明播放器、弹幕、Emby 接口没有公开，仓库不能独立编译。`settings.gradle.kts:20–58` 还要求同级 `gugugagaemby/native` 里的版本目录和六个模块；`:ui` 通过 `api(project(":app"))` 使用这些类型。
2. 两者数据模型不同。Gugaart 围绕 Emby 服务器、用户、媒体库和 TMDB 等媒体身份组织数据。Cinema 原生业务是片源插件、VOD/live、订阅和播放记录；本次未找到内置 Emby 媒体库层。第三方插件可能另有扩展能力，不能把静态检查扩大为所有片源均不支持 Emby。
3. Gugaart 页面采用 Compose Multiplatform，并依赖未公开的 AppGraph、设计系统和播放器控制器。Cinema 使用配对原生 Views/Leanback 与已有 Compose 控件；整套迁入需要大量适配，与当前不做框架重写的规则冲突。
4. 两仓库的主许可证均为 GPL-3.0。复用公开代码需保留来源、版权与适用许可证，分发遵守 GPL；这不等于未公开模块已经可获得，也不能直接推断所有字体、图片、品牌资产都适合原样复用。

## 值得借鉴的点

| 建议 | 用户收益 | Cinema 当前情况与落地边界 |
| --- | --- | --- |
| 中文字幕置顶，字幕状态说明来由 | 不用翻长列表；看得懂当前是手选、自动还是上次选择 | `SubtitleOrder.kt` 的语言码+标题稳定排序可单独适配。Cinema 已有系统语言偏好和 Track 持久化/恢复，不应把字幕记忆当全新缺失功能；先审查现有策略再补差异 |
| 播放器操作显示当前值，面板减少遮挡 | 字幕、音轨、倍速不必反复进入查看，设置时仍能看画面 | Gugaart `TvPlayerScreen.kt:134–143,789–820`。Cinema 已有相关入口；借鉴布局和状态呈现。缩小视频给面板留位涉及 SurfaceView/TextureView，必须回归窗口/全屏和 EXO/MPV，不能当纯样式改动 |
| 静态焦点外框与角标 | 坐远仍能识别焦点，焦点变化不挤动列表 | `TvFocus.kt:101–129` 在内容外绘制框与四角标记，不占布局。保留 Cinema 配色和低动效，按实际电视可读性选择造型，无需整套迁入 Compose |
| 本地拼音首字母匹配 | 遥控器输入几个字母就能找到收藏、历史中的影片 | `Pinyin.kt` + 汉字表为可抽取算法。只用于已有本地名称，存在多音字限制；不能把缩写交给所有远程片源就承诺能搜到中文片名 |
| 续看动作与进度更明确的首页层次 | 进入首页后更快继续观看 | 借鉴“续看第几集/进度/从头看”的信息组织；Cinema 已有历史、收藏和首页缓存，属于呈现优化。元数据不足时使用现有海报/文字回退 |

更合适的实施顺序：中文字幕菜单排序和状态呈现 → 本地搜索增强 → 首页/播放器布局试验。用户尚未要求实施，不生成开发事项、工期或未经确认的验收标准。

## 不适合照搬的部分

- **中文对白自动关闭字幕**：`SubtitlePick.kt:83–85` 直接由中文对白推断不需要外挂字幕。中文对白不能证明画面已烧录字幕；Cinema 的插件元数据也可能缺失，应尊重手选并保留可解释的回退，不能复制此默认行为。
- **3D 海报流、液态玻璃、持续自动切换及高分辨率剧照优先**：增加图片、绘制与调度负担；当前低端电视规则已选择克制动效和图片预算。没有实机测量，不能断言必然卡顿，也不能凭截图认定足够流畅。
- **重做播放内核/更新/WebDAV**：Cinema 已有 EXO/MPV、轨道恢复、WebDAV 同步与包名/版本/签名/ABI/hash 更新校验，不应仅因对方也有这些能力就替换。引入 VLC 需要新的原生依赖、包体和兼容验证。
- **照搬备份格式**：Gugaart `ConfigBackup.kt` 排除 Emby 登录令牌和请求头，但仍导出 TMDB token 和可能含 token 的弹幕地址，并非完全不含凭据。Cinema 现有同步白名单与并发保护应保留。
- **Trakt、多弹幕服务器**：Trakt 强依赖可信的影视身份映射，插件结果未必提供；多服务器有额外的匹配、候选与错误处理成本。可以研究交互，但不是拿一个模块就能接好的升级。
- **直接运行 HTML 设计稿**：`design/README.md` 说明数据包/图片包等未公开，完整 demo 不能直接还原。公开截图和布局源码仍可用于参考。

## 设备和包体

README 宣称手机 Android 13+、TV Android 9+；壳构建配置列出 `armeabi-v7a`、`arm64-v8a`、`x86_64`。对用户电视属于声明层面的匹配，未审计发布 APK 的实际 ABI/manifest，也未验证安装播放。

GitHub v1.2.5 TV asset 为 179,228,288 字节（约 179.2 MB）；Cinema 已记录的云端 ARMv7 包约 78.7 MB。两者 ABI、内核和缩减配置不同，不能把全部差值归于 UI，也不能用 APK 大小代替帧率、内存或播放稳定性实测。Gugaart 公开 Release 配置关闭 R8 和资源缩减。

若用户确有 Emby 服务器，Gugaart 发布版可以作为独立应用试用；它不会替代 Cinema 的现有片源服务。是否向 Cinema 增加 Emby 数据源是另一个产品需求，本轮没有授权实施。

## 一手来源

访问日期：2026-11-16。下列源码均对应固定提交，Release 为读取时最新版本。

- [README 与不完整源码说明](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/README.md#L182-L185)
- [构建挂载关系](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/settings.gradle.kts)
- [Android 版本、ABI 与 release 配置](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/shell-android/build.gradle.kts)
- [字幕排序](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/ui/src/commonMain/kotlin/com/gugaart/ui/player/SubtitleOrder.kt)
- [字幕选择策略](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/ui/src/commonMain/kotlin/com/gugaart/ui/player/SubtitlePick.kt)
- [TV 播放器](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/ui/src/commonMain/kotlin/com/gugaart/ui/tv/player/TvPlayerScreen.kt)
- [TV 焦点](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/ui/src/commonMain/kotlin/com/gugaart/ui/tv/TvFocus.kt)
- [本地拼音匹配](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/ui/src/commonMain/kotlin/com/gugaart/ui/data/Pinyin.kt)
- [备份字段与还原](https://github.com/camille3344520/gugaart/blob/60f3cff782f2cc23e32be6512b7e1cf8dc97dbb5/ui/src/commonMain/kotlin/com/gugaart/ui/backup/ConfigBackup.kt)
- [v1.2.5 Release](https://github.com/camille3344520/gugaart/releases/tag/v1.2.5)

## 状态与下一步

调研完成；仅新增本评估与更新调研计划/进展、context 索引，没有修改应用代码、依赖或构建输入，没有提交、推送或发布。后续如用户要求开发，先选择具体体验改进，再以现有生产 recipe 和测试界定增量。
