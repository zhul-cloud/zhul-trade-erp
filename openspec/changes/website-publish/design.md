## Context

- **fouwell.com 是纯静态站**：
  - 仓库 `zhul-cloud/fouwell-website` 嵌在 llm-wiki 目录下。
  - 数据集中在 `js/data.js`（`PRODUCTS`，148 个商品）、`js/photos.js`、`js/videos.js`、`js/i18n-ru.js`。
  - 页面由 `scripts/build-product-pages*.js`、`build-hub-pages*.js` 生成，英文和俄文各一套，俄文脚本依赖英文的产物。
  - 部署用 `scripts/deploy.sh`：改缓存版本号后，用 rsync 推到 SiteGround，端口 18765，密钥从 macOS 钥匙串加载，再跑 IndexNow。
  - GitHub Actions 自动部署试过，目前已暂停。
- **data.js 的字段**：
  - 全部商品都有：brand、model、series、cat、spec、status、photo、linkedin；
  - 部分商品有：售价三个字段（120 个）、深度页字段 specs、applications、faq、compatibility（约 88–104 个）、overview 和 meta_description 的英文、俄文（42 个）；
  - 只有少数商品有：authenticity_note、order_terms_note、condition_note、reviews_verified、origin、video_heading 等。
- **llm-wiki 工作流 G 的教训**（见 `publish-to-website` 技能）：
  - 素材路径必须以 `/` 开头；
  - 型号里带 `/` 时需要别名映射；
  - 部署前必须用真实浏览器、禁用缓存验证主图 naturalWidth、缩略图数量、视频区块；
  - 品类映射要强制校验；
  - 非正品品牌要登记；
  - SiteGround 的动态缓存只能在后台手工清理。
- **ERP 现状**：
  - 独立站英文、俄文营销内容已在营销任务里写入（SEO Title、Meta Description、H1、URL Slug、首屏定义块、页面正文、FAQ、图片说明文字）；
  - 规格、应用场景、FAQ、型号关系在共享商品库；
  - 询价回价 `sourcing_quote.unit_price_cny`；
  - 发品任务按「商品 × 平台」生成；
  - 凭据加密用 `AesUtils`；
  - AI 编排服务有提交、回调和共享令牌的模式可以照搬。

## Goals / Non-Goals

**Goals:**
- ERP 不关心站点内部怎么生成页面，只通过「站点数据约定」交换数据，以后的新站点可以是完全不同的静态站；
- 发布过程可以追溯：每一步有记录，失败时线上不变（部署前失败），或者明确告知线上可能只更新了一部分（部署失败）；
- 接管 fouwell.com 时不能改坏现有页面。

**Non-Goals:**
- 站点模板和主题编辑（站点仓库自己维护）；
- 动态站、无头 CMS；
- 多个发布服务实例并行；
- 下架、网址跳转、自动清缓存（SiteGround 没有可用的接口）。

## Decisions

### 1. 发布服务独立成容器，由 ERP 调度（不用 GitHub Actions）

新增 `scripts/site-publisher/`：
- 基础镜像带 Node 20、git、rsync、openssh-client、Chromium（Playwright），外层是一个 FastAPI 或 Node 的小服务；
- 限制 `cpus: 1.5`、`mem_limit: 1.5g`，不映射端口；
- 工作目录用命名卷 `site-publisher-work`，按站点各保留一份仓库克隆，下次增量拉取。

**为什么不用每个仓库自己的 GitHub Actions**：
- 站群有多个仓库，每个都要配部署密钥、工作流，维护分散；
- fouwell.com 的自动部署已经暂停，原因还没查；
- ERP 需要每一步的结果，以及上线链接的回调。

由 ERP 统一调度，凭据集中在 ERP 里加密管理，发布单可以追溯。

