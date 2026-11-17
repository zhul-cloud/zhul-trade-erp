# Tasks

## 1. 工具

- [x] 1.1 `scripts/audit/ledger.py`：读写 `ledger.jsonl`（只替换目标行、原子落盘），操作人识别（Git 用户名、Claude Code 环境变量、`--via` / `--session` / `--dev`），北京时间。
- [x] 1.2 读取命令 `open`（含 `--mine`、`--status`）、`show`（附任务记录「结论 / 下一步」）。
- [x] 1.3 写入命令 `new`、`start`、`handover`、`link`、`note`、`defer`、`na`、`close`、`reopen`，按 spec「写入校验」与「当前负责人与他人条目的保护」校验：同一开发者换工具自动写接手记录，不同开发者拒绝（接手须 `--consent`）。所有拒绝信息给出原因与可直接执行的下一步命令，每个命令的帮助带示例。
- [x] 1.4 状态推导：按 `prs` / `commits` 在 `origin/develop`、`origin/main` 与 `v*` 标签中推导；可选用 `gh` 查发版运行结论，查不到时注明「部署结果未核实」。
- [x] 1.5 `guide`：输出操作规则（规则文本只在工具内维护一份）；`open` 输出末尾提示可运行 `guide`。
- [x] 1.6 `check`：格式、ID 唯一与编号规则、记录字段、任务记录文件存在、暂缓到期提醒。
- [x] 1.7 单元测试 `scripts/audit/test_ledger.py`：用临时 Git 仓库覆盖 spec 各场景（未关闭列表、ID 不存在、操作人记录、缺少方式时提示完整命令、同一开发者换工具自动接手、不能关闭别人的条目、对方同意后接手、合并后待发布、发版后待验收、部署结果未核实、关闭缺证据、暂缓缺日期、新编号分配、两人改不同条目合并无冲突、自检失败与到期提醒、`AGENTS.md` 该节与 `guide` 输出一致）。

## 2. 历史转换

- [x] 2.1 转换脚本把 `issues.md` 255 行写成 `ledger.jsonl`：`migrate` 记录、已关闭补 `close`、暂缓补 `defer`（回顾日期 2026-12-17）、UI-101 / UI-102 / UI-103 按任务记录补 `prs`。
- [x] 2.2 逐条比对：ID 集合、标题、优先级、任务记录路径一致；推导状态与原状态一致。全部一致后删除 `issues.md` 与转换脚本。

## 3. 文档与入口

- [x] 3.1 重写 `audit/09-remediation/README.md`：工具用法、状态含义、证据要求，只经工具写入。
- [x] 3.2 `tasks/README.md` 模板开头加「结论 / 下一步」，去掉同步维护 `issues.md` 的说明。
- [x] 3.3 `AGENTS.md`「审查整改记录」改为与 `guide` 一致的操作规则，覆盖：什么情况用 `audit/`（缺陷 / 审查发现 / 用户反馈；新功能走 OpenSpec）、开工先 `open`、只经工具写入、Codex 传 `--via Codex --session <任务ID>`、发现新问题先查重再新建 ID 一并处理、别人负责的只备注且跨开发者接手须对方同意、关闭需要的验收证据、汇报只讲自己负责的且不重复暂缓项、不写知识库。
- [x] 3.4 `audit/README.md` 入口改为先运行 `ledger open`，标明审查基线只在追溯原发现时阅读。
- [x] 3.5 `audit/` 内指向 `issues.md` 的链接改指 `09-remediation/README.md`（历史任务记录只改链接）。

## 4. CI 与验证

- [x] 4.1 `ci.yml` 增加台账检查任务：运行 `ledger.py check` 与单元测试。
- [x] 4.2 本机实测：`open` 列出 18 条未关闭；`show` UI-103 推导为「已发布待验收 v1.31.1」；在测试分支模拟 Claude Code 认领 → 以 Codex 身份（`--via Codex --session`）关闭时自动接手、另一开发者被拦截并收到下一步提示；`check` 通过。
- [x] 4.3 PR 发往 develop，说明 `issues.md` 已删除及回退方式。

## Workflow follow-up

- 合并后归档 `audit-ledger-tool`，同步主规范 `development/audit-ledger`。
- 本机 Claude 记忆中与台账有关的条目（audit-not-in-wiki、ledger-acceptance-by-owner、no-repeat-deferred 等）改为指向仓库规范，不再各自维护规则。
