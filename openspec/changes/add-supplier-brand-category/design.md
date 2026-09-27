## Context

- 询盘解析（`scripts/ai-orchestrator/skills/inquiry-parser`）输出的 `brand` 是客户原文（"中文品牌写中文，英文品牌保留英文原文"），`category` 取自固定的 38 项中文枚举；拆单（`order-splitter`）按「品牌 + 品类」把一次询盘拆成多张询盘单，`inquiry_order.brand` / `category` 与明细上的同名字段都是自由文本
- 商品主数据是平台级共享（`tenant_id = 0`），写入仅限平台账号：`product_brand`（`brand_name` 唯一、无别名）、`product_category`（一级、英文名、`category_code` 用于独立站地址，`parent_id` 已预留未使用）。商品 `product.category_id` 只挂一级品类。品类规格的 Purpose 写明品类"也是询盘拆单的分组依据"
- 供应商表有自由文本 `main_brands`（`enrich-supplier-basic-info` 已从管理页隐藏，仍由询盘快速创建写入）
- 开发库只有一级品类 `plc`（名称 PLC，挂 1 个商品）和品牌 Delta；系统种子数据以《公司主营产品》表（`llm-wiki/raw/assets/sales/sop/01-公司主营产品.xlsx`）为准（2026-09-26 确认）

## Goals / Non-Goals

**Goals:**
- 品类、品牌形成一套全系统共用的词表：品类两级（一级给独立站、二级对应询盘细分品类），品牌带别名
- 供应商主营产品结构化录入（品牌 → 二级品类），可筛选；手填品牌不丢，由平台归并
- 为下一个变更（询盘单自动推荐供应商）备好可直接查询的数据

**Non-Goals:**
- 匹配 / 推荐逻辑、AI 解析改造、商品挂二级品类、独立站展示二级品类

## Decisions

### 1. 数据结构

```
product_category (tenant 0)          product_brand (tenant 0)
 id, parent_id NULL=一级              id, brand_name
 category_code, category_name(英)          │
 category_name_zh  ◀── 新增                 │ 1:N
      ▲                              product_brand_alias  ◀── 新增
      │ category_id (NULL=全部品类)      id, brand_id, alias, alias_key(唯一)
      │                                     ▲
supplier_product_scope (租户)  ◀── 新增     │ brand_id (NULL=待确认)
 id, tenant_id, supplier_id ────────────────┘
 brand_id NULL | pending_brand_name, pending_key
 category_id NULL
 deleted_at + 系统字段
```

- `supplier_product_scope` 一行 = 供应商 × 品牌（正式或待确认）× 一个二级品类；某品牌"全部品类"存为一行 `category_id = NULL`。例：「Siemens：PLC、HMI」存两行，「ABB：全部品类」存一行。这种形状让下一步的匹配查询是简单的 `WHERE brand_id = ? AND (category_id = ? OR category_id IS NULL)`
- `pending_key` = 待确认名去首尾空格、转小写，用于跨供应商汇总和确认时批量关联
- `product_brand_alias.alias_key` 同样规则，唯一索引；与 `product_brand.brand_name` 的交叉唯一（别名不能等于其他品牌的名称）由服务层校验
- 索引：`supplier_product_scope` 上 `(tenant_id, supplier_id)`、`brand_id`、`category_id`、`pending_key`、`deleted_at`
- 不加外键；保存主营产品时整体替换：软删除旧行、插入新行（行本身没有需要保留的身份）

### 2. 品牌归一（`BrandResolver`，放 `product` 模块）

`resolve(text) → brandId | null`：按 `lower(trim(text))` 先比品牌名，再比别名。供应商保存主营产品、询盘快速创建、存量迁移都走它；下一个变更里询盘匹配也用它。启动时不缓存，查询走索引（品牌量级是几百）。

- **别名按品牌属性处理**：编辑品牌时整体替换它的别名列表（与改品牌简介同性质），`product_brand_alias` 不设软删除字段
- **筛选口径**：同时按品牌和品类筛选时，「该品牌全部品类」算命中；只按品类筛选时只命中明确选了该品类的供应商——全部品类不对应具体品类，算进去会让单独按品类筛选失去意义

### 3. 待确认品牌确认（平台操作）

- 放在客商模块（数据在 `supplier_product_scope`），调用商品模块的品牌服务新增别名或品牌；接口 `/api/v1/masterdata/pending-brands`，与商品主数据其他写操作一样要求品牌权限且必须是平台账号
- 列表：`SELECT pending_key, MIN(pending_brand_name), COUNT(DISTINCT supplier_id) FROM supplier_product_scope WHERE brand_id IS NULL AND deleted_at IS NULL GROUP BY pending_key`，**跨租户读取**——只有平台账号可调，只返回名称和计数，不返回租户、供应商；代码处加注释标明这是有意的跨租户查询
- 设为别名 / 新建品牌：同一事务内 ① 写别名或新建品牌 ② `UPDATE … SET brand_id = ?, pending_brand_name = '', pending_key = '' WHERE pending_key = ?` ③ 对受影响的供应商逐个合并同品牌重复行（任一行 `category_id IS NULL` 则只保留一行全部品类，否则按品类去重）

