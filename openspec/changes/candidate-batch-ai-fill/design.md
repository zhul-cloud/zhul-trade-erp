## Context

- 候选池 `product_candidate` 是平台级（tenant_id 恒为 0），在 `TenantTables.EXCLUDED` 中，查询由代码自己带条件。唯一键为「品牌键 + 归一化型号」；品牌识别到时 `brand_id` 有值。
- 品类建议现由 `ProductArchiver.suggestCategory()` 按来源品类文字匹配，匹配不上落到兜底一级品类「其他」（`CandidateConstants.CATEGORY_OTHER`）。
- 品牌识别统一走 `BrandResolver.resolve(name)`（名称与别名）。
- AI 任务：`AiTaskService.createAndSubmit(skillId, input, user)` 提交编排服务，回调按任务所属租户执行 `AiTaskResultHandler`；`DescriptionTranslations` 是现成的「批量条目 → 每条结果」样板，前端轮询任务状态。

## Goals / Non-Goals

**Goals:**
- 一次勾选最多 200 条，后台跑完，结果直接落到候选，审核时可改。
- 品牌只匹配已有品牌与别名，不自动新建。

**Non-Goals:**
- 不做全量自动解析或定时解析；不联网搜索；不改审核规则（批量通过仍要求品牌已确认、品类已选）。
- 不改型号、名称、描述。

## Decisions

1. **一个按钮一个 AI 任务，技能 `candidate-classify`，输入带 `mode`（category / brand）。** 输入条目：`key`（候选 ID）、品牌原文、型号、名称、中文描述、来源品类文字；品类模式另带现有启用品类清单（ID、一级 / 细分名称）。输出：每条 `key` 对应 `categoryId` 或 `brand`（可为空）。编排服务按 50 条一批调用模型，合并后一次回调。备选：每条一个任务——200 个任务、200 次回调，放弃。
2. **提交时记录快照，回写时条件更新。** 提交接口先筛出需要处理的候选（规则见 spec），把待处理 ID 交给 AI；回调时逐条条件更新：`status = 待审核` 且品类模式下 `category_id` 仍为提交时的值（或为空）、品牌模式下 `brand_id IS NULL`。不改 `brand_key`（唯一键不动，避免与已有候选撞键）；审核通过时按 `brand_id` 建档，与现在手选品牌一致。
3. **新增字段（迁移 V1.5.20）：**
   - `ai_category tinyint(2) NOT NULL DEFAULT 0 '品类是否 AI 建议（0-否、1-是）'`
   - `ai_brand tinyint(2) NOT NULL DEFAULT 0 '品牌 AI 解析（0-未解析、1-AI 匹配到已有品牌、2-AI 未识别）'`
   - `ai_brand_name varchar(64) NOT NULL DEFAULT '' 'AI 给出的品牌名（未识别时供审核参考）'`
   审核（通过、并入）与手动改品类、品牌时，提交的值与 AI 值不同则清零。回退：删除三个字段，不影响其他数据。
4. **进度与并发。** 编排服务跑完才一次回调，中途没有进度，页面只显示「AI 正在解析 · 共 N 个、跳过 M 个」和加载状态，不显示已处理条数。 提交返回任务 ID、交给 AI 的条数和跳过条数，前端每 3 秒轮询 `GET /product/candidates/ai-fill/{taskId}`（只能查自己提交的），任务 ID 存 localStorage（`zhul:candidate:ai-fill`，读写包 try/catch），回到页面继续轮询。提交前检查当前用户是否有未结束的 `candidate-classify` 任务，有则拒绝 `CANDIDATE_AI_FILL_RUNNING`。结果汇总（填了 / 匹配 / 未识别 / 没写入）在回调处理时写入任务输出的 summary，查询接口直接返回；跳过条数由提交接口返回，前端随任务 ID 一起保存。回调 key 为「模式（c / b）+ 候选 ID#提交时的品类 ID」，回调据此区分模式、判断品类是否被改过。
5. **租户。** AI 配置取当前公司（`resolveForCurrentTenant`）；回调在任务所属租户下执行，候选表在排除清单内，更新语句自己按 ID 条件写，不依赖拦截器。
6. **权限。** 两个接口都要求 `product:candidate:review`，与审核一致。

## Risks / Trade-offs

- [模型给错品类] → 只写「AI 建议」，审核时可见可改；批量通过前运营仍需过目。
- [200 条一次的耗时] → 4 批串行，约 1–3 分钟；后台运行、可离开页面。本地联调用 `scripts/ai-orchestrator-stub`，已加 `candidate-classify` 假结果。
- [AI 用量] → 按任务记录用量，与其他技能一致；不联网搜索。
