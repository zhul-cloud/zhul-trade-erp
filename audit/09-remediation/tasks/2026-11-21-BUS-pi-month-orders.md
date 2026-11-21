# BUS-090 PI 列表「本月转订单」把手动创建的订单也算进去

## 结论 / 下一步

已修复，待合并：`PiServiceImpl.stats()` 的本月订单查询加「来源为由 PI 转成」（`source = SO_FROM_PI`），手动创建、售后补发、费用订单不再计入「本月转订单」；「凭水单先转、还未到账」用同一份订单列表，口径随之一致。下一步：PR 合入 develop 后随下次发版上线，上线后验收。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Ezio · Claude Code |
| 关联 ID / 原专题 | 见台账；发现于 `openspec/changes/ui-redesign` 任务 7.1 |
| 目标与影响范围 | 形式发票列表「本月转订单」的数字（改造后在汇总行）偏大；「凭水单先转、还未到账」只按有 PI 的订单算，不受影响。只改统计口径，不改数据 |
| 起点 commit / 工作分支 | 发现于 `feat/ui-redesign` `8a804d49`；修复分支 `fix/bus-090-pi-month-orders`，起点 develop `d693ede8` |
| OpenSpec | 口径修正，不改需求，不需要提案 |
| PR / 目标分支 | 见台账 link；目标 develop |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 未定 |

## 实施与验证结果

- 实施：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/PiServiceImpl.java` `stats()` 的 `orders` 查询加 `.eq(SalesOrderDO::getSource, SalesConstants.SO_FROM_PI)`。
- 测试：`FeeOrderContractTest` 建手动订单与费用订单后断言 PI 统计 `monthOrderCount` 为 0；`AfterSalesReplacementContractTest` 由 PI 转单再建补发订单后断言为 1。本机两个测试类 7 个用例通过；撤掉修复后 `FeeOrderContractTest` 失败（期望 0，实际 2），确认测试能发现问题。
- 不改数据，没有迁移脚本。
