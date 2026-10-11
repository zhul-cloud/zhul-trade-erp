## Context

品类匹配现在只有一处实现：`ProductArchiver.suggestCategory`，三轮匹配，命中细分就上卷到一级。询盘这边 AI 从 `inquiry-parser` 技能里写死的约 40 个品类中选，`inquiry_item.category`、`sourcing_task.category` 只存文字；询价任务按 `brandKey|category` 分组，分配规则按文字精确比较。商品保存时 `ProductServiceImpl` 拒绝细分品类。

## Goals / Non-Goals

**Goals：** 商品、候选、询盘型号都能落到细分品类；一套匹配规则多处共用；询价任务的粒度不变粗也不变细（按一级品类）；已有分配规则不失效。

**Non-Goals：** 不回填历史型号明细与询价任务的品类 ID；不改供应商主营产品；不改独立站发布逻辑本身（现在没有按品类发布的代码，只在规格上约定细分归入上级）。

## Decisions

**1. 匹配规则抽成公共服务 `CategoryMatcher`（商品模块）**
- 输入品类文字和启用品类清单，输出品类 ID（细分或一级）或空；「其他」兜底由调用方决定是否使用（候选要兜底；询盘确认关联不上时只留文字，不兜底成「其他」，否则询价任务会都并到「其他」里）。
- 每轮先收集命中品类，再按所属一级归并：多个一级 → 空并停止；一个一级 → 恰好一个细分命中取细分，否则取一级；第②轮命中一级后在它的细分里按第③轮的比较方式再找一次，唯一则细化。
- 同时提供 `rootOf(categoryId)`，询价任务分组、分配规则、商品筛选都用它取一级。
- 品类清单每次调用时从库里查（一百多行），不另加缓存；需要时复用 `CategoryServiceImpl` 已有的选项缓存。

**2. 数据结构**
- `inquiry_item.category_id int NULL`、`sourcing_task.category_id int NULL`（任务存一级品类 ID），都加索引；`category` 文字列保留，新数据写所关联品类的中文名（任务写一级中文名）。不回填历史。
- 商品不加字段，`product.category_id` 直接允许细分品类。

**3. AI 解析**
- 后端在解析请求的 `input` 里加 `categories`：启用品类的 `code`、`nameZh`、`name`、`parentCode`，按排序。品类表是唯一来源，编排服务不再维护品类清单。
- `server.py` 把清单拼进提示词，覆盖 `inquiry-parser` 里的「category 合法枚举值」；每个型号新增输出 `categoryCode`；分组的 `category` 改为一级品类中文名（只影响话术模版里的品类称呼）。`inquiry-parser/SKILL.md` 的枚举段改成「品类从请求带来的品类清单中选」，单位换算表里的品类称呼不动。
- 后端生成草稿行时：`categoryCode` 有效 → 关联该品类，品类文字取其中文名；否则用 `CategoryMatcher` 匹配行上的品类文字（取 AI 分组的 `category`）。草稿行新增 `categoryId`。
- 占位服务 `ai-orchestrator-stub` 返回的固定数据补上 `categoryCode`（西门子 → `plc_cpu` 对应的编码，施耐德 → 对应细分），以实际编码为准。

**4. 确认与询价任务**
- `ConfirmRowRequest` 加 `categoryId`；有且有效则用它，否则 `CategoryMatcher` 匹配文字，匹配不上只留文字。
- 分组键：有品类 ID 时 `brandKey|root:{一级ID}`，否则 `brandKey|text:{品类文字}`。确认页前端预览用同一规则（下拉选项带 `parentId`，可算一级）。
- 分配规则：品类规则命中条件 = 文字相等，或 `rootOf(match(value)) == task.category_id`。

**5. 商品与筛选**
- 去掉「商品只能选择一级品类」校验；错误码 `CATEGORY_LEVEL_INVALID` 若因此没有其他引用，一并删除（前端若有对应提示映射也删）。
- 商品列表、内容任务按 `categoryId` 筛选时：传入的是一级品类就展开为「它 + 它的细分」做 `IN` 查询。
- 品类列表商品数：一级品类 = 自己 + 细分之和。「品类下已有商品不能改为细分品类」这条限制的理由是商品只能挂一级，随之去掉；「删除」「改编码」等限制按各自引用判断，不变。
- 前端商品表单、候选审核的品类下拉改用 `BrandCategoryPicker` 同样的分组方式（一级为组标题且自身可选，下列细分）。商品列表的品类筛选按钮仍只列一级。

**6. 候选**
- `upsert` 时：型号明细有 `category_id` 且启用 → 直接用；否则 `CategoryMatcher` + 兜底「其他」。
- `CandidateBackfill` 启动时：待审核、建议品类为一级的候选，用新规则重算，结果是该一级的细分时更新。只更新 `category_id` 等于该一级的行（条件更新，防并发覆盖）。

## Risks / Trade-offs

- 分配规则按一级命中会变宽：「伺服驱动器 → 李工」现在也会接到只有伺服电机的任务。任务本来就按一级拆，规则无法比任务更细；在规则页把匹配说明写清楚。
- AI 可能给出不在清单里的编码或选错层级，靠后端校验 + 文字兜底，业务员在确认页可改。
- 提示词变长（140 个细分品类约 3K 字），解析耗时与费用略增。

## Migration Plan

- Flyway `V1.2.51__sub_category_match.sql`：两张表加列与索引。可重复执行性按项目习惯（结构变更只执行一次）。
- 部署后重启 AI 编排服务以加载新提示词；旧编排服务收到多出的 `categories` 字段会忽略，不返回 `categoryCode` 时后端走文字匹配，兼容。