**凭据传递**：后端在内存里解密，放进提交给发布服务的请求体里。只在 Compose 内网传输，用共享令牌鉴权。发布服务不落盘保存凭据：私钥写入临时文件（权限 600），任务结束就删除。所有日志输出前，都把已知的凭据值替换成 `***`。后端也不记录这次请求的请求体。

### 2. 站点数据约定（ERP ↔ 站点仓库）

仓库根目录约定 `erp/` 目录：

| 文件 | 作用 |
| --- | --- |
| `erp/site-data.json` | ERP 每次发布全量写入，内容为 `{schemaVersion: 1, site, generatedAt, products: [...]}` |
| `erp/build.sh` | 站点提供：读取 `site-data.json`，生成站点数据文件，运行全部构建，并改缓存版本号 |
| `erp/verify.js` | 站点提供：输入链接列表和期望值，用 Playwright 禁用缓存检查，输出 JSON |
| `erp/export-existing.js` | 站点提供：把现有商品按 `site-data.json` 的商品格式输出，供导入使用 |

图片和视频不进站点仓库（见第 8 条）：`site-data.json` 里 ERP 管理的素材是 OSS 公开存储桶的完整链接（`https://…`），导入时沿用的旧素材仍是仓库里以 `/` 开头的路径，适配脚本两种都要支持。

`site-data.json` 是全量的：包含站点上所有由 ERP 管理的商品，加上「只在站点上有」的商品（按原样）。这样站点的构建是幂等的，不依赖上一次的状态。

**fouwell-website 的适配**（单独提 PR 到站点仓库）：
- `build.sh` 把 ERP 数据转换成 `PRODUCTS`、`PRODUCT_PHOTOS`、`PRODUCT_VIDEOS`、`PRODUCT_SPEC_RU` / `PRODUCT_DEEP_RU` 等对象，替换掉原来由 llm-wiki 脚本生成的部分，再按顺序运行 `build-product-pages.js`、`build-hub-pages.js`、`build-product-pages-ru.js`、`build-hub-pages-ru.js` 和缓存版本号替换；
- 型号带 `/` 的别名问题，在适配脚本里统一用 `model` 作为键，不再依赖目录名；
- 原来的 llm-wiki 发布脚本保留，但在接管后停止使用；
- 仓库的 `.gitignore` 已忽略 `assets/products/`、`assets/videos/`、`assets/linkedin/` 下的新文件（2026-11-27），已在仓库里的旧素材继续保留。

### 3. 步骤顺序：先提交后部署，最后推送仓库

拉取 → 写入 → 构建 → 本地检查 → 本地提交 → 部署 → 线上检查 → 推送仓库 → IndexNow。

- **先提交**：部署出去的内容在仓库里一定有对应的提交，失败时可以 `git reset --hard origin/<分支>` 回到干净状态。
- **最后推送**：推送失败不影响线上；下一张发布单开始时先 `git pull --rebase` 再补推。
- **不先推送再部署**：避免仓库领先于线上，而部署又失败，造成两边不一致。

**线上检查**：直接请求上线链接，带 `Cache-Control: no-cache` 和随机查询参数，绕过浏览器缓存，检查 200 和页面里的发布单标记（`build.sh` 写入 `<meta name="erp-release" content="WP…">`）。SiteGround 的动态缓存可能仍返回旧页面，所以线上检查没通过不阻断，只提示，由人工清缓存后确认。

### 4. 数据模型

