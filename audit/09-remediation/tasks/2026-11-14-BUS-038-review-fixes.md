# BUS-038 等：整改后复审 11 项修复（2026-11-14）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 2026-11-14 委托接手复审发现的修复。复审与缺陷见证由 Codex 完成，见[复审报告](2026-11-14-SEC-005-post-remediation-review.md) |
| 关联 ID / 原专题 | 新增：BUS-038、SEC-013、SEC-014、UI-052、UI-053、UI-054、AI-009、AI-010；重开：SEC-005、UI-004、AI-005；本轮新发现：SEC-015、SEC-016（见下文） |
| 目标与影响范围 | 后端退款申请、退款单、附件链接与挂接、退货入库单详情、本地分片直传；前端标签页刷新与未保存登记、单据附件卡、销售退货列表、退款弹窗；AI 编排服务大附件下载、预处理、心跳与用量汇总。不含 14 个暂缓项与 DOC-002 |
| 起点 commit / 工作分支 | develop `74240e86` / `fix/review-1114-remediation` |
| OpenSpec | 缺陷修复，不新开提案；没有新界面，刷新确认沿用关标签确认的样式与文案 |
| PR / 目标分支 | [#312](https://github.com/zhul-cloud/zhul-trade-erp/pull/312) → develop |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 下一个发布版本；发布前按本机等同生产验收 |

## 原因与实施

- **BUS-038 负退款**：执行退款申请的请求体复用 `SaveRefundRequest`，订单与原因由服务从申请带入，不能在入口直接加 `@Valid`（会把正常执行拒掉）。改为在直接登记与执行申请共用的 [`CustomerRefundService.create`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerRefundService.java) 里校验：金额按两位 `HALF_UP` 舍入后须大于 0（`0.004` 舍入为 0 也拒绝，原来是系统错误）；填了人民币时同样舍入后须大于 0；省略人民币时照旧按到账汇率折算。拒绝发生在写退款单之前，同一事务回滚，申请仍待财务处理。
- **SEC-013 退款申请按 ID 入口**：[`RefundRequestService`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/RefundRequestService.java) 新增 `visible(id)`，详情、改备注、挂接 / 移除附件都先过它：退款单菜单（财务）与有审批权限的人处理本租户全部申请，只从订单、售后进来的人按订单负责人数据范围，与按订单列出申请一致；看不到时统一回「退款申请不存在」。
- **SEC-005 附件链接**：[`AttachmentOwnerScope`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/attachment/service/AttachmentOwnerScope.java) 把全部附件类型逐一归类，`default` 改为拒绝。补上：销售退货（按售后单）、退货入库（仓库菜单看全部，否则按售后单）、采购退货（按负责人）、退款申请（财务菜单看全部，否则按订单）、出运单面单与单据（按出运单负责人，与出运单详情一致）；拍摄、素材、询价回价、付款、报销、费用、其他收入、应付、结汇等详情本来只校验租户的类型显式列出。
- **SEC-015（本轮新发现）退货入库单详情**：退货入库单详情接口允许销售退货、售后菜单进入，服务只校验租户，同租户业务员按 ID 能看别人的退货入库单（含成本）。[`ReturnReceiptService.detail`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/ReturnReceiptService.java) 改为：退货入库菜单（仓库）看全部，否则与售后单详情一致，售后单负责人或订单负责人在数据范围内才能看。
- **SEC-016（本轮新发现）挂接别人的未挂附件**：[`AttachmentService.attach`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/attachment/service/AttachmentService.java) 挂接还没挂到单据上的附件时不核对上传人，同租户用户按附件 ID 能把别人刚上传、还没保存的文件挂到自己的单据上再读取，绕过「未挂附件只有上传人能看」。改为未挂附件只有上传人能挂；后台任务没有当前用户，保持原样。所有挂接调用的附件 ID 都来自同一用户的上传，正常流程不受影响（后端全量测试通过）。
- **SEC-014 本地直传重写**：[`DirectUploadService`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/upload/service/DirectUploadService.java) 本地分片写入与完成互斥：写分片拿读锁（各片仍可并行），完成拿写锁，等在途写入结束后作废分片地址再校验；之后的写入、续签分片地址都被拒绝，认领的就是校验过的内容。本地存储只在单个后端实例上使用（文件在本机磁盘），用进程内按会话分段的读写锁，不引入 Redis 锁依赖。OSS 不受影响（完成后 multipart 已消耗）。
- **UI-004 右键刷新**：[`PageTabs`](../../../zhul-erp-frontend/src/components/PageTabs/index.tsx) 右键「刷新」重建的是标签当前显示的页面，它有未保存修改时先确认（「刷新这个页面？」「不保存，刷新」），取消时页面与草稿不变。
- **UI-052 附件卡备注草稿**：[`DocFilesCard`](../../../zhul-erp-frontend/src/components/DocFiles/index.tsx) 备注改了还没保存时登记到所属标签，并在离开浏览器页面时提示；保存成功或取消编辑后撤销，保存失败时保留。为免和页面自己的未保存登记互相覆盖，[`useTabDirty`](../../../zhul-erp-frontend/src/components/PageTabs/hooks.ts) 改为同一页面可多处登记，任一处未保存这一页就算未保存。
- **UI-053 销售退货列表**：新增 `useOnReactivate`，页面从隐藏回到可见时执行一次；[销售退货列表](../../../zhul-erp-frontend/src/pages/sales/returns/index.tsx)返回时重新取列表与计数，页面不卸载，筛选、分页、滚动位置保留。按复审结论只改销售退货列表。
- **UI-054 退款弹窗迟到响应**：[`RefundModal`](../../../zhul-erp-frontend/src/pages/finance/refunds/RefundModal.tsx) 每次打开的取数在关闭或换单时作废，上一张申请迟到的成功或失败都丢弃，币种、汇率换算与提交都属于当前申请；手填人民币照旧。
- **AI-005 下载与预处理期限**：[`large_parse.download`](../../../scripts/ai-orchestrator/large_parse.py) 每次读取前检查整任务期限，并把这次读取的等待限制在剩余期限内；改用 `read1`（只等一次网络数据），慢速但不断有数据的下载也会在期限到时停下；等待被期限截断时按任务超时报告，不算附件读不了。预处理每页 PDF、每组表格行之前检查期限，到期抛任务超时而不是记成文件损坏。
- **AI-009 读取阶段心跳**：[`parse_large`](../../../scripts/ai-orchestrator/server.py) 的心跳从读取附件开始，覆盖下载、预处理、提取、合并与核验，任务退出时统一停止。
- **AI-010 失败用量**：`parse_large` 在 `finally` 里把工作线程已产生的用量汇总回回调线程，成功、失败、超时都随回调上报；只汇总一次，回调重投复用同一请求体，不重复记录。

## 验证

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| [`AfterSalesContractTest`](../../../zhul-erp-backend/src/test/java/com/zhul/erp/modules/sales/AfterSalesContractTest.java) `refundRequestExecute_rejectsZeroAndNegativeAmounts`：负 / 零 / 舍入为零的原币与人民币各一例，正常省略人民币 | 本机 MySQL；修复前 / 后 | 修复前失败、修复后通过 | 修复前执行 `-1.00` 返回 `code=0` 并生成 `USD -1.00` 退款单；修复后 6 例都拒绝，申请仍待财务处理、无退款单、凭证未挂上，正常执行成功且人民币为正 | 通过 |
| 同上 `refundRequestAndReturnDocs_followOrderOwnerScope`：同租户业务员（本人范围）对别人订单的退款申请、退货入库单与三类附件；财务、仓库、本人对照 | 本机 MySQL；修复前 / 后 | 修复前失败、修复后通过 | 修复前业务员读到别人的申请（`code=0`）；修复后详情 / 备注 / 挂接 / 移除都回「退款申请不存在」且备注、附件归属不变，退货入库单「不存在」，退款申请 / 销售退货 / 退货入库附件链接 403；财务读写申请与链接正常，仓库看退货入库单与附件正常；订单回到本人后详情、备注、挂接、移除与三类链接都正常；别人上传的未挂附件不能挂上（SEC-016） | 通过 |
| [`DirectUploadContractTest`](../../../zhul-erp-backend/src/test/java/com/zhul/erp/modules/upload/DirectUploadContractTest.java) 新增 2 例：完成后改写与续签、完成与在途写入交错 | 本机 MySQL、本地存储；修复前 / 后 | 修复前失败、修复后通过 | 修复前完成后改写分片返回 `code=0`、完成不等在途写入；修复后改写与续签被拒，临时文件与认领后的正式文件都是校验过的内容，完成等在途写入结束后才校验 | 通过 |
| 后端完整 `mvn test` | 本机 MySQL | 通过 | 1662 条，0 失败、8 条跳过 | 通过 |
| [`test_large_parse.py`](../../../scripts/ai-orchestrator/test_large_parse.py) 新增 9 例：慢流、阻塞读取、期限内完成；每页 / 每组行检查期限、到期不算文件损坏；读取阶段心跳；无型号、超时、成功三种用量 | 编排服务镜像，2GB 内存上限容器；修复前 / 后 | 修复前失败、修复后通过 | 修复前慢流读完整个文件不停、阻塞读取报读取超时、读取期间只报 1 次进度、失败与超时回调用量为空；修复后全部通过。成功用量一例修复前后都通过，作为不重复计数的回归 | 通过 |
| 编排服务全部测试 | 同上 | 通过 | 148 条通过（原 139 条） | 通过 |
| 前端新增：[`TabBar.test.tsx`](../../../zhul-erp-frontend/src/components/PageTabs/TabBar.test.tsx)、[`hooks.test.tsx`](../../../zhul-erp-frontend/src/components/PageTabs/hooks.test.tsx)、[`DocFilesCard.test.tsx`](../../../zhul-erp-frontend/src/components/DocFiles/DocFilesCard.test.tsx)、[`RefundModal.test.tsx`](../../../zhul-erp-frontend/src/pages/finance/refunds/RefundModal.test.tsx) | Jest；修复前 / 后 | 修复前失败、修复后通过 | 修复前右键刷新不确认、两处登记互相覆盖、附件卡备注不登记、迟到响应覆盖为 EUR 汇率 7（人民币 70）；修复后 9 例通过，B 的提交为 `execute(2, amount 10, amountCny 90, method 2)` | 通过 |
| `npm run lint`、`npx antd lint ./src`、`npm test` | 本机 | 通过 | 无错误；525 个文件无问题；71 组 349 条通过 | 通过 |
| 本机真实页面与 HTTP 复测 | 本机 Docker，合并后按 develop 重建 | 修复生效、正常场景不回归 | 未执行 | 合并后执行 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-038 | 执行退款申请拒绝负数、零与舍入为零的原币和人民币，拒绝时无部分落库；正常执行与省略人民币自动折算正常 | 未发布 | — | 待合并 |
| SEC-005 | 销售退货、退货入库、采购退货、退款申请、出运单附件链接跟随单据数据范围；未归类类型拒绝 | 未发布 | — | 待合并 |
| SEC-013 | 退款申请详情、备注、挂接与移除附件按订单负责人；财务、审批人看全部 | 未发布 | — | 待合并 |
| SEC-014 | 本地直传完成后不能改写；校验与在途写入不交错；正常乱序、重复与认领不受影响 | 未发布 | — | 待合并 |
| SEC-015 | 退货入库单详情：仓库看全部，销售侧按售后单 / 订单负责人 | 未发布 | — | 待合并 |
| SEC-016 | 未挂附件只有上传人能挂接 | 未发布 | — | 待合并 |
| UI-004 | 右键刷新有未保存修改时先确认，取消保留草稿 | 未发布 | — | 待合并 |
| UI-052 | 附件卡备注草稿登记未保存，关闭 / 刷新 / 返回时确认 | 未发布 | — | 待合并 |
| UI-053 | 销售退货详情改状态后返回列表，列表行与计数更新 | 未发布 | — | 待合并 |
| UI-054 | 退款弹窗只用当前申请的响应 | 未发布 | — | 待合并 |
| AI-005 | 下载与预处理受整任务期限约束 | 未发布 | — | 待合并 |
| AI-009 | 读取与预处理阶段有心跳 | 未发布 | — | 待合并 |
| AI-010 | 失败与超时回调带上已产生的用量 | 未发布 | — | 待合并 |

## 阻塞与下一步

- 合并后状态为「已合并待发布」；发布须由用户决定，发布后按本机等同生产完成验收再逐 ID 关闭。
- 其他保活列表从详情返回时可能有与 UI-053 相同的旧数据问题，本轮没有逐个核实，未扩大范围；`useOnReactivate` 可直接复用。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-14 / Claude | 认领复审 11 项，接续 Codex 复审报告；在 develop `74240e86` 上实施修复，新增 SEC-015、SEC-016 两个审查外发现并一并修复；修复前后测试对照见上表 | 已创建 [#312](https://github.com/zhul-cloud/zhul-trade-erp/pull/312)；合并后在本机按 develop 重建，做真实页面与 HTTP 复测 |
