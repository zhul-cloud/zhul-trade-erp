# Design

## Context

见 proposal.md「Why」。与本设计相关的现状：

- `inquiry_item` 有 `original_model`、`confirmed_model`、`actual_model`（≤128）、`actual_model_key`。`actual_model` 目前只由采购在「我的询价」保存（`MyTaskServiceImpl.saveActualModel` / `updateActualModels`），保存后经 `productArchiver.sync` 重新做商品匹配。
- 报价行由 `QuotationServiceImpl` 生成，型号取 `modelOf(src)` = 确认型号，否则取原始型号。PI 行从报价行复制，销售订单行从 PI 行复制，采购需求从销售订单行复制（`RequirementLifecycle.onOrderCreated`），草稿采购订单行从需求复制（`PurchaseDrafts`）。各级都是快照，后续不再回读询盘。
- 描述已经从询盘复制到报价行、PI 行、销售订单行（`description` 中文、`description_en` 英文，均 ≤300）；`purchase_requirement`、`purchase_order_item` 没有描述字段。
- 报价单、PI 有「草稿 / 编辑中的新版本」与「已发送」两种可写性；采购订单有草稿、已下单、已取消。销售订单行的进度来自可配置字典，不适合当作「是否还能改」的判断依据；出库单行（`outbound_order_item.so_item_id`）可以作为客观的「已出库」依据。
- 已有同步事件的先例：`PayableSyncService`、`ListingTaskSync` 用 `@EventListener` 在发布方事务内同步处理。

## Goals / Non-Goals

**Goals:**
- 真实型号只有一个存储位置（`inquiry_item.actual_model`），所有写入口共用一段保存与下游同步逻辑。
- 下游同步与保存真实型号在同一个事务里，要么全部成功，要么全部回滚。
- 「已发出单据的提示」不另外存状态，展示时比较得出，存量单据上线后也能自动出现提示。

**Non-Goals:**
- 不回溯修改上线前已生成的单据。
- 不改价格台账、询价记录、回价按型号匹配的键（仍为确认型号的 `model_key`）。
- 不给手动创建的销售订单行补描述，不给采购订单提供导出。
- 不修改租户的初始单据模版文件。

## Decisions

### D1 单据型号与客户型号的计算放在一处

在询盘模块新增静态工具（与 `InquiryModelSummary` 同目录），提供两个方法：

- `docModel(item)`：真实型号非空取真实型号，否则取确认型号，否则取原始型号。
- `customerModel(item)`：确认型号，否则取原始型号。

报价行生成、出新版本、候选型号展示（`QuoteCandidateServiceImpl`）都改用 `docModel`，替换现有的私有 `modelOf`。

「型号是否不同」统一按 `PriceKeys.model` 归一化后比较，即忽略大小写和空格。

不选「每张单据实时回读询盘」，原因有两个：单据是快照，导出、版本对比和 CI 都依赖快照，实时回读会让已发出的单据悄悄变化；也不符合「已发出不改」的规则。

### D2 客户型号存快照列，「是否显示」在展示时判断

`quotation_item`、`pi_item`、`sales_order_item` 各加一列 `customer_model varchar(128) NOT NULL DEFAULT ''`：

- 生成时写入 `customerModel(item)`。
- PI 行、销售订单行从上一级复制。
- 存量行保持空串。存量行的型号就是确认型号，空串表示和型号相同，所以不需要回填。

页面和 `${item.customerModel}` 在 `customer_model` 非空，且与 `model` 归一化后不同时才输出。

不选「展示时经 `inquiry_item_id` 关联查询」，原因：版本快照、导出、手动行都要单独处理，并且每个列表、导出都要多查一张表。

### D3 下游同步用同步事件，与保存真实型号在同一事务

所有写入口都调用询盘模块的同一个服务方法 `ActualModelService.save(itemId, value, operator)`：
- 采购：`MyTaskServiceImpl` 的两处现有保存。
- 业务员：询盘详情的新接口、报价单草稿的新接口。

该方法在一个 `@Transactional` 方法内依次执行：
1. 校验询盘未取消。
2. 写 `actual_model`、`actual_model_key`、`actual_model_by`、`actual_model_at`。
3. 调 `productArchiver.sync`，沿用现有逻辑。
4. 发布 `InquiryItemModelChangedEvent(tenantId, inquiryItemId, docModel, customerModel)`。

报价、销售、采购模块各自用 `@EventListener`（同步，不用 `@TransactionalEventListener(AFTER_COMMIT)`）处理，所以监听方的异常会回滚整个保存。

| 监听方 | 更新的范围 | 不更新的范围 |
| --- | --- | --- |
| 报价 | 报价单状态为草稿的行，以及编辑中新版本的行（`is_current` 之外的未发送版本） | 已发送的当前有效版本、历史版本 |
| PI | 草稿 PI 的行、编辑中新版本的行 | 已发送、已转订单、已作废、已关闭 |
| 销售订单 | `inquiry_item_id` 匹配、`quantity > 0`、没有未作废出库单行的订单行 | 已出库或已全部取消的行 |
| 采购 | 上一步实际改了的销售订单行，对应的采购需求（`REQ_ORDER_CANCELLED` 除外）和草稿采购订单行，并写采购订单日志 | 已下单的采购订单行只写日志、不改型号；已取消的采购订单不处理 |

