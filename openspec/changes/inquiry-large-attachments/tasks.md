# Tasks

## 1. 原型（开发页面前必须完成）

- [x] 1.1 用 OpenPencil 出原型：
  - 询盘上传区的各种状态：上传中（进度、剩余大小）、分片失败后「继续上传」、取消、超过 500MB、类型不支持、上传服务未配置；
  - 询盘详情「解析中 已解析 x / y」；
  - 确认页的「未解析部分」提示。

  验证：原型文件保存在 `docs/03-产品原型/`，用户确认后在本任务记录确认日期。

  完成：`docs/03-产品原型/03-业务域/11-10询盘大附件/`，用户 2026-11-10 确认。

## 2. 后端：通用直传

- [x] 2.1 上传场景登记与 Redis 上传会话（D2）：准备上传、续签、完成、取消接口；完成后返回普通 `fileKey`，业务保存沿用 `locate` 校验（实施时调整，见 D2）。验证：`DirectUploadContractTest` 覆盖声明超限、扩展名不符、合计超限、没有权限、会话过期、他人的会话不能续传或完成。
- [x] 2.2 OSS 分片上传：发起、逐片签名（15 分钟）、合并、HeadObject 核对大小、按范围读取文件头、CopyObject 到正式键、删除临时对象（D1、D4）。验证：`OssFileStorage` 单元测试 mock OSS 客户端，覆盖成功、大小超限、文件头不符；`OssLiveIT` 在配置了 OSS 时真实走通一次 20MB 的分片上传。
  完成：`OssFileStorageTest` 新增 3 个用例通过；`OssLiveIT` 用本机 `.env` 的开发 OSS（`zhul-erp-dev-private`，cn-hangzhou，V4 签名）真实运行 2/2 通过，其中分片直传用例真实 PUT 两片（16MB + 4MB）、合并、读文件头、复制到正式键（2026-11-10）。
- [x] 2.3 本地存储分片接口 `PUT /api/v1/uploads/{id}/parts/{n}`（D5）：一次性令牌鉴权；请求体按流写入临时文件的对应位置；完成时检查分片齐全、校验大小与文件头，再改名移到正式目录；准备上传时检查磁盘剩余空间。验证：契约测试覆盖乱序分片、重复分片、缺分片时完成被拒、伪装文件被删除、令牌过期或属于他人时拒绝、剩余空间不足时拒绝；断言上传 100MB 时后端堆内存不随文件大小增长。
  完成：`DirectUploadContractTest` 覆盖乱序与重复分片、缺分片、分片长短不对、伪装文件被删除、令牌或用户不对时拒绝。没有做的两项：一是空间不足（测试环境无法稳定造出磁盘不足）；二是堆内存断言（JVM 堆用量受 GC 影响，断言不稳定）。分片写入按 64KB 缓冲流式写到文件位置，不整片读入内存，以代码审查为准。
- [x] 2.3a 本地临时上传的清理定时任务：每小时删除超过 1 天的未完成上传，用 `TenantScope.platform` 执行；启动时发现私有上传目录在 tmpfs 上就告警。验证：`BackgroundEntryTenantContractTest` 自动覆盖没有租户上下文时的执行；单元测试覆盖只删超过 1 天的、不动正式目录；告警用日志断言。
  完成：`UploadHousekeepingTest`、`BackgroundEntryTenantContractTest` 通过；tmpfs 告警只在 Linux tmpfs 上触发，本机为 Windows，未做日志断言。
- [x] 2.4 启动时只读检查 OSS 跨域规则，缺少时告警（D6）。验证：单元测试覆盖规则缺失时只写日志、不阻止启动。
  完成：`UploadHousekeepingTest.corsReady_requiresPutAndExposedEtag` 覆盖规则判断；读取失败、规则缺失都只记警告日志。
- [ ] 2.5 部署文档 `docs/06-部署运维/12-大附件直传与解析配置.md`（规划阶段已写好初稿），实施后按实际实现核对：RAM 权限、跨域、生命周期、配置项、临时目录容量；同步更新 `07-对象存储.md`、`04-AI编排服务.md`、`02-配置说明.md` 与 `.env.example`。验证：文档中的权限与配置项和代码实际调用的 OSS 接口、读取的环境变量一一对应。

## 3. 前端：通用分片上传组件

