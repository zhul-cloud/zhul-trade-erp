# 核心数据模型

## v1.0.0 — 系统基础 & 用户域

SQL 文件：`sql/build/sql/schema_v1.sql`

### 表清单（共 20 张）

| # | 表名 | 说明 | tenant_id |
|---|------|------|-----------|
| 1 | `tenant_package` | 租户套餐 | — |
| 2 | `tenant` | 租户 | — |
| 3 | `resource` | 菜单/按钮资源（平台级，不隔离） | — |
| 4 | `role` | 角色 | ✅ |
| 5 | `role_resource` | 角色-资源关联 | — |
| 6 | `role_org` | 角色-机构关联 | — |
| 7 | `department` | 部门 | ✅ |
| 8 | `position` | 岗位 | ✅ |
| 9 | `user_basic` | 用户基本信息 | ✅ |
| 10 | `user_department` | 用户-部门关联 | — |
| 11 | `user_position` | 用户-岗位关联 | — |
| 12 | `account` | 登录账号 | ✅ |
| 13 | `account_local_auth` | 账号密码认证 | — |
| 14 | `account_access_token` | 访问Token | — |
| 15 | `account_role` | 账号-角色关联 | — |
| 16 | `account_org` | 账号-机构关联 | — |
| 17 | `dict` | 字典（平台级） | — |
| 18 | `sys_config` | 系统配置 | — |
| 19 | `sys_log` | 操作日志/登录日志 | ✅ |
| 20 | `chinese_area_code` | 中国行政区编码 | — |

### 核心实体关系

```
tenant_package ──< tenant
                     │
              ┌──────┼──────┐
           role    department  position
              │         │         │
         account_role  user_department  user_position
              │              └────────────┘
           account ──────── user_basic
              │
      account_local_auth
      account_access_token

resource ──< role_resource >── role
```

### AUTO_INCREMENT 起始值设计

| 表 | 起始值 | 原因 |
|----|--------|------|
| `tenant` | 1000 | 租户编号从 TN1000 开始 |
| `resource` | 100000 | 资源编码 RS100000 |
| `role` | 100 | 预留前 100 给系统内置角色 |
| `department` | 10000 | 部门编码 DP10000 |
| `position` | 10000 | 岗位编码 POS10000 |
| `user_basic` | 1000000000 | 用户ID 10位，全局唯一 |
| `account` | 10000000 | 账号ID 8位起 |

### 字段规范（适用全项目）

```sql
-- 所有表必填审计字段
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
`update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
`update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',

-- 业务表额外必填
`tenant_id`  int(11)  NOT NULL DEFAULT 0 COMMENT '租户ID',
`deleted_at` datetime NULL COMMENT '软删除时间，NULL表示未删除',

-- 金额三元组（外贸业务表）
`amount_original` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '原币金额',
`currency_code`   CHAR(3)       NOT NULL DEFAULT 'USD' COMMENT '币种（ISO 4217）',
`exchange_rate`   DECIMAL(18,6) NOT NULL DEFAULT 1.000000 COMMENT '汇率（原币→本位币）',
`amount_cny`      DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '本位币金额（CNY）',
```

### sys_log type 枚举

| 值 | 含义 |
|----|------|
| 1 | 登录日志 |
| 2 | 操作日志 |
| 9 | 其他 |

---

## v1.1.0 — 主数据域 & 询盘中心

SQL 文件：`sql/build/sql/schema_v1.1.sql`（依赖 `schema_v1.sql` 先执行，不修改 v1.0.0 任何已有表）

来源：`openspec/changes/add-inquiry-management/`（proposal.md / design.md / specs），PRD 见
`docs/02-产品PRD/03-业务域/00-询盘中心/00-询盘单/询盘单-PRD-V1.0.md`。

### 表清单（新增 8 张）

| # | 表名 | 说明 | tenant_id |
|---|------|------|-----------|
| 1 | `customer` | 客户主数据（最小可用） | ✅ |
| 2 | `supplier` | 供应商主数据（最小可用） | ✅ |
| 3 | `customer_inquiry` | 客户询盘（原始诉求全貌） | ✅ |
| 4 | `inquiry_order` | 询盘单（按品牌+品类拆分的可分配单元） | ✅ |
| 5 | `inquiry_order_item` | 询盘单明细 | ✅ |
| 6 | `inquiry_order_supplier` | 询盘单-报价来源关联（正式供应商/电商询价渠道） | ✅ |
| 7 | `inquiry_order_item_quote` | 型号×报价来源交叉报价表 | ✅ |
| 8 | `ai_task` | 通用 AI 任务（Java↔AI编排服务异步契约，非询盘专属） | ✅ |

### 核心实体关系

