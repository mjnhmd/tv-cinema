# Cinema 体验与性能优化 — 2026-11-16

## 本轮追加：Bugly 性能诊断与 Git 交付

用户明确要求提交推送前述优化，并希望利用已接入的 Bugly 监控实际使用问题和卡顿原因。核实 SDK 的自动上报边界，针对真实异常场景补充有限频次的自动诊断；避免把普通 logcat 当作实时云日志。完成验证后提交并推送当前 `fongmi` 分支；没有授权发布 Release 或安装设备。

初始状态：HEAD `a925babf7023274906590a34cdb60332a1e6a26c`，dirty 文件为本对话上一轮性能优化、测试和调研文档；应包含在本次授权交付中。查询 ppm context 为 partial，历史没有本机登记检出，目录位于允许的四个工作根目录之外，不写 ppm 台账。

现有 `CinemaMonitoring.start()` 启用 Bugly Android `4.1.9.7`，维护菜单可主动提交 caught exception；现有可选 `DiagnosticManager` 是另一套本地采集，不是自动向 Bugly 实时传输。直接检查本机 AAR 的 `CrashReport`：`postCatchedException` 在 SDK 初始化后派发给 SDK worker；它是异常上报请求，不是服务端收件确认；`BuglyLog` 是事件随异常附带的线索，不按普通日志实时推送。

本轮方案：仅有明显异常时提交非致命诊断，附有限阶段耗时/实际界面卡顿栈/播放器引擎与缓冲状态；使用去重和每进程数量上限，减少旧电视负担。既有 Bugly 全局原始 Logcat 设置遵循用户之前对私人电视的明确选择；新增结构化字段不添加片源 URL、headers、账号或 token。正式包和隔离测试包严格区分，测试不得向生产 Bugly 投递模拟故障。

### 新增自动诊断

| 场景 | 触发与记录内容 |
| --- | --- |
| 界面无响应 | 仅前台界面、屏幕交互期间每秒检查主线程回执；等待至少 2 秒仍未处理时，在后台抓当时主线程堆栈。排除已经恢复或处于消息队列空闲的栈，不等恢复后再取栈 |
| 界面持续慢帧 | FrameMetrics 本地聚合 5 秒窗口；至少 10 帧、其中至少 8 帧 ≥50ms 且占比 ≥25% 时提交计数、最慢帧和 input/layout/draw/sync 中测得耗时最大的阶段。缺失回调单独计数；该阶段是线索，不直接作为根因 |
| 页面恢复首帧慢 | resume 到实际 FrameMetrics 回调 ≥8 秒时提交 `slow_ui_draw`；并非冷启动总耗时 |
| 片源地址获取 | `SiteViewModel.playerContent` 开始/结束，≥8 秒提交 `slow_source`；失败提交 `source_error`，切源/退出取消不报；只附 source type |
| 起播和播放中缓冲 | 解析/prepare/first_frame 总等待 ≥8 秒提交 `slow_start`，音频流以 READY 完成；用户暂停不算等待。完成起播后缓冲 ≥5 秒提交 `slow_buffer`，标记最近是否 seek，结束后不重复提交 |
| 播放错误与解码 | 播放/解析失败和原有起播 timeout 提交事件码；EXO 附实际解码器名称、格式、分辨率，单次回调丢 ≥20 帧且平均 ≥2 帧/秒提交 `video_dropped`；MPV 首帧附 `hwdec-current` 和 codec。设置的 decode 偏好不等于实际硬解状态 |

所有自动异常使用 Bugly caught exception，交给独立后台队列；同一事件类型 10 分钟最多一次、每进程最多 20 次。正常阶段用有限 breadcrumb，缓冲/起播结束后无待诊断工作便停止轮询；没有逐帧网络上传、截图/视频采集或遥控按键日志。阈值是排查严重问题的初始策略，不代表流畅度合格线。手机共用这些监控，通过既有 `leanback`/`mobile` channel 区分。

### 本轮监控验证

