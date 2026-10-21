## Context

现有文件存储的实际情况（动机见 proposal.md）：

| 存储代码 | 用户 | 位置 | 读取方式 |
| --- | --- | --- | --- |
| `PrivateFileStorage`（通用，按模块名分目录） | 客户询盘、商机、单据模板、询价导入、收款水单 | `private-uploads/{模块}/{租户}/{年月}/` | `resolveOwned` 返回本地路径 |
| `LocalAttachmentStore`（实现 `AttachmentStore` 接口） | 业务附件 `biz_attachment`：发货、入库、实拍、面单、财务凭证、询价回价 | `private-uploads/attachment/…` | `open` 返回资源 |
| `PurchaseAttachmentStorage`、`SupplierAttachmentStorage` | 采购合同、供应商资质 | 包装 `PrivateFileStorage` | 本地路径 |
| `ProductMediaStorageServiceImpl` | 商品图片与视频 | 公开目录 `uploads/product/{年月}/`，地址写进 `product_media.file_url` | 静态资源映射 |
| `ConfigServiceImpl` | 站点外观图片 | 公开目录 `uploads/images/` | 静态资源映射 |

- 按本地路径读取的代码共 18 处。其中两处必须拿到真实文件：客户询盘的 AI 解析，以及单据模板转换（调用 LibreOffice 进程）。
- 前端约 21 处用「带登录凭证、以 blob 方式下载、再生成本地链接」来预览和下载；视频也是整段下载后才播放。
- `biz_attachment` 已有 `storage` 和 `url` 字段，`AttachmentStore` 接口当初就是为接入 OSS 预留的；其他附件表没有 `storage` 字段。
- 开发库本地文件约 48MB。生产库的量未知，迁移需要能在大量文件下中断续跑。

## Goals / Non-Goals

**Goals:**
- 一个存储服务，业务代码只面对「文件键 + 存储方式」，不再接触本地路径。
- 本地方式与 OSS 方式的外部行为一致（都用临时签名链接），开发环境不需要 OSS 也能完整运行和测试。
- 迁移可预演、可续跑、可核对，删除本地文件是单独的一步。

**Non-Goals:**
- 不合并各附件表：客户询盘附件等仍在各自表中，只增加存储方式字段。
- 不引入 CDN 防盗链、图片按需处理（OSS 图片处理参数）、前端直传 OSS。
- 不做素材库与型号归类（后续变更）；本次只把实拍任务素材按「品牌 / 型号」放目录。

## Decisions

### 1. 统一的 `FileStorage` 服务，两个实现

- 接口：`put(StorageTarget, 文件流, 元信息) → StoredFile`、`openStream`、`signedUrl(key, 原文件名, 有效期)`、`publicUrl`、`exists/size`、`delete`。
- `StorageTarget` 描述可见性（私有或公开）和目录（环节、租户、年月，或品牌和型号），目录规则集中在一个类里，与规格「存储目录」表一一对应。
- 两个实现：`LocalFileStorage`（沿用现有私有目录与公开目录）和 `OssFileStorage`（阿里云 OSS Java SDK）。
- 现有 8 套存储代码改为调用这个服务。各模块原有的校验（扩展名白名单、文件头、大小上限）保留在一个公共校验器里，不在各处重复实现。
- `AttachmentStore` 接口并入 `FileStorage`，`biz_attachment.storage` 继续沿用。
- **备选**：只给 `AttachmentStore` 增加 OSS 实现，其他模块不动。被否决：其余 7 套仍在本地，达不到「统一迁移、本地目录可以清空」。

### 2. 访问统一走「鉴权后签发临时链接」

- 各模块的下载接口改为返回 `{url, fileName, expiresAt}`，权限校验逻辑不变。
- OSS 方式：签名链接有效期 10 分钟，带 `response-content-disposition` 参数，保证下载时使用原文件名。
- 本地方式：后端生成 `/api/v1/files/local/{key}?exp=…&sig=…` 形式的链接，签名用 HMAC（密钥由 `JWT_SECRET` 派生），由后端校验签名和过期时间后返回文件，并支持 Range 请求。这个地址不需要登录，安全性来自签名和有效期，与 OSS 一致。
- 前端：预览和下载统一用一个 `useFileUrl` 工具，先取链接，再交给 `<img>`、`<video>` 或打开新窗口下载；不再用 blob 方式。
- 公开文件（商品图片、站点外观）直接存公开地址，不需要签名。
- **备选 A**：后端接口 302 跳转到签名链接，前端尽量不改。被否决：前端 blob 下载在跨域跳转时需要配置存储桶跨域规则，而视频仍是整段下载到内存，问题没有解决。
- **备选 B**：后端代理 OSS 流。被否决：大文件和视频全部经过后端带宽，和「统一使用 OSS 链接」的要求相反。

