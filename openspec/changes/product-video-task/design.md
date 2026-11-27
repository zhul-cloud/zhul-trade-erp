## Context

- 视频现在在本机 llm-wiki 的「工作流 E」里生成：手工写镜头脚本，调用本机的 MoneyPrinterTurbo（`mpt_agent.py`）。参数是 `--video-script` 直接传字幕、`en-US-GuyNeural` 配音、语速 1.1–1.15、`9:16`、`sequential` 拼接，生成 15–20 秒的视频。
- llm-wiki 踩过的坑：
  - 源图比例不对时，MoneyPrinterTurbo 按「fit」缩放，会留黑边，没有参数可以改成铺满。只能在生成前先把图片裁成目标比例。
  - 字幕不能引入未核实的参数。
- MoneyPrinterTurbo 自带 HTTP 接口（`POST /api/v1/videos`）：
  - 支持 `video_source=local` 加 `video_materials` 使用本地素材，以及 `video_script`、`video_aspect`、`voice_name`、`voice_rate`、`subtitle_enabled`；
  - 镜头时长只有统一的 `video_clip_duration`，不能逐镜头设置，视频总长由配音决定；
  - 结果需要轮询任务状态获取。
- 生产服务器：4 核，内存 7.3G（可用约 4G），磁盘剩余 35G，没有显卡。现有容器有 MySQL、Redis、后端、前端和 AI 编排服务。
- 现有可以复用的：
  - AI 编排服务的提交、回调模式（共享令牌、按技能路由结果、超时看门狗 `AiTaskTimeoutWatchdog`）；
  - 附件存储（本地或 OSS，私有文件走签名链接）；
  - 后端已有 ffmpeg 转码队列 `VideoTranscoder`；
  - 素材库按单据附件汇总。

## Goals / Non-Goals

**Goals:**
- 生产服务器上可靠地夜间批量生成，不拖慢白天的 ERP；
- 复用 llm-wiki 已验证的生成参数和裁图规则，视频不留黑边；
- 文件只存一份，素材库和商品档案都只是引用。

**Non-Goals:**
- 改造 MoneyPrinterTurbo 本身（不改上游代码，只做外层封装）；
- 多台生成机、GPU、分布式队列；
- 精确到镜头的时间轴剪辑。

## Decisions

### 1. 视频生成服务 = MoneyPrinterTurbo 加一层薄封装，独立容器

新增 `scripts/video-generator/` 镜像：基础是 MoneyPrinterTurbo 官方代码（固定版本），外层用一个小的 FastAPI 封装（`server.py`）。封装做四件事：

1. 接收后端的生成请求：任务号、版本号、两个画幅、图片下载地址（后端内部接口，带共享令牌取图，只在任务生成中可取）、按镜头的字幕、配音参数、回调地址；
2. 下载图片，用 Pillow 按目标比例从中心裁切铺满（9:16 裁到 1080×1920，16:9 裁到 1920×1080）；
3. 在同一进程里调用 MoneyPrinterTurbo 的生成函数：`video_source=local`、`video_concat_mode=sequential`、`video_clip_duration = 估算总时长 ÷ 镜头数`，字幕逐句拼成 `video_script`；
4. 先生成竖屏再生成横屏，完成后用一次 multipart 回调把两个 mp4、两张封面和时长一起交给后端（实现时合并了「上传」和「回调」两步，少一个接口、不会出现只传了一半的状态）。

- **为什么不直接调用 MoneyPrinterTurbo 的 HTTP 接口**：它要求素材在本地文件系统，生成前要先裁图，结果也要轮询，这些都放进封装里，后端只需要一次提交和一次回调，模式与 AI 编排服务一致。
- **为什么不在后端里用 ffmpeg 自己拼**：要自己实现配音、字幕对齐和转场，等于重写 MoneyPrinterTurbo。用户已经指定沿用它。
- **资源**：容器限制 `cpus: 2`、`mem_limit: 3g`，不映射宿主机端口，只在 Compose 内网可以访问。封装内部同一时间只接一个任务，有任务在跑时再收到请求返回 409。临时文件放容器内的临时目录，任务结束就删除。
- **配音**：用 Edge TTS（`en-US-GuyNeural`，语速 1.1），需要能访问外网；失败按生成失败处理。不需要配置大模型，因为脚本由 ERP 直接传入。

### 2. 队列由后端调度，不放在生成服务里

- 新增定时任务 `VideoQueueJob`：每分钟执行一次，按 `TenantScope.platform` 跨租户读队列。
- 只在夜间时段内、且没有「生成中」的任务时，取 `queued_at` 最早的任务，乐观更新为「生成中」后提交。
- 用一条带条件的 UPDATE 认领任务（`status = 排队中` 且全库没有「生成中」的任务时才改成「生成中」），多个实例同时派发也只有一个成功。项目实际没有引入 Redisson，所以没用分布式锁；暂停标记和「连续不可用次数」放在 Redis 普通键里。
- 回调、超时处理用 `TenantContext.runAs(任务的租户)`。
- **为什么放在后端**：
  - 队列状态、排队位置、时段设置、跨租户先后顺序都在 ERP 数据库里，页面可以直接查询；
  - 生成服务保持无状态，重启也不丢队列。
