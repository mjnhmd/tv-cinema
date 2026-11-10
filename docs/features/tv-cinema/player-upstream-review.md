# MPV / EXO 上游适配核查 — 2026-11-10

## 结论与范围
- 有可借鉴内容，但不是整体替换播放器：优先接通现有 EXO 诊断面板，其次评估 MPV JNI 生命周期加固。
- 本次只读核查源码、提交与生产输入；没有修改播放行为、升级依赖、构建、安装、提交或推送。其他会话的图标、片源与弹幕 WIP 不属于本报告。
- API28 ARM64 模拟器的 MPV Surface 崩溃已有复现；用户 Android9 ARMv7 电视的堆栈、实际解码器和 EXO 卡顿类型仍缺证据。不能把模拟器平台根因等同于电视芯片缺陷。
- 当前目录在 `/Users/mengjingnan/code/tv-cinema`，不在用户四个 ppm 工作根内；只读查询原有 BUG-0038 / REQ-0009，不创建或回写事项。

## 固定比较对象
| 对象 | 分支 / 提交 | 用途 |
| --- | --- | --- |
| FongMi/media | `release-1.11.0-fongmi` / `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d` | 实际定制分支，不只看默认 `release` |
| FongMi/mpv-android | `fongmi` / `c3ceb8af5be34cee57c043ef2b4b845259ded0d1` | 最新 JNI 生命周期与参数参考 |
| 生产 TV | `17abcd7453a0ecbdbc9ed40a22f3fd3632158495` | `distribution/baseline.json` 指向的 paired TV |
| 生产 media | `342f6590e02ac390e0bd6b4488b8a1bff55747c1` | paired media，不是根目录旧 app 实验的 AAR |
| 生产 MPV 原生运行库 | FongMi/mpv-android `99a60ad214` | FFmpeg9 native ZIP；不等于采用其 JNI 桥 |
| 生产 JNI 桥 | Aryan447/mpvlibAndroid `5d658fe77b607db7f6e4141770fe7861c888b1e0` | 保留扩展 API、按 FFmpeg9 重编的 bridge ZIP |