### 4. 品类两级

- 规则：`parent_id` 为空是一级；二级的父必须是一级；二级 `category_name_zh` 必填；删除一级前必须没有二级；商品保存时校验 `category_id` 是一级
- 接口：新增 `GET /api/v1/product/categories/tree`（全部租户可读，含停用标记）；原 `options` 接口加参数 `level`，不传时仍只返回一级，商品页不受影响
- 独立站相关的编码规则、"有商品后编码不可改"对二级同样生效

### 5. 初始数据（迁移脚本）

以《公司主营产品》表（`llm-wiki/raw/assets/sales/sop/01-公司主营产品.xlsx`）为准（表结构：品类 → 品牌 → 系列）。全部按编码 / 名称幂等写入，已存在的不改动；SQL 由脚本从表格生成，品牌名、别名、系列与表格逐条对应。

| 一级编码 / 英文名 / 中文名 | 二级（编码：中文名） |
|---|---|
| `plc` Programmable Logic Controller 可编程控制器 | `plc_cpu`：PLC、`io_module`：I/O模块、`comm_module`：通信模块、`industrial_network`：工业网络 |
| `hmi` Human Machine Interface 人机界面 | `hmi_panel`：HMI |
| `inverter` AC Inverter 变频器 | `vfd`：变频器、`soft_starter`：软启动器 |
| `servo` AC Servo 伺服 | `servo_drive`：伺服驱动器、`servo_motor`：伺服电机 |

- 一级只有表中 4 类；表格没有细分品类，细分只保留这 4 类下供应商主营和询盘匹配用得到的 9 个。其他品类（传感器、低压电器、液压等）不预置，需要时由平台在品类页添加
- 一级编码 `plc` 与开发库已有的一级品类同编码，迁移直接复用它、只补中文名，不再另建
- 品牌 24 个，统一为官方写法（如 MITSUBISHI → Mitsubishi、WE!NTEK → Weintek、Allen Bradley → Allen-Bradley、Schneider → Schneider Electric、LS → LS Electric），与官方写法不只差大小写的表格原写法作为别名；另预置常用中文别名（三菱、欧姆龙、西门子、施耐德、台达、安川等，共 35 个别名）；原产地按系统国家清单的英文名填写，HIDER 不确定留空
- 系列 182 条（表中同品牌重复的系列已合并），挂到对应品牌；品牌名称或别名已存在时挂到已有品牌
- 表格里「Huam Machine Interface」是笔误，入库为 Human Machine Interface

### 6. 存量 `main_brands` 迁移

SQL 做不了别名归一，放在应用启动任务里（与客户 `name_key` 补算同样的幂等写法）：处理 `main_brands <> ''` 且该供应商还没有任何主营产品行的记录；按 `[,，、/;；]` 拆分、去空，逐个 `BrandResolver.resolve`；去重后写入全部品类行。`main_brands` 列保留，写入入口全部去掉

### 7. 前端

- 供应商表单页新增「主营产品」卡片：每行 = 品牌选择（可搜索主数据，可直接输入新名称作为待确认）+ 二级品类多选（按一级分组，不选即全部品类）+ 删除；「添加品牌」追加一行
- 详情页：按品牌分行显示「Siemens：PLC、HMI」「ABB：全部品类」，待确认品牌带灰色「待确认」标签
- 列表：筛选区加「主营品牌」「主营品类」；表格加「主营品牌」列（最多 3 个标签 + "等 N 个"）
- 商品品类页：沿用现有的一级品类卡片布局，每张卡片里列出它的细分品类（标签）并提供「+ 细分品类」；细分品类的新增 / 编辑弹窗含「上级品类」「中文名称」
- 品牌页：编辑弹窗加别名输入（标签式）；新增「待确认品牌」页签，行操作「设为别名」（选品牌）、「新建为品牌」
- `SupplierQuickCreateModal`：「主营品牌」改为可输入的多选
- 以上新增界面按项目约定先用 OpenPencil 出原型

## Risks / Trade-offs

- **[风险] 询盘解析的 38 项品类枚举多于种子里的 9 个细分品类**：下一个变更（询盘匹配）需要把解析结果映射到品类主数据，映射不上的品类由平台先补细分品类
- **[风险] 待确认品牌长期堆积**：平台不处理时，这些供应商在下一步匹配里只能靠名称模糊匹配 → 品牌页签上显示待确认数量作为提醒；匹配变更里对待确认品牌按名称兜底
- **[权衡] 二级品类只给供应商和询盘用，商品仍挂一级**：商品也挂二级会牵涉独立站 URL 和已有商品迁移，收益要等匹配上线后再评估
- **[权衡] 主营产品整体替换保存**：行没有独立身份，整体替换最简单；代价是每次保存都会产生软删除行，数据量（每供应商最多几十行）可以接受
- **[跨租户读取]** 待确认品牌汇总跨租户，只暴露名称和计数，不暴露租户与供应商