- `cinema-prepare.py` / `cinema-verify-inputs.py` 与 32 项 build-tool tests 通过。最终 35 项针对性 JVM 检查通过，其中监控 timeline 6、frame window 2、限流 2；另有规格/首页/字幕/轨道回归。电视 ARM64 debug 与测试 APK 完整打包、手机 ARM64 Java/Kotlin 编译通过，见 `build/logs/cinema-bugly-final-debug.log`、`cinema-bugly-tools.log`。
- API28 ARM64 task-owned emulator-5590、隔离包 `com.fongmi.android.tv.sourceprobe`：16 项运行时测试通过（21.463s），其中监控 3、真实 EXO/MPV 窗口播放 4、性能 3、加载队列 4、真实搜索页 2。监控测试在主线程尚阻塞时断言堆栈含 `blockMain`，并验证慢片源去重、取消和失败。结果 `build/logs/cinema-bugly-final-runtime.log`；阶段样例 `cinema-bugly-final-perf.log`。
- 测试包不初始化 Bugly（测试显式断言），只在 debug 中注入本地接收器验证投递路径；生产包保持原 SDK 初始化。模拟的主线程阻塞不会向生产控制台发报告。首次运行命令误写性能用例 package，产生 ClassNotFoundException；纠正为 `ui.custom.CinemaPerformanceAndroidTest` 后完整 16 项通过，无运行时代码绕过。
- 尚未验证：生产 Bugly 控制台真实收件/延迟、用户电视 Android9 ARMv7 的界面帧率/网络/解码/长时播放、真实片源和同条件 OK 影视 A/B。SDK 调用成功和本地测试通过均不证明远端收到；需要安装包含本轮代码的包后产生真实事件再核验。
- 最终只读 review 无阻断；保留的诊断边界：20 次是每进程累计，常驻进程达到后停止自动报告，后续可按真实事件量改为时间窗口；解析阶段的用户暂停还不能精确扣除，未知视频轨道在 READY 时可能按音频完成而漏掉慢首帧。监控结果用于指引取证，不是完整性能覆盖。FrameMetrics 后台逐帧聚合及每秒回执也需在真实电视观察自身开销。

### 包含监控的交付包

- `build/outputs/tv-cinema-armv7-performance-monitor.apk`，canonical 同时为 `tv-cinema-armv7.apk`；`0.1.9-performance-monitor-local` / code `217988972`，78,930,556 字节（78.93 MB），SHA-256 `ea26523286516757abe7c05a8aaf717abe22875c176d581c6f99d926ea06eeb6`。
- `TV_VERSION_CODE=217988972 TV_VERSION_NAME=0.1.9-performance-monitor-local bash scripts/cinema-build.sh`：ARMv7 签名 Release 完整成功，R8/资源缩减开启（3m1s /901 tasks）。实际包名 `com.mjnhmd.tvcinema`、minSdk24、非 debug、ARMv7-only、与上轮测试包同签名；38 个裁剪后 native libraries、两个精简依赖归档与其 hashes 通过，满足现有 79,000,000B 上限。
- 完整证据在 `build/logs/cinema-bugly-armv7-release.log`、`cinema-bugly-final-apk.json`，本轮所有 patch/overlay/build 输入指纹 `cinema-bugly-final-inputs.sha256`。SDK 和监控代码均存在于最终 Release 的 R8 mapping 中；为日后解读混淆栈，已将本包 mapping 单独保留为 `build/outputs/tv-cinema-armv7-performance-monitor-mapping.txt`（不入库）。专用 emulator-5590 已停止，未改连接手机、既有模拟器或用户电视数据。
- 此包取代下文“本地交付包”的早期无监控包作为当前测试交付；两者不混用。没有创建 GitHub Release，也没有更新公共 OTA 或安装到用户设备。

## 目标与授权

用户授权落实 gugaart 调研中的体验优化，并反馈 Cinema 比原版 OK 影视在界面操作、播放视频、加载速度上更卡。优先排查和降低可验证的额外开销，保留片源、历史、字幕、弹幕及用户已设置的行为；不更换播放底座，不凭推测宣称用户电视根因。

初始授权为开发、调研、本地构建与隔离验证；本轮后续明确授权提交推送，并追加 Bugly 自动诊断。Release 发布仍未授权。当前目录不在四个 ppm 工作根目录内，不创建或更新台账。ppm context 返回历史范围不完整，不能将全机近似命中归于此项目。

## 原始需求与检查项

