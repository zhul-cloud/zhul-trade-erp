## Decisions

1. **追加批次单独成表。** `customer_inquiry_append`：`customer_inquiry_id`、`raw_content`、`ai_task_id`、`status`（1-解析中、2-待确认、3-解析失败、4-已确认、5-已放弃）、`parse_mode`（1-AI、2-手动）、`parse_error`、`item_count`。询盘单本身的状态与 `ai_task_id` 不动，避免和首次解析的状态机互相干扰。附件仍放 `customer_inquiry_attachment`，用 `append_id` 区分（0 为录入时的附件）；首次解析只取 `append_id = 0` 的附件。

2. **解析回调分流。** 解析技能不变（`inquiry-parse-and-split`），回调时先按 `ai_task_id` 找询盘单，找不到再找追加批次。追加批次解析成功进入「待确认」，失败进入「解析失败」并记原因。

3. **确认复用首次确认的逻辑。** 把首次确认里「逐行校验、建型号、复用历史价」抽成公共方法，追加时行号从现有最大行号 + 1 开始；询价任务 `createTasks` 增加起始后缀（取该询盘单已有任务数），任务编号不重复。

4. **状态与计数**：型号数、总数量、已回价数、待核实数、任务数累加；有待询价的新型号 → 询价中；否则询价中保持，其余 → 可报价。确认时锁询盘单行（与回价进度同一把锁）。

5. **前端**：确认页路由沿用 `/inquiry/customer-inquiries/:id/confirm`，带 `?append=批次ID` 时取追加草稿、提交到追加确认接口，标题显示「追加型号」，只列这次追加的内容与附件。
