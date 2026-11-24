# BUS-093 询盘生成报价单报系统错误（毛利率超出字段范围）

## 结论 / 下一步

已修复并有单元测试：合计毛利率只算已定价的型号；单行与合计毛利率低于 −999.99% 时按 −999.99% 存（报价、PI、订单三处）。下一步：走 hotfix v1.47.2 上线后，用户在生产对 IQ20261123006 重新生成报价验收。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | 烛龙开源 · Claude Code |
| 关联 ID / 原专题 | BUS-093 |
| 目标与影响范围 | 询盘生成报价单时，有采购成本但策略没给建议毛利率的型号售价为 0，成本却计入合计毛利率，毛利率低于 −999.99%，`quotation.margin_rate`（decimal(5,2)）写入失败。同类风险：手工售价远低于成本时，报价 / PI / 订单的单行与合计毛利率同样会超范围。 |
| 起点 commit / 工作分支 | main aa8d8301（v1.47.1）；hotfix/1.47.2 |
| OpenSpec | 不适用：缺陷修复，合计毛利率口径与「售价为 0 时毛利率为空」保持一致 |
| PR / 目标分支 | 见台账 link |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | v1.47.2 生产 |

## 实施与验证结果

根因：生产日志 2026-11-23 16:52–17:07 共 15 次 `Out of range value for column 'margin_rate'`，均在 `QuotationServiceImpl.create`。IQ20261123006 的回价货况为 0（未填），租户策略对货况 0 没有毛利率，只有成本 ≤ 300 的 3 个型号按金额档定价，其余约 8 万元成本的型号售价 0；`QuotationCalculator.applyTotals` 把所有有成本型号的成本计入，合计毛利率约 −13000%。

修复：`QuotationCalculator.applyTotals` 只把售价大于 0 的型号计入合计毛利率；新增 `QuotationPricing.storableMargin`，单行（`calculate`）、报价合计、PI 合计（`PiCalculator`）、订单合计（`OrderTotals`）存储前低于 −999.99% 按 −999.99%。净利润口径不变。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `QuotationPricingTest#totals_marginIgnoresUnpricedLines` | 单元测试 | 合计毛利率约 50% | 通过（修复前按算法为约 −36200%） | 通过 |
| `QuotationPricingTest#margin_farBelowCost_clampedToColumnRange` | 单元测试 | 按 −999.99% 存 | 通过 | 通过 |
| QuotationPricingTest、PiCalculatorTest、OrderTotalsTest、QuotationContractTest | 本机测试库 | 全部通过 | 18 + 4 + 4 + 23 通过 | 通过 |
| 生产只读查询询盘、回价、定价策略 | 生产 v1.47.1 | 确认根因 | 与推算一致 | 无需修数据（报价单未创建成功，事务已回滚） |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-093 | IQ20261123006 生成报价单 | 未部署 | 未验收 | 待上线后重试 |

## 阻塞与下一步

上线 v1.47.2 需用户确认。
