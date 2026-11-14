# 批次 D：资金一致性（BUS-003、004、008、009、011、012、019）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 决定规则取舍与验收 |
| 关联 ID / 原专题 | [BUS-003、004、008、009](../../01-business/02-销售与财务.md)、[BUS-011、012](../../01-business/04-业务边界与接口契约补查.md)、[BUS-019](../../01-business/05-全量业务代码审查.md) |
| 目标与影响范围 | 只改后端资金相关服务：结汇、其他收入、报销、客户退款、采购单与应付同步、付款、订单成本、出库撤销交货代。没有迁移，没有配置项 |
| 起点 commit / 工作分支 | `develop` `15afd772`；每项一个分支，见下表 |
| OpenSpec | 不适用：缺陷修复，按用户确认的规则执行 |
| PR / 目标分支 | 每项一个 PR 发往 `develop`，CI 通过后自动合并 |

## 规则决定（用户 2026-11-04 确认）

- **BUS-004**：应付有任何付款、欠款抵扣或退款时，拒绝修改采购单币种；没有资金往来时，采购单与应付的币种、汇率、人民币金额一起更新。
- **BUS-009**：原付款形成的欠款已被有效的抵扣或退款使用时，拒绝作废原付款，并列出需要先作废的单据；先作废后续单据，再作废原付款，可以成功。
- **BUS-019**：外币欠款抵扣按来源应付的实付汇率（实付人民币 ÷ 实付原币）折算人民币成本，转入目标应付，来源相应减少。中间汇率不舍入，转移额按 HALF_UP 保留 2 位；抵扣行的现金流 `amount_cny` 仍为 0。

## 实施与验证结果

每项都先在修复前的代码上运行缺陷见证测试，确认它按审查描述失败，再在修复后确认通过。

