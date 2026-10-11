## Context

- 商品主数据（`product` 及规格、应用场景、FAQ、技术资料、型号关系）为平台级（tenant 0），写入由 `PlatformScopeGuard` 限制；第一期已为候选审核加了「在审核范围内放开平台账号限制」的线程标记。
- 规格（`spec_key` 同商品唯一，整体替换保存）、应用场景、FAQ（来源 1/2/3，3 为 AI 待审核只对平台可见）都没有语言字段；技术资料有 `language`；型号关系支持 `related_mpn` 文本（关联型号可不在库）。
- 商品的 `spec_summary` 单字段；系统内展示用中文，独立站等对外用英文、俄文。
- 外部内容生产：llm-wiki 工作流 C 产出「04-独立站内容包」与「01-营销内容」SEO 文案（Markdown，英文为主）。

## Goals / Non-Goals

**Goals:** 每个商品对每家公司有内容任务；任务包下载；三语 Markdown 上传、解析预览、确认写入；事实类进共享库、SEO/GEO 按公司；多语言读取与展示。

**Non-Goals:** 系统内调用 AI 生成；社媒文案、销售话术、价格、图片视频（第三期）；共享库 FAQ 模块的任何改动；修改 llm-wiki 的 skill。

## Decisions

1. **任务不预先扇出**：内容任务列表 = 商品（启用、未删除）左连接本公司的 `product_content_task`；没有任务行的视为「待生成」。第一次下载或上传时才插入任务行。这样「所有商品自动有任务」不需要在建档时给每个租户写数据，新租户也天然覆盖。
2. **数据模型**
   - `product_specification`、`product_application` 增加 `lang varchar(8) NOT NULL DEFAULT 'en'`；规格唯一约束改为 (product_id, spec_key, lang)；读取接口增加可选 `lang` 参数，不传返回英文（兼容现有调用方与独立站）。
   - `product_locale`（平台级）：(product_id, lang) 唯一，存 `spec_summary`；英文同步写回 `product.spec_summary` 保持兼容。
   - `product_relationship_note`（平台级）：(relationship_id, lang) 唯一，存兼容说明的各语言文本；英文同步写回 `product_relationship.note`。
   - `product_seo`（租户级）：(tenant_id, product_id, lang) 唯一，存 `seo_title`、`meta_description`、`long_description`、`geo_answer`、`import_id`。
   - `product_seo_faq`（租户级）：(tenant_id, product_id, lang) 下的 FAQ 列表（问题、答案、排序）。FAQ 归公司：内容包 FAQ 含本公司质保、发货、联系方式，而共享库 FAQ 规定只描述商品本身；共享库 `product_faq` 不动。
   - `product_content_task`（租户级）：(tenant_id, product_id) 唯一，`zh_at/en_at/ru_at`（写入时间）、`zh_by/en_by/ru_by`、`downloaded_at`、`status`（1 待生成 2 进行中 3 已完成，冗余便于筛选排序）。
   - `product_content_import`（租户级）：每个确认过的文件一行，存 `task_id`、`lang`、`file_name`、`raw_text`（原文追溯）、`summary`（写入条数 JSON）、`confirmed_by/at`。
3. **解析器 `ContentMarkdownParser`（纯函数）**：解析 frontmatter（YAML 简单键值）与 `## ` 二级标题分节；章节名按固定中文标题的开头匹配（兼容「首屏定义块（40-60词…）」这类带括号说明的标题），正文只有「（无）」「（尚未生成…）」这类占位的视为空（模版里中英俄文件都用同一套中文标题，避免翻译标题导致解不出），也接受少量英文别名（Spec Summary、SEO Title 等）。表格按 `|` 分列，跳过表头与分隔行；应用场景按 `|` 三段；FAQ 识别 `Q:`/`A:`（中文「问：」「答：」也接受）；技术资料识别 Markdown 链接。返回结构化结果与逐块问题列表。用单元测试覆盖 llm-wiki 现有 04 内容包样例。
4. **预览不落库**：上传接口只解析并返回预览（含每块的建议写入与问题）；前端可改后把结构化结果提交确认。确认接口重新校验长度与格式（不信任前端），按商品+语言逐个独立事务写入（`TransactionTemplate`），汇总成功与失败。
5. **写入规则**：规格摘要直接替换；规格 / 应用场景 / 兼容 / 技术资料：规格 / 应用场景先软删除该商品该语言 `verified=0` 的旧行，再按「同名不重复（已核实的保留）」写入新行；兼容型号与语言无关，按归一化后的关联型号合并：已有关系只写该语言的 `product_relationship_note`（英文同步 `note`），没有则新增 `relationship_type=4`、`confidence=5` 的关系（关联型号命中库内同品牌商品时填 `related_product_id`），不删除已有关系；技术资料按地址合并、只增不删；FAQ 写入本公司 `product_seo_faq`，该语言整体替换。确认写入由内容服务直接操作各表（写入规则与平台维护接口不同），不经过带 `requirePlatform` 的平台维护服务；权限由「商品内容维护」按钮控制。
6. **任务包生成**：后端按商品组装 Markdown（商品信息块 + 模版 + 格式说明），批量时用 `ZipOutputStream`；模版文本放在 `resources/product-content/template.md` 便于后续调整。
7. **商品详情**：增加 `GET /api/v1/product/products/{id}/seo?lang=` 返回本公司 SEO/GEO 与 FAQ；规格、应用场景读取接口加 `lang`；前端详情各卡片加「中文 / English / Русский」切换。
8. **菜单与权限**：商品资料下「内容任务」`/product/content-tasks`，按钮 `product:content:edit`（确认写入）；授予内置管理员与套餐。

## Risks / Trade-offs

- [上传覆盖了人工维护的内容] → 只替换未核实的同语言内容，已核实的保留；预览逐块可跳过；保留原文追溯。
- [多语言条目对不齐（中文 FAQ 6 条、英文 7 条）] → 各语言独立列表，不强制对齐。
- [现有读取接口默认英文] → 不传 `lang` 时行为不变，独立站与现有页面不受影响。
- [租户账号写共享库] → 与第一期一致，只在确认上传路径放开并记录写入人与原文。