- 界面：减少轻量模式下与内容无关的模糊、背景处理、动画和隐藏控件唤醒；D-pad/Back 与焦点可见性保持有效。
- 加载：区分配置/插件取数、影片地址解析、网络首包、播放器缓冲；只对证据明确的瓶颈改实现，保留超时和取消边界。
- 播放：保留 Android9 已验证的渲染/内核兼容策略，检查隐藏页面和控制层是否仍占用 UI/解码资源；不将 MPV 的硬解拷贝模式误称为纯软解。
- 字幕：中文字幕列表优先且稳定排序，当前选择不变；缺失/错误语言字段有回退，不因中文对白关闭字幕。
- 每个独立改动先做针对性验证，再继续下一项；可行时给出同环境前后测量。模拟器结果不代表真实 ARMv7 电视，缺失项明确 not_tested。
- 其他已同意的体验建议以不增加性能负担为前提。本地拼音搜索及布局改进在检查现有入口后确定实现方式。

## 当前状态

开工 HEAD `a925babf7023274906590a34cdb60332a1e6a26c`。已有 dirty 内容仅为本轮前一步 gugaart 调研文档与计划、`.gitignore` 例外；保留这些修改。

ADB 当前显示一台 PLG110 手机和已有 emulator-5554；均不作为本任务性能/安装目标。不自动连接或操作用户电视。将使用独立测试包和任务自己的模拟器。

## 关键决策与原因

- 先优化可证实开销，再讨论是否替换 UI/内核。当前生产入口仍为 `distribution/baseline.json` + patch/overlays + 配对 TV/Media3 源码，避免修改不出包的根 `app/` 实验代码。
- 与 OK 影视的对比需固定设备、版本、片源、影片、缓存与网络；缺少用户实际版本/片源时仅作源码机制对比，不承诺已追平。

## 验证、进展与下一步

