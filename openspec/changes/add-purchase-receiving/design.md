## Context

- 第①期：采购需求 → 采购单（草稿 / 已下单 / 已取消），订单型号「待采购 ↔ 已下单」由 `OrderPurchaseProgress` 推进。
- 采购单附件已有 `purchase_order_attachment`，存服务器私有目录（`PrivateFileStorage`）。
- 编号服务支持新增类型代码；角色只支持一个主角色（`user_basic.role_code`），`account_role` 表未启用。
- 上传大小配置当前为 100MB。

## Goals / Non-Goals

**Goals:**
- 发货、入库、差异、暂存、拍摄形成闭环，订单型号「已入库」自动推进。
- 仓库只记事实，处理方式归采购员。
- 附件存储可切换到对象存储，不改业务表。

**Non-Goals:**
- 不做暂存货「用到其他订单」、退货运费与折价入成本、应付（第③期）。
- 拍摄任务不管发布（B2B 平台、独立站），以后归运营管理。
- 不支持多仓库、库位，不做库存账。

## Decisions

### 1. 数据模型

```
purchase_order_item 1──N supplier_shipment_item N──1 supplier_shipment (SD)
supplier_shipment 1──0..1 purchase_receipt (GR) 1──N purchase_receipt_item ──1 supplier_shipment_item
purchase_receipt_item 1──N receiving_discrepancy
receiving_discrepancy 0..1──1 stock_hold
purchase_receipt_item 1──0..1 shoot_task ──N media_asset（品牌 + 型号）
biz_attachment（owner_type + owner_id）← 发货单、入库单、拍摄任务
```

- **`supplier_shipment`**：
  - 编号与来源：`sd_no`、`po_id`、`source`（1-采购员登记、2-仓库补登）；
  - 物流：`carrier`、`tracking_no`、`ship_date`；
  - 状态：`status`（1-在途、2-已入库、3-已作废）、`void_reason`、`note`。
- **`supplier_shipment_item`**：`shipment_id`、`po_item_id`、`requirement_id`、`so_item_id`、型号快照、`quantity`。
- **`purchase_receipt`**：
  - 编号与关联：`gr_no`、`shipment_id`、`po_id`；
  - 收货：`received_date`、`received_by`；
  - 状态：`status`（1-有效、2-已冲销）、`reverse_reason`、`note`。
- **`purchase_receipt_item`**：
  - 关联：`receipt_id`、`shipment_item_id`、`po_item_id`、`requirement_id`、`so_item_id`；
  - 型号快照；
  - 数量：`shipped_qty`（快照）、`received_qty`、`qualified_qty`、`defective_qty`、`note`。
- **`receiving_discrepancy`**：
  - 关联：`receipt_item_id`、`po_id`、`po_item_id`、`purchaser_id`（冗余，数据权限用）；
  - 差异：`type`（1-少发、2-不良、3-多发）、`quantity`；
  - 处理：`status`（1-待处理、2-已处理）、`resolution`（1-等补发、2-不补了、3-退货换货、4-退货不补、5-折价接收、6-退回、7-暂存）；
  - 金额与退货：`discount_amount`、`return_carrier`、`return_tracking_no`、`return_freight`；
  - 其他：`free_of_charge`（多发白送）、`handled_by`、`handled_at`、`note`。
- **`stock_hold`**：
  - 型号与数量：型号、品牌、品类、`quantity`、`cost_price`；
  - 来源：`discrepancy_id`、`receipt_id`、`po_id`；
  - 状态与处置：`location_note`、`status`（1-暂存中、2-已退回、3-已报废、4-已转样品）、处置信息。
- **`shoot_task`**：
  - 关联：`receipt_item_id`、`so_id`；
  - 型号与素材键：型号、品牌、品类、`asset_key`（品牌 + 型号，小写去空格）；
  - 状态：`status`（1-待拍摄、2-已完成、3-已跳过）、`skip_reason`、`completed_at`；
  - 其他：`reused_from_task_id`、`shooter_id`。
  - 不记录发布：发布到 B2B 平台、独立站以后归运营管理，读取 `media_asset` 即可。
- **`media_asset`**：
  - 素材：`asset_key`、品牌、型号、`attachment_id`、`media_type`（1-拆箱视频、2-验货视频、3-实物图）、`task_id`；
  - 同型号素材按 `asset_key` 查询，供复用与第⑤期素材库。
- **`biz_attachment`**：
  - 归属：`owner_type`（SHIPMENT / RECEIPT / SHOOT）、`owner_id`；
  - 文件：`kind`（1-图片、2-视频）、`storage`（LOCAL / OSS）、`file_key`、`url`（OSS 时保存链接，本地为空）、`file_name`、`file_size`、`content_type`；
  - 其他：`uploaded_by`、`deleted_at`。
- **采购单行的已发、已入库、合格数量**：和第①期需求数量一样不落库，按发货单行、入库单行实时汇总（`GROUP BY po_item_id`、`so_item_id`）。
- **字段规范**：全部带 `tenant_id`、审计字段、`deleted_at`（业务表），外键列加索引，不建数据库外键。

