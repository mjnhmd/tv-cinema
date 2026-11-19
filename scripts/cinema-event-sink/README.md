# TV Cinema 事件接收端

电视端把埋点事件攒成小批（默认每 10 秒一次，只发增量）POST 到这里，这里只做一件事：按天追加写文件。
没有第三方依赖，单个 Python 文件，约 200 行。

## 为什么是批量而不是一条一请求

电视是 ARMv7 的盒子，真正贵的是「每次请求都新建连接」。当前做法：

- 每 10 秒检查一次，没有新事件就一个请求都不发；
- 一批最多 400 行（约 10–40 KB），只发上次成功之后的增量；
- 全部走一个后台优先级守护线程，复用同一个 OkHttp 连接（keep-alive），不占用播放线程；
- 失败不重试轰炸，下一拍再发；积压超过内存环形缓冲（2000 行）就跳过并记一条 `log gap`。

一集 45 分钟大约 270 个请求、总计几百 KB，对电视基本无感。

## 部署

### 极空间（推荐）

1. 极空间「Docker」→ 项目 → 新建，把本目录（`server.py` + `Dockerfile`）传上去，或直接用镜像构建。
2. 建一个共享目录，例如 `/sata14/my/data/tv-cinema-logs`。
3. compose：

```yaml
services:
  cinema-sink:
    build: .
    image: cinema-event-sink:1
    container_name: cinema-sink
    restart: unless-stopped
    environment:
      CINEMA_SINK_TOKEN: "改成你自己的长随机串"
    ports:
      - "13000:13000"
    volumes:
      - /sata14/my/data/tv-cinema-logs:/data
```

Token 生成：`openssl rand -hex 16`。

### 任意机器直接跑

```bash
CINEMA_SINK_TOKEN=你的token CINEMA_SINK_DIR=./events CINEMA_SINK_PORT=13000 python3 server.py
```

## 接口

全部要求 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/events` | body 为 `text/plain`，一行一个事件；追加写当天的 `YYYYMMDD.log`，返回 `{"ok":true,"lines":N}` |
| GET | `/health` | 运行时长、当天行数、会话数 |
| GET | `/tail?n=200` | 当天最后 n 行（最多 5000） |
| GET | `/files` | 各天文件与大小 |
| GET | `/file?date=20261119` | 某天完整文件 |

配置项（环境变量）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `CINEMA_SINK_TOKEN` | 必填 | 唯一凭据，所有路由都要 `Authorization: Bearer <token>` |
| `CINEMA_SINK_DIR` | `./events` | 落盘目录 |
| `CINEMA_SINK_PORT` / `CINEMA_SINK_HOST` | `13000` / `0.0.0.0` | 监听端口与地址 |
| `CINEMA_SINK_KEEP_DAYS` | `90` | 保留天数（约三个月），新的一天第一次写入时删掉更早的 `YYYYMMDD.log`；`0` 表示全保留 |
| `CINEMA_SINK_MAX_BYTES` | `33554432`（32 MiB） | 单日文件上限，超过后不再追加，只写一条 `[sink] day file would exceed ...` 提示；`0` 表示不限制 |

落盘格式：

```
2026-11-19 01:26:14.324 abc12345 [0.1.14/218183703] [+0.100s] app start version=0.1.14
```

依次是：写入时间、会话 id、`X-Cinema-Version`（缺省用来源 IP）、事件行原文。

## 电视端

构建时把地址和 token 编进去（从环境变量读，不进仓库）：

```bash
CINEMA_LOG_UPLOAD_URL=http://192.168.31.x:13000/events \
CINEMA_LOG_TOKEN=你的token \
bash scripts/cinema-build.sh --mode leanback --abi armv7
```

不传这两个变量时，上传功能完全不启用，`设置 → MPV 日志 → 导出` 仍然可以把文件写到 Download。

## 验证

同一局域网里任意一台机器：

```bash
curl -H "Authorization: Bearer 你的token" http://192.168.31.x:13000/health
printf '[+0.000s] test hello\n' | curl -X POST -H "Authorization: Bearer 你的token" \
  -H "X-Cinema-Session: manual" --data-binary @- http://192.168.31.x:13000/events
curl -H "Authorization: Bearer 你的token" "http://192.168.31.x:13000/tail?n=5"
```

电视上装好包、播一段、退出，看 `/tail` 里是否出现新的会话行；出现 `log upload fail code=401` 说明 token 不对，`code=404` 说明路径不是 `/events`。

## 边界

- 只监听局域网，不要把这个端口映射到公网；数据里有设备型号、IP、片名。
- token 是唯一凭据，泄露了就换一个并重新打包。
- 增长是有界的：按天分文件，`CINEMA_SINK_KEEP_DAYS` 到期自动删旧文件，单日文件到 `CINEMA_SINK_MAX_BYTES` 停止追加。正常使用一天几百 KB 到几 MB，32 MiB 的单日上限约等于 30 倍余量。
- 设备端也有界：内存环形缓冲 2000 行、每个 area 10 秒最多 60 条，写不进缓冲的事件直接丢并计数，不会把电视拖慢。
