# 整改台账：命令与规则

[总入口](../README.md) · [任务模板与命名](tasks/README.md)

台账数据在 `ledger.jsonl`（每行一个永久 ID，记录之间空一行），**只给 AI 用，只经 `scripts/audit/ledger.py` 读写**，不手改。使用规则见 `AGENTS.md`「审查整改记录」，与 `python scripts/audit/ledger.py guide` 的输出相同。本文是命令细节。

## 命令

以下 `ledger` 指 `python scripts/audit/ledger.py`。写入命令在 Claude Code 中自动识别开发者、方式与会话；Codex 加 `--via Codex --session <任务ID>`，人工加 `--via 手工`。

| 命令 | 用途 | 示例 |
| --- | --- | --- |
| `open [--mine] [--status 前缀]` | 未关闭条目，每条一行 | `ledger open --mine` |
| `show <ID>` | 单条详情：推导状态、负责人、处理记录、任务记录「结论 / 下一步」 | `ledger show UI-103` |
| `guide` | 使用规则 | |
| `new <类别> "<标题>" --pri P1\|P2\|P3 [--task 路径]` | 新建并认领，自动分配编号 | `ledger new UI "列表筛选丢失" --pri P2 --task tasks/2026-11-20-UI-104-filter.md` |
| `start <ID>` | 认领 | `ledger start UI-104` |
| `link <ID> --pr N [--commit SHA]` | 关联 PR 或提交，用于推导合并、发布状态 | `ledger link UI-104 --pr 380` |
| `note <ID> "<内容>"` | 追加备注，任何人可用 | |
| `close <ID> --method 本机\|生产\|自动化\|文档 --evidence "..."` | 关闭 | `ledger close UI-104 --method 本机 --evidence "PR #380；BrowserOS 实测筛选保留"` |
| `defer <ID> --reason "..." --until YYYY-MM-DD` | 暂缓，必须有回顾日期 | |
| `na <ID> --reason "..." --confirmer "..."` | 判为不适用 | |
| `reopen <ID> --reason "..."` | 复发时重开，沿用原 ID | |
| `handover <ID> --reason "..." [--consent "..."]` | 接手；别人负责的必须带对方同意来源 | |
| `check` | 自检（CI 运行） | |

被拒绝时以退出码 2 结束，并给出下一步命令。

## 状态

| 状态 | 来源 |
| --- | --- |
| 待认领 | 没有负责人，也没有关联 PR |
| 处理中 | 有负责人，还没有关联 PR 或提交 |
| 待合并 | 关联的 PR 还没合并（提示具体 PR） |
| 已合并待发布 | 已进入 `develop`，还不在任何发版标签中 |
| 已发布待验收 vX.Y.Z | 已在发版标签中；能查到发版运行时核实部署结果，查不到时注明「部署结果未核实」 |
| 部署失败 vX.Y.Z | 该版本的发版运行失败 |
| 暂缓 / 不适用 / 已关闭 | 由 `defer` / `na` / `close` 记录 |

合并与发布状态按本地 Git（`origin/develop`、`origin/main` 与 `v*` 标签）实时推导，不手工维护；需要最新结果时先 `git fetch`。

## 负责人

- 当前负责人是最近一次认领或接手的开发者、方式与会话；关闭后不再有负责人，任何人可以重开。
- 同一开发者换工具或会话：工具自动记录接手。
- 别人负责的条目：只能 `note`；接手须对方同意，用 `handover --consent` 写明同意来源。迁移前的历史条目（负责人显示「历史」）同样先确认归属。

## 证据

- 关闭前按原问题验证，`--evidence` 写 PR、CI 运行、测试源码或实测说明；mock、隔离数据库、真实浏览器与目标环境的证据不能相互替代，写清是哪一种。
- 多个 ID 共用一个任务时逐 ID 关闭，不用一个测试结果替全部条目关闭。
- 需要历史数据修复或生产操作时先取得授权，在任务记录里写清范围。
- 证据与过程写在任务记录里，不保存凭据、真实敏感业务数据或完整工具日志。