## 1. EXO：大部分优化已经继承
- [跨 fork 比较](https://github.com/FongMi/media/compare/3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d...wobuhui666:342f6590e02ac390e0bd6b4488b8a1bff55747c1)返回 ahead=2、behind=0，唯一文件差异是 `DefaultExtractorsFactory.java` 的 Matroska / MMT-TLV 探测顺序。因此整体换成 FongMi 当前分支不会获得额外 EXO 修复。
- 实际逐文件核对 Git blob：MediaCodecVideoRenderer、VideoFrameReleaseHelper、DebugTextViewHelper、DecodeTrackSelector、SampleQueue、H264Reader、HlsMediaChunk、AudioCapabilities、DefaultLoadControl 均与生产一致。
- [VSync 去重修复](https://github.com/FongMi/media/commit/fd8a6b2c5750729120bee3b9bb52a8603c96da1d)避免显示变化后重复 Choreographer 回调与 CPU 唤醒；已经包含，不是待移植优化。
- [MediaCodecVideoRenderer](https://github.com/FongMi/media/blob/3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java#L3193)已有老系统切换不同帧率时重建 codec、API<=28 部分 MiTV/MiBox 的 setOutputSurface 绕行。后者按 `Build.DEVICE` 匹配，不能只凭 MiTV-MFTP0 的显示名称断言命中。
- [音频短于视频时 seek 挂起修复](https://github.com/FongMi/media/commit/7c725b22f0b102e1447dd03dec557cc845db5049)为 seek 目标之前结束的音轨补 EOS；[H.264 修复](https://github.com/FongMi/media/commit/aac6ec964681dd0476a33e3ad220ca7b5bf771f6)改善无 AUD / recovery-point 等边界；二者已继承，不能解释所有持续掉帧。
- [HLS 容错](https://github.com/FongMi/media/commit/f0eb7b514d5fcaba843dfe93d92acfff19a14e9e)容忍错误 codec 声明，且会把分片 IOException 当 gap 跳过。这不是吞吐提速，可能跳内容；不能把“没有报错”当完整流畅播放。
- TV 的 ExoUtil 还已启用 decoder fallback 和解码模式筛选。软解回退提高能播概率，但 ARMv7 CPU 不一定能实时解高码率 / 高位深视频。

## 2. EXO：最值得补的是现有调试面板接线
- 上游 [DebugTextViewHelper](https://github.com/FongMi/media/blob/3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/util/DebugTextViewHelper.java#L765)已有缓冲秒数、解码器名称 / 硬解标识、输入与解码后丢帧、处理偏移、帧率、码率和显示刷新率；生产文件完全相同。
- 生产 `PlayerEngineDialog.java:71` 的“调试”按钮已调用 `PlaybackActivity.toggleDebugView()`；但 `PlaybackActivity.java:198` 至 `hideDebugView()` 只读写布尔值，未调用真正的 `PlayerView.toggleDebugView()`。全 app 调用检索及固定 TV 提交均确认该缺口；本轮没有改代码。
- 应复用已有入口与面板，不新增常驻刷新界面。仅 EXO 先接通；生产 MPV 是自有 MpvPlayer，不是上游 Media3 MpvPlayer，不能假设反射 `toggleGeneralStats()` 会成功。
- 分类顺序：缓冲耗尽且进入 BUFFERING，优先查网络 / 源 / 分片；缓冲充足而丢帧上升，优先查解码能力 / 后处理 / 渲染；缓冲和丢帧正常但周期顿挫，再查视频与显示节奏及音频时钟。分类只是定位方向，不是单指标根因判定。
- 需用 Android9 的 R8 Release 验证真实显示、关闭、切内核、Back 和页面退出后的更新停止；虽然库已有反射 keep 规则，不能仅凭源码认定运行正常。取证勿记录带令牌的媒体 URL / headers。

## 3. MPV：新 JNI 加固有价值，但生产尚未包含
- [2026-11-09 的 8da68c3a](https://github.com/FongMi/mpv-android/commit/8da68c3a283e5aea2b5e68b8c5ff8c7577567681)增加 native handle 并发保护、异步请求队列、显式错误码、Surface 引用保留与 shutdown 协调。
- [render.cpp](https://github.com/FongMi/mpv-android/blob/c3ceb8af5be34cee57c043ef2b4b845259ded0d1/app/src/main/jni/render.cpp#L35)让 replace / detach 等待；[request.cpp](https://github.com/FongMi/mpv-android/blob/c3ceb8af5be34cee57c043ef2b4b845259ded0d1/app/src/main/jni/request.cpp#L241)等待对应 SET_PROPERTY_REPLY / 销毁完成后才释放旧 Surface 引用。目标是减少快速切台、返回、切内核时 Surface 先销毁而 native 仍使用它的竞态。
- 生产 `mpv/build.gradle:37` 明确排除 native ZIP 的 `libplayer.so`，再放入重建的扩展 bridge。当前 bridge 的 render.cpp 直接设 wid 后 DeleteGlobalRef；main.cpp 通过 pthread_join 和 mpv_terminate_destroy 同步销毁。所以“原生 ZIP 来自 FongMi”不代表这套新 JNI 语义已经进入产线。
- 生产 Java 已有 nativeGeneration / DESTROYING / CacheLease 保护及后台销毁，并非完全没有生命周期治理；新方案的价值是 native 使用者与 Surface 完成确认，不能全量替换现有状态机。
- 适配必须保留原有 23 个 JNI API、节点读写 / 命令、快缩略图与缓存能力，并验证 FFmpeg9 的版本化符号、ARMv7 与 ARM64。不能只替换一份 AAR 或 so。
- 特别注意：上游 `destroy()` 改为入队后返回。我们 `MpvPlayer.java:612` 当前把 destroy 返回视作 native 已完成，随后置 IDLE 并释放缓存租约。直接替换会使门禁过早开放；必须改成真实 shutdown 完成通知 / 同步兼容层后再交还所有权。
- 建议按独立增量验证 create/destroy、重复 detach、切台 / 切内核、返回重进、后台 Surface 重建及错误回滚；等待放错线程或出现锁循环可能造成 ANR。宿主 fake-mpv 单测不能代替 Android MediaCodec 真机验证。

## 4. 这不是已证实的 Android9 原生崩溃修复
- 已复现堆栈是 API28 ARM64 的 CodecLooper / CCodecBufferChannel::setSurface / CCodec::configure SIGSEGV。上述引用寿命修复针对另一类竞态，所查源码与提交未证明能修这个 configure 崩溃。
- FongMi 的默认 hwdec 仍为 `mediacodec,mediacodec-copy`，没有本项目 `MpvHwDecPolicy` 的 SDK<=28 门禁。硬解返回错误可以回退；进程因 SIGSEGV 退出不会继续尝试下一项，Java try/catch 也不能兜住。
- 暂保留当前 copy-mode 缓解。copy 可能增加帧复制成本，必要时还可能回退软解；先真机确认稳定性和解码性能，再决定是否按实际 codec / 设备收窄门禁，不能直接恢复 surface-first。

## 5. 参数只做受控比较，不盲抄
- [MPVView 默认参数](https://github.com/FongMi/mpv-android/blob/c3ceb8af5be34cee57c043ef2b4b845259ded0d1/app/src/main/java/is/xyz/mpv/MPVView.kt#L19)中的 profile=fast、GLES、audiotrack/opensles、API28 的前向 / 后向 64MiB demux 上限，生产 MpvOptions 已有。上限不代表起播立即分配两份满额缓存。
- `display-fps-override` 是当前差异：上游显式使用 Android 上报刷新率。仅在实际观测到显示节奏问题时 A/B；电视系统可能误报或切换显示模式，固定值不能默认承诺改善。
- 不建议为“顺滑”统一开启插帧、gpu-next、Vulkan、Anime4K，或一味增大缓存。它们可能增加 GPU / CPU / 内存负担，反而损害 Android9 ARMv7 电视。

## 建议优先级与验收边界
1. 接通现有 EXO 调试按钮的真实面板，取得同一片源 / 片段的缓冲、实际 decoder、丢帧、帧率与刷新率证据；收益是停止盲调参数，而不是立即保证不卡。
2. 单独评估 / 移植 MPV native handle 与 Surface 完成确认；先解决 JNI / destroy / 缓存所有权契约，再做双 ABI 与反复切换验证。保留 API28 copy-mode 保护。
3. 有真机证据后再试 tunnel、解码器、显示节奏或缓存的小范围 A/B；保持默认 EXO、Surface、预载节制，不把升级内核当优化结论。
- 本轮通过：公开仓库分支 / 提交核对、跨 fork ancestry 比较、13 个文件 Git blob 一致性、实际生产 JNI 输入与调试调用链检查。
- 本轮未测：新 JNI 编译、面板实际显示、Android9 ARMv7 真机、长时播放 / 内存 / 功耗 / 帧节奏。既有 0.1.7 模拟器通过不升级为电视验收。
- 下一步需要开发授权后按以上顺序实施；当前只留下调研文档。共享 context.md 正有其他独立任务写入，本轮不覆盖其内容。

## 实施状态
- 2026-11-10：第 1 项已实现并编译通过；`PlaybackActivity` 不再只翻转标记，MPV 下提示不支持。无连接设备，面板运行与 R8 Release 行为仍为 not_tested。第 2 项 MPV JNI 尚未改动。
