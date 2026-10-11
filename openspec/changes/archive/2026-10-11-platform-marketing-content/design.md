## Context

上一个变更（split-product-facts-marketing，已归档）之后的结构：
- **营销内容**：只有独立站一套。`product_seo` 按公司、商品、语言存 SEO 标题、SEO 描述、长描述、GEO，`product_seo_faq` 存营销问答。
- **进度**：`product_content_task` 是本公司任务，用 `en_at/ru_at` 判断营销阶段，`downloaded_at` 是营销包下载时间。
- **阶段计算**：任务列表 SQL 用 `CASE` 算阶段，`FactTaskService.stage` 在 Java 里算同一口径。
- **章节与写入**：`ContentSection` 用 `shared` 区分资料、营销章节；`ContentMarkdownParser` 校验 `type` 和 `lang`；`ContentWriter` 按 `facts` 布尔值分两条路径。
- **公司设置**：已有 `sys_config` 的「公司覆盖、平台模板兜底」做法，见 `SourcingSettings`。
- **开发库数据**：独立站英文 2 条、俄文 1 条，另有中文 1 条（旧数据，不显示）；内容任务 3 行。

## Goals / Non-Goals

**Goals**
- 四个平台的内容各自存、各自写入、各自计进度，互不覆盖。
- 完成口径只在一处定义：SQL 和 Java 用同一份平台清单。
- 独立站沿用现有表和数据，不迁移。

**Non-Goals**
- 不做发布状态、发布链接和自动发布。
- 不做平台内容的在线编辑，内容只通过营销包写入。
- 营销包的生成输入不按平台区分，都用同一份中文资料。

## Decisions

**1. 平台定义集中在一个枚举**
- 新增 `MarketingPlatform`，每个平台定义编码、中文名、允许的语言、章节和是否需要分语言完成：
  - `alibaba`（阿里国际站）：en；
  - `mic`（中国制造网）：en；
  - `site`（独立站）：en、ru；
  - `social`（社媒）：en。
- 解析、写入、下载、阶段计算、前端常量都从这里取。前端的同名常量手写一份，并在注释里写明以后端为准。

**2. 存储：独立站不动，另外三个平台合一张表**
- 新表 `product_platform_content`：
  - 键：`tenant_id`、`product_id`、`platform`、`lang`，唯一；
  - 列：`title`、`selling_points`（每行一条）、`detail`（中国制造网商品详情，mediumtext）、`linkedin_post`、`facebook_post`、`youtube_description`；
  - 另有 `import_id`、软删除和审计列。
- 每个平台只用自己的列，其余留空。
- 写入：按块逐列更新，跳过的块不动，没有行先插入。
- **备选方案：** 每个平台一张表，表多且结构几乎相同；或通用的「块」表（平台、块名、内容），查询和展示都要拼装，字段长度也没法按列约束。都不采用。
- **为什么独立站不并进来**：它已有 FAQ 子表和数据，合并要迁移数据并改现有读写，收益不大。

**2b. 关键词库单独一张表**
- 新表 `product_platform_keyword`：
  - 列：`tenant_id`、`product_id`、`platform`、`lang`、`keyword`、`tier`（1 核心、2 主要、3 长尾）、`sort_order`（档内顺序）、`import_id`；
  - 另有软删除和审计列。
- 读取按 `tier, sort_order` 排序。
- 写入时整份替换：软删除该公司、该商品、该平台、该语言的旧词，再批量插入。
- 去重按小写比较，保留第一次出现的。
- **为什么不存成一列文本：** 词库不限个数，以后发品时要按档位取词（例如阿里只取前几个核心词），还可能统计词的使用情况。拆成行便于排序和按档位查询。
- **备选方案：** 用数字权重（1-100）。这要 AI 给出分数，人工也难判断，三档加顺序已经能表达「核心词排前面」，不采用。

**3. 进度与不发布**
- `product_content_task`：
  - 加 `alibaba_at/by`、`mic_at/by`、`social_at/by`；
  - 原 `en_at/by`、`ru_at/by` 改注释为独立站英文、俄文，不改名。改名要动现有映射，没有收益。
- 新表 `product_marketing_skip`：
  - 列：`tenant_id`、`product_id`、`platform`、`marked_at`、`marked_by`；
  - 唯一键 `(tenant_id, product_id, platform)`；
  - 撤销是软删除，再标记复用同一行并更新标记人与时间；
  - 重复标记不报错，保留原标记人。
- **备选方案：** 在任务表上加四个跳过列。标记人要另加四组列，也不便扩展平台，不采用。

**4. 阶段计算：按本公司启用平台动态拼接**
- 服务层读取启用平台，作为参数传给任务列表 SQL。
- MyBatis 按每个启用平台拼一个「未完成」条件：该平台写入时间为空，并且 `NOT EXISTS` 不发布标记；独立站是英文或俄文为空。
- 任一条件成立即为阶段 3，否则为 4。页签计数和筛选用同一段 SQL 片段。
- 页签计数先在子查询里算出阶段再分组。阶段表达式带参数，直接 `GROUP BY` 表达式会被 `only_full_group_by` 拒绝。
- Java 侧的 `FactTaskService.stage` 改为接收启用平台和不发布集合，保证商品详情和列表一致。
- 不发布标记表按 `tenant_id, product_id` 建索引，`NOT EXISTS` 走索引。