```
customer ──< customer_inquiry ──< inquiry_order ──< inquiry_order_item
                    │                    │                   │
                 ai_task            ai_task          inquiry_order_item_quote
                                         │                   │
                                inquiry_order_supplier ──────┘
                                         │
                                     supplier（source_type=1 时关联；
                                     source_type=2 电商询价渠道时为空）
```

### 关键设计点（详见 design.md 决策记录）

- `inquiry_order.customer_inquiry_id` 可空：支持跳过 AI 手动创建询盘单
- `ai_task` 是通用表，不与询盘业务耦合，未来新 skill 复用同一张表（`skill_id` 区分）
- `inquiry_order_supplier.supplier_id` 可空：`source_type=2`（电商询价渠道，如淘宝/1688/闲鱼）时用 `channel_platform`/`channel_name`/`channel_link` 代替，不创建 `supplier` 主数据记录（决策11）
- `inquiry_order_item_quote` 关联 `inquiry_order_supplier_id`（不直接存 `supplier_id`），正式供应商与电商询价渠道两种来源统一取报价方信息
- 询盘相关业务编号（`inquiry_code`/`item_code`）沿用"日期+流水号"惯例，与部门/岗位这类内部管理编码（前缀+自增主键）分开，不占用 AUTO_INCREMENT 起始值设计

---

## v1.2.0 — 商品主数据

SQL 文件：`sql/build/sql/schema_v1.2.sql`（依赖 `schema_v1.sql`、`schema_v1.1.sql` 先执行，不修改任何已有表）；菜单与按钮权限种子：`sql/build/data/data_v1.2.sql`

来源：`openspec/changes/archive/2026-09-20-add-product-master-core/`（proposal.md / design.md 决策 1–14 / specs），PRD 见
`docs/02-产品PRD/02-商品域/00-商品主数据/商品主数据-PRD-V1.0.md`。

### 平台级共享数据（与其他表的重要区别）

本版本 13 张表**全部是平台共享数据**：`tenant_id` 列保留（沿用统一的租户过滤与索引约定），但**值固定为 0**，所有租户读到同一份数据；写入必须同时满足权限码（`product:*`）和平台账号（JWT `tenantId=0`，由 `PlatformScopeGuard` 判定），读取只要求登录。

### 表清单（新增 13 张）

| # | 表名 | 说明 |
|---|------|------|
| 1 | `product_brand` | 品牌（含 `is_genuine` 原厂/兼容标记） |
| 2 | `product_category` | 品类（`category_code` 沿用独立站 URL，有商品后不可改） |
| 3 | `product_series` | 系列（归属品牌） |
| 4 | `product` | 商品主表（Part Number 实体，唯一键 `tenant_id + brand_id + mpn_normalized`，含已软删除行） |
| 5 | `product_specification` | 规格参数（整体替换保存） |
| 6 | `product_relationship` | 型号关系（替代 / 兼容 / 交叉引用，含置信度） |
| 7 | `product_document` | 技术资料（只存文件地址） |
| 8 | `product_application` | 应用场景 |
| 9 | `product_faq` | FAQ（`source=3` 待审核，不对租户账号返回） |
| 10 | `product_media` | 图片与视频（一个商品一张主图） |
| 11 | `product_logistics` | 物流信息（一对一） |
| 12 | `product_customs` | 海关信息（一对一，HS 编码只存数字） |
| 13 | `product_reference_price` | 平台参考价（一对一，原币 + 币种 + 汇率 + 本位币，不是报价） |

### 关键设计点

- 型号归一化：NFKC → 小写 → 去掉所有非字母数字，存 `mpn_normalized`；原始型号 `mpn_raw` 只去首尾空格
- 所有表软删除（`deleted_at`）；商品被引用后不可删除（`ProductUsageChecker` 扩展点），已软删除的型号仍占唯一键，重复新建走"恢复"
- `product_specification` 的 `(product_id, spec_key)` 只建普通索引：整体替换会软删旧行，唯一键会拦住相同编码的重新插入
- 品牌、系列名称的大小写不敏感来自 `utf8mb4_0900_ai_ci`；该排序规则不补齐尾部空格，所以首尾空格由 Service 去除
- 档案完整度（10 个模块）不落库，由各子表实时计算

---

## 后续版本规划

| 版本 | 域 | 核心表（待设计） |
|------|----|-----------------|
| v2.0 | 业务域 | quotation, sales_order, order_item |
| v2.1 | 采购域 | purchase_order, purchase_item |
| v2.2 | 仓储域 | warehouse, inventory, stock_in, stock_out |
| v2.3 | 配送域 | shipment, logistics_tracking |
| v3.0 | 财务域 | voucher, receivable, payable, payment |
