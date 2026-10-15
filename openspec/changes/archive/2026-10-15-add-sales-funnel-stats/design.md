## Context

| 指标 | 表与字段 |
|---|---|
| 商机 | `opportunity`（`create_time`、`owner_id`） |
| 询盘单 | `customer_inquiry`（`inquiry_date`、`customer_type` 1 新 2 老、`owner_id`、`status`，10 为已取消） |
| 型号回价状态 | `inquiry_item.quote_status`（1 待回价、2 有价、3 无货） |
| 询价任务分配 | `sourcing_task`、`sourcing_task_assignee`，与 `inquiry_item.sourcing_task_id` 关联 |
| 报价单 | `quotation`（`sent_at`、`owner_id`、`customer_id`、`total_amount_cny`） |
| PI | `proforma_invoice`（`sent_at`、`receipt_status` 4 为已到账、`status`、`closed_at`、`total_amount_cny`） |
| 销售订单 | `sales_order`（`sales_date`、`customer_type`、`owner_id`、`status`） |

收入与毛利由 `OrderCostService` 计算，利润统计也用它。数据权限的写法沿用 `SalesOrderServiceImpl.scoped()`，即 `DataScopeResolver`。

原报表的比率我用历史数据验算过：
- 2025 年 5 月：询盘 173 ÷ 商机 115 = 150.43%；订单 11 ÷ 询盘 173 = 6.36%；老客户订单 4 ÷ 11 = 36.36%；老成交客户 3 ÷ 10 = 30%。
- 2026 年 1 月：询价完成 219 ÷ 232 = 94.40%。

## Goals / Non-Goals

**Goals：**
- 一页看到漏斗、转化率和复购率，口径与原报表一致。
- 收入与毛利与利润统计完全一致，不另算一套。

**Non-Goals：**
- 不做导出；
- 不做同比、环比；
- 不做目标与达成率；
- 不改「商机统计」页（商机阶段分析，继续隐藏在侧边栏外）。

## Decisions

1. **各指标按各自单据的日期归入期间。** 和原报表一样：商机按创建日期，询盘按询盘日期，报价与 PI 按发送日期，订单按销售日期。所以一个月的比率是同期数量相除，不是同一批客户的转化率。页面上注明这一点。

2. **新老客户。**
   - 询盘单和订单用单据上已有的标记；
   - 报价单和 PI 没有存标记：发送日期之前，该客户已有有效销售订单的算老客户，在内存里按客户最早的订单日期判断；
   - 成交客户：在期间内，按客户首张订单的标记计。

3. **一次查完，内存里汇总。** `SalesStatsService.stats(query)`：
   - 按日期范围和数据权限，各表各查一次；
   - 订单交给 `OrderCostService.of(...)` 算收入和毛利；
   - 再按月份、业务员或客户分组。
   - 每年几百单、几千张询盘，数据量没有问题。

4. **数据权限。** 用户只能看本人销售订单时，五类单据都只取 `owner_id = 本人`；能看全部的，再按页面上的业务员筛选。复用 `DataScopeResolver` 判断。

5. **询价统计的两种口径。**
   - 按月份按询盘单统计，和原报表对齐。一张询盘单的型号里有任何待回价的，算「询价中」；没有待回价、且至少一个有价，算「询价完成」；全部无货，算「无法报价」。只统计已经进入询价（状态大于等于 5）、未取消的询盘单。
   - 按采购员按型号统计，因为一张询盘单会拆成多个询价任务、分给不同的人。

6. **菜单与授权。**
   - 菜单 100113「销售统计」`/sales/stats`，放在销售管理最后；
   - 菜单 100114「询价统计」`/inquiry/sourcing-stats`，放在采购管理「砍价统计」之前；
   - 有销售订单菜单（100079）的角色得到销售统计，有询价分配菜单（100054）的角色得到询价统计，ROLE_ADMIN 都授。

## Risks / Trade-offs

- **不是同批转化率**：同月数量相除，跨月成交的订单会让单月转化率波动，这和原报表一致。页面注明「按各自日期统计」。
- **业务员能看到自己订单的毛利**：订单详情本来就对业务员显示毛利，销售统计沿用同样的权限，没有新增暴露。