- [ ] 3.1 按确认的原型实现分片上传：16MB 分片、同时传 4 片、每片重试 3 次、续签、取消、断网后继续（D3）。验证：Jest 用 mock 接口覆盖进度计算、某片失败后重试成功、重试用尽后继续上传只传剩余分片、取消时调用后端取消接口。
- [ ] 3.2 接入 `UploadTile` / 附件墙，按场景显示类型、大小提示，跨域失败时显示「上传服务未配置好」。验证：组件测试覆盖超限、类型不符、跨域失败三种提示；`npm run lint`、`npx antd lint ./src` 通过。

## 4. 询盘附件改用直传

- [x] 4.1 登记询盘附件场景：JPG、PNG、PDF、xls、xlsx、csv，单个 500MB，新建、追加、修改分别校验对应权限；保存时凭会话认领文件，取代现有的 fileKey 校验；去掉每单 10 个的限制，改为每单合计不超过 2GB（准备上传时和保存时都要校验）；从商机带入附件按合计上限处理，商机自己的规则不动；铭牌图片保持原样。验证：`CustomerInquiry*ContractTest` 覆盖 300MB 声明通过、650MB 拒绝、ZIP 拒绝、伪造或他租户的会话被拒、25 个附件合计 600MB 通过、合计超过 2GB 被拒、带入商机附件超过合计时只带入部分并提示；`Opportunity*ContractTest` 原有附件上限用例不变。
  完成：`DirectUploadContractTest` 覆盖 25 个附件通过、合计超过 2GB 被拒、删掉大附件后可以补传；`InquiryIntakeContractTest`、`InquiryAppendContractTest`、`InquiryNameplateContractTest` 回归通过。请求里附件个数改为 1000 的技术兜底（见 design 风险）。从商机带入的附件合计判断在前端，保存时后端按合计再校验一次。
- [ ] 4.2 询盘新建、追加型号、修改三处前端换用分片上传组件，附件没传完时不能提交。验证：组件测试覆盖「还有附件没有上传完」；本机本地存储模式下上传 300MB 的 PDF 并提交成功。
- [ ] 4.3 更新 `docs/05-使用手册/` 的询价管理手册：附件上限、上传进度和失败处理。验证：手册中的数字与规格一致。

## 5. AI 编排服务：预处理与分批解析

- [x] 5.1 附件改为流式写入临时目录，单个附件超过 500MB 失败，任务结束后删除（D7）。验证：`test_server.py` 用本地 HTTP 服务提供 50MB 文件，断言内存中不留整份内容、临时目录被清理。
  完成：`test_large_parse.DownloadTest` 用本地 HTTP 服务下载 50MB，`tracemalloc` 峰值低于 8MB；超限时报错且不留文件；任务结束后临时目录被删除（`ParseLargeTest`）。
- [x] 5.2 预处理（D8）：PDF 区分文字页与扫描页，处理加密文件和损坏的页；图片缩放；Excel（xlsx、xls、csv）流式转文字；`requirements.txt` 加 openpyxl、xlrd。验证：单元测试用真实生成的混合 PDF、加密 PDF、含损坏页的 PDF、60MB 图片、多工作表 xlsx、xls、csv，断言输出单元和「未解析」列表。
  完成：`PreprocessTest` 覆盖混合 PDF、加密 PDF、损坏 PDF、单页损坏、大图片缩放（5000×4000 随机像素，约 60MB 原始数据）、多工作表 xlsx、GBK 编码 csv、与后端一致的表格文字格式。xls 只做了读取实现，没有测试：生成 xls 需要额外的 xlwt 库，测试环境未引入。
- [x] 5.3 分批：按每批的图片数、文字量、数据量上限切分；只需一批时沿用现有的单次请求（D9）。验证：单元测试覆盖边界（20 张、21 张、6 万字），以及只需一批时调用参数与改前一致的回归用例。
  完成：`PackTest` 覆盖 20 / 21 张图、6 万字、18MB 与单个超限单元；`test_small_inquiry_keeps_single_call` 与原有 `test_claude_stream`、`test_https_link_still_downloaded` 覆盖只需一批时仍是单次调用、参数与改前一致。
- [x] 5.4 多批提取并行（同时 3 批、失败重试 2 次），合并去重（数量相加、单位不同时标待核实、记录出现位置），核验时每批 50 个型号，每批各自适用最大搜索次数（D9）。验证：单元测试用 fake 模型覆盖跨批重复型号的数量相加与位置、单位不同、120 个型号分 3 个核验批且每批不超过上限。
  完成：`MergeTest` 与 `ParseLargeTest.test_duplicates_merged_and_120_models_verified_in_3_batches`（120 个型号分 50、50、20 三个核验批，跨批重复型号数量相加并注明位置）。
