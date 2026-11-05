# 批次 E：库存与履约一致性（BUS-005、006、007、010、016、018，新发现 BUS-027）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 决定规则取舍与验收 |
| 关联 ID / 原专题 | [BUS-005、006、007、010](../../01-business/03-采购仓储与物流.md)、[BUS-016、018](../../01-business/05-全量业务代码审查.md)；BUS-027 为本批实施中的新发现 |
| 目标与影响范围 | 后端出库通知、入库冲销、出运单与直发货、采购单改量、货代账单、订单取消与重转。没有迁移，没有配置项 |
| 起点 commit / 工作分支 | `develop` `2f8335d8`；每项一个分支，见下表 |
| OpenSpec | 不适用：缺陷修复，按用户确认的规则执行 |
| PR / 目标分支 | 每项一个 PR 发往 `develop`，CI 通过后自动合并 |

## 规则决定（用户 2026-11-04 确认）

- **BUS-005**：订单取消重转、接回采购行时，原采购行的发货、入库、实拍记录一并改挂到新订单对应的型号行；差异挂在采购行上，随采购行一起走。新订单可以按真实合格数出库，库存不重复计算。
- **BUS-010**：入库的型号已被出库通知、打包、交货代或出运占用时，拒绝冲销入库，并列出需要先撤回的出库单号。
- **BUS-027**：采用方案 A。取消订单时，只拦还在仓库的出库单（待打包、已打包），已交货代、已出运的不拦，保持现有业务；重转时，旧订单已交货代、已出运的出库明细随货改挂到新订单，新订单不会把已发出的货再算成可出库。

## 实施与验证结果

每项都先在修复前的代码上运行缺陷见证测试，确认按审查描述失败，再在修复后确认通过。

