# UI-101 采购草稿看不到、已在草稿的型号不能一起生成

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户（报告与验收） |
| 关联 ID / 原专题 | UI-101、UI-102（新发现，用户 2026-11-17 生产反馈，查重无既有 ID） |
| 目标与影响范围 | UI-101：采购订单列表默认按下单日期筛最近 3 个月，草稿没有下单日期被全部挡掉。修复后草稿按创建时间落入日期范围。UI-102：采购需求全选后只要有型号已在草稿，「生成采购订单」整体置灰。修复后已在草稿的数量一起生成，挪到所选分组（保留已填单价），原草稿空了自动作废；已全部下单的型号仍不可选 |
| 起点 commit / 工作分支 | `799d4a5f2bbff14eeccab1c1b5b93bff9af64932` / `fix/generate-po-with-drafts` |
| OpenSpec | 不适用：缺陷修复；生成规则沿用「选渠道」已有的草稿挪动方式 |
| PR / 目标分支 | [#362](https://github.com/zhul-cloud/zhul-trade-erp/pull/362) → `develop`；随 `release/1.30.0` → `main` |
| 合并证据 | develop `6da2d54b`；main `1a64282e`（标签 v1.30.0） |
| 目标版本 / 环境 | v1.30.0 / 生产，租户 1000 |

## 实施与验证结果

根因（生产只读核对）：SO20261111003 的 4 条需求在 2026-11-16 被重新排进 3 张草稿（11304–11306，未选供应商、无单号、`order_date` 为空），可生成数量均为 0，按钮置灰；租户 1000 全部 3 张草稿 `order_date` 为空，被采购订单列表默认日期筛选挡掉。

- `PurchaseOrderServiceImpl.page`：日期范围对有下单日期的单按下单日期，对没有的按创建时间。
- `PurchaseRequirementServiceImpl`：`visibleAvailable` 只拦全部下单的需求；`generate` 先把草稿行挪出（同一采购员、同一采购对象的不动），带原单价放进新分组，`settle` 作废空草稿；预览行 `availableQty` = 需求数量 − 已下单，新增 `draftQty`。
- 前端 `purchase/requirements/index.tsx`：去掉置灰条件；生成弹窗标出「含草稿 N」并说明会挪过来。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `mvn -o test -Dtest=PurchaseContractTest`（新增 `generate_movesDraftLinesIntoOneDraft_keepsPrice_listShowsDrafts`，改写原「已排入草稿不能生成」断言） | 本机隔离库 | 两型号进同一草稿、单价保留、原草稿作废、列表默认日期能看到草稿 | 19/19 通过 | 通过 |
| `mvn -o test -Dtest=ReceivingContractTest,SalesOrderDeleteContractTest,SalesOrderTrackingContractTest` | 本机隔离库 | 无回归 | 38/38 通过 | 通过 |
| `npm run lint`、`npx antd lint ./src`、`npx jest src/pages/purchase` | 本机 | 无错误 | 通过（12 例） | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| UI-101 | 采购订单列表默认条件下能看到 3 张草稿 | v1.30.0 生产发版运行 [43997592692](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43997592692) 成功，前后端容器 healthy | 用户 / 2026-11-18 | 生产验收通过，已关闭 |
| UI-102 | SO20261111003 全选 4 个型号可生成，选供应商后进同一张草稿，原 3 张草稿作废 | v1.30.0 生产发版运行 [43997592692](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/43997592692) 成功，前后端容器 healthy | 用户 / 2026-11-18 | 生产验收通过，已关闭 |

## 阻塞与下一步

无。2026-11-18 用户生产验收通过，UI-101、UI-102 已关闭。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-17 / Claude | 用户反馈，生产只读定位根因，实施并本机验证 | 提 PR 合入 develop |
| 2026-11-17 / Claude | #362 合入 develop；台账编号与另一会话的 UI-096/097 重复，改为 UI-101/UI-102 | 随 v1.30.0 发版 |
| 2026-11-17 / Claude | #364 合入 main，v1.30.0 生产发版成功；合回 develop 版本升到 1.31.0 | 用户生产验收后关闭 |
| 2026-11-19 / Claude | 用户 2026-11-18 确认生产验收通过，台账关闭 UI-101、UI-102 | 无 |
