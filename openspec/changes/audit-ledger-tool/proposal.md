# Proposal

## Why

`audit/` 的问题台账只给 AI 用（Claude Code、Codex，多位开发者各自的会话），但现在是一张给人看的 255 行 Markdown 大表（69KB），93% 已关闭：每个 AI 开工都要通读、靠理解散文规则手改表格行，发版后要手工推进状态（曾有 90 个 ID 上线后台账落后数天），负责人都写「Claude」，无法判断一条是谁用什么方式处理的，「别人负责的由其自行验收」只能靠 AI 自己记。

## What Changes

- 新增命令行工具 `scripts/audit/ledger.py`（Python 3，只用标准库）：AI 通过它读写台账，不再直接读写大表。命令：`open`、`show`、`guide`、`new`、`start`、`handover`、`link`、`note`、`close`、`defer`、`na`、`reopen`、`check`。
- 台账数据改为 `audit/09-remediation/ledger.jsonl`：每行一个永久 ID，含基本信息与处理记录；每条处理记录写明开发者、方式（Claude Code / Codex / 手工）、会话或任务 ID、分支、时间。
- 「待合并、已合并待发布、已发布待验收」不再手工记录，由工具按关联 PR、提交与发版标签实时推导；只记录认领、接手、暂缓、不适用、关闭、重开等需要判断的节点。
- 写入时校验：状态跳转、证据必填、只能关闭自己负责的条目（别人的只能追加备注或正式接手）、暂缓必须有回顾日期。
- **BREAKING（仅对 AI 工作流）**：删除 `audit/09-remediation/issues.md`；255 条历史用脚本一次性转换，转换前后逐条核对。已有任务记录保留不动。
- `audit/09-remediation/README.md` 缩短为「如何使用工具」；任务模板开头固定「结论 / 下一步」摘要；`audit/README.md` 与 `AGENTS.md` 的入口改为「先运行 `ledger open`」。
- 使用规范收进仓库，任何开发者的 AI 开工自动获得：`AGENTS.md` 写完整简短的操作规则，`guide` 命令输出同一份规则，README 写命令与示例。原本只存在 Claude 本地记忆里的规则（发现新问题一并编号处理、不写知识库、别人负责的由其验收、汇报只讲自己的且不重复暂缓项）一并写入。
- CI 增加 `ledger check`：格式、ID 唯一、引用的任务文件存在、暂缓项是否超过回顾日期（超期只警告）。

## Capabilities

### New Capabilities

- `development/audit-ledger`：缺陷台账的数据、读写命令、处理记录与操作人、状态推导与写入校验。

### Modified Capabilities

（无）

## Impact

- 新增：`scripts/audit/ledger.py` 及其单元测试、`audit/09-remediation/ledger.jsonl`、一次性转换脚本（转换完成后删除）。
- 修改：`audit/09-remediation/README.md`、`audit/09-remediation/tasks/README.md`、`audit/README.md`、`AGENTS.md`「审查整改记录」一节、`.github/workflows/ci.yml`。
- 删除：`audit/09-remediation/issues.md`；`audit/` 内其他文件指向它的链接改指 README。
- 不涉及业务代码、数据库与生产部署；不影响 OpenSpec、Git Flow 与发版规范。
- 依赖：本机与 CI 已有 Python 3.12；推导发布状态用 `git`，确认部署结果时可选用 `gh`（没有时如实标明「部署结果未核实」）。
