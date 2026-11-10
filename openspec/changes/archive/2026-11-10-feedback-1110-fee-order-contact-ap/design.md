## Context

- **销售订单来源**：`sales_order.source` 现有三种取值：1-PI 转成、2-手动创建、3-售后补发。售后补发已经用 `original_so_id` 关联原订单。
- **订单进度**：由型号行推出（`OrderProgress`），客户跟进的履约看板（`OrderFollowupSync`、`FulfilDeriver`）也按型号进度推导。
- **收款**：手动订单的收款状态由 `piSummary.refreshOrder` 重算。
- **未认领到账**：存放在 `payment_receipt`（kind=到账，pi_id=0），表里已有 `file_keys`（水单附件，最多 5 个）。
- **应付已付金额**：由 `PayableSyncService` 按 `fin_payment_allocation` 重算。只改状态，下一次重算就会变回未付。
- **生产只读核对（2026-11-10）**：历史迁移、下单日期早于 11-01、仍未付的采购单应付共 609 张，全部人民币，合计 ¥4,564,623.02，已付 0，没有核销，各有 1 期付款计划。

## Goals / Non-Goals

**Goals**
- 费用订单复用销售订单的编号、收款、操作日志和权限，不另建单据类型。
- 联系人显示不新增字段，每页一次批量查询。
- 历史应付的处理可审查、可回退、重算不反弹。

**Non-Goals**
- 不生成给客户的运费账单 PDF。费用订单不出 PI，需要单据时沿用现有做法。
- 单据上不记录「哪位联系人」。
- 不补历史付款记录。

## Decisions

### 1. 费用订单：`source = 4`，复用 `original_so_id`

- **建单**：新接口 `POST /sales/orders/{id}/fee-orders`，权限沿用「手动创建订单」。在同一事务内锁原订单，校验原订单状态（有效、非补发、非费用订单、非迁移），然后插入订单和费用行；不调用采购需求生成。
- **进度**：
  - 建单时 `progress_code = PENDING_PURCHASE`，库里仍用内置首步，避免字典和统计出现空值。前端在来源为 4 时把它显示为「待收款」。
  - 收款重算（`refreshOrder`）后，来源为 4 且已到账时置为 `COMPLETED` 并记完成时间；到账回退时恢复为 `PENDING_PURCHASE`、清空完成时间。
  - 订单列表按进度筛选「待采购」时排除来源 4；按来源筛选可以单独看费用订单。
- **排除点**：
  - 采购需求生成；
  - 销售出库、出运的候选订单；
  - 「客户已收货」与手动推进进度；
  - 部分取消；
  - 履约跟进同步（`OrderFollowupSync` 跳过来源 4，否则没有型号的订单会被当成「待采购」）；
  - 工作台里「待采购订单」类统计。
- **备选方案**：在原订单上放开「已完成也能改费用」。没有采用，因为已完成订单的结算状态和利润已经定了，改它会让已结算的数据回退。

### 2. 收入口径

费用订单走 `OrderCostService` 的订单收入逻辑。没有型号行时跳过行分摊，实际成本为 0。结算条件只检查收款和结汇，型号入库、外币货款、运费账单三项天然满足。利润统计不需要改代码，按订单计入即可，只在明细里显示来源标识。

### 3. 主联系人

- 主联系人姓名已由 `PrimaryContactSync` 同步到 `customer.contact_name`，各列表组装 VO 时已经批量查了客户，直接取这个字段（`InquiryLookups.contactName`），不另加查询。
- 各列表 VO 增加 `customerContact`。
- 前端 `CustomerCell` 增加可选的 `contact`，第二行显示「国家 · 联系人」。没有用 `CustomerCell` 的列表（商机、询盘、售后单、收款单、退款申请、客户余额）在各自的客户列里加同样的第二行。
- **不显示的列表**：采购需求、询价看板、利润统计、销售统计、结汇、货代账单、出运单、退货、出库。这些是内部作业或统计口径，联系人对操作的人没有帮助。如果用户另有要求，再加。

### 4. 未认领到账附件

- 先登记到账，再用 `POST /finance/receipts/unclaimed/{id}/files` 上传附件（multipart，存财务目录），写入已有的 `file_keys`；只能给有效、未认领的到账补附件，合计最多 5 个。
- 列表与认领候选返回附件名 `fileNames`；预览走私有文件签名地址：财务用 `GET /finance/receipts/{id}/files/{index}/url`，业务员认领时用 `GET /sales/pis|orders/{id}/claimable-receipts/{receiptId}/files/{index}/url`（只能看未认领、同币种的到账）。
- 认领时附件随记录保留，不复制。

### 5. 应付排序

