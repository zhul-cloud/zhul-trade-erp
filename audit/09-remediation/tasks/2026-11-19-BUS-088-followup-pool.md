# BUS-088 客户放入公海后，跟进单据仍出现在今日待办与跟进列表

## 结论 / 下一步

已修复，PR 发往 develop，随下一个版本上线。上线后确认公海客户不再出现在今日待办，再关闭。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Ezio·Claude Code |
| 关联 ID / 原专题 | BUS-088（用户 2026-11-19 反馈：客户 Snabburstroy 移至公海后，今日待办仍显示，点跟进提示「在公海，认领后才能操作」） |
| 目标与影响范围 | 客户跟进：今日待办、跟进列表、按客户分组、链路卡片计数、团队视图。不改公海与认领规则本身 |
| 起点 commit / 工作分支 | develop `88db96f9`；`fix/followup-pool-todo` |
| OpenSpec | 缺陷修复，规则已在 master-data/customer-pool「公海客户认领前不能操作」中，不改规范 |
| PR / 目标分支 | 见台账 link |
| 合并证据 | 见台账（按 Git 推导） |
| 目标版本 / 环境 | 下一版本；所有租户 |

## 实施与验证结果

根因：放入公海时，客户名下未结束的跟进单据负责人被改为 0（`FollowupSync.ownerChanged(id, 0L)`），单据本身不关闭。查看「全部」或团队范围时，跟进查询不按负责人过滤，这些单据仍会出现；点跟进时被公海检查拦下。

修复：
- 跟进查询的公共条件（`FollowupItemMapper.WHERE`）加「客户不在公海」。只查某个客户时不加（客户详情仍能看到）。
- 链路卡片计数、今日待办的「明天 / 本周还有」、团队视图三处单独查询，同样排除公海客户。
- 认领后负责人改回认领人，单据自动回到列表，逻辑不变。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `FollowupApiContractTest#poolCustomer_hiddenFromTodayListsAndOverview`，修复前运行 | 本机测试库 | 复现 | 今日待办仍有 2 个客户（应为 1） | 复现 |
| `FollowupApiContractTest`（31）、`FollowupQuickActionsContractTest`（8）、`CustomerPoolContractTest`（4） | 本机测试库，本分支 | 通过 | 全部通过 | 通过 |

## 合并、部署与逐 ID 验收

待合并、发版后补充。
