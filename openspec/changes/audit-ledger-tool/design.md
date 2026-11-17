# Design

## Context

- 现状见 proposal.md「Why」。台账 `audit/09-remediation/issues.md` 现有 255 个 ID（BUS 85、UI 103、SEC 24、AI 14、DEP 11、OPS 10、DOC 8），18 条未关闭；51 份任务记录在 `tasks/`。
- 仓库合并 PR 一律用合并提交，提交信息形如 `Merge pull request #375 from …`；发版由 `release.yml` 在 `release/*`、`hotfix/*` 合并到 `main` 后打 `vX.Y.Z` 标签并部署。
- 本机与 CI 都有 Python 3.12；Claude Code 会话里有 `CLAUDECODE`、`CLAUDE_CODE_SESSION_ID` 环境变量；Codex 是否有可识别的环境变量未知。
- 使用者是 AI：读取量与写入可靠性优先，人工可读性不是目标。

## Goals / Non-Goals

**Goals:**
- 工具只用 Python 标准库，单文件，可在任何装了 Python 3.10+ 与 Git 的机器上运行。
- 迁移后 255 条的 ID、标题、优先级、状态、任务记录与转换前逐条一致。

**Non-Goals:**
- 不做网页界面，不同步到 GitHub Issues。
- 不自动关闭条目：验收仍由负责人按证据关闭。
- 不改写、不搬迁历史任务记录的内容。

## Decisions

### 1. 数据：单个 JSONL 文件，每个 ID 一行

`audit/09-remediation/ledger.jsonl`，每行一个 JSON 对象、**记录之间空一行**，按类别（BUS、UI、AI、SEC、OPS、DEP、DOC，新类别排在后面）与编号排序：

```json
{"id":"UI-103","title":"商机列表的关键词搜不到商机编号和客户编号","pri":"P2","source":"tasks/2026-11-17-UI-103-opportunity-keyword.md","task":"tasks/2026-11-17-UI-103-opportunity-keyword.md","prs":[372],"events":[{"at":"2026-11-17T12:40:00+08:00","act":"start","dev":"Ezio","via":"Claude Code","session":"1a5ed417-…","branch":"fix/ui-103-opportunity-keyword","text":"…"}]}
```

- `source` 为原专题或任务记录的相对路径（原表第一列的链接），`task` 为任务记录相对路径，可为空。
- `events` 只追加。`act` 取值：`migrate`、`start`、`handover`、`note`、`link`（关联 PR/提交）、`defer`（带 `until`）、`na`（带 `confirmer`）、`close`（带 `method`、`evidence`）、`reopen`。
- 写入时先读最新文件，只替换目标 ID 那一行，写临时文件后用原子替换落盘。
- 记录之间空一行：实测 Git 合并时相邻两行分别被改会冲突，中间隔一行空行则能自动合并，这样相邻 ID 由不同开发者同时修改也不冲突。两人同时在同一类别新建 ID 仍会冲突（且编号相同），属于预期：后合并的一方 rebase 后重新 `new`，CI 的 `check` 兜底发现重复编号。

**为什么不用每个 ID 一个文件**：255 个文件对 AI 检索、对 Git 都更重；单文件按行合并已能避免不同条目之间的冲突。
**为什么不用 SQLite**：二进制文件无法在 Git 里合并，多人协作会直接冲突。
**为什么不用 GitHub Issues**：证据离开仓库，AI 读取要走 API；且用户要求整改记录保留在 `audit/`。

### 2. 操作人识别

| 字段 | 来源 |
| --- | --- |
| `dev` | `git config user.name`；取不到时要求 `--dev` |
| `via` | 有 `CLAUDECODE` 环境变量时为 `Claude Code`；否则必须传 `--via`（`Codex`、`手工` 或其他工具名） |
| `session` | `Claude Code` 取 `CLAUDE_CODE_SESSION_ID`；其他方式用 `--session` 传入（Codex 传任务 ID），手工可为空 |
| `branch` | `git branch --show-current` |
| `at` | 北京时间（Asia/Shanghai），ISO 8601 带 `+08:00` |