### 3. 图片压缩在服务端同步完成

- 用 Java ImageIO，加 TwelveMonkeys 插件以读取 WEBP、处理方向信息。
- 处理顺序：读取方向信息并转正 → 缩放到长边不超过 2048 像素（高质量插值）→ 重新编码。
- 编码规则：JPG、WEBP 输出为质量 0.85 的 JPG；PNG 保持 PNG，用默认的最高压缩级别（无损）。重新编码本身就会丢弃全部元数据。
- 压缩结果不小于原文件时，原文件只去掉元数据后保存。
- **备选**：保存后由前端或 OSS 图片处理按需缩放。被否决：用户要求的是节省存储空间，按需处理只节省传输流量，原图仍完整占着存储。
- **备选**：PNG 截图也转成 JPG。被否决：水单截图上的数字容易糊，凭证的可读性优先。

### 4. 视频转码用 ffmpeg 后台队列

- 上传时先按当前存储方式保存原片，`processing_status = 1（转码中）`，提交给专用线程池 `media-transcode-pool`（核心与最大线程数均为 1，队列 100，可以配置）。
- 后台任务：下载到本地临时目录 → `ffmpeg -c:v libx264 -crf 28 -preset veryfast -vf scale='min(1920,iw)':-2 -c:a aac -b:a 128k -movflags +faststart` → 上传为新的文件键 → 在一个事务里更新记录的键、大小、类型和状态 → 删除原片。
- 转码失败时状态为 3（失败），保留原片，记录 WARN 日志。
- 启动时把仍处于「转码中」的记录重新入队（应对服务器重启时没转完的情况）。
- 只有业务附件有视频；商品视频同样在商品图片与视频记录上处理，`product_media` 增加处理状态字段。
- 后端镜像加装 `ffmpeg`；本地开发没装 ffmpeg 时，跳过转码并把状态记为「失败」，不影响上传。
- **备选**：阿里云媒体处理服务（MPS）。被否决：按分钟计费，还要额外开通、回调，现阶段视频量不大，自己转码足够。

### 5. 各附件表增加 `storage` 字段

- 新迁移给以下表增加 `storage varchar(16) NOT NULL DEFAULT 'LOCAL'`：
  - `customer_inquiry_attachment`
  - `opportunity_attachment`
  - `supplier_attachment`
  - `purchase_order_attachment`
  - `document_template_version`
  - `sourcing_import`
- 收款水单记在 `payment_receipt.file_keys`（JSON 数组），不加列，而是在数组的每一项里增加 `storage` 属性，没有时按 `LOCAL` 处理。迁移过程中，同一张收款单的水单可能一部分已迁移、一部分还没迁移，所以要逐项记录。
- `file_key` 改为新目录规则下的完整键，读取时按 `storage` 选择实现。
- `inquiry_order_supplier.quote_file_url` 没有代码引用、也没有数据，属于早期遗留字段，不处理。
- `product_media.file_url` 与站点外观配置里保存的地址，迁移时直接改写为 OSS 公开地址；新上传的文件直接保存公开地址。

### 6. 配置

- 环境变量：
  - `STORAGE_TYPE`：`local` 或 `oss`，默认 `local`；
  - `OSS_ENDPOINT`、`OSS_REGION`；
  - `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`；
  - `OSS_PRIVATE_BUCKET`、`OSS_PUBLIC_BUCKET`；
  - `OSS_PUBLIC_DOMAIN`：可选，配置自定义域名或 CDN；
  - `OSS_KEY_PREFIX`：可选，多个环境共用存储桶时用来区分。
- 选择 `oss` 时，启动阶段校验必填项，缺哪一项就报哪一项。
- **备选**：放进系统设置页面，在界面里配置。被否决：访问密钥放进数据库，会扩大泄露面；切换存储方式本来就是部署层面的事，不应该由业务管理员在页面上随手改。