销售订单到采购这一段，不另发事件，由销售模块调用采购模块现有的 `RequirementLifecycle`（新增方法 `onSoItemModelChanged(soItemIds, newModel)`）。原因是销售订单生成采购需求时已经是直接调用，保持同一个依赖方向。

为什么用事件，而不是询盘模块直接调用：询盘是上游模块，直接依赖报价、销售、采购会形成循环依赖；同步事件能保持依赖方向不变，同时仍在同一事务内。

**事务边界**：一次保存真实型号是一个事务，覆盖询盘型号、商品匹配、报价行、PI 行、销售订单行、采购需求、草稿采购订单行和采购订单日志。不涉及金额与汇率，不碰财务凭证。

**并发**：两人同时保存同一个询盘型号时，后提交的覆盖先提交的，这是规范允许的；下游更新按行 `UPDATE ... WHERE id IN (...)`，在同一事务内完成。

**多租户**：
- 所有入口都是登录请求，有租户上下文，MyBatis 拦截器自动带租户条件。
- 事件里带 `tenantId` 只是为了日志可读。
- 不在没有登录上下文的线程里处理事件，所以不需要 `TenantContext.runAs`。

### D4 已发出单据的提示：展示时比较得出

报价单详情、PI 详情、采购订单详情在组装行数据时，把该行的 `model` 与来源当前的型号做比较：
- 报价单、PI：来源是询盘型号的 `docModel`。
- 采购订单：来源是采购需求的 `model`。

两者不同时返回 `modelUpdatedTo`，前端显示提示。只对规范列出的状态返回。

不存「已变更」标记的原因：比较的结果本身就是事实，也能覆盖上线前已经存在的不一致。代价是详情多查一次来源行，查询按 id 批量进行，量很小。

### D5 描述带到采购侧

- `purchase_requirement`、`purchase_order_item` 各加一列 `description varchar(300) NOT NULL DEFAULT '' COMMENT '描述（中文，来自销售订单型号行）'`。
- 需求生成时复制 `sales_order_item.description`。
- 草稿行、需求池生成采购订单行时复制需求的描述。
- 历史数据导入（`OrderImporter`）写空串。
- 迁移用 `UPDATE ... JOIN sales_order_item ON so_item_id` 回填存量数据，同一租户内按 `so_item_id` 关联。
- 采购需求关键字筛选加上 `description LIKE`。

### D6 业务员入口与权限

| 入口 | 接口 | 权限 |
| --- | --- | --- |
| 询盘详情 | `PUT /api/v1/inquiry/customer-inquiries/{id}/items/{itemId}/actual-model` | 与「修改询盘」相同：负责人或有数据权限的人 |
| 报价单草稿 | `PUT /api/v1/quotations/{id}/items/{itemId}/actual-model` | 与编辑报价单相同；报价单不是草稿或编辑中版本时拒绝 |

两个接口都调用 D3 的同一个服务。返回值是更新后的行，前端直接替换显示，不用重新加载整张单据。

### D7 页面

- 共用的 `DocModelCell` 增加两个可选属性：`customerModel`，以及 `modelUpdatedTo`（配合提示语气：报价单、PI 用普通提示，采购订单标红）。
- 销售订单、采购需求、采购订单的明细加「描述」列。
- 同时更新 `.claude/context/ui-design-patterns.md` 的「单据页统一规范」，加上「描述列紧跟型号列」和「客户型号」两条。
- 开发前先用 OpenPencil 出原型，覆盖以下页面，经用户确认后再开发：
  - 询盘详情：真实型号输入和最后修改人。
  - 报价单草稿：真实型号入口。
  - 报价单、PI 详情：客户型号和更新提示。
  - 销售订单详情：描述列和客户型号。
  - 采购需求两个视图：描述列。
  - 采购订单详情：描述列和标红提示。

## Risks / Trade-offs

- [已出库后才发现型号错了] → 销售订单行不再修改，交给售后处理，与现在一致；已出库的事实优先。
- [已下单的采购订单还是旧型号，采购没注意到提示] → 详情标红，并写操作日志。本期不发通知，上线后看使用情况再决定要不要加站内通知。
- [真实型号被填错，下游草稿跟着错] → 改回或清空真实型号会再同步一次。修改人和时间有记录，可以追责。
- [一个询盘型号被多张报价单、订单引用，一次保存更新很多行] → 实际引用数量小（同一型号一般 1～3 张单据）；全部按 id 批量更新，事务时间可以接受。
- [客户型号的显示依赖归一化比较] → 与价格台账使用同一个 `PriceKeys.model`，行为一致。

## Migration Plan

1. 迁移（版本号开发时取当时最新的下一个，预计 V1.5.45）：
   - `inquiry_item` 加 `actual_model_by varchar(32) NOT NULL DEFAULT ''`、`actual_model_at datetime NULL`。
   - 三张单据行表加 `customer_model`。
   - 两张采购表加 `description`，并回填存量数据。
   - 文件末尾写明回退 SQL：删除新增的列。回填的数据随列一起删除，不影响原有数据。
2. 没有新增配置项。
3. 回滚：
   - 应用回滚到上一版即可，旧代码不读取新列。
   - 需要连同数据库一起回退时，执行文件末尾的回退 SQL，再删除 `flyway_schema_history` 中该版本的记录。
