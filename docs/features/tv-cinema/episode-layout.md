# 电视选集排序与详情布局 — 2026-11-17

## 目标与范围

用户两张电视照片显示：预览/简介占上半屏，来源与范围掉到选集后面，需要反复滚动；带大小/日期的综艺文件列表混乱。用户要求参考网络交互规范，出方案并实现，并让独立验证 agent 反驳和检查方案/代码。本轮授权设计、代码、本地构建和隔离验证；未授权提交、推送、发布或安装用户设备。HEAD `637d2df28078883f51c4d064e0979c0a97c6c199`，初始工作区干净。用户此前明确不需要监控 CI，本轮不查询或监控旧发布任务。

ppm context 只读查询成功；主题仍标未核验。checkout 位于四个允许根目录以外，不登记/更新台账。当前代码和测试为真相源。

## 当前问题与证据

- `Flag.setEpisodes` 按源返回的顺序构造，合并只追加，界面原样展示；“倒序”仅反转。没有集号/日期自然排序。截图能证明显示乱序，不能证明片源为何返回该顺序。
- 960×540dp 下原预览432×243、top48，下部约201dp，三行选集加标题已占满。仅交换控件顺序解决不了可视面积。
- 独立布局/回归审查共同指出：排序必须作用于controller真实列表并保持位置/URL身份；固定来源范围；选集脱离外层NestedScrollView；同步显式焦点链及全屏显隐；预览尺寸保持静态以兼容mFrameParams缓存。

## 采用方案

- 左栏：320×180预览（16:9），下方紧凑标题/元数据/全屏/简介/收藏/跨站换源；次要清晰度/关联/搜索结果单独小滚动区。
- 右栏：来源、集数范围固定；选集上方保留当前焦点完整原名；剩余高度给独立网格。上下滚动选集时来源与范围不离屏，行首左键返回工具条。
- 不以集数改变主要控件位置或扩大按钮。少集数网格按内容收缩，剩余显示简介摘要；多集数用满可用高度。短标题自然宽度，可靠解析的长文件名压缩显示语义，原name/URL不改，完整名随焦点显示。
- 排序TV显式启用：优先可靠集号（季+集+上中下+版本），其次有效日期；低置信/合集/无法识别时保持源序；未知项稳定。反转仍基于真实列表；当前Episode对象/URL保持，历史与下一集同序。最终规则以测试证据补全。
- 保留EXO Surface/MPV Texture与现有全屏切换，不新增视频纹理、模糊、图片/字库依赖或逐帧计时。

## 来源与交互依据

已通过android-cli检索阅读Google TV [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts) 与 [Navigation on TV](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv)：960×540dp基准、固定16:9、沿横纵轴优化；遥控路径应短且可预测，无法直达的控件应重新放置。采用现有Android TV组件和低动效风格，未套用网页视觉框架。

## 可执行验收

- 排序：截图6个混乱文件、2/10、中文期号、上中下、日期、花絮/加更、缺失/坏日期、合集、无编号；不按GB/4K排序，不漏/重条目。
- 身份：排序/合并/倒序后同一当前URL、同一选中对象；下一集/上一集与网格一致，历史恢复同一集，手机未启用新策略。
- 布局：3/6/61/1000集、长中文/英文、空列表；来源与范围首屏可见；焦点/播放选中独立；方向键能进出网格/工具条、范围跳转不自动开播；返回和对话框恢复。
- 真实页面截图：标题/按钮/边框不裁切，预览16:9、网格内部滚动不遮挡工具条；独立审查agent读代码并检查截图/测试，不以口头设计代替验证。
- EXO/MPV窗口/全屏4项原回归与相关JVM/工具检查；ARMv7签名构建及实际manifest/ABI/signer检查。API28 ARM64模拟器与用户ARMv7电视证据分开。

## 状态与下一步

已实现：`CinemaEpisodeOrder`（TV 显式启用、Flag 位置按对象重算、无法可靠解析保原序）、`CinemaEpisodeLabels`（长文件名紧凑标签+重复回退原名+焦点完整名）、新 `activity_video.xml` 左栏预览/紧凑详情/次要项，右栏固定来源与范围、选集独立滚动；`Flag.reorderEpisodes` 用新列表承接排序以兼容不可变输入。

## 2026-11-17 追加：紧凑详情与播放态

- 删除详情区重复的“全屏观看”按钮：视频本身可聚焦，确认键直接进入全屏；选中正在播放的剧集再次确认也会进入全屏。三个次要操作（简介/收藏/换源）排在标题与元数据下方，不再被底部空隙推开；详情高度 216→168dp，次要项区域 72→96dp。
- 当前播放集用金色 `▶`、琥珀描边和琥珀文字标记；遥控焦点仍是白色描边，两种状态可同时辨认。选中标签宽度计入播放标记，避免“▶ …”被截断。
- 来源/范围横条在首尾补左右移交：范围行首按左返回详情/预览，不再被困在横向列表里。
- 真实内置源验证（API28 ARM64、960×540dp）：从首页 TMDB 条目“Search & Play”真实搜索到 14 个源，进入 `比特┃不卡` 的《今天是太阳雨》详情，20 集真实剧集；确认标题/元数据/操作按钮紧凑排列、来源与范围固定、当前集金色播放标记、遥控白框可落在其它集、按确认键在视频上可真实全屏播放。截图 `build/outputs/cinema-real-series-selected.png`、`cinema-real-focus-vs-play.png`、`cinema-real-fullscreen2.png`。
- 独立 `JetStreamEpisodeGridAndroidTest` 的两项长标题跑马灯断言在该模拟器上失败；临时去掉播放标记后同样失败，确认与本次样式无关，记录为既有/设备相关焦点时序问题，不冒充通过。

