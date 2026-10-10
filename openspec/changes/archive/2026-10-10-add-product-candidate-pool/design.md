## Context

- 商品主数据（`product`、品牌、品类、系列）是平台共享数据（tenant 0），写入由 `PlatformScopeGuard.requirePlatform()` 限制为平台账号；品牌有别名；已有「待确认品牌」池（来自供应商主营品牌）。
- 询盘型号（`inquiry_item`，租户数据）在确认时已经按商品主数据同一套规则算出 `brand_id`、`brand_key`、`model_key`（`PriceKeys` → `BrandResolver` / `MpnNormalizer`），并有 `product_id` 字段但从未赋值；还有 AI 解析出的 `description`、`description_en`、`category`（自由文本）。
- 销售订单行（`sales_order_item`）记录来源的 `inquiry_item_id`，可以追溯成交。
- 询盘确认入口：`CustomerInquiryServiceImpl.confirm`；回价保存与提交：`MyTaskServiceImpl.saveQuotes / writeQuotes`；销售订单由 PI 转换生成。

## Goals / Non-Goals

**Goals:** 询盘型号自动匹配商品或进入候选池；候选可审核建档、并入、驳回；询盘型号与商品形成关联并统计热度；历史数据补齐。

**Non-Goals:** SEO/GEO 内容方案（第二期）与多平台分发（第三期）；候选自动通过；手动与导入来源的录入界面（只预留来源类型）；用 AI 判断品类或型号真伪。

## Decisions

1. **数据模型**
   - `product_candidate`（平台级，`tenant_id = 0`）：`brand_id`（可空）、`brand_text`、`brand_key`、`mpn_raw`、`mpn_normalized`、`category_id`（建议，可空）、`category_text`、`product_name`、`description`、`description_en`、`status`（1 待审核、2 已建档、3 已并入、4 已驳回）、`level`（1 询盘出现、2 采购问到有货、3 已成交，冗余存最高级便于排序）、`source_count`、`first_seen_at`、`last_seen_at`、`product_id`、`reject_reason`、`reject_note`、`reviewed_by`、`reviewed_at`；唯一键 (`brand_key`, `mpn_normalized`)，与状态无关（驳回后再出现只追加来源）。
   - `product_candidate_source`：`candidate_id`、`tenant_id`（来源公司）、`source_type`（1 询盘、2 采购回填真实型号、3 采购回价有货、4 成交、5 手动、6 导入）、`customer_inquiry_id`、`inquiry_item_id`、`so_id`、`create_time`。可见范围按 `tenant_id` 过滤。
   - `inquiry_item` 新增 `actual_model`、`actual_model_key`、`archive_status`（0 未处理、1 已建档、2 候选中、3 待回填真实型号）。
2. **建档服务 `ProductArchiver`（product 模块，供询盘、回价、订单调用）**：`archive(itemIds)` 逐个型号取「真实型号优先，否则确认型号」→ 按 `brand_key` + 归一化型号查启用且未删除的商品 → 命中则写 `product_id`、状态已建档；未命中且像型号则 upsert 候选并加来源、状态候选中；否则待回填。`upgrade(itemIds, type)` 给候选中的型号追加回价有货或成交来源并提升 `level`。「像型号」规则：含至少一个数字，且不含逗号、中文与三个以上空格分隔的词。
3. **调用点与事务**：询盘确认事务末尾调 `archive`；真实型号走单独接口 `PUT /api/v1/inquiry/my-tasks/{id}/items/{itemId}/actual-model`（离开输入框即保存，不受报价锁定限制），保存后对该型号调 `archive`（先删除该型号在旧候选上的询盘与回填来源；旧候选因此没有来源且仍待审核时软删除）；提交回价写入有货报价后调 `upgrade(…, 回价有货)`；销售订单生成时对订单行的 `inquiry_item_id` 调 `upgrade(…, 成交)`。都在调用方事务内，失败随业务回滚。
4. **审核**：`ProductCandidateService` 校验可见性（平台全部；租户须有来源属于本租户）与权限码 `product:candidate:review`；通过建档调用商品服务的内部创建方法（绕过 `requirePlatform`，只在候选审核路径使用），品牌别名、新品牌、新系列同理走内部方法；创建前再查一次商品库，已有则改为并入。通过 / 并入后批量更新来源中的 `inquiry_item.product_id` 与 `archive_status`，并为后续热度使用。审核行加锁（`SELECT … FOR UPDATE`）防止两人同时审核。
5. **品类建议**：用询盘型号的品类文字按品类中文名、英文名精确匹配，再按包含匹配，取唯一命中；匹配不到留空由审核员选。
6. **热度**：不另存计数，查询时按 `inquiry_item.product_id`（未删除）计数询盘次数、按 `sales_order_item` 关联到商品（经 `inquiry_item.product_id`）的去重订单数计数成交次数；租户按 `tenant_id` 过滤。商品列表增加两列与排序（子查询计数，分页内取）。
7. **历史补齐**：启动时执行一次（`sys_config` 标记 `product.candidate.backfill-done`），对已确认（询价中及之后状态、未删除）的询盘型号按创建时间调 `archive`，并对已成交的调 `upgrade`。幂等：已有来源的不重复追加（按 `inquiry_item_id` + `source_type` 去重）。
8. **菜单**：商品资料下新增「商品候选」`/product/candidates`，按钮 `product:candidate:review`；授予管理员角色与平台套餐。

## Risks / Trade-offs

- [租户账号写入共享商品库] → 只在候选审核路径放开，记录审核人与操作日志，平台可看到全部并事后修改。
- [「像型号」规则误判] → 误判为待回填的可由采购填真实型号；误判进池的可驳回为「不是型号」。
- [确认询盘事务变长] → 每个型号几次索引查询，型号数通常几十以内，可接受。
- [热度实时计数的性能] → 分页内计数、`inquiry_item.product_id` 加索引。
