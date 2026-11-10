# 安装包后续瘦身核查 — 2026-11-10

核查对象是已删除实时字幕的 ARMv7 签名包 `build/outputs/tv-cinema-armv7-no-speech.apk`：83,922,421 字节，SHA256 `98634969fa7bb0488eaced3f29e91f1eab8b69c840088e7dbaea552936a0122e`。本文 MB 均为十进制字节；测量副本位于 `build/diagnostics/apk-size-audit/`，没有修改正式代码、资源、运行依赖或交付 APK。

## 建议先做的两项

| 项目 | 实测依据 | 预计 APK 缩减 | 用户影响与验收 |
| --- | --- | --- | --- |
| 修复原生库符号裁剪 | `libc++_shared.so`、`libffmpegJNI.so`、`libtv-quickjs-budget.so` 仍带 `.debug_*` / `.symtab`；对副本执行 NDK29 `llvm-strip --strip-unneeded`，相同 deflate 参数比较 | 3,432,366 字节，约 3.43 MB | 不删功能。保留独立符号用于诊断；验证实际 JNI 加载、EXO/MPV/QuickJS 后再交付。副本的运行时分配段和动态符号表逐字节不变，但未运行裁剪后的库。 |
| 排除 Python 三方包的 `test` / `tests` 目录 | `requirements-common.imy` 含 514 个测试目录条目。重打对照 ZIP 与排除测试的 ZIP，保留每个剩余条目的原始内容 | 1,976,199 字节，约 1.98 MB | 保留 Python/NumPy/pywasm 及其运行库。只排除测试目录，不顺便删 `numpy.testing`、头文件、元数据或 native helper；需验证 Python import、片源、WASM/央视频路径，未承诺所有插件兼容。 |

两项收益相互独立，粗算 83.92 MB → **78.51 MB**，再减约 5.41 MB（6.44%）。这是基于副本测量的目标，尚未作为正式构建配置实现，也没有生成新的签名交付包。

原生裁剪还有明确构建线索：`build/logs/cinema-speech-removal-armv7-release.log` 的 `stripLeanbackArmeabi_v7aReleaseDebugSymbols` 阶段报告默认 NDK `28.2.13676358` 缺少 `source.properties`，随后输出 `Unable to strip ... packaging them as they are`。本机已安装的 NDK29 可以正常处理这些文件。正式修复应统一选择可用的固定 NDK，并让发布检查拒绝遗漏的调试段，不能只在交付 APK 上临时手工加工。云端是否有同样问题仍须检查云产物。

## 需要接受显示差异的字体选择

| 项目 | 当前 APK 占用 | 副本测量/收益 | 代价 |
| --- | --- | --- | --- |
| MPV 字幕备用字体 Go Noto Current | 7,570,512 字节 | 只保留拉丁/西里尔、中日文字及相关符号范围，相同压缩比较省约 3.03 MB | 从 32,388 个映射字符减到 13,881 个；韩文、阿拉伯文、泰文、印度文字等需要系统字体回退。老电视字库未验收，不建议直接删整个字体。 |
| 三种 MiSans UI 字重 | 合计约 3.55 MB | 保留一种字体文件可少约 2.37 MB | 另外两种字重依靠合成；标题/选中态粗细和排版可能变化。当前文件本来已经是约 7,140 字符的子集，不能再把“做子集”当无损大收益。 |
| 仅删字体 hint 指令 | 同上 | 四个字体合计约 0.50 MB | 保留全部映射字符，但小字号/旧系统的栅格化可能不同；收益不大，不优先。 |

字体两条大收益路径都要重新验证字幕语言、文字宽度、焦点/选中态和实际电视观感，不能称为完全无影响优化。

## 不能按冗余删除的大项

- Python 相关资产和原生运行库合计约 **23.81 MB**。`PyLoader` 按需初始化，不用 Python 片源时不启动解释器；包体大不代表一直运行。`pywasm==1.0.8` 的 wheel 元数据声明依赖 NumPy，现有 `test_original_yangshipin_keygen_pinned_pywasm_and_numpy` 覆盖了依赖它的央视频 keygen 路径。删 NumPy、OpenBLAS 或整个 Python 会改变片源兼容范围；按需下载是完整的运行时交付方案，需要 ABI/版本/签名/失败恢复，不能只移走文件。
- MPV 专用的 `libmpv.so` / `libplayer.so` 约 **6.40 MB**；共享 FFmpeg 库约 **10.83 MB**。FFmpeg 也被 EXO 扩展使用，两者不能简单相加当成“删 MPV 省 17 MB”。实际 APK 没有重复的顶层原生库；`pickFirsts` 已经对共享 FFmpeg/C++ 合并。
- 迅雷、荐片、Forcetech 等特殊协议原生负载分别约 **2.09 / 1.28 / 2.71 MB**；移除会影响使用相应协议的源，不作为默认瘦身动作。
- Java/Kotlin 保留规则仍偏宽，Kotlin、OkHttp、Gson、SMB/密码学库有后续精简空间。但外部片源 JAR、JNI 和反射依赖这些类型，需逐组收窄再打包/测试。本次没有编造一个可直接相加的收益数字。
- 单 ABI、R8、资源压缩、原生 ZIP 压缩均已开启。`libavcodec.so`、`libmpv.so`、`libass.so` 本身已无上述调试段，继续盲目 strip 没有同等收益。普通测试 JS 资产总共只有 819 字节，不值得作为体积优化重点。

## 验证与证据

- 原始交付 APK 最终哈希保持不变；未提交、推送、发布或安装实验包。
- `strip-audit.json`：3 个库裁剪前后和相同 deflate 压缩对照；`strip-runtime-sections.json`：运行段/动态符号一致性。
- `python-tests-audit.json`：514 个 `test` / `tests` 条目、对照 ZIP 和剩余条目原文一致性；`requirements-no-test-directories.imy` 只是实验副本。
- `font-audit.json` / `subtitle-font-subset-audit.json`：字体原始覆盖、hint / 限定字符集实验；没有做电视渲染验收。
- `dex-size-audit.json`：包名分组的类/方法/指令字节，仅用于定位宽泛保留规则，不等于可删除体积。
- 原生裁剪后运行、精简 Python import/片源、字体/电视视觉、重新签名包大小、云端产物均为 `not_tested`。实施优先级：原生裁剪 → Python 测试目录 → 经用户选择后的字体方案。