| 表 | 主要字段 |
| --- | --- |
| `website_site` | tenant_id、名称、网址（全平台唯一索引）、语言 JSON（代码、前缀、顺序）、地址模板、发布方式、仓库地址、分支、部署主机 / 端口 / 用户 / 目录、商品范围 JSON、非正品品牌 JSON、售价系数、币种、需要清缓存、清缓存说明、接管状态、接管确认人和时间、status、deleted_at |
| `website_site_secret` | site_id、类型（仓库令牌 / 部署私钥 / IndexNow 密钥）、密文、末四位 |
| `website_category_map` | site_id、ERP 品类 id、站点品类代码 |
| `website_product` | tenant_id、site_id、product_id、listing_task_id、slug（站内唯一）、slug_locked、手工售价、计算售价和来源明细 JSON、站点状态、原有内容 JSON、导入时间、字段改用 ERP 的标记 JSON、上次发布快照 JSON、上次发布单 id |
| `website_release` | tenant_id、site_id、编号 WP…、状态（排队中 / 执行中 / 已完成 / 失败 / 部署失败）、发起人、开始和结束时间、提交号、警告 |
| `website_release_item` | release_id、website_product_id、数据快照 JSON、上线链接 JSON、线上检查结果 |
| `website_release_step` | release_id、步骤、状态、开始和结束时间、输出摘要（去掉凭据，最多 50 行） |
| `website_site_job` | site_id、类型（导入 / 比对）、发给发布服务的任务号、状态、结果 JSON、差异确认接受记录 JSON（字段、原因、确认人、时间）、失败原因 |

- 导入和比对是异步任务，结果要留到用户确认（导入预览、差异确认接受），所以单独存 `website_site_job`（实现时补充，2026-11-27）。
- 子表也都带 tenant_id，回调时用 `TenantContext.runAs` 按所属公司写入。
- 带 tenant_id 的表归为私有表。`website_site` 的网址唯一性校验需要跨租户，用 `TenantScope.platform` 显式查询。
- 发品任务 `listing_task` 加 `site_id`（独立站任务必填，其他平台为 0，不用 NULL，以免唯一键失效），唯一键从「商品 + 平台」扩展为「商品 + 平台 + 站点」；新增状态值 4「发布中」、5「已上线待确认」，以及最近的发布单、「内容有更新」标记、确认人和确认时间。发起前的状态记在 `website_release_item.prev_status`，失败时恢复。
- 公司还没有站点时，独立站任务与原来一样按平台生成一个（`site_id` 为 0，手工发布）；建好第一个站点后，发品任务对齐时把这些任务挂到第一个站点上（不另写一次性任务，2026-11-27 实现时确定）。

### 4a. 链接别名（2026-11-27 实现时确定）

- 站点数据约定第 1 版要求 `slug` 与站点按品牌、系列、型号算出的一致，否则构建失败，所以 ERP 用同一算法生成（`SiteSlug`，单元测试的期望值由站点的 JS 实现算出），不用英文营销内容里的 URL Slug。
- 发布或导入后锁定。锁定后品牌、系列、型号又改了，站点重算的链接会和锁定的不一致，预览列为阻断项，需要先恢复资料或另行处理链接迁移。
- 站内唯一不用唯一索引（预览时会为多个商品算别名，可能暂时相同），由预览阻断和同一站点同一时间只执行一张发布单保证。预览只把已锁定的别名（确实在站点上的）算作站内冲突，同一批里的重复另外检查；没发布过的别名会随资料变化，不算冲突。
- 预览除规格列出的阻断项外，还阻断「缺少一句话规格」（站点构建要求 `spec` 非空）和「正在发布中」；营销内容的三项检查只对取 ERP 内容的语言做（导入后没改过的沿用站点原有内容）；「只复制」站点不检查是否以 ERP 为准。
- 回价经 `inquiry_item.product_id` 关联商品；只取已提交（status=2）、有货、未删除、单价大于 0 的。

### 4b. 站点数据的取值与导入后的取舍（2026-11-27 实现时确定）