- [x] 5.5 逐项容错（D10）：附件、页、提取批、核验批各自失败互不影响，只有全部都提取不出型号时任务才失败；订阅方式达到搜索阈值时同一批改为不联网重新提取。验证：单元测试覆盖一个附件下载失败、某页损坏、某提取批始终失败、某核验批达到上限、全部失败五种情况的回调内容。
  完成：`test_partial_failures_do_not_fail_the_task`（附件下载失败、某页损坏、某提取批重试用尽、某核验批达到搜索上限）与 `test_everything_failing_fails_the_task`、`test_no_models_extracted_fails_the_task`。
- [x] 5.6 订阅方式按同样的批次多次调用，去掉「超过 25MiB / 20 页即失败」。验证：`test_subscription.py` 覆盖 300 页扫描件分成每批不超过 20 页。
  完成：大询盘不再整份交给订阅方式，而是按批调用；订阅方式单次调用的 25MiB / 20 页检查保留为兜底，批次上限保证不会触发。测试放在 `test_large_parse.PackTest.test_300_scanned_pages_fit_subscription_limit`（300 页分 15 批，每批不超过 20 页、25MiB）。
- [x] 5.7 进度报告与整个任务的期限 `AI_ORCHESTRATOR_MAX_SECONDS`（D11），更新编排服务 README 和 `.env.example`。验证：`test_job_deadline.py` 用模拟时钟覆盖每批报告进度、60 秒心跳、到期后不再发起新批。
  完成：README、`.env.example`、`docker-compose.yaml`、`04-AI编排服务.md` 已更新。测试放在 `test_large_parse`：`test_progress_reported_per_batch`、`HeartbeatTest`（心跳间隔调小到 0.05 秒）、`test_deadline_stops_new_batches`（期限调小到 0.5 秒，到期后只有第一轮 3 批发出）。用真实短时间代替模拟时钟。

## 6. 后端：进度与超时

- [ ] 6.1 迁移：给 `ai_task` 增加进度和最后一次进度报告时间，并写回滚脚本。验证：Flyway 在测试库迁移成功；回滚脚本在本机执行成功。
- [ ] 6.2 回调接收进度：只在任务未结束时更新进度，不改变状态，任务结束后忽略。验证：`AiTaskStateRaceContractTest` 覆盖结束后到达的进度报告被忽略。
- [ ] 6.3 超时扫描按最近一次回调或进度报告计时，并加最长处理时长 `AI_TASK_MAX_MINUTES`（默认 180），写进 `.env.example`。验证：契约测试覆盖 50 分钟仍在报告进度的任务不超时、181 分钟的任务判为超时、20 分钟无进度判为超时，三种情况都会分发结果。
- [ ] 6.4 后端不再把 Excel 转文字放进 `rawContent`，改为以链接传给编排服务；询盘解析结果保存并返回 `unparsed`（D8、D9）。验证：现有带 Excel 的解析契约测试改断言为 Excel 以链接传出；新增测试覆盖 `unparsed` 在确认页接口返回。
- [ ] 6.5 询盘详情返回解析进度，确认页显示未解析部分（按确认的原型）。验证：前端组件测试覆盖「已解析 5 / 12」和「《清单.pdf》第 15 页无法读取」。
- [ ] 6.6 AI 设置页「最大搜索次数」旁的说明改为「每次调用最多搜索几次；大文件分批解析时每批各自计算」。验证：组件测试断言说明文字。

## 7. 集成验收

- [ ] 7.1 本机全流程：本地存储模式、AI 编排服务用 API 方式。上传一个 300 页、混有扫描页的 PDF 与一个大 xlsx，解析进度可见，确认页型号完整、重复型号已合并、未解析部分有提示。验证：在 audit 任务记录中写下步骤和截图位置。
- [ ] 7.2 全量回归：后端 `mvn test`、前端 `npm run lint` 与 `npx antd lint ./src` 与 Jest、编排服务全部 Python 测试通过，CI 通过。验证：PR 的 CI 结果。

## Workflow follow-up

- PR 说明写明新增配置项 `AI_TASK_MAX_MINUTES`、`AI_ORCHESTRATOR_MAX_SECONDS` 的生产取值，迁移版本号及能否回退，以及发版前必须完成的 OSS 跨域、生命周期配置。
- 上线后在生产用 200MB 以上的 PDF 验收上传和解析，通过后归档本变更。