`PayableQueryService` 的默认排序改为 `status ASC, update_time DESC, id DESC`：状态值 1-未付、2-部分付款在前，已付清、已关闭在后。用户选了按到期日排序时，仍按原规则。

### 6. 历史迁移视为已付

- **迁移 `V1.5.23`**：
  - `fin_payable` 新增 `settled_by_migration tinyint(2) NOT NULL DEFAULT 0`（0-否、1-是）。
  - 一次性更新符合条件的应付单：采购单来源、`purchase_order.migrated = 1`、`order_date < '2026-11-01'`、状态未付或部分付款、没有有效核销。更新内容：
    - 标记置 1；
    - `paid_amount = amount`，`paid_cny = amount_cny`；
    - `credit_amount = 0`；
    - `status = 3`；
    - `next_due_date = NULL`。
  - 同时把这些应付单的付款计划各期 `paid_amount = amount`、`status = 3`。
  - `update_by = 'migration'`。
- **重算**：`PayableSyncService` 重算时，如果应付单带标记，就跳过按核销计算，直接按上面的规则赋值，状态为已付清。来源采购单取消时仍按现有规则关闭（迁移订单受 `MigratedGuard` 保护，实际不会取消）。
- **登记付款**：未付为 0，付款选单里自然不出现，不需要额外拦截。
- **成本口径核对**：`OrderCostService` 读取了应付的 `paid_cny`。这批应付都是人民币，`paid_cny = amount_cny` 与按应付金额计成本一致。实现时用迁移订单的成本快照（见 `finance/order-cost`「迁移订单按历史核算快照」）回归一遍，确认这些订单的成本不变。
- **回退**（写在迁移注释里）：
  1. 把带标记的应付单的 `paid_amount`、`paid_cny` 恢复为 0，`status` 恢复为 1，付款计划恢复为未付；
  2. 按到期日重新计算 `next_due_date`，或在下次同步时重算；
  3. 删除字段。

### 7. 采购订单单价为 0

- **「没填」和「0」的区分**：`purchase_order_item.unit_price` 允许为空，空表示没填。列表的「未填单价」计数（`missingPriceCount`）只统计空值，不用改。
- **确认下单的校验**：`PurchaseOrderServiceImpl` 第 989 行的条件从 `null || signum() <= 0` 改为只判断 `null`。请求 DTO 已经是 `@DecimalMin(0)`，不用改。
- **前端**：
  - 确认弹窗统计单价为 0 的行数，大于 0 时提示。
  - 砍价列遇到单价为 0 的行显示「单价为 0」。
- **砍价**：单价为 0 的行不计算砍价，后端汇总时排除。
- **应付**：
  - 应付单本身已经满足需求：`PayableSyncService.apply` 在应付金额为 0 时直接置为已付清。
  - 付款计划还需要改：`InstallmentPlanner.allocate` 在已付为 0 时把各期标为未付，金额为 0 的期次也会显示未付。改成「本期金额为 0 时为已付清」。`nextDue` 已经排除了未付金额为 0 的期次，不用改。
- **订单成本**：单价为 0 的行，采购成本按 0 计入，符合样品、赠送的实际情况，口径不用改。
- **前端**：单价输入框本来就允许 0，不用改；付款条件的百分比输入框保持最小 0.01。

### 8. 供应商联系人搜索

`SupplierPageQuery` 新增 `contact` 参数，查询条件是 `(contact_name LIKE ? OR contact_phone LIKE ? OR wechat LIKE ? OR contact_email LIKE ?)`，再和其他条件一起生效（AND）。供应商数量是千级，不加索引。

## Risks / Trade-offs

- **费用订单出现在按进度统计的地方** → 实现时把 `PROGRESS_PENDING` 和订单状态统计的使用点逐个检查（`OrderProgress`、`OrderFollowupSync`、`FulfilDeriver`、`ReplacementOrders`、`OrderPurchaseProgress`、工作台数据块），并为每个排除点写一条用例。
- **主联系人是实时读取的** → 换主联系人后历史单据显示也会变，这一点已和用户确认。
- **迁移按下单日期划线** → 下单日期为空的迁移单为 0 张（已核对），所以不需要回退到按创建时间判断。
- **标记以后不能撤销单张** → 第一期不提供界面操作；如需撤销，按回退 SQL 处理指定 ID。

## Migration Plan

1. 发版前，在本机脱敏副本上执行 `V1.5.23`，核对受影响行数为 609、未付合计变为 0、迁移订单成本不变。
2. 在 PR 说明里写明迁移版本、影响行数和回退 SQL。
3. 上线后只读核对生产：带标记的应付单数、状态分布、应付页统计。

## Open Questions

- 联系人显示的列表范围以第 3 节为准，原型确认时如需增减，在原型里调整。