已完成首轮优化、源码对照和本地签名构建；最终状态以本文记录的实际检查项为准。官方性能资料经 `android docs` 查询并阅读 [Overview of measuring app performance](https://developer.android.com/topic/performance/measuring-performance)，关注启动、滚动、后台竞争与内存，未把平台建议数值作为本电视承诺。

首个可复现瓶颈：`JetStreamAmbient.push` 的输出在当前纯色背景中无人读取，但焦点变化仍下载、模糊和取色；播放规格胶囊每次显示都启动视频回读模糊。轻量模式均未拦截。API28 ARM64 隔离测试包 `com.fongmi.android.tv.sourceprobe`、任务 emulator-5590 的改前结果：8 次海报更新产生 8 次 loopback 图片请求，1 秒规格展示产生 2 次视频回读，两个约束测试均如期失败。日志 `build/logs/cinema-performance-before-runtime.log` 和同名 logcat 输出；不是用户电视卡顿幅度测量。

尚未验证：用户 Android9 ARMv7 电视上的遥控器响应、起播、缓冲、解码帧率和长时播放；与原版 OK 影视同条件 A/B。

## 已实施与用户影响

| 改动 | 机制与用户影响 | 证据 |
| --- | --- | --- |
| 轻量模式跳过 `JetStreamAmbient.push` | 当前背景不消费氛围图结果，停止焦点触发的重复下载、位图模糊和取色；实际海报照常显示 | 同一 loopback 图片夹具：8 次切换的请求 8 → 0；非轻量模式仍能取图 |
| 轻量模式禁用规格胶囊的视频回读 | 保留编解码/分辨率提示和纯色背景，起播/窗口切换时不为装饰执行 PixelCopy、Bitmap 分配和模糊 | 同环境展示 1 秒：视频回读 2 → 0；规格仍可见 |
| Compose 控件遵守轻量模式 | 焦点颜色/外框保留，移除额外缩放、淡入和滑入时长；关闭轻量模式仍保留原参数 | API28 断言 120ms/220ms → 0、focusScale → 1，恢复设置后原参数有效 |
| 首页按当前分类加载 | 默认请求 7 个栏目而非 12；电影/剧集仍包含 trending 主推来源。其余分类切换时加载，已有 30 分钟缓存复用 | `CinemaHomeFeedPlanTest` + `PosterHomeCatalogTest` 10 项通过；不是实际 HTTP 请求总数统计 |
| compact 首页 3 个栏目任务并行 | 首条非空内容立即交付，后续 160ms 合批；栏目 timeout 从实际开始计 24s，独立取消 tag；推荐页仍可并行 12 个栏目 | 真实 loopback 测试覆盖 3 个活动任务、按顺序交付、close 不发排队项、单项超时让位、推荐页保持并发。regional 内部可串行回退多次 HTTP，不能说总请求只有 3 个 |
| 播放器进度轮询下沉 | 300ms 定时状态只更新可见进度条，信息层/隐藏控制条不再为不用的时间轮询；相同 command 不重复写入和校验 | 编译与实际 EXO/MPV 播放/窗口回归通过，未测真实电视 Compose 重组耗时 |
| 字幕菜单中文优先 | 按语言码、标题稳定排序：简体、其他中文、繁体、其他；不更改已选轨道、不改默认字幕策略 | 2 项 JVM 检查识别/稳定性/选中对象保留，原轨道持久化保持 |
| 播放器侧栏显示字幕/音轨当前值 | 复用实际选中轨道，显示标题或语言；关闭字幕显示关闭，不伪造“自动/上次”的来源 | 2 项 JVM 轨道状态检查；缺少名称/语言时保留非空提示，入口仍可触发 |
| 收藏/历史本地拼音搜索 | 使用 Android 内置中文索引，无新增拼音字典依赖；最多后台读 300 个当前订阅历史/收藏名称，输入匹配最多 12 个。远程建议合并且保留原文，用户可继续按全名搜源 | API28 `庆余年` → `qyn`、`周星驰` → `zxc` 和真实 SearchActivity 离线建议验证通过；多音字按系统默认，不承诺所有读音 |

源码交付位于 `distribution/cinema.patch`、明确 overlays 与 `baseline.json`，每阶段运行编译/针对性验证后再继续。没有迁入 gugaart 私有类型/播放器/字体或图片，没有新的依赖。副作用回归检查保留原始 source、history、render 和 decoder 路径。

## 实际执行与边界

- 环境：macOS、Android Studio JBR21、Python3.10、锁定 TV `17abcd7453a0ecbdbc9ed40a22f3fd3632158495` / Media3 `342f6590e02ac390e0bd6b4488b8a1bff55747c1`。
- 任务设备：复用 task-owned `cinema-search-api28-arm64` AVD，在独立端口 `emulator-5590`、1280×720、API28 ARM64；既有 emulator-5554 和连接手机未安装/修改。不是 ARMv7 硬件性能证据。
- `python3 scripts/cinema-prepare.py`、`python3 scripts/cinema-verify-inputs.py` 通过；全 production patch/blob 与 overlay 字节校验通过。`python3 -m unittest discover -s scripts/tests -p 'test_cinema_*.py' -v`：32 项通过，`build/logs/cinema-performance-tools.log`。
- Gradle 针对性 JVM：`CinemaHomeFeedPlanTest` 2、`PosterHomeCatalogTest` 8、`CinemaSubtitleOrderTest` 2、`CinemaTrackStatusTest` 2；共 14 项。ARMv7 Debug Java/Kotlin 与 Mobile ARM64 Java 编译通过。日志 `cinema-performance-combined-build.log`、`cinema-performance-status-build.log`。
- API28 ARM64：`CinemaPerformanceAndroidTest` 3、`CinemaFeedLoaderAndroidTest` 4、`CinemaLocalSearchAndroidTest` 2、`CinemaWindowRenderAndroidTest` 4。最终合计 13 项通过，16.064s（其中本地搜索测试含真实页面和焦点重排），命令为 `adb -s emulator-5590 shell am instrument -w -r -e class <四个完整类名> com.fongmi.android.tv.sourceprobe.test/androidx.test.runner.AndroidJUnitRunner`；结果见 `build/logs/cinema-performance-final-runtime.log`。窗口测试使用保留的本地 H.264/AAC 夹具，不运行第三方插件。结束后已停止专用 emulator-5590，保留 AVD 数据，未清理用户数据。
- 老 preview 套件 6 项中 3 项失败：导航返回后 D-pad 跳到 Search 的期望、最终空结果清空缓存 hero 的期望、时移触摸后再 D-pad seek。将本轮修改的 `JetStreamVodControlView`、`JetStreamAnimations`、`PosterHomeController` 三个文件换为本轮前 patch 版本、重新构建同 package APK，同模拟器同测试仍是相同 3 项失败（36.874s）；恢复本轮文件后 blob 校验通过。因此记录既有行为/测试契约不一致，不宣称这些项目本轮通过，也没有顺手修改时移/hero 空缓存策略。日志 `cinema-performance-preview-runtime.log` 与 `cinema-performance-preview-baseline-runtime.log`。
- 过程中的夹具错误分别修正：搜索框光标导致 `startActivitySync` 等不到 idle，改为等真实 resumed Activity；轨道测试把不同语言放同一 TrackGroup 导致 Media3 日志 stub 异常，改为独立字幕组；首次窗口回归缺本地夹具，复制后 4 项通过。均未把失败当成设备故障。

## 本地交付包

- `build/outputs/tv-cinema-armv7-performance.apk`（canonical 为 `tv-cinema-armv7.apk`），`0.1.9-performance-local` / code `217986333`，78,922,828 字节（78.92 MB）。
- SHA-256：`8fb5a0648791703ba5763143087c9130841632d689d8dd1fcb90dbbd5c5244b6`。
- `TV_VERSION_NAME=0.1.9-performance-local TV_VERSION_CODE=217986333 bash scripts/cinema-build.sh`：正式 ARMv7 Release，R8/资源缩减/签名完整构建通过（最终 3m45s / 901 tasks）。包名 `com.mjnhmd.tvcinema`、minSdk24、仅 ARMv7、非 debug；38 个裁剪后 native libraries 和两个精简依赖归档及 hashes 检查通过。签名与保留的此前 slim APK 一致。日志 `cinema-performance-armv7-release-final.log`、`cinema-performance-final-apk-audit.log/json`。
- 先前验证包使用临时 code `218012001`，已被最终包覆盖并排除交付，避免高于未来 CI 的时钟版本。没有安装到用户电视或手机，没有 public Release / OTA 更新。
- 打包后的唯一新增差异是 `CinemaLocalSearchAndroidTest.java` 中的焦点重排断言，属于测试源，已重建测试 APK 并纳入最终 13 项通过；所有运行时 source/patch/overlay 与最终 Release 输入指纹一致。指纹 `build/logs/cinema-performance-final-inputs.sha256`，签名与包体证据 `cinema-performance-final-apk.json`。

## 下一轮优化方案

| 优先级 | 方案与原因 | 需要的验证 |
| --- | --- | --- |
| 1 | 固定用户实际 OK 影视版本、同片源同一集，在同电视对比冷启动、首次焦点、地址解析完成、首帧、缓冲次数和掉帧，确定主要差距 | TV 版本/engine/render/codec、`am start -W`、`dumpsys gfxinfo ... framestats`、Media3/MPV 数据、同网络重复 3 次；不能用本机 curl 替代电视网络 |
| 2 | 若是列表/详情卡，合并 `VideoActivity.updateDetailView` 的多次状态刷新，并把收藏状态查询从每次 UI 刷新移到生命周期管理的后台读取；首帧后启动 DLNA 的初始化也可测 | 实际主线程 trace、焦点/收藏/退出生命周期回归；不能简单在后台保留可被 Glide 回收的 bitmap |
| 3 | 若播放过程卡，优先核对 EXO 硬解/SurfaceView、解码名称/帧率、4K/HEVC/HDR 和 MPV 实际 hwdec-current；必要时提供可选低端播放档 | TV 对照测量；MPV Android9 的 mediacodec-copy 是硬解后拷贝，因旧崩溃保留，不能贸然恢复 surface 模式；不强改用户画质或禁弹幕 |
| 4 | 只有确认内存/CPU瓶颈后再调 Glide 16/8 MiB 与 32/16 MiB、预加载 manager 懒初始化、Compose baseline profile | 同 TV PSS/GC/掉帧与起播；缓存扩大不天然更快，小图取色搬后台需先解决 bitmap 所有权 |
| 5 | 片源取数慢则单独测配置源、插件解析、DNS、首包和坏线路回退；可做同配置上次成功结果快显/失效回退 | 真实插件数据和用户网络，保留 token/UA/headers，取消和缓存归属；不靠缩短所有 timeout 制造“变快” |

公开 FongMi 当前 `app/build.gradle` 为 5.8.8，EXO 实现引入 decoder manager/prewarming/runtime updates、buffer 选项，和本项目锁定配对实现有接口差异；[ExoUtil 一手源码](https://github.com/FongMi/TV/blob/f16d7eb6094ced10b7a5931c2e5a7d4961f7e3be/app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java)。本项目 Media3 默认起播门槛 1000ms、流式 min/max buffer 50s，后者是持续缓存目标，不能说“需要先等 50 秒才播放”。公开源码对照不能确定用户口中的 OK 影视包版本；未获得该 APK 和相同内容实测前，不将 FongMi 最新实现直接等同用户的原版。

本轮不做画面缩小给设置侧栏留位或 UI 框架重写；已有旧电视窗口渲染故障历史，需优先拿真实设备证据再决定。后续开发继续从本文和当前 recipe/测试开始，不依赖聊天记忆。
