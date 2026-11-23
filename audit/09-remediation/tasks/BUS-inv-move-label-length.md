# BUS-092 商品候选批量通过报系统错误

## 结论 / 下一步

已修复并有回归测试：库存流水说明字段放宽到 128 字，过账时超长说明截断兜底。下一步：走 hotfix 上线后，在生产对商品 11028、11033、11034、11095、10925、10929 重新批量通过验收。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | 烛龙开源 · Claude Code |
| 关联 ID / 原专题 | BUS-092 |
| 目标与影响范围 | 商品候选审批通过且并入已有商品时，若原商品「品牌 / 型号」较长，写「商品合并」库存流水失败，整批回滚并提示系统错误。涉及 `inv_move.biz_label` 与 `InventoryPosting.Doc.label`。 |
| 起点 commit / 工作分支 | develop dcfb0c8c 之后；fix/inv-move-label-length，上线走 hotfix/1.47.1 |
| OpenSpec | 不适用：缺陷修复，不改变业务规则 |
| PR / 目标分支 | 见台账 link |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | v1.47.1 生产 |

## 实施与验证结果

根因：`InventoryMerge` 写流水说明「商品合并 · 品牌 / 型号 并入」，原商品标签最长截到 100 字，而 `inv_move.biz_label` 只有 varchar(32)，MySQL 报 `Data too long for column 'biz_label'`（生产日志 2026-11-23 10:21 起 4 次，均在 `ProductCandidateService.batchApprove`）。

修复：迁移 `V1.5.61__inv_move_label_length.sql` 把字段放宽到 varchar(128)；`InventoryPosting.Doc.label` 超过 128 字时截断。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `InventoryFlowContractTest#productMerge_movesStockAndReservations`（原商品品牌键改长） | 本机测试库，修复前 | 复现 | Data too long for column 'biz_label' | 复现 |
| 同上 + `InventoryFlowContractTest` 全部 17 条 | 本机测试库，修复后 | 合并成功、说明完整 | 通过 | 通过 |
| `InventoryDocLabelTest` | 单元测试 | 超长截断到 128 | 通过 | 通过 |
| 生产只读查询 6 个商品 | 生产 v1.47.0 | 失败已回滚 | 均仍为候选，无 MERGE 流水 | 无需修数据 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-092 | 商品候选批量通过（并入已有商品） | 未部署 | 未验收 | 待上线后重试 6 个商品 |

## 阻塞与下一步

上线 v1.47.1 需用户确认。
