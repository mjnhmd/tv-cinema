# TV Cinema ARMv7 CI

## Status

`fongmi` push 只构建并保留 7 天 Actions artifact，不创建 Release、不生成可用的更新 URL。
公开仓库的 artifact 仍要求下载者登录 GitHub；它不是公开 APK 直链。
`workflow_dispatch` 可以手动指定版本号；只有明确把 `publish` 设为 `true` 才会创建
`com.mjnhmd.tvcinema` 的 GitHub Release，并上传 APK 与 OTA manifest。

2026-11-09 用户已明确授权本次提交推送、配置签名和发布。五个签名 secrets 已配置；
首次 workflow push、云端运行和公开 Release 正在交付，完成结果以 `verification.md` 为准。

## Required GitHub secrets

在仓库 `Settings -> Secrets and variables -> Actions` 配置以下 **repository secrets**。
CI 只读取已有发布密钥，绝不生成临时 keystore，也不会退回 debug 签名。

| Secret | 内容 |
| --- | --- |
| `CINEMA_KEYSTORE_BASE64` | 本地现有 release keystore 的 base64 内容 |
| `CINEMA_KEY_ALIAS` | 现有 keystore 的 alias |
| `CINEMA_STORE_PASSWORD` | keystore password |
| `CINEMA_KEY_PASSWORD` | key password；可与 store password 相同 |
| `CINEMA_CERT_SHA256` | 本地已签名 APK 的 signer certificate SHA-256，64 位十六进制 |

`GITHUB_TOKEN` 由 Actions 自动提供；只有 `publish` job 获得 `contents: write`。
不要把这些值写进 workflow、文档、artifact 或日志。

本地 keystore 转 base64（示例，输出不要粘贴到聊天或日志）：

```sh
base64 < /absolute/path/to/release.jks | tr -d '\n'
```

本地证书指纹（任选一种，最终必须是 64 位 hex）：

```sh
apksigner verify --print-certs build/outputs/tv-cinema-armv7.apk \
  | sed -n 's/.*SHA-256 digest: //p' \
  | tr -d ':' \
  | tr 'A-F' 'a-f'
```

`CINEMA_CERT_SHA256` 是公开的签名身份，不是密码，但它能让 CI 证明产物与本地发布 APK
使用同一签名；缺少任一项时 `scripts/cinema-ci.py signing` 会在 build 前明确失败。

## Triggers and manual versioning

- push `fongmi`：自动构建 ARMv7 signed Release APK，上传 7 天 Actions artifact；
  下载需要 GitHub 登录，不是公开 APK 直链；不发布。
- `docs/**` 和 `*.md` 的 push 不触发，避免文档改动消耗构建时间。
- `workflow_dispatch`：
  - `version_code` / `version_name`：要么都填，要么都不填；都填时必须满足
    `10001 <= version_code <= 2100000000`。不填时使用 `0.1.5+YYYYMMDD.HHMMSS` 和时间戳
    versionCode，保证默认递增。
  - `publish`：默认 `false`；只有 `true` 才创建 Release。
  - `release_notes`：留空使用当前提交 subject。

Manual 版本必须大于已安装包的 versionCode；CI 无法替代设备上的安装状态判断。

## Build recipe

workflow 复用生产 recipe，不另建一套 Gradle 逻辑：

1. `actions/setup-java` JetBrains Runtime JDK 21（Linux；匹配 pinned
   `gradle-daemon-jvm.properties` 的 `toolchainVendor=JETBRAINS`）
2. `actions/setup-python` Python 3.10
3. `android-actions/setup-android`：
   `platforms;android-37.0`、`build-tools;37.0.0`、`ndk;29.0.14206865`、`cmake;3.22.1`
4. `scripts/cinema-ci.py signing`：把已有 keystore 写进 `RUNNER_TEMP` 下的临时文件，
   生成 0600 的 root `local.properties`；不打印任何密码
5. `scripts/cinema-prepare.py` + `scripts/cinema-verify-inputs.py`：
   校验 native archive SHA-256、固定提交、Cinema patch、overlay 复制和签名输入
6. 校验 Media3 stub symlink：
   `build/reference/.github -> wobuhui-tv/.github`
7. `bash scripts/cinema-build.sh --abi armeabi-v7a`：
   `com.mjnhmd.tvcinema`、debuggable=false、minSdk<=28、只含 `armeabi-v7a`，
   并用 `apksigner` 验证签名