AGENTS.md 写明 Codex 调用时传 `--via Codex --session <任务ID>`；即使没写，工具在识别不出方式时也会在报错里给出这条完整命令。

当前负责人取最近一次 `start` / `handover` 的 `(dev, via, session)`：

| 操作人与当前负责人 | 处理 |
| --- | --- |
| 开发者、方式、会话都相同 | 直接执行 |
| 同一开发者，方式或会话不同 | 自动写入 `handover`（`text` 注明从哪个方式、会话转来，`auto: true`），再执行原操作 |
| 不同开发者 | 拒绝；只允许 `note`。`handover` 必须带 `--consent "<对方同意的来源>"`，AI 只在用户明确告知对方已同意时才使用 |

**为什么同一开发者换工具不需要人工接手**：用户要求 AI 自行处理、不靠人提醒；同一开发者在不同工具间接续属于正常工作流，自动记录接手即可保证可追溯。跨开发者转交涉及别人的工作，必须有对方同意，不能由 AI 自行决定。

### 2a. 让 AI 自行知道怎么做

- 每个拒绝都输出：原因、当前负责人（若相关）、一条可直接执行的下一步命令或需要确认的人。
- `-h` 帮助每个命令都带示例。
- AGENTS.md 「审查整改记录」写一段简短操作规则（开工先 `open`、只经工具写入、Codex 带 `--via` 与 `--session`、别人的条目只备注），Claude Code 与 Codex 开工都会读到；README 只作补充。

### 3. 状态推导

```
最近一次 close / defer / na 之后没有 reopen ──▶ 已关闭 / 暂缓 / 不适用
否则：
  没有 prs、没有 commits ─────────────────────▶ 处理中（有负责人）/ 待认领（无负责人）
  PR 的合并提交在 origin/develop 或 origin/main 中找不到 ──▶ 待合并
  合并提交在 origin/develop，不在任何 v* 标签中 ─────────▶ 已合并待发布
  合并提交在某个 v* 标签中 ──────────────────────────────▶ 已发布待验收（最早的版本）
```

- PR 的合并提交用 `git log --grep "Merge pull request #<n> " origin/develop origin/main` 查找，不依赖 GitHub API；`commits` 用于没有 PR 的直接提交（例如台账文档项）。
- 最早包含的版本用 `git tag --contains <sha> --list 'v*'` 后按版本号排序取最小。
- 部署结果：有 `gh` 时查 `release.yml` 对应 `release/X.Y.Z` 或 `hotfix/X.Y.Z` 的运行结论，失败显示「部署失败」；没有 `gh` 或查询失败时注明「部署结果未核实」。
- 推导基于本地的 `origin/*` 与标签；`show` / `open` 输出最近一次 `git fetch` 的时间提示，必要时由 AI 先 fetch。

**为什么不在发版流程里写回台账**：写回需要发版流程向仓库提交，增加一次提交和冲突面；推导结果每次查询都与 Git 一致，不会落后。

### 4. 命令

| 命令 | 说明 |
| --- | --- |
| `open [--mine] [--status S]` | 未关闭条目，每条一行；`--mine` 只看当前负责人是自己的 |
| `show <ID>` | 完整信息与推导状态，附任务记录开头「结论 / 下一步」一节（没有时附前 40 行） |
| `new <类别> "<标题>" --pri P2 [--task 路径]` | 分配下一个编号并写入 `start` 记录 |
| `start <ID>` / `handover <ID> --reason` | 认领 / 接手 |
| `link <ID> --pr N [--commit SHA]` | 关联 PR 或提交 |
| `note <ID> "<内容>"` | 追加备注（任何人可用） |
| `defer <ID> --reason --until YYYY-MM-DD` / `na <ID> --reason --confirmer` | 暂缓 / 不适用 |
| `close <ID> --method 本机|生产|自动化|文档 --evidence "<链接或说明>"` | 关闭 |
| `reopen <ID> --reason` | 重开 |
| `check` | 自检，CI 使用 |