| ID | 修复 | 缺陷见证（修复前 → 修复后） | PR / merge commit |
| --- | --- | --- | --- |
| BUS-008 | 作废结汇后，到账按主键合并，保留内存中已扣减的版本，不再和 `lockOpen` 读出的数据库旧版本一起放进按全字段判等的 `Set` | `FxSettlementContractTest.voidPartialSettlement_restoresSettledAmountToValidAllocations`：修复前已结汇额期望 0、实际 50.00（有效分摊为 0）→ 修复后通过 | [#150](https://github.com/zhul-cloud/zhul-trade-erp/pull/150) `c5956cdf` |
| BUS-012 | 编辑与作废都先 `FOR UPDATE` 锁住其他收入的这一行，再判断状态 | `OtherIncomeConcurrencyContractTest`（SpyBean 让编辑停在读取之后，同时作废）：修复前最终是有效（期望 2、实际 1）→ 修复后仍为作废，作废原因保留 | [#151](https://github.com/zhul-cloud/zhul-trade-erp/pull/151) `0cea39b9` |
| BUS-011 | 提交报销先按固定顺序（付款含退款、快递、退货运费、费用单，各自按 ID 升序）`FOR UPDATE` 锁住所选来源，再读取待报销项并校验。补测其余来源时发现，撤销交货代是先查报销、后锁快递，已改为先锁快递再查 | 费用单：`ReimbursementSourceRaceContractTest`，修复前费用被作废却仍在报销单里（期望 1、实际 2）→ 修复后作废被拒绝。其余 4 类（`ReimbursementContractTest.race_*`）：员工垫付付款作废、收到退款作废、退货运费重新打开在修复前就已正确（先锁行再查报销），测试通过；快递撤销交货代在修复前被放行（出库单回到已打包），改顺序后被拒绝 | [#152](https://github.com/zhul-cloud/zhul-trade-erp/pull/152) `c1b7209c`；其余来源：`fix/bus-011-other-sources` |
| BUS-003 | 可退额度与订单毛利中的退款，对 PI 转成的订单改为按 PI 归集，包含已取消的旧订单；手动订单不变，迁移订单快照不变 | `OperatingReportContractTest.refundOnCancelledOrder_countsAgainstReconvertedOrder`：修复前重转后可退 309.19（应为 209.19）→ 修复后可退 209.19，毛利含旧订单退款，超退被拒绝 | [#153](https://github.com/zhul-cloud/zhul-trade-erp/pull/153) `96c8b89a` |
| BUS-004 | 新增 `PayableSyncService.hasMoneyMovement`；已下单且有资金往来时拒绝改币种；同步应付时币种随采购订单 | `PayableContractTest.currencyChange_followsPayableUntilMoneyMoves`：修复前应付仍是 CNY（期望 USD）→ 修复后 USD 100 / CNY 715，付款后改币种被拒绝 | [#154](https://github.com/zhul-cloud/zhul-trade-erp/pull/154) `72e036be` |
| BUS-009 | 作废付款前检查：这笔付款实付进去的应付，是否被其他有效单据用作欠款来源（抵扣或退款）；有则拒绝，并列出单据编号 | `PayableContractTest.voidPaymentWhoseCreditIsUsed_rejectedUntilOffsetVoided`：修复前作废被允许 → 修复后被拒绝；先撤抵扣、再撤原付款可以成功，已付都回到 0 | [#155](https://github.com/zhul-cloud/zhul-trade-erp/pull/155) `044de045` |
| BUS-019 | `PayableSyncService.totals` 中的抵扣行，按来源实付汇率转移成本人民币 | `PayableContractTest.usdCreditOffset_carriesActualCnyToTarget`：修复前目标已付人民币为 0（应为 710.00）→ 修复后目标 710.00、来源 0.00，抵扣行现金流仍为 0 | [#156](https://github.com/zhul-cloud/zhul-trade-erp/pull/156) `2f8335d8` |

- **合并冲突**：#154、#155、#156 都在 `PayableContractTest` 末尾追加用例，按合并顺序解决，所有用例都保留。合并后 `PayableContractTest` 12 个用例，以及应付、成本、报销、采购、结汇、经营报表相关测试类都通过。
- **说明**：BUS-019 没有数据迁移。已有应付在下次刷新时按新口径重算（付款、作废、采购变更等都会触发刷新）；历史数据不批量重算。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-003 | 取消重转后可退额度与毛利按 PI | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-004 | 有资金往来锁币种，无往来应付随之换币 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-008 | 部分结汇作废后已结汇额等于有效分摊 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-009 | 欠款被使用时拒绝作废原付款 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-011 | 五类来源与报销提交互斥 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-012 | 编辑与作废交错后仍为作废 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-019 | 抵扣按来源实付汇率转移成本人民币 | v1.8.0 已部署（[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) `75c325bd`，生产发版运行 41950405348 成功，2026-11-05）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-04 / Claude | 认领批次 D 共 7 项；BUS-004、009、019 的规则经用户确认；完成 BUS-008 的缺陷见证与修复验证 | 逐项实施，每项单独提 PR |
| 2026-11-04 / Claude | 7 项逐项实施并验证，#150～#156 全部合并到 `develop`（merge commit 见上表） | 用户要求补测 BUS-011 其余来源 |
| 2026-11-04 / Claude | 补测 BUS-011 其余 4 类来源：员工垫付、收到退款、退货运费在修复前就已互斥；快递撤销交货代先查后锁，修复前被放行，已修复 | 随 v1.8.0 发布后验收 |
| 2026-11-04 / Claude | 补记：[#157](https://github.com/zhul-cloud/zhul-trade-erp/pull/157) 已于当日合并到 `develop`（`0bc02335`），台账 BUS-011 当时漏改，现改为已合并待发布 | 随 v1.8.0 发布后验收 |
| 2026-11-04 / Claude | BUS-011 发布前用 develop 与脱敏副本在本机验收通过，见[本机发布前验收记录](2026-11-04-ACCEPT-local-develop.md)；不是生产验证 | v1.8.0 部署成功后关闭 |
| 2026-11-05 / Claude | 随 v1.8.0 发布到生产：[#181](https://github.com/zhul-cloud/zhul-trade-erp/pull/181) 合并到 `main`（`75c325bd`），生产发版运行 41950405348 构建与部署（含健康检查）成功；合回 develop 见 [#182](https://github.com/zhul-cloud/zhul-trade-erp/pull/182) | 生产验收后关闭（验收人与日期补进上表） |
| 2026-11-14 / Claude | 本机验收（用户确认本机与生产效果相同）：develop `6bdde1f7`（业务代码与生产 v1.22.1 一致）上后端完整 `mvn test` 1652 条、前端 `npm test` 340 条、AI 编排服务 139 条全部通过，含本记录各 ID 的回归测试。用户 Ezio 作为验收人确认关闭 BUS-003、BUS-004、BUS-008、BUS-009、BUS-011、BUS-012、BUS-019 | 无 |
