## Context

- `POST /api/v1/quotations` 接收 `CreateQuotationRequest`：`inquiryIds`（合并成一张）或 `itemIds`（挑选型号），`QuotationServiceImpl.create` 一次生成一张。
- 候选询盘接口 `/candidates/inquiries` 已按状态分组、分页 20 个，已返回是否有草稿。
- 前端 `NewQuotationModal.tsx` 的按询盘模式已有「同一客户」限制、勾选、生成。

## Goals / Non-Goals

**Goals:** 一次操作生成多张报价单，每张与单独生成时完全一致。

**Non-Goals:** 批量发送、批量导出、批量调整定价；跨客户批量（一次只处理一个客户，沿用现有限制）。

## Decisions

### D1 新接口 `POST /api/v1/quotations/batch`
请求 `{inquiryIds: [..]}`（1–20 个，同一客户），响应 `{created: [{quotationId, quotationNo, inquiryNo, itemCount, totalAmount, currencyCode, pendingCount}], skipped: [{inquiryId, inquiryNo, reason}]}`。不改原接口，避免影响「合并成一张」和「去报价」。

### D2 复用单张生成
批量对每个询盘调用一次现有的 `create`（同一个 bean 内自调用，加入批量的事务）；`create` 的业务校验都在写入之前，校验失败（`BizException` 的已报价、已有草稿、不在数据范围）收集到 `skipped`，其他异常照常抛出回滚。整个批量在 `create` 所在服务的一个 `@Transactional` 内，编号由编号服务依次分配。

**事务边界**：一个批量 = 一个事务。生成报价单、询盘状态同步（`InquiryStatusSync`）都在这个事务内；跳过的询盘不产生任何写入。全部跳过时抛出业务异常，前端显示原因。

### D3 「全选该客户可报价询盘」
新接口 `GET /api/v1/quotations/candidates/ready-inquiries?customerId=` 返回该客户数据范围内可报价、没有草稿的询盘卡片（不分页，上限 200），前端合并进勾选集合，不需要先滚动加载；返回卡片而不是 id，是为了底部摘要能算出型号数。

### D4 前端
- 勾选 ≥2 个询盘时底部出现分段选择「每个询盘一张 / 合并成一张」，默认前者；勾选 1 个时不显示。
- 「每个询盘一张」调批量接口，完成后抽屉内切到结果清单（不关闭），清单可点击进入编辑，底部「完成」关闭并刷新列表。
- 「合并成一张」保持原逻辑（生成后直接进入编辑页）。

## Risks / Trade-offs

- 一次 20 张、每张最多几十个型号，单事务时间可接受；超过 20 个让用户分批，避免长事务。
- 金额：合计沿用报价单现有计算（DECIMAL，HALF_UP 两位小数），批量不引入新的金额计算。