| ID | 修复 | 缺陷见证（修复前 → 修复后） | PR / merge commit |
| --- | --- | --- | --- |
| BUS-006 | `updateNotice` 软删除旧行后按占用（已不含本单）重新校验，去掉重复加回的本单旧数量；`buildItems` 的 `own` 参数随之删除 | `OutboundShippingContractTest.editNotice_cannotExceedQualifiedQuantity`：合格 10、通知 10，修复前改为 20 被允许 → 修复后被拒绝，减到 6 后再增到 11 仍被拒绝 | [#159](https://github.com/zhul-cloud/zhul-trade-erp/pull/159) `48fe2484`（原 #158 因台账冲突并入） |
| BUS-010 | 冲销入库前，对每个型号行比较「冲销后剩余合格数」与「出库占用数」，不够时拒绝并列出出库单号。多张入库共同满足出库、其他入库仍够时允许冲销。检查前按 ID 锁住相关销售订单，与新建发货通知互斥；没有出库占用的不拦（部分取消转闲置后剩余按 0 计） | `OutboundShippingContractTest.reverseReceipt_blockedWhileOutboundUsesTheGoods`：修复前冲销被允许 → 修复后被拒绝；分批 6+4 入库、出库占 4 时冲销 6 件那张成功 | [#159](https://github.com/zhul-cloud/zhul-trade-erp/pull/159) `48fe2484` |
| BUS-018 | 直发货加入出运单时先 `lockById` 锁住再查归属 | `OutboundShippingContractTest.directShipment_concurrentAttach_onlyOneWins`（SpyBean 让第一个请求停在读完直发货之后）：修复前两张出运单都创建成功 → 修复后第二张被拒绝，直发货归属第一张 | [#160](https://github.com/zhul-cloud/zhul-trade-erp/pull/160) `8db356c0` |
| BUS-016 | 保存采购单时按需求汇总校验：同一需求各行增减合并后不超过可下单数量 | `PurchaseContractTest.twoLinesOfSameRequirement_shareRemainingQuantity`：需求 10、两行各 3，修复前两行都改 7 被允许（合计 14）→ 修复后被拒绝，两行各 5 可以保存 | [#161](https://github.com/zhul-cloud/zhul-trade-erp/pull/161) `17ec57d3` |
| BUS-007 | 新建运费账单先按 ID 锁住本月候选出运单，再查已占用并在锁内复核；确认对账时出运单已对账则拒绝。没有加唯一约束（账单行软删除，草稿要能重建） | `StatementConcurrencyContractTest`（SpyBean 让第一个请求停在写完账单行、提交之前）：修复前第二张账单也纳入同一出运单 → 修复后第二个请求被拒绝；历史重复草稿后确认被拒绝，只有一笔运费应付 | [#162](https://github.com/zhul-cloud/zhul-trade-erp/pull/162) `44b96c23` |
| BUS-005 | 接回采购行时调用 `moveGoods`，把发货行、入库行改挂到新需求与新型号行，实拍任务改挂到新订单。拆行时：未发货的按原逻辑拆；已发货且发货量不超过新订单所需时，带货的原行整行给新订单，剩余数量拆成新行留在原需求；发货量超过新订单所需时，拒绝转订单并提示先处理多出的货 | `OutboundShippingContractTest.reconvertedOrder_takesReceivedGoods`：修复前重转后新订单报「A-1 还没有可以出库的数量」→ 修复后全入库 10 件可出库 10；入库 4、新订单要 6 时可出库 4；入库 8、新订单要 6 时转订单被拒绝 | [#163](https://github.com/zhul-cloud/zhul-trade-erp/pull/163) `9bcb1b79` |
| BUS-027 | 取消订单时，有待打包或已打包的出库单则拒绝；重转接回时，旧型号行上已交货代、已出运的出库明细改挂到新型号行 | `OutboundShippingContractTest.cancelOrder_blockedWhileOutboundActive`：修复前有待打包通知时取消被允许 → 修复后被拒绝，撤回后可以取消。`reconvertAfterHandOver_handedGoodsNotOutboundAgain`：交货代 4 件后取消重转；不改挂出库明细时新订单可出库 7 件（重复计算）→ 修复后最多 6 件，新订单显示已交货代 4 件 | [#163](https://github.com/zhul-cloud/zhul-trade-erp/pull/163) `9bcb1b79` |

- **合并冲突处理**：#158、#159 都在 `OutboundShippingContractTest` 末尾追加用例，#158 还与 `develop` 的台账冲突，所以将 #158 并入 #159、关闭 #158。之后各项的测试改放在新测试类或文件中段，审查记录在批次末统一回写。
- **回归**：BUS-005/027 合并 `develop` 后，以下测试类通过：`OutboundShippingContractTest`（11 个）、`PurchaseContractTest`、`SalesOrderContractTest`、`SalesOrderPartialCancelContractTest`、`ReceivingContractTest`、`AfterSalesContractTest`、`AfterSalesReplacementContractTest`、`SalesOrderTrackingContractTest`、`PayableContractTest`、`OrderCostContractTest`、`OperatingReportContractTest`、`PiContractTest`。
- **已知取舍**：BUS-010 没有把新检查加入入库详情的「可冲销」标记，按钮仍显示，点击后后端给出明确提示。BUS-027 改挂的出库明细，其出库单表头仍属原订单，费用分摊（`freight_allocation`）仍按原型号行记。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-005 | 重转后新订单按真实合格数出库 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| BUS-006 | 编辑通知不超过合格可用量 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| BUS-007 | 同一出运单只进一张账单、只有一笔应付 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| BUS-010 | 有出库占用时拒绝冲销入库 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| BUS-016 | 同一需求多行合计不超量 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| BUS-018 | 同一直发货只能进一张出运单 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |
| BUS-027 | 在仓库的出库拦取消；已交货代的随货改挂 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；生产未验收 | — | 已发布待验收 |

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-04 / Claude | 认领批次 E 共 6 项；BUS-005、010 的规则经用户确认；完成 BUS-006 的缺陷见证与修复验证 | 逐项实施，每项单独提 PR |
| 2026-11-04 / Claude | BUS-006/010（#159）、018（#160）、016（#161）、007（#162）合并到 `develop` | 实施 BUS-005 |
| 2026-11-04 / Claude | 实施 BUS-005 时发现取消订单不检查出库单，新编 BUS-027；用户选方案 A；与 BUS-005 一起实施并验证 | 随 v1.8.0 发布后验收 |
| 2026-11-04 / Claude | BUS-005、027（[#163](https://github.com/zhul-cloud/zhul-trade-erp/pull/163)）合并到 `develop`，merge commit `9bcb1b79`；批次 E 全部 7 项已合并 | 随 v1.8.0 发布后验收 |
| 2026-11-04 / Claude | BUS-005、006、007、010、016、018、027 发布前用 develop 与脱敏副本在本机验收通过，见[本机发布前验收记录](2026-11-04-ACCEPT-local-develop.md)；不是生产验证 | v1.8.0 部署成功后关闭 |
| 2026-11-05 / Claude | 随 v1.8.0 发布到生产：[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) 合并到 `main`（`75c325bd`），生产发版运行 41950405348 构建与部署（含健康检查）成功；合回 develop 见 [#182](https://github.com/zhul-cloud/zhul-trade-erp/pull/182) | 生产验收后关闭（验收人与日期补进上表） |
