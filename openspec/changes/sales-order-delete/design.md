## Context

- `sales_order` 已有 `deleted_at`，订单相关的 MyBatis 查询大多已按 `deleted_at IS NULL` 过滤（约 31 处）；手写 SQL 中 `AfterSalesService`、`CustomerRefundService` 未过滤，但有售后、退款的订单本身不能删。
- 取消订单（`SalesOrderServiceImpl.cancel`）已处理了删除需要的关联数据：PI 回到「已发送」并刷新应收合计、报价成交状态重算、采购需求关闭、余额抵扣释放、商品候选成交来源同步、跟进钩子。
- 按钮权限沿用 `resource` 类型 3 + 套餐 `menu_ids` + 内置角色授权的做法（参考 V1.5.17 `sales:order:fee-edit`）。

## Goals / Non-Goals

**Goals:**
- 一个预检接口给出删除档位和单据清单，前端和删除接口用同一套判断。
- 进行中订单删除时复用取消订单的关联处理，不另写一套。

**Non-Goals:**
- 不提供恢复已删除订单的界面。
- 不级联删除 PI、报价单、询盘、采购需求记录（只改状态）。
- 列表不加批量删除。

## Decisions

1. **预检与删除共用一个检查方法。** `deleteCheck(id)` 返回 `{level: DIRECT | CONFIRM | BLOCKED, blockers[], related[]}`，每项含单据类型、单号、可跳转的 id。`delete(id, reason)` 在锁住订单后重新调用同一方法，档位为 BLOCKED 时报错，避免预检后数据变化导致误删。
2. **挡住删除的条件**（任一即 BLOCKED）：
   - 订单到账金额大于 0，或 PI 订单的 PI 已有到账；
   - 采购订单行（`purchase_order_item.so_id`）所在采购单不是草稿且未取消；
   - 有未删除的销售出库单（`outbound_order.so_id`），含已出运；
   - 有售后单、销售退货、退款申请或客户退款记录；订单本身由售后单生成（`after_sales_id` 非空）；
   - 有生效的余额抵扣（付款方式为余额抵扣的收款记录，随收款记录一起检查）；有暂存中的货物（`stock_hold`）；
   - 有费用分摊（`fin_expense_allocation`）；
   - 有以此订单为原订单的费用订单（`original_so_id`）；
   - 订单已完成或为迁移订单。
   
   备选方案是直接复用取消的校验（只拦仓库中的出库单），但取消是保留单据的操作，删除会让下游单据失去来源，因此条件更严。
3. **需确认的关联单据**：PI（PI 订单）、未下单的采购需求、草稿采购单上的行。草稿采购单上的行在删除时一起删掉（软删除），采购单若因此为空保持草稿由采购员处理。
4. **进行中订单按取消处理关联再删。** 抽出 `cancel` 的关联处理为私有方法 `releaseLinks(order, pi)`，取消和删除都调用。删除时写 `deleted_at`、`deleted_by`、`delete_reason`，同时把状态改为已取消（不写取消原因、取消人），这样只按状态筛有效订单、没筛 `deleted_at` 的查询也不会再算它。已取消订单只写删除字段。
5. **事务边界**：删除在一个 `@Transactional` 内完成：锁 PI → 锁订单 → 重新检查 → 释放关联 → 写删除字段 → 操作日志。加锁顺序与取消一致（先 PI 后订单），避免死锁。
6. **订单的各个接口对已删除订单返回 `ORDER_DELETED`「订单已删除」**；删除时挡住会返回 `ORDER_DELETE_BLOCKED`，`detail` 带最新检查结果，前端提示「订单已删除」并由返回条回到原页面。

## Risks / Trade-offs

- [遗漏未过滤 `deleted_at` 的查询，删除的订单仍出现在统计或看板] → 实现时用 `grep` 列出所有引用 `sales_order` 的手写 SQL 与 Mapper XML，逐个核对，并补一条看板/统计的集成测试。
- [预检到提交之间有人登记到账] → 删除时在锁内重新检查，档位变 BLOCKED 时拒绝并返回最新清单。
- [售后分支未合并，同样使用 V1.5.25 和资源号] → 本变更用 V1.5.27 与资源 110234；售后分支合并前改号。

## Migration Plan

- 迁移 V1.5.27：`sales_order` 加 `deleted_by`、`delete_reason`；插入资源 110234；追加到含 100079 的套餐；授给内置租户管理员和名称含「总经理」的角色。
- 回滚脚本 `sql/build/rollback/V1.5.27__sales_order_delete_rollback.sql`：删资源与授权、从套餐移除、删两列。已删除的订单回滚后仍保持 `deleted_at`，不自动恢复。