所有写入命令支持 `--via`、`--session`、`--dev`；写入成功输出一行结果，失败以非零退出码结束且不改动文件。

### 5. 历史转换

一次性脚本解析 `issues.md` 的 255 行：

- 每条写入一条 `migrate` 记录：`dev` 为「历史」，`via` 为原负责人列（`Claude`、`Claude / Codex`、`Ezio（决定）`），`text` 写原状态与原最后更新日期。
- 原状态为已关闭：追加 `close`，`method` 为「历史记录」，`evidence` 指向任务记录。
- 原状态为暂缓：追加 `defer`，`until` 设为 2026-12-17（迁移后一个月统一回顾），原因取自任务记录或注明「迁移前暂缓，原因见任务记录」。
- 原状态为已发布待验收（UI-101、UI-102、UI-103）：按任务记录补 `prs`，使推导结果仍为「已发布待验收」。
- 转换后逐条比对：ID 集合、标题、优先级、任务记录路径一致；推导状态与原状态一致（原「已关闭 / 暂缓 / 已发布待验收」三类）。全部一致才删除 `issues.md` 与转换脚本。

### 6. 使用规范分三处、内容一致

| 位置 | 何时被读到 | 内容 |
| --- | --- | --- |
| `AGENTS.md`「审查整改记录」 | Claude Code、Codex 每次开工自动加载 | 完整操作规则，控制在约 20 行 |
| `ledger.py guide` | 任务中途查阅；`open` 输出末尾提示 | 与 AGENTS.md 同一份规则 |
| `audit/09-remediation/README.md` | 需要命令细节时 | 命令、状态、证据要求、示例 |

- `guide` 的文本是规则的唯一来源，存放在工具内；单元测试校验 `AGENTS.md` 中该节与 `guide` 输出一致，避免两处漂移。
- 原本只在 Claude 本地记忆中的规则写入这份规则后，本地记忆只保留指向仓库的说明，不再各自维护一份。
- `audit/README.md`（审查基线总入口，24KB）标明只在追溯原始审查发现时阅读，日常不读。

### 7. 文档与 CI

- `audit/09-remediation/README.md` 改为：工具位置、常用命令、状态含义（一张表）、证据要求。原散文规则中能由工具校验的部分删除。
- `tasks/README.md` 模板开头加「结论 / 下一步」一节，删去要求同步维护 `issues.md` 的说明。
- `audit/README.md`、`AGENTS.md`「审查整改记录」：入口改为「先运行 `python scripts/audit/ledger.py open`」。
- `audit/` 内指向 `issues.md` 的链接改指 `09-remediation/README.md`（历史任务记录只改这类链接，正文不动）。
- `ci.yml` 增加轻量任务：`python scripts/audit/ledger.py check` 与工具单元测试，不依赖编排服务镜像。

## Risks / Trade-offs

- [AI 绕过工具直接改 JSONL] → CI 的 `check` 校验格式与记录字段；README 与 AGENTS.md 写明只经工具写入。
- [推导依赖本地 Git 是否最新] → 输出最近 fetch 时间；`open`、`show` 不自动 fetch，避免无网络时失败。
- [合并提交信息格式变化导致找不到 PR] → 同时支持 `--commit` 直接关联提交；找不到时状态为「待合并」并提示核对。
- [开发者用 Git 用户名区分，不同机器名字不一致] → 首次写入时提示当前识别结果；名字不一致属于配置问题，记录里如实保留。
- [迁移把暂缓回顾日期统一设为 2026-12-17] → 到期 `check` 只提醒，不阻塞；由负责人逐条重新评估。

## Migration Plan

1. 实现工具与单元测试。
2. 运行转换脚本生成 `ledger.jsonl`，逐条比对通过后删除 `issues.md` 与转换脚本。
3. 改文档、AGENTS.md、CI；本机跑 `check` 与测试。
4. 一个 PR 发往 develop。回退方式：还原该 PR（`issues.md` 随之恢复）；迁移后新增的记录需手工补回旧表。