收口修正（2026-11-17，独立审查阶段发现）：
- `CinemaEpisodeOrder` 原用单一 `known` 分组，会把“有日期但没有集号”的花絮/发布会条目当成第 0 集排到第 1 集之前。改为 `numbered` / `dated` 两套证据分别分组：按集号排序时无集号条目稳定排在编号集之后，按日期排序时无有效日期条目排在最后。新增回归 `datedExtrasWithoutEpisodeNumberStayAfterTheNumberedEpisodes`（9 条带集号 + 1 条日期花絮），修正前实际输出 `[extra, u1…u9]`，修正后 `[u1…u9, extra]`；另补纯日期列表的正序/倒序回归（无日期条目两种方向都在末尾）。
- `mergeFlags()` 的刷新路径漏刷新：`mergeFlag()` 先按合并后的原始顺序 `renderEpisodes`，随后 `normalizeEpisodeOrder(true)` 会换成一个新的已排序 List，网格仍停留在旧顺序，而 `getRelativeEpisode()` 已按新顺序算上一集/下一集，导致“下一集”和屏幕相邻格不一致。现在归一化后再对激活来源重新 `renderEpisodes`，触发入口是 `RefreshEvent.VOD`（YouTube 解析/服务端推 `vod`）。该路径没有自动化测试覆盖（需要完整 `VodPlaybackController` 宿主），属已修但仅靠代码审查与编译验证的项。

证据（2026-11-17）：聚焦 JVM 225 项通过、0 失败（`source.*` 190 项，含排序 9、标签、导航；`playback.vod.*`；`ui.custom.Cinema*`/`JetStream*`）；32 项工具检查通过；`cinema-verify-inputs.py` PASS，补丁在干净 HEAD 的临时 worktree 上 `git apply --check` 通过。API28 ARM64、960×540dp 窗口：37 项全部通过（17.262s），含面板 1、真实 EXO/MPV 窗口 4、EpisodeNavigation、ExplicitEpisodeSelection 7、性能 3；截图 `build/outputs/cinema-episodes-long.png`（61 集）与 `cinema-episodes-short.png`（3 集，简介填充）。截图确认来源/范围在选集上方首屏、选集占右栏主要高度、焦点完整原名可见、短列表不被拉成大按钮。

证据边界：37 项 API28 运行发生在排序分组修正之前。`CinemaEpisodePanelAndroidTest` 直接调用 `renderEpisodes`，不经过 controller 排序，因此该运行不覆盖比较器改动；比较器改动由 JVM 测试覆盖，未在模拟器上重跑。

审查发现的既有问题：`detailLoaded` 直接 `item.getFlags().removeIf(...)`，遇到不可变 flags 会抛 `UnsupportedOperationException`。现改为先复制成 `playableFlags` 再过滤，只有确实移除导航列表时才回写 `item.setFlags(...)`；不改变正常 Gson 列表行为，并使上述 7 项回归恢复通过。

最终 ARMv7 签名 Release（紧凑详情 + 播放态样式后重建）：canonical `build/outputs/tv-cinema-armv7.apk`，拷贝 `build/outputs/tv-cinema-armv7-episode-layout.apk`，`0.1.9-episode-layout-local` / `218046140`，78,935,176 字节，SHA-256 `13521102df68a84c165a9aa74db5bde4eec0bd383dc11ddab24ae8519d964919`；901 tasks，Release Java/Kotlin 重新编译，R8 与资源缩减、38 个裁剪 native libraries、两个精简依赖归档、签名/包名/minSdk24/ARMv7-only 均通过（`build/logs/cinema-episodes-armv7.log`、`cinema-episodes-apk.json`）。

此前交付路径 `tv-cinema-armv7-episode-layout.apk` 曾是 `218015630`（78,935,532B，`abb26a60…`），不含排序分组修正；该文件已改名为 `tv-cinema-armv7-episode-layout-superseded-218015630.apk`，同名路径现指向本次 `218016100`，避免装到旧包。

已知非阻断项：右栏没有任何次要行时，详情区按“下”没有落点，需要按“右”进入来源/选集；有来源或选集时路径正常。`updateFocus()` 在全部行都隐藏时把详情区的右/下指回自身。

剩余：用户 TV 实机外观/遥控/真实片源仍 not_tested。未提交/推送；Release/OTA 未更新。