8. `scripts/cinema-ci.py stage`：再次校验 APK ZIP、binary manifest、dex、
   package、version、单一 ABI、每个 `.so` 的 ELF32 little-endian `e_machine=40`
   （EM_ARM）、signer fingerprint；复制成
   `leanback-armeabi_v7a-coexist.apk`

py3.10 是 pinned Chaquopy baseline 的要求；JDK 21 是 Gradle/Java 编译要求；
NDK/CMake 是 pinned quickjs 原生模块要求。Linux runner 上不使用 macOS 的 Android
Studio JBR 路径。

## OTA manifest

只有 `publish=true` 才生成并上传 `leanback-coexist.json`。它必须满足本仓库与
`com.mjnhmd.tvcinema` 的读取契约：

上游 `tools/ota-release.py` 的版本公式与 ZIP/ABI 校验被这里复用；不能直接调用它的
manifest 命令，因为它固定要求 `wobuhui666/TV` 和 ARM64+ARMv7 两个 asset。当前 CI
只发布一个 Cinema ARMv7 asset，因此由 `scripts/cinema-ci.py` 生成同一读取契约下的
单 ABI manifest。

```json
{
  "schema": 1,
  "code": 10003,
  "name": "0.1.5",
  "desc": "release notes",
  "packageName": "com.mjnhmd.tvcinema",
  "mode": "leanback",
  "assets": {
    "armeabi_v7a": {
      "url": "https://github.com/mjnhmd/tv-cinema/releases/download/<tag>/leanback-armeabi_v7a-coexist.apk",
      "size": 123,
      "sha256": "64-hex"
    }
  }
}
```

- `UpdateSources` 对 `com.mjnhmd.tvcinema` 读取
  `https://github.com/mjnhmd/tv-cinema/releases/latest/download/leanback-coexist.json`。
- `UpdateManifest` 只需要被请求的 ABI；这里只提供 `armeabi_v7a`，不伪造 ARM64 资源。
- asset URL 必须绑定不可变 tag，不能写 `latest`。CI 使用 `build-<run_id>[-<attempt>]`；
  本地验证包可使用与 APK versionName 一致的 `v<version>`（本次 `v0.1.5`）。
- push/未发布 run 不生成 manifest，因此不会输出看似可用但实际不存在的下载 URL。

## Publishing and authorization

当前交付已获本次明确授权，五个 secrets 已配置。发布顺序：

1. 小帅确认复用本地 release keystore，并在 GitHub 配置上表五个 secrets。
2. 主代理提交并 push workflow 文件；这一步仍需当次明确授权。
3. 手动运行 `workflow_dispatch`，`publish=false`，先检查 7 天 Actions artifact 的包名、
   versionCode、minSdk、ABI、signature 和实际安装。
4. 当次明确授权 `publish=true` 后，workflow 才执行 `gh release create` 并输出真实
   Release URL；下载可以用于电视验收，不能把发布成功描述为 Android9 ARMv7 播放或性能已通过。
5. Release 后应用读取 `leanback-coexist.json` 和
   `leanback-armeabi_v7a-coexist.apk`；在此之前不得把 artifact 描述为公开下载或已发布版本。
   artifact 即使来自 public repository 也需要 GitHub 登录，且 7 天后过期。

## Verification boundary

CI 子任务未运行 GitHub runner、未设置 secret、未 push 或发布。已执行：

- `python3.10 -m py_compile scripts/cinema-ci.py scripts/tests/test_cinema_ci.py`
- `python3.10 -m unittest discover -s scripts/tests -p 'test_cinema_ci.py' -v`
- Ruby YAML 解析 workflow

初次发布增加了同版号版本 tag 和完整 draft 发布门控，CI helper 的 20 项测试通过；
已用本地实际 `0.1.3` / `10003` ARMv7 APK 执行
`verify-apk` / `stage` 校验包名、API24、单一 ARMv7/ELF32 载荷和既有签名。SDK37 的
`V2 Signer:` 输出与经典 `Signer #1` 均已覆盖，额外不同证书会拒绝。原生 assets 和
Python `.imy` 内的原生模块也校验 ELF32/EM_ARM，实际包共 169 项且无其他 ABI。没有生成指向未发布
Release 的 OTA manifest；这些本地结果不等于云端 build 成功。

真实 Ubuntu runner 的 SDK/NDK 下载、Media3 composite、ARMv7 release 构建和 Android 9
真机安装仍需在启用 workflow 后验证。注意当前仓库 `.gitignore` 的 `/docs/` 与
`test_*.py` 会忽略本文件及 `scripts/tests/test_cinema_ci.py`；提交时需要显式
force-add 这两个文件，或由主代理调整忽略规则。
