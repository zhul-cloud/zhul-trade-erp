# BUS-031 剩余型号已报给客户后询价任务卡住（2026-11-09）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证） |
| 关联 ID / 原专题 | BUS-031；来源为 2026-11-09 用户反馈：询价任务 10 个型号，采购报了 8 个，剩下 2 个业务员直接按无货报给客户，任务一直挂着，既不能退回也无法完结 |
| 目标与影响范围 | 询价任务完结判断（`SourcingProgress`）、报价单状态同步（`InquiryStatusSync`）；一次性数据修正迁移 V1.5.19；不改表结构、不改前端 |
| 起点 commit / 工作分支 | `aebe1d31`（origin/develop，v1.10.0 合回后）/ `fix/inquiry-task-quoted-items` |
| OpenSpec | 缺陷修复，不新开提案 |
| PR / 目标分支 | [#205](https://github.com/zhul-cloud/zhul-trade-erp/pull/205)：fix/inquiry-task-quoted-items → develop（未合并） |
| 合并证据 | — |
| 目标版本 / 环境 | 下一个发布版本；生产 |

## 原因

- 型号出现在已发送 / 成交 / 部分成交 / 未成交报价单的当前版本里，就被锁定（`QuotationLocks.lockedItemIds`），采购不能再回价。
- 退回任务只交出未锁定的型号，剩下的全被锁定时提示「没有可以交出的型号」。
- 任务完结要求待询价型号数为 0，被锁定的待询价型号永远不会变成已回价，任务一直停在询价中。

## 实施

- 用户确认的规则：报价单发出时，任务里仍待询价、但已经报给客户的型号算有结果，任务随之完结。
- `SourcingProgress.refreshTask()`：待询价型号中去掉已锁定的，剩余为 0 时完结；已完结的任务又出现未锁定的待询价型号（报价单作废、型号重新开放）时退回询价中、清空完结时间。新增 `refreshTasksOf(inquiryId)`。
- `InquiryStatusSync.sync()`：每次报价单状态变化后刷新该询盘下询价中和已完结的任务。
- 迁移 `V1.5.19__complete_tasks_with_quoted_items.sql`：把已经卡住的任务（询价中、至少一个待询价型号、且待询价型号全部已报出）改为已完结。可重复执行；回退把这些任务改回状态 2 即可，型号与报价单不受影响。
- 型号的报价状态不改（仍为待询价），任务明细里已有的「已锁定」标记照常显示，不需要改界面。

## 验证

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `mvn test -Dtest=QuotationContractTest`（新增 `remainingModelsQuotedAsNoStock_completeTask_voidReopens`：3 个型号回价 1 个、报价单含全部 3 个；草稿时任务仍询价中，发送后完结，退回提示没有可交出的型号，作废后重新回到询价中） | 本机 JDK 17，未提交 | 21 个通过 | 21 个通过 | 通过 |
| `mvn test` 询价相关 7 个类（SourcingContractTest、SalesFirstSourcingContractTest、QuoteReviewContractTest、SourcingQuoteEnhancementsContractTest、SourcingBoardSearchContractTest、SourcingExcelContractTest、InquiryAppendContractTest） | 本机 JDK 17，未提交 | 全部通过 | 45 个全部通过 | 通过 |

未覆盖：迁移 SQL 未在生产数据副本上演练，生产执行后需查卡住的任务是否已完结。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-031 | 剩余型号报给客户后任务自动完结；作废报价单后任务回到询价中；已卡住的任务上线时一次性完结 | 未部署 | — | 待验证 |

## 阻塞与下一步

已创建 PR #205 发往 develop，用户确认后合并，随下次发版上线并在生产验收。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-09 / Claude | 用户反馈确认为缺陷，登记 BUS-031；用户选定「报价单发出时自动算有结果并完结」；实施并通过上述测试 | 提交并创建 PR |
