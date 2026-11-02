## Context

售后单已有 `received_at`（收到客户反馈时间）、`next_update_at`（手填下次更新时间）、`level_code`、`status`，列表按 `next_update_at < now` 标「已过期」，顶部统计也按它算逾期数。系统没有工作日历与站内消息。工作台组件由 `WorkbenchWidget` 提供，按菜单权限显示、按数据范围取数。

**Goals:** 按 SOP 等级时限自动给出截止时间，在列表、详情、工作台提醒。
**Non-Goals:** 时限可配置、节假日、消息推送、达成率统计、内部升级通知。

## Decisions

1. **截止时间落库**：售后单新增 `responded_at`（首次告知客户时间）、`last_contact_at`（最近告知客户时间）、`sla_start_at`（更新间隔的起算时间：告知客户或改回处理中时更新）、`due_at`（当前有效截止时间）、`due_type`（1 首次响应、2 更新客户、3 手填时间）。每次登记、跟进、改等级、改状态、改下次更新时间、结案、撤销时在同一事务内重算并保存，列表筛选与排序、工作台计数都直接查 `due_at`，不在查询时计算。
   - 未首次响应：`等级截止 = 工作时间加(received_at, 首次时限)`；
   - 已响应且「处理中」：`等级截止 = 工作时间加(sla_start_at, 更新间隔)`；S5 没有等级截止；
   - 「待客户确认」：没有等级截止；
   - `due_at = min(等级截止, next_update_at)`，`due_type` 取较早的那一项（相等时取等级截止）；都没有时为空；已结案、已撤销为空。
2. **工作时间计算**：工具类 `WorkHours.add(start, hours)`，周一至周五 9:00–18:00，每天 9 个工作小时；起点不在工作时间内时从下一个工作时间开始；「1 个工作日」= 9 个工作小时（即下一个工作日的同一时刻）。S1 用自然时间。时区为 Asia/Shanghai。
3. **时限表**：常量写在 `AfterSalesConstants`（小时数，S1 自然小时，其余工作小时）：首次 S1 1 / S2 2 / S3 9 / S4 9 / S5 9 / 待确认 2；更新间隔 S1 2 / S2 9 / S3 18 / S4 27 / S5 无 / 待确认 9。待确认按 S2 的时限，用工作时间计算。
4. **已告知客户**：`after_sales_log` 新增 `customer_notified tinyint(1) default 0`；添加跟进记录接口加 `customerNotified`。只有跟进记录（NOTE）能勾。勾选时更新 `last_contact_at`、首次时写 `responded_at`、更新 `sla_start_at`。
5. **快到期**：`due_at - now < 1 小时` 且未过期。由后端返回 `dueState`（OVERDUE / SOON / NORMAL），前端只展示。
6. **存量数据**：迁移中把未结案的售后单 `responded_at = last_contact_at = create_time`、`sla_start_at` 留空（没有起算时间就不按等级计时），`due_at = next_update_at`、`due_type = 3`；即只看手填时间，下次告知客户后进入等级时限。避免上线时所有旧单立刻逾期。
7. **工作台**：业务员「我的待办」加「售后要更新客户」：本人负责的、已逾期或快到期的售后单数，`alert` 为真，点进列表「只看逾期」；业务负责人「待跟进」加「售后逾期」：数据范围内已逾期数，附最久逾期单号。组件菜单清单加售后管理页，没有售后菜单权限不显示该项。
8. **顶部统计「需要更新客户」**改为按 `due_at < now` 计数。

## Risks / Trade-offs

- 不考虑节假日：国庆、春节期间会出现逾期误报，负责人可手填下次更新时间到节后——但手填只会更早生效，不能推迟等级截止。→ 接受；节假日日历另排期。
- 重算散落在多个写操作：统一走 `AfterSalesSla.refresh(a)`，每个写方法保存前调用，并有测试覆盖每个入口。

## Migration Plan

迁移 `V1.4.5__after_sales_sla.sql`（版本以开发时最新为准）：`after_sales` 加 `responded_at`、`last_contact_at`、`sla_start_at`、`due_at`、`due_type` 与 `idx_as_due` 索引；`after_sales_log` 加 `customer_notified`；回填存量。回退：删除新增列与索引（语句写在迁移注释中），旧版本代码不读这些列。