**5. 平台设置**
- 存 `sys_config`：
  - 键 `product.content.platforms`，值为逗号分隔的平台编码；
  - 平台模板（tenant 0）由迁移写入四个都启用；
  - 公司第一次保存时生成自己的行。做法照 `SourcingSettings`，新建 `MarketingPlatformSettings`。
- 读取时过滤掉无效编码。结果为空时按四个都启用处理，防止配置被手工改坏后任务全部算作完成。
- 接口（都在内容任务下）：
  - `GET /api/v1/product/content-tasks/platforms`：内容任务菜单；
  - `PUT /api/v1/product/content-tasks/platforms`：`product:content:edit`，至少一个；
  - `PUT /api/v1/product/content-tasks/products/{id}/skips/{platform}`：标记不发布，`product:content:edit`；
  - `DELETE /api/v1/product/content-tasks/products/{id}/skips/{platform}`：撤销不发布，`product:content:edit`。

**6. 营销包与解析**
- 下载接口：
  - 营销包加必填参数 `platform`，平台没启用时返回业务错误；
  - 资料包不变。
- 模版：
  - 拆为 `marketing-alibaba.md`、`marketing-mic.md`、`marketing-site.md`（原 `marketing-template.md` 改名）、`marketing-social.md`；
  - frontmatter 加 `platform` 行；
  - 文件名后缀改为平台编码，如 `型号.site.md`。资料包仍是 `型号.facts.md`。
- `ContentSection` 增加平台归属：
  - 新增章节：标题、关键词库、卖点、商品详情、LinkedIn、Facebook、YouTube 视频描述；
  - 原独立站章节归属 `site`；
  - 「标题」的别名只在阿里国际站、中国制造网包内生效。按平台过滤后再匹配，所以不会和「SEO 标题」冲突。
- `ContentDraft` 加 `platform`。解析器校验平台是否存在、语言是否允许；「本公司是否启用」在预览服务里校验（解析器不依赖租户）。
- 卖点按行拆分，去掉列表符号和序号，超过 5 条截断并给提示；单条超长的报块错误。
- 关键词库按表格解析（复用规格参数表的表格解析）。档位支持中文和英文写法；缺失或无效的报块错误。重复的词去掉并提示，超过 200 个截断并提示。
- 社媒和中国制造网商品详情保留原文，只去掉首尾空行。

**7. 写入**
- `ContentWriter` 营销路径按平台分发：
  - `site` 走原 `product_seo` 逻辑；
  - 其他平台写 `product_platform_content`。
- 写入后更新任务表对应的 `*_at/by`。确认时再校验一次平台是否启用，防止预览后设置被改。

**8. 前端**
- **任务列表**
  - 营销列按启用平台显示小标签：阿里、中制、独立站 EN、RU、社媒。已写入为绿色，未写入为灰色，不发布为划线灰色并在悬停时显示标记人。
  - 「下载营销包」改为下拉选择平台，表头和行内都是。
  - 表头加「平台设置」按钮，打开带四个勾选项的弹窗。
- **商品详情**
  - 运营内容卡片改为平台页签。原 `SeoCard` 拆出独立站页签内容，新增阿里、中制、社媒三个展示组件。
  - 每个页签顶部是状态条：写入状态、下载营销包、不发布开关。
- **上传抽屉**：预览分组标题加平台名。
- 先用 OpenPencil 出两张原型：任务列表的营销列和平台设置、详情页的平台页签。

## Risks / Trade-offs

- **[已完成的任务回退]** 新平台默认启用，原来「已完成」的任务都会变回「营销待生成」。→ 上线说明里提示公司先在平台设置里关掉不做的平台。开发库只有 3 行任务，影响可以忽略。
- **[动态 SQL 变复杂]** 阶段表达式随启用平台变化。→ SQL 片段只在一处生成；用集成测试覆盖启用平台组合、不发布、独立站缺一种语言这几种情况。
- **[平台模板改动影响所有未设置的公司]** 平台账号的设置就是默认值。→ 平台设置弹窗里给平台账号加一行说明。
- **[社媒内容长]** 帖子带表情符号，库表须为 utf8mb4。→ 新表使用 utf8mb4，测试数据包含表情符号。

## Migration Plan

- Flyway `V1.2.53__platform_marketing_content.sql`：
  - 新建 `product_platform_content`、`product_platform_keyword`、`product_marketing_skip`；
  - `product_content_task` 加 `alibaba_at/by`、`mic_at/by`、`social_at/by`，并更新 `en_at`、`ru_at` 的注释；
  - 插入 tenant 0 的 `sys_config` 行 `product.content.platforms = alibaba,mic,site,social`。
- 只有结构变更和一条默认配置，不回填数据。
- 部署后通知运营：营销包要按平台重新下载。
