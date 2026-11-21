# BUS-090 PI 列表「本月转订单」把手动创建的订单也算进去

## 结论 / 下一步

未修复。界面改造（`feat/ui-redesign`，任务 7.1）迁移形式发票列表时读代码发现：`PiServiceImpl.stats()` 的 `monthOrderCount` 统计本月创建的全部有效销售订单，没有限定由 PI 转成（`pi_id` 非空），手动创建、售后补发、费用订单都被算进「本月转订单」。下一步：从 develop 拉 `fix/*` 分支，查询加 `pi_id IS NOT NULL`（售后补发、费用订单的 PI 情况一并核对），补单元测试。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Ezio · Claude Code |
| 关联 ID / 原专题 | 见台账；发现于 `openspec/changes/ui-redesign` 任务 7.1 |
| 目标与影响范围 | 形式发票列表「本月转订单」的数字（改造后在汇总行）偏大；「凭水单先转、还未到账」只按有 PI 的订单算，不受影响。只改统计口径，不改数据 |
| 起点 commit / 工作分支 | 发现于 `feat/ui-redesign` `8a804d49`；修复分支未创建 |
| OpenSpec | 口径修正，不改需求，不需要提案 |
| PR / 目标分支 | 未创建 |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 未定 |

## 实施与验证结果

未实施。代码位置：`zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/PiServiceImpl.java` 的 `stats()`，`orders` 查询条件。