| 站点字段 | ERP 来源 | 字段组（取舍单位） | 组的更新时间 |
| --- | --- | --- | --- |
| `spec` / `spec_ru` | 多语言一句话规格（`product_locale`），英文没有时用商品档案的规格摘要 | spec / spec_ru | 摘要行的更新时间 |
| `overview`、`meta_description`（及 `_ru`） | 本公司营销内容的首屏定义块、SEO 描述 | content_en / content_ru | 营销内容写入时间（`enAt` / `ruAt`） |
| `specs`、`applications` | 商品资料（英文） | specs / applications | 资料行最近更新（含删除） |
| `faq` | 本公司英文 FAQ；没有时用商品资料里已审核、模板生成的 FAQ | faq | 同上 |
| `compatibility` | 型号关系；置信度低、未知的不标关系类型 | compatibility | 同上 |
| `deep_ru` | 俄文规格、应用场景、关系说明，本公司俄文 FAQ | deep_ru | 以上各项最近更新 |
| `photo`、`photos`、`videos` | 发品任务的素材（见第 8 条） | media | 该型号素材记录最近更新、选参考素材的时间 |
| `sell_price` 等 | 站点售价 | price | 询价最低价的回价时间；手工售价改为直接以 ERP 为准 |
| `status` | 站点状态 | status | 无；手工改状态后以 ERP 为准 |

- 品牌、型号、系列、站点品类、链接别名、发布单号始终用 ERP；`linkedin` 图和站点独有字段（如 `reviews_verified`）ERP 没有对应位置，始终原样保留。
- 站点数据约定第 1 版没有 SEO 标题字段（站点自己生成 `<title>`），SEO 标题只在预览里检查、不导出。
- ERP 的值为空时不写这个字段，站点按没有处理。
- 已导入商品手工改售价或站点状态时，自动把这一组记入「改用 ERP 内容」（`erp_fields`），因为这两组没有可比的更新时间。
- 预览只计算素材链接、不复制；发布时才复制到公开桶。

### 5. 售价计算

- 取本公司近 180 天、有货的 `sourcing_quote.unit_price_cny` 的最小值，按商品 id 匹配；
- 美元价 = 最小值 × 系数 ÷ 系统美元汇率（人民币 / 美元），全程用 BigDecimal，最后四舍五入保留 2 位；
- 计算结果和计算依据（单价、系数、汇率、汇率日期、回价 id）存入 `website_product`；
- 每次预览重新计算，与上次发布快照比较，判断是否「内容有更新」。
- 询价回价、汇率变化后不做实时推送，改为在发品任务列表加载和定时任务（每天 06:00）时重新判断。

### 6. 「内容有更新」的判断

- 预览时生成当前的站点数据，与 `上次发布快照` 做深比较，得到变化的字段，这个结果同时用于预览显示。
- 列表用的「内容有更新」标记，由每天的定时任务和相关写入事件（营销内容写入、商品资料修改、素材标记变更、站点设置修改）触发重算，写回发品任务。

### 7. 导入与比对复用发布服务

- **导入**：运行 `erp/export-existing.js`，得到现有商品，素材以仓库里的路径记录（不复制进 ERP 存储，也不搬到 OSS，仍放在仓库和 SiteGround 上）。`site-data.json` 引用这些已有路径。
- **比对**：在临时工作区运行 `build.sh`，对比生成的数据文件与当前仓库版本的商品条目（解析 JS 对象后按字段比较），不部署、不提交。

所以接管 fouwell.com 时不需要把 148 个商品的素材搬进 ERP；以后 ERP 素材库里有了「用于发品」的素材，导入后更新过的字段才改用 ERP 的素材。

### 8. 素材放 OSS 公开存储桶，不进站点仓库（2026-11-27 用户决定）

素材库里已经有一份图片和视频，再复制进站点仓库会让仓库越来越大（本机仓库已约 1.7 GB，推送 GitHub 经常中断），所以：