### 7. 迁移与清理是两个独立的平台命令

- 由启动参数触发的 `ApplicationRunner`（`StorageMigrationRunner`）执行：
  - `zhul.storage.migrate=dry-run|run`
  - `zhul.storage.cleanup=dry-run|run`
- 执行完输出报告并退出，不在正常运行的服务里触发，避免误点。
- 按表逐批处理（每批 200 条）：读本地文件 → 压缩或原样 → 按新规则上传 → 校验 OSS 上的大小 → 更新这一条记录。单条失败记入报告并继续。
- 已经是 OSS 的记录跳过，所以可以重复执行。
- 清理步骤遍历本地目录，对每个文件找到对应记录，确认 OSS 上存在且大小一致后才删除；没有记录引用的文件只列入报告。

### 8. 实施中补充的决定

- **AI 编排服务改用临时链接取附件**：原来后端把本地路径传给编排服务，编排服务直接读共享磁盘；迁到 OSS 后这条路不通。现在后端给图片和 PDF 签发 60 分钟的链接（解析任务可能排队），编排服务下载后再发给模型。本地存储方式下，链接拼上 `AI_TASK_CALLBACK_BASE_URL`。编排服务仍兼容旧的本地路径参数。
- **实拍素材上传时带上任务 ID**：附件是先上传、后挂到单据的，上传时不知道型号。最初设想挂到任务时再移动文件，但这会和后台视频转码争抢同一个文件。最终改为上传接口接收选填的 `ownerId`，由仓库模块提供的 `ModelFolderResolver` 查出品牌和型号，直接放进型号目录；没传任务时放 `media/unsorted/{年月}/`。
- **迁移记录表 `storage_migration_log`**：迁移会把业务记录改成新文件键，之后就无法从记录反查本地旧文件。所以每迁移一个文件，都记下旧键、新键和新文件大小，清理时按这张表逐个核对后再删除。
- **新文件键沿用旧键的年月**：历史文件不会全部挤进迁移当月的目录。
- **商品图片与视频增加 `storage`、`file_key` 字段**：只靠公开地址无法可靠地定位存储里的文件，迁移和删除都需要文件键。
- **上传时实际字节数多于声明大小的，直接拒绝**：沿用商品媒体原有的防护。

## Risks / Trade-offs

- [压缩是有损的，历史凭证在迁移时也会被压缩] → 只缩放到 2048 像素、JPG 质量 85，肉眼基本看不出差别；PNG 截图走无损。用户已确认凭证类也压缩。迁移期间本地原件保留到清理那一步，发现问题可以回退重迁。
- [签名链接被转发，10 分钟内别人也能打开] → 有效期短，链接与具体文件绑定；这和 OSS 的标准用法一致，可以接受。
- [视频转码占用 CPU，影响接口响应] → 线程池限定 1 个并发，ffmpeg 使用 `veryfast` 预设，并降低进程优先级（`nice`）。
- [存储桶跨域规则没配置，浏览器 `<video>` 播放失败] → 部署文档给出跨域规则示例；用 `<img>`、`<video>` 直接加载签名链接本身不需要跨域，只有前端用脚本读取文件内容时才需要。
- [迁移时 OSS 网络不稳定] → 单条失败不影响其他文件，可以重复执行；报告列出失败清单。
- [前端 21 处同时改动，容易漏改] → 每个模块的预览和下载都改用统一工具，任务按模块逐一列出；浏览器逐页检查。

## Migration Plan

1. 发布包含新存储服务的版本，`STORAGE_TYPE` 仍为 `local`。行为不变，只是访问改为签名链接，可以先在生产环境验证前端改动。
2. 创建私有和公开两个存储桶，以及一个 RAM 子账号（只授予这两个桶的读写权限），配置跨域规则。
3. 设置 `STORAGE_TYPE=oss` 并重启：新上传的文件写入 OSS。
4. 用 `migrate=dry-run` 预演并核对报告，再执行 `migrate=run`；如有失败，重复执行。
5. 抽查各模块的文件能否正常打开后，执行 `cleanup=dry-run`，再执行 `cleanup=run`。
6. 回退：第 5 步之前，把 `STORAGE_TYPE` 改回 `local` 即可，新上传的文件写回本地，已迁移的记录仍按 `OSS` 读取。第 5 步之后不再回退。