- **超时**：生成中超过 30 分钟就标为失败（本机实测一次约 13 分钟，生产只给 2 个 CPU，留出余量）（与 AI 看门狗同样的模式）。迟到的回调发现任务已经不是「生成中」时直接丢弃，并记录日志。
- **服务不可用**：提交时连接失败，任务退回「排队中」，`next_try_at` 推后 10 分钟。同一晚连续 3 次失败，写一个「当晚暂停」标记，到下一个时段开始时清除。

### 3. 数据模型

| 表 | 主要字段 |
| --- | --- |
| `product_video_task` | tenant_id、product_id、品牌 / 型号快照、status、script（JSON：标题、镜头[图片附件 id、字幕]）、script_source（AI / 手工）、queued_at、next_try_at、started_at、finished_at、fail_reason、review_by / review_time / reject_reason、current_version、deleted_at |
| `product_video_material` | task_id、attachment_id、sort、used |
| `product_video_output` | task_id、version、aspect（9:16 / 16:9）、video_attachment_id、cover_attachment_id、duration_sec、status（待审核 / 已通过 / 已替换） |

- 另加一张 `product_video_log` 记录加入队列、开始、完成、失败、通过、退回等历史，详情页展示。
- 四张表都是私有表（PRIVATE），要在 `TenantTables` 里归类。
- 上传的图片作为附件挂在视频任务上（业务类型 `VIDEO_TASK`），生成的视频和封面挂在输出上（`VIDEO_OUTPUT`），沿用私有存储和签名链接；文件按「品牌 / 型号」目录存放，与素材库一致，型号统一用 `mpn_raw`。
- 素材库按现有「附件即素材」的方式汇总：
  - 「图片素材」= 挂在视频任务上的图片附件；
  - 「视频素材」= 状态为「已通过」的输出。
- 夜间时段存入系统设置：`video.queue.window = "20:00-07:00"`。

### 4. 脚本生成走 AI 编排服务

- 新增 AI 技能 `video-script`。输入：商品资料（规格、卖点、经过核实的事实）、社媒的 YouTube 视频方案原文、选用图片的顺序和说明（图片名、所属类别）。
- 输出 JSON：标题和镜头列表，每个镜头包含图片序号和一句字幕。
- 技能提示词写死「只能用输入里的事实」，并规定估算时长（约每秒 2.5 个词）落在 15–20 秒。
- 结果回来后由后端校验镜头数和估算时长，不合格就标为「脚本需修改」，由运营手动修改。

### 5. 商品档案引用本公司素材；设为官方时才复制

- 区块直接调用素材库按「品牌 + 型号」查询，只读，不新建表。
- 「设为官方素材」时，把私有文件复制到公开存储，并新建一条 `product_media` 记录。
  - 复制是必须的：共享商品库的文件要通过公开固定地址访问，私有文件不能公开。
  - 新增字段 `source_attachment_id`，用于判断「已是官方素材」。

## Risks / Trade-offs

- [4 核机器生成慢，一晚跑不完] → 实测单条耗时，在页面显示预计完成时间；跑不完的第二晚自动继续。必要时把并发保持为 1，同时把 `cpus` 放宽到 3（夜间 ERP 几乎没人用）。
- [MoneyPrinterTurbo 升级后内部函数签名变化] → 固定上游版本，封装只依赖少量入口；升级时单独验证。
- [Edge TTS 是免费的非官方接口，可能限流或失效] → 失败按生成失败处理，原因写清楚；配音可替换，以后需要时再接付费配音。
- [镜头和字幕不能精确对齐] → 按镜头平均分配时长，与 llm-wiki 现状一致；审核环节把关。
- [视频文件占磁盘或 OSS 空间] → 一个任务约 2 个视频 × 10–20MB。重新生成后，已替换的旧版本保留 30 天后删除文件（记录保留）。
- [跨租户共用一个队列，某个公司一次放几百个] → 第一版按加入时间先后排；真出现抢占再加按公司轮流。

## Migration Plan

1. 先部署 `video-generator` 镜像和服务。这时后端不派发任务（功能开关 `VIDEO_GENERATOR_URL` 为空时，队列任务不执行），不影响生产。
2. 合并后端和前端，执行数据库迁移：三张新表、菜单、权限、系统设置。
3. 在生产 `.env` 补齐 `VIDEO_GENERATOR_URL` 和 `VIDEO_TASK_CALLBACK_TOKEN` 后，生成功能才开始工作。
4. 回滚：清空 `VIDEO_GENERATOR_URL` 即可停止派发；数据库迁移只新增表和字段，回滚时可以保留。

## Open Questions

- 横屏视频是否需要片头片尾的品牌画面（福唯标志）：不影响规格，以后可以在封装里加一个可选参数。