### 2. 数量口径

- **未发数量** = 订购数量 − 有效发货单（在途 + 已入库）的发货数量 − 「退货换货」的不良数量（这部分重新计为未发）。
- **合格数量**（订单型号）= 有效入库单行的合格数量 + 已处理为「折价接收」的不良数量。
- **「不补了」「退货不补」**：直接把 `purchase_order_item.quantity` 减去差异数量，然后重算采购单合计、砍价，并刷新需求更新时间。这部分数量自然回到需求的可下单数量，也就回到了需求池。
- **多发**：只计入入库单行的实收与差异，不增加订单型号的合格数量。

### 3. 订单进度联动

- 扩展 `OrderPurchaseProgress.sync`：对有采购需求、当前进度在「待采购 / 已下单 / 已入库」之一的型号，按「合格数 ≥ 型号数 → RECEIVED；否则已下单数 ≥ 型号数 → ORDERED；否则 PENDING_PURCHASE」推进；字典里对应步骤被停用时跳过该步。
- **调用点**：确认入库、冲销入库、处理差异（折价接收、不补了、退货不补）、以及原有的采购单操作。
- **手动推进校验**：从「下单数量」扩展为「合格入库数量」，「已入库」也加入不可手动选的步骤。

### 4. 附件存储

- **接口**：`AttachmentStore` 提供 `put(module, tenant, file) → StoredRef(storage, key, url)` 与 `open(ref) → Resource`，本期实现 `LocalAttachmentStore`（复用 `PrivateFileStorage`）；以后加 `OssAttachmentStore`，用配置 `zhul.attachment.storage=oss` 切换新上传的存储。
- **读取**：按记录上的 `storage` 选实现，已有本地文件不需要迁移。
- **格式识别**：`PrivateFileStorage.detect` 增加：
  - WEBP（`RIFF....WEBP`）；
  - MP4 / MOV（偏移 4 处为 `ftyp`，按 brand 区分 `qt  ` 为 MOV）。
- **大小**：图片 10MB、视频 200MB 在业务层校验；`spring.servlet.multipart.max-file-size / max-request-size` 调到 210MB。
- **上传流程**：先上传文件拿到附件 ID（暂不挂单据，`owner_id = 0`，24 小时内没被挂上的由定时任务软删除），保存单据时传附件 ID 列表挂上。
- **下载**：鉴权后按 `owner_type` 校验菜单；支持 `Range`，返回 206，便于视频拖动。

### 5. 编号、菜单、角色

- **编号**：`DocumentType` 增加 `SD(false)`、`GR(false)`。
- **菜单**：
  - 「采购管理」下「供应商发货」`/purchase/shipments`，排在采购单之后、供应商之前；
  - 新分组「仓库管理」`/warehouse`，排在采购管理之后：入库验收 `/warehouse/receipts`、暂存货 `/warehouse/holds`、拍摄任务 `/warehouse/shoots`。
- **按钮**：
  - `purchase:shipment:create` 登记发货（修改、作废同权限）；
  - `purchase:discrepancy:handle` 处理到货差异；
  - `warehouse:receipt:create` 验收入库（直接收货、冲销同权限）；
  - `warehouse:hold:handle` 处置暂存货；
  - `warehouse:shoot:edit` 拍摄任务编辑。
- **内置角色**（tenant 0，`is_built_in = 1`，数据权限「全部」）：
  - `ROLE_WH`「仓库」（角色代码最长 12 位）：三个仓库菜单与对应按钮；
  - `ROLE_SHOOTER`「拍摄」：拍摄任务与编辑按钮。
- **授权**：采购员角色（有「采购单」菜单的）授予「供应商发货」与登记发货、处理差异；内置租户管理员授予全部。
- **仓库数据权限**：仓库页不按采购员过滤，只看本租户。

### 6. 事务边界

| 动作 | 同一事务 |
|---|---|
| 登记发货 | 发货单、行、附件挂载、采购单日志 |
| 验收入库 | 入库单、行、发货单状态、差异、拍摄任务、订单进度、需求更新时间 |
| 直接收货 | 补发货单（仓库补登）＋验收入库的全部 |
| 冲销入库 | 入库单状态、发货单回在途、差异与拍摄任务作废（校验未处理、未开始）、订单进度 |
| 处理差异 | 差异状态、采购单行数量与合计（不补）、暂存货（暂存）、订单进度、需求更新时间 |

## Risks / Trade-offs

- **本地存视频占空间**：单个 200MB 上限，并预留切换对象存储的实现。上线前确认服务器磁盘余量；切换后新文件不再落本地。
- **数量实时汇总**：采购单、订单详情、需求列表都要聚合发货与入库行。按 `po_item_id`、`so_item_id` 建索引，单次查询按 ID 列表批量 `GROUP BY`。
- **一张发货单只验收一次**：分两次到货的情况，由采购员按实际登记两张发货单；仓库发现少了，走少发差异。
