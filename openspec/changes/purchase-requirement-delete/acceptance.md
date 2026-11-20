# 采购需求删除 — 验收记录

## 本机（2026-11-20）

- 迁移：本机 Flyway 执行 V1.5.52 成功（`removed_qty`、`remove_reason` 两列，按钮权限 110247 授给有「拆分采购需求」的角色）。
- 走查（walkthrough 账号，租户 1005 管理员）：
  - 采购需求列表操作列出现「删除」；弹窗与原型 R1 一致（需求数量 / 草稿中 / 已下单 / 删除、三个原因、部分取消提示）。
  - 删除 SO20261013001 的 6ES7417-4XT05-0AB0 × 1（仓库有库存：仓库有 1 个现货）：提示「已删除」，需求整条软删除、`removed_qty = 1`。
  - 销售订单详情该型号进度下显示「不需采购 1（仓库有库存：仓库有 1 个现货）」，货物列按「系统外采购」。
- 自动化：
  - 后端 `PurchaseContractTest#removeRequirement_draftWhole_deletesDraftAndCountsAsNotNeeded`（草稿整条删除、草稿自动删除、其他须填说明、无权限 403、删除后订单显示不需采购）、`#removeRequirement_partial_onlyUnordered_progressByNeededQty`（部分下单只删没下单的、状态「已下单」、订单进度按需采购数量变为已下单与已入库、全部下单后不能删）；相关的 `SalesOrderPartialCancelContractTest`、`SalesOrderTrackingContractTest`、`TenantIsolationContractTest` 通过。
  - 前端 `requirements/removeModal.test.ts`（整条删除、部分删除、其他须填说明）、`sales/orders/notNeeded.test.ts`。

## 生产

待发版后验收。
