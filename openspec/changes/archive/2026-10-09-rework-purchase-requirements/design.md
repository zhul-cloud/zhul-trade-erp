## Context

第①期 `add-purchase-order` 已上线：采购需求按型号平铺，采购单只能对供应商下，两个列表默认按销售日期 / 创建时间排序。业务反馈三点：采购员要先看订单再看型号；临时合作的线上店铺不该强制建供应商；列表要遵守「列表页统一规范」。

## Goals / Non-Goals

**Goals:**
- 采购员打开页面就知道自己要买什么：按订单分组，默认只看自己的、还没下完的。
- 线上店铺与老供应商一样能直接下采购单，店铺需求也自动出草稿。
- 两个采购列表遵守列表页统一规范。

**Non-Goals:**
- 不改采购单的价格、砍价、付款条件、确认下单与取消规则。
- 不做供应商发货、入库（第②期）。
- 不做店铺维度的报表（第③期采购谈判报表再按「平台 · 店铺」统计）。

## Decisions

### 1. 采购对象 = 供应商或「平台 + 店铺」

- **字段**：`purchase_order` 增加 `channel`（4-供应商，1-淘宝、2-1688、3-闲鱼、5-其他为线上店铺）与 `shop_name`；`supplier_id` 店铺时为 0。
- **存量迁移**：已有采购单 `channel = 4`。
- **合并键**：草稿合并、追加用 `(purchaser_id, supplier_id)` 或 `(purchaser_id, channel, shop_name)`。
- **店铺名比较**：去掉首尾空格后精确比较，不做模糊匹配。
- **代码结构**：
  - 新增值对象 `Counterparty`（供应商 ID 或渠道 + 店铺名），`PurchaseDrafts.Placement` 改为携带它；
  - 自动生成、需求池生成、拆分、改采购员、改到其他供应商、选定渠道全部走同一个 `place`。
- **需求侧**：需求的建议渠道沿用 `suggested_supplier_id / suggested_channel / suggested_shop_name`，渠道已定的条件为「有启用供应商」或「有店铺名」。
- **店铺采购单的默认值**：
  - 付款条件默认 `[{100%, 下单后}]`；
  - 前端不显示合同卡片，后端拒绝给店铺采购单上传合同。
- **备选方案**：店铺自动建成一条「电商店铺」类型的供应商。放弃原因：会让供应商档案混进大量一次性店铺，也违背「不强制建档」的诉求。

### 2. 店铺转为供应商

- **接口**：`POST /purchase/orders/{id}/convert-shop`，只对线上店铺的采购单可用，需要新建供应商的权限。
- **步骤**：
  - 复用 `SupplierService.createFromChannel`（同名已存在时直接用已有供应商）；
  - 同一事务内，把同一 `(tenant, channel, shop_name)` 的草稿与已下单采购单改为 `channel = 4, supplier_id = 新 ID, shop_name = ''`；
  - 把建议该店铺的有效需求改为建议该供应商。
- **合并**：转换后若同一采购员对该供应商有多张草稿，不自动合并，避免改动采购员已填的内容；之后新增的需求按规则追加到最近的一张。

### 3. 按订单分组的查询

- **接口**：`POST /purchase/requirements/orders/page`，参数与按型号视图相同，外加 `mine`。
- **分页**：先查订单这一层：在需求表上按筛选条件 `GROUP BY so_id`，按 `MAX(update_time) DESC` 分页（SQL 只涉及需求表与必要的子查询，不 JOIN 超过 3 张表）。
- **组装**：再按这一页的订单 ID 取需求（同样的筛选），在 Service 层拼订单头（订单、客户、业务员）。
- **订单头汇总**：每个订单的已下单、草稿中、未安排的型号数，在拿到的需求上计算。
- **`mine=true`**：等价于 `purchaserId = 当前用户`。
- **默认范围**：由前端按 `purchase:requirement:assign` 决定（有指派权限默认「全部」，否则「我负责的」）。数据权限仍在后端生效，「全部」不会越权。

### 4. 更新时间维护

- MyBatis-Plus 的更新填充会在每次 `updateById` 时刷新 `update_time / update_by`，需求自身被修改时天然更新。
- 需求的数量状态变化发生在采购单行上（排入草稿、移出、确认下单、取消），由 `RequirementTouch.touch(requirementIds)` 统一刷新这些需求的 `update_time / update_by`（`update_by` 取 `SecurityUtils.getCurrentUsername()`，系统自动操作为 `sys`）。
- 调用点：`PurchaseDrafts.place`、`takeFromDrafts`、删除或移出草稿行、确认下单、取消采购单、订单取消与重转接回。

### 5. 品类

`purchase_requirement` 增加 `category`，生成时取订单型号行的品类，存量从 `sales_order_item` 回填。

### 6. 列表规范

- 按型号视图：默认 `ORDER BY update_time DESC, id DESC`。
- 采购单列表：默认改为 `ORDER BY update_time DESC, id DESC`，表头排序不变。
- VO 补 `createBy / updateBy`，前端四个审计列按规范顺序放在业务列之后、操作列之前，时间用 `formatDateTime`。

### 7. 采购员候选

`GET /purchase/purchasers` 已在第①期之后补上（本变更一起提交）：本租户启用用户的 ID 与姓名，有采购需求、采购单或销售订单任一菜单即可。

## Risks / Trade-offs

- **同名店铺**：两个平台上恰好同名的店铺按平台区分；同一平台内名称相同即视为同一店铺，可能把不同卖家合并。以销定采场景下概率低，而且采购员可以改到其他店铺。
- **转为供应商后草稿不合并**：同一供应商可能并存两张草稿，需要采购员自己挑一张下单。接受这一点，换取不擅自改动草稿内容。
