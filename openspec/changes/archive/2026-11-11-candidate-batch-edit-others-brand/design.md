## Context

- 商品候选（`product_candidate`）的品牌 `brand_id` 为空表示「待确认品牌」，品类 `category_id` 可空。AI 标记字段由 V1.5.20 加入：
  - `ai_category`：0、1
  - `ai_brand`：0 未解析、1 匹配到、2 未识别
  - `ai_brand_name`：AI 给出的名称
- 批量 AI 解析由 `CandidateAiFill` 完成：提交时按「模式 + 候选 ID + 提交时的值」生成 key，回调时只改仍待审核、值未被人工改过的候选。目前 AI 没给出品牌时不写入；匹配不上时写 `ai_brand = 2`，`brand_id` 仍为空。
- 品牌是平台数据（`tenant_id = 0`），按 `product_brand_industry` 决定对哪个行业的租户可见（`IndustryScope.brandVisible`）。
- 审核弹窗的品牌区有两种：候选已有 `brand_id` 时显示品牌下拉；没有时显示「设为已有品牌的别名 / 新建品牌」，新建时默认填 AI 名称或原文。
- `candidate-batch-ai-fill` 已实现但还没归档，它的规格里写着「匹配不上的保留原文、标记 AI 未识别」，与本次规则冲突。

## Goals / Non-Goals

**Goals：**
- 待审核候选可以勾选后一次改品牌和（或）品类。
- AI 解析品牌给不出结果的候选落到 Others，可以直接审核通过。
- 审核 Others 候选时仍能方便地改成真实品牌（新建品牌时默认填 AI 名称）。

**Non-Goals：**
- 不回填已有候选；不新增「自动通过」。
- 不让运营在品牌库维护页把 Others 删除或改名的额外保护。只在代码里按固定名称查找，被改名后 AI 解析会提示配置缺失。

## Decisions

1. **Others 用迁移种子数据，按名称查找。**
   - V1.5.26 插入 `brand_name = 'Others'`、`tenant_id = 0`、`is_genuine = 0`。按名称判断是否已存在，不区分大小写，因为列的排序规则本身不区分。
   - 再给字典 `industry` 的全部行业各插一行 `product_brand_industry`，用 `INSERT IGNORE`。
   - 代码里用常量 `ProductConstants.OTHERS_BRAND = "Others"`，经 `BrandResolver.resolve` 取 ID（同时校验当前行业可见）。
   - 提交 AI 解析品牌时先检查 Others，取不到（被删或改名）就拒绝提交，提示「品牌库缺少 Others，请联系管理员」。回调时若 Others 已不在，匹配不上的只标「AI 未识别」，品牌仍待确认（即旧规则），不报错。
   - 新增行业时要补关联，这一点在品牌维护里没有自动化，只在迁移注释里说明。
   - 备选方案是在系统参数里配置 Others 的品牌 ID。不采用，因为多一个配置项，而且品牌是平台级数据，ID 在各环境不同。

2. **AI 未识别仍用 `ai_brand = 2`，只是同时写 `brand_id = Others`。**
   - 这样不改表结构，`ai_brand_name` 保留 AI 原名称。
   - 列表的品牌单元格在 `brandId` 有值且 `aiBrand === 2` 时显示「Others · AI 未识别：XXX」。
   - 「待确认品牌」筛选和排序仍按 `brand_id IS NULL`，Others 候选不再排在最后。这符合「视为已确认品牌」。
   - 品牌筛选新增「AI 未识别（Others）」（`brandState = 3`，按 `ai_brand = 2`），AI 解析完成提示里的「查看 Others」用它。

3. **AI 没给出品牌的也写 Others。**
   - 回调里品牌为空的条目写 Others 和 `ai_brand = 2`，`ai_brand_name` 为空。AI 直接返回「Others」的也按未识别处理。
   - AI 漏返回的候选不处理，计入「没写入」。任务入参没有落库，回调拿不到提交时的完整列表；编排服务按约定逐条返回，漏返回属于异常情况。
   - 写入条件不变：仍待审核且 `brand_id IS NULL`。
   - 汇总的 `unmatched` 含义改为「记为 Others」，字段名保持 `unmatched`，避免前后端接口变动。

4. **审核弹窗对 Others 候选的处理。**
   - 候选品牌为 Others 时，品牌下拉之外多一个「新建品牌」选项，默认名称填 `ai_brand_name`，没有时填品牌原文。
   - 品牌原文自动成为新品牌的别名，规则同现有新建品牌。
   - 不提供「设为别名」：Others 不该积累别名。

5. **批量修改的接口与事务。**
   - 接口：`POST /api/v1/product/candidates/batch-edit`，参数 `{ ids: long[], brandId?: long, categoryId?: long }`。
   - 校验：
     - `ids` 为 1 到 200 个
     - 两项至少一项
     - 品牌存在、未删除、当前行业可见
     - 品类存在且启用
   - 服务层在一个事务内逐条条件更新，条件为「`id` 匹配、仍待审核、未删除」，影响 0 行算跳过。每条成功的写操作日志，内容为改前、改后。返回 `{ updated, skipped }`。
   - 改品牌时 `ai_brand` 置 0，保留 `ai_brand_name`。候选上没有系列字段，不涉及系列。
   - 改品类时 `ai_category` 置 0。
   - 不加分布式锁：重复提交的结果相同，条件更新本身就是幂等的。

6. **前端。**
   - 「批量修改」按钮放在「批量通过」旁边，条件与「批量通过」相同：待审核页签、有审核权限、勾选数大于 0。
   - 弹窗有两个可清空的下拉：品牌（可搜索），品类（两级树选择）。两个都空时确定按钮不可用。
   - 成功后提示结果、刷新列表与各页签条数、清空勾选。
   - 原型见 `docs/03-产品原型/02-商品域/01-商品候选/商品候选.op` 的 C9、C10，经用户确认后开发。

## Risks / Trade-offs

- **Others 下商品会越来越多**：用户已接受。商品档案可以按品牌 Others 筛选后再逐个改品牌。
- **型号重名**：商品主数据唯一键为品牌 + 型号。两个不同真实品牌的同名型号都落到 Others 时，第二个审核会因商品已存在被并入第一个。
  - 发生概率低。审核时可以先改成真实品牌再通过。
  - 在 tasks 中加测试，确认并入时行为符合「审核前商品库已有相同品牌与型号则改为并入」的现有规则，不报错。
- **独立站、单据展示 Others**：`is_genuine = 0` 已防止正品类表述。客户单据显示品牌名 Others 可以接受；如不接受另提需求。

## Migration Plan

- V1.5.26 是纯插入，可重复执行（`INSERT IGNORE`）。回退 SQL 在 `sql/build/rollback/V1.5.26__brand_others_rollback.sql`：先确认没有候选或商品引用 Others（有引用时改为禁用），再删除关联行和品牌行。
- 版本号：develop 已有 V1.5.25（AI 任务进度，随 v1.18.0 上线），本变更用 V1.5.26。售后分支原来的 V1.5.25 与之冲突，须改为 V1.5.27 或更大；合并前按当时 develop 上的最大版本号复核。