- 发布时，后端把这个商品「用于发品」的素材（本公司素材在私有桶）复制到现有公开桶 `zhul-erp-prod-public`，键为 `website/{siteId}/{slug}/{素材id}.{扩展名}`；键固定，已存在就跳过，重复发布不重复上传。共享商品库的官方图片本来就在公开桶，直接用原地址。
- `site-data.json` 只写链接，链接由 `FileStorageService.publicUrl` 在导出时生成；数据库里存键，不存链接。以后配了 `OSS_PUBLIC_DOMAIN`（自定义域名或 CDN），全站重新发布一次就全部换成新域名。
- 复制到公开桶的文件不删除：素材取消「用于发品」后，下一次发布不再引用即可。
- 本机 `STORAGE_TYPE=local` 时公开地址是 `/uploads/...`，只用于本机调试，站点上打不开；本地检查照样按链接加载。
- **已知限制**（已实测）：生产桶在杭州、用默认域名。从马来西亚取一张 433 KB 的图约 0.5 秒；默认域名返回 `Content-Disposition: attachment`，网页里 `<img>`、`<video>` 正常显示，只有直接打开链接会下载。绑定 `media.fouwell.com` 这类自定义域名需要 ICP 备案，所以先不配；海外访问慢或搜索引擎图片收录有问题时，再开香港或新加坡的存储桶。

### 9. 发布单调度

- 后端定时任务每 30 秒检查一次：每个站点有排队的发布单、且没有执行中的，就提交给发布服务（同一时间只执行一张，用 Redisson 锁 `zhul:erp:lock:website:release:{siteId}`）。
- 发布服务按步骤回调进度；后端用 `TenantContext.runAs` 处理回调。
- 超过 40 分钟仍未完成，标为失败（看门狗）。

## Risks / Trade-offs

- [ERP 数据和 llm-wiki 现有页面字段不完全对应，接管时可能改坏页面] → 用「站点原有内容」兜底，加上一致性比对，比对确认前禁止发布。
- [SiteGround 的缓存导致线上检查看到旧页面] → 线上检查没通过不阻断，由人工清缓存后确认上线；确认框显示清缓存说明。
- [发布服务的 Chromium 镜像较大（约 1GB），占用内存] → 只在发布时启动浏览器，用完就关闭；内存限制 1.5G。
- [凭据经过内网传给发布服务] → 只在 Compose 内网传输，用共享令牌鉴权，不落盘、日志脱敏；与现有凭据规范一致。
- [两边同时修改：在 llm-wiki 里手工改了站点，又从 ERP 发布] → 接管后在 llm-wiki 的 `publish-to-website` 技能里注明停止使用；发布开始时先拉取最新代码，`site-data.json` 只覆盖 ERP 管理的条目；发现站点数据被人改过（与上次发布的提交不一致）时，在发布单里提示警告。
- [素材在杭州 OSS、默认域名，海外加载偏慢，链接不是自己的域名] → 先接受（实测 0.5 秒一张图）；数据库存键不存链接，以后换存储桶或配 CDN 只需重新发布。
- [生产服务器访问 GitHub 不稳定（本机经常出现 EOF 和 SSL 错误）] → 拉取和推送带重试（3 次，指数退避）；推送失败只算警告。

## Migration Plan

1. 站点仓库先合并 `erp/` 适配脚本。这时 llm-wiki 的原有发布流程不受影响：适配脚本只在 ERP 调用时运行。
2. ERP 部署发布服务和后端、前端，执行数据库迁移（新表、发品任务字段、菜单、权限）；生产 `.env` 补齐 `SITE_PUBLISHER_URL`、`SITE_PUBLISHER_TOKEN`。
3. 在站点管理里新建 fouwell.com，检测连接 → 导入 → 一致性比对 → 处理差异 → 开始以 ERP 为准。
4. 先拿 1–2 个商品试发布，人工核对线上页面后，再放开批量发布。
5. 更新 zhul-skills 的 `publish-to-website` 技能：接管后的商品改由 ERP 发布。
6. **回滚**：清空 `SITE_PUBLISHER_URL` 就停止发布；站点仓库里每次发布都是一个独立提交，可以 `git revert` 后用原来的 `deploy.sh` 部署回去。

## Open Questions

- 线上检查用的页面标记，是否同时用于搜索引擎看不到的调试信息：不影响规格，由站点适配脚本决定放在 meta 里还是注释里。
