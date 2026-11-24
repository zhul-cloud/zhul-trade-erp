# AGENTS.md

本文件为在本仓库工作的 AI 编程助手（Claude Code、Codex 等）提供指引，是唯一正文：Claude Code 通过 `.claude/CLAUDE.md` 中的 `@../AGENTS.md` 引用加载，Codex 直接读取。修改规则只改本文件。

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

# 外贸业财一体化中台 — 项目专属配置

## 项目定位

**外贸业财一体化中台**：将外贸业务流程与财务系统深度打通，实现业务与财务实时联动。

**核心模块：**
- 客户与商机管理（CRM）
- 订单全流程管理（OMS）
- 采购与供应商管理
- 货运与清关跟踪（物流）
- 应收应付账款（AR/AP）
- 成本核算与利润分析
- 多币种汇率管理
- 合规单据管理（报关单、信用证、提单）

## 项目专属规则

### NEVER
- 不删除用户数据，任何删除必须软删除（deleted_at 标记）
- 不跳过汇率换算，所有金额必须同时存原币种和本位币
- 不修改已结算的财务凭证
- 不在未确认币种的情况下存储金额字段

### ALWAYS
- 输出计划前先列出影响范围
- 凭据按用途保护：租户运行时需调用的 API Key 等第三方凭据可按租户隔离加密存入业务库，管理查询与展示脱敏；系统用户登录密码只存安全单向哈希，具体遵循 [编码规范「凭据与密码」](.claude/rules/coding.md#凭据与密码)。
- 财务相关计算必须注明精度规则（四舍五入方式）
- 涉及跨模块数据联动，须说明事务边界
- 金额字段统一用 decimal/Numeric 类型，禁用 float
- 带 tenant_id 的新表须在 `TenantTables` 归类；定时任务、异步、回调等没有登录上下文的入口须用 `TenantScope.platform` / `TenantContext.runAs`；JdbcTemplate 须手写租户条件。详见 [编码规范「多租户规范」](.claude/rules/coding.md#多租户规范)（2026-11-03 追加）
- 日期时间统一存北京时间（Asia/Shanghai），展示时不再换算；数据库与后端容器都设 TZ=Asia/Shanghai（与 `.claude/rules/coding.md` 一致）。客户所在地时间只在需要时由前端换算（`customer/time.ts`）
- 所有 UI 设计 / 原型设计任务，统一使用 OpenPencil（`op` CLI / openpencil-design 技能）完成，无需用户每次特别说明。原型经用户确认后才能开发页面；pencil MCP 连不上时改用 `op` 命令行，命令行也不可用时请用户解决 OpenPencil 工具问题，不能跳过原型直接开发（2026-10-29 追加）
- 所有列表页（分页的业务数据表格）默认按更新时间倒序，并固定显示「创建」「更新」两列（每列人名加时间，顺序固定，紧邻业务字段之后、操作列之前；2026-11-23 由四列合并）；例外与细则见 `.claude/context/ui-design-patterns.md` 的「列表页统一规范」。新做或改动列表页（含原型）时逐条对照（2026-10-09 提升到此处）
- 从一个页面跳到另一个页面（点单号、型号、客户名等）后必须能返回原页面：由标签页框架的返回条统一提供，页面不另写「返回」按钮、不在地址里带 `from`；详情页用 `usePageTitle` 登记标题，有未保存修改的页面用 `useTabDirty` 登记。上一张单据统一叫「来源单号」。细则见 `.claude/context/ui-design-patterns.md` 的「标签页与返回」「来源单号」（2026-11-11 追加）
- 所有单据页（列表与详情，含原型）以销售订单为标准：客户、来源单号各自单独一列，品牌、品类、型号的显示与详情页的表头、明细、布局都照销售订单；每张单据都是「表头 + 明细」，有独立详情页，支持备注和可预览的附件。细则见 `.claude/context/ui-design-patterns.md` 的「单据页统一规范」（2026-11-11 追加）
- 所有原型的界面风格和交互必须符合消费级互联网产品该有的质感：视觉克制、层次清晰、反馈及时、状态完整，不能停留在"能用的后台表单"。具体要求见 `.claude/context/ui-design-patterns.md` 的「消费级质感」一节（2026-09-19 追加）
- 所有界面设计优先考虑用户体验和观感，追求卓越而不是能用：广泛参考多家优秀设计、总结成本项目的规则（不照搬一家），通用写法做成公共组件统一维护，用户或评审指出一处问题时按同类问题全站处理并补成通用规则；原型交 UI 设计师角色评审，并和标杆模板按对标检查表逐项对比达标后再交用户。原则、流程与检查表见 `.claude/context/ui-design-patterns.md`「设计原则与流程」和 `openspec/changes/ui-redesign/design-references.md`（2026-11-23 追加）
- 界面问题不迎合、求最优：用户或评审指出问题时，先找根因（多半在令牌、公共组件或通用规则，不只在那一页），对照参考设计（至少三家，优先官方设计系统的令牌与规范）给出最优方案并写明依据；用户的说法与最佳实践冲突时直接指出。视觉取舍出对比图并标出推荐项和理由。所有界面改动都过专业 UI 审核：UI 设计师角色评审、对标检查表验收、独立审核者复审、Windows 浏览器实测（2026-11-24 追加）
- 规则服务于目标（把界面做到专业级），不代表一定正确：套用规则后观感变差、或规则与专业产品的通行做法冲突时，先回到目标判断，确认是规则的问题就主动改规则（`.claude/context/ui-design-patterns.md` 等）并写明更正原因，免得其他开发者用 AI 时重复同样的错误；不要只在一页上绕开（2026-11-24 追加）
- 配色：组件一律跟主题色（按钮、开关、进度、完整度、步骤条、选中态），只有本身要区分多类的组件用多色（图表系列、状态标签、类别图标块）；同一内容只分两类且无好坏之分时用主题色深浅（`palette.chart` / `chartSoft`），有风险的一类用状态色；进度类用深浅表示程度，完成也不换色。细则见 `.claude/context/ui-design-patterns.md`「层级与表面」，lint:ui `progress-color`、`primary-soft` 把关（2026-11-24 追加）
- 表面分层：页面底最深，白卡最亮，卡内的浅灰（表头、凹格、分组头、悬停）介于两者之间；卡内底色不得等于或深于页面底，否则贴到卡边时像露出页面；直接放在页面上的控件（分段控件轨道等）用比页面深一档的灰。细则见 `.claude/context/ui-design-patterns.md`「层级与表面」（2026-11-24 追加）
- 删除多余的标语、说明文字和次要标签；如果删减后界面就难以理解，说明布局本身有问题，应直接重新设计
- 每次修改都要兼顾用户体验、开发体验和 AI Agent 的使用体验，三者都要改善，且不能破坏现有功能

## 代码结构查询（CodeGraph）

- 查询代码结构、调用关系和变更影响时优先用 CodeGraph MCP（指定当前仓库的 `projectPath`）或 `codegraph explore "问题或符号" --path .`；不可用时用源码搜索继续，图谱不能替代源码判断与测试。
- 先在当前仓库根目录运行 `codegraph status --json` 核对项目路径与索引状态，尤其切换分支或 worktree 后；仅缺少索引时运行 `codegraph init .`，不要复用其他 worktree 的索引。
- 正常改动由 MCP 自动增量同步；仅状态显示待同步或结果落后时运行 `codegraph sync .`。索引损坏、构建失败或版本提示需要重建时才用 `codegraph index .`，不反复全量重建，也不额外启动后台 watcher。
- 团队共享索引排除项维护在根目录 `codegraph.json`；索引、缓存和运行状态留在已忽略的 `.codegraph/`（或 `.codegraph-*/`），不提交个人绝对路径、凭据或本地 MCP 配置。命令选项以已安装版本的 `--help` 为准。

## 审查整改记录

缺陷台账在 `audit/`，只给 AI 用，只经 `scripts/audit/ledger.py` 读写。以下规则与 `python scripts/audit/ledger.py guide` 的输出一致；改规则时改 `ledger.py` 里的 `GUIDE`，再同步到这里（单元测试校验两处一致）。现有 OpenSpec、Git Flow 与发版规范不变。

<!-- audit-guide:start -->
审查整改台账（audit/）使用规则
1. 范围：缺陷、审查发现、用户反馈的问题记在 audit/；新功能走 OpenSpec，功能验收台账放在变更目录。整改进度只记在 audit/，不写进知识库。
2. 开工先运行 `python scripts/audit/ledger.py open` 看未关闭条目，`show <ID>` 看单条；不要通读 ledger.jsonl。
3. 台账只经本工具写入，不手改 ledger.jsonl。Claude Code 自动识别身份；Codex 每条写入命令加 `--via Codex --session <当前任务ID>`；其他工具加 `--via <名称>`。
4. 新问题先用 `open`、`show` 查重，确认没有再 `new <类别> "<标题>" --pri P1|P2|P3 --task <任务记录>`（类别 BUS/UI/AI/SEC/OPS/DEP/DOC）。修复中发现未登记的问题一并新建并处理，不要只写「未处理」。
5. 认领用 `start <ID>`；提 PR 后 `link <ID> --pr <编号>`。合并、发布状态由工具按 Git 自动推导，不手工维护。
6. 过程写在 audit/09-remediation/tasks/ 的任务记录里（模板见 tasks/README.md），开头的「结论 / 下一步」随进展更新。
7. 关闭：验收后 `close <ID> --method 本机|生产|自动化|文档 --evidence "<PR、CI、测试或实测说明>"`。暂缓 `defer <ID> --reason ... --until YYYY-MM-DD`；不适用 `na <ID> --reason ... --confirmer ...`；复发 `reopen <ID> --reason ...`。
8. 负责人：同一开发者换工具或会话时，工具自动记录接手。别人负责的条目不改状态，只能 `note`；接手须对方同意，再 `handover <ID> --reason ... --consent "<同意来源>"`。迁移前的历史条目也要先确认归属。
9. 汇报：只讲自己负责、需要处理的条目；别人负责的最多一句说明归属；暂缓项不重复列出。
10. 命令被拒绝时按提示里的下一步执行。命令细节见 audit/09-remediation/README.md。
<!-- audit-guide:end -->

## 分支与发版

详见 `docs/06-部署运维/11-分支与版本规范.md`（分支、版本号、操作步骤）和 `docs/06-部署运维/08-生产发版.md`（检查、部署、回滚）。

- 按 Git Flow：`feat/*`、`fix/*` 从 `develop` 拉出，PR 发往 `develop`；发版从 `develop` 拉 `release/X.Y.Z`；线上紧急修复从 `main` 拉 `hotfix/X.Y.Z`
- `release/X.Y.Z`、`hotfix/X.Y.Z` 合并到 `main` 就是生产上线：自动打版本标签、构建镜像、部署，中间没有人工确认；合并后必须再合回 `develop`
- 新增或改名 `.env` 配置项（`.env.example`）时：在 PR 说明里列出配置项名称和生产应填的值；合并到 `main` 之前，必须由有服务器权限的人先在生产 `.env` 补齐，否则部署会在预检阶段停止
- 带数据库迁移的改动，在 PR 说明里写明迁移版本号以及能否回退

## 上下文文件索引

| 文件 | 内容 |
|------|------|
| `.claude/context/business.md` | 外贸业务流程与术语 |
| `.claude/context/finance.md` | 财务模块规则与科目体系 |
| `.claude/context/tech-stack.md` | 技术选型与架构决策 |
| `.claude/context/data-model.md` | 核心数据模型 |
| `.claude/context/ui-design-patterns.md` | 前端 UI 设计规范（列表页/表单弹窗结构、Design Token） |
| `.claude/rules/coding.md` | 编码规范（建表、API、Java 分层、Redis、日志、多租户、前端），写代码前必读 |
| `.claude/rules/finance-rules.md` | 财务计算规则 |
| `docs/06-部署运维/08-生产发版.md` | 生产发版流程、合并到 main 前的检查、重新部署与回滚 |
| `docs/06-部署运维/11-分支与版本规范.md` | Git Flow 分支规范、版本号规则、发版与紧急修复步骤 |
