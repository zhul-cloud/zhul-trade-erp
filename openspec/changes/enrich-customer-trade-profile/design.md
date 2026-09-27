## Context

- `customer` 表（`V1.1__master_data_and_inquiry.sql`）只有 name / country / contact_name / contact_phone / contact_email / status。`add-customer-supplier-management`（已实现、未归档）加了分页、更新、启停、删除接口和弹窗式管理页 `src/pages/partner/customer/index.tsx`，菜单资源在 `V1.2.7`（客户管理 100061，按钮 110141–110144）
- 客户被询盘模块使用：
  - 后端 `CustomerInquiryServiceImpl` 在内部调用 `customerService.getById` 校验客户存在、调用 `customerService.search(name)` 按客户名筛选询盘
  - 前端询盘列表用 `GET /customers/{id}` 逐个取客户名；询盘录入用 `GET /customers?keyword=` 做选择器、`components/CustomerQuickCreateModal` 快速创建（依赖 `duplicate` / `force` 语义）
- 数据权限配置已存在但从未被用于过滤：`role.permission_scope`（0 无权限 / 1 全部 / 2 自定义 / 3 仅本人），自定义部门在 `role_org(role_code, org_code)`；用户主角色 `user_basic.role_code`、主部门 `user_basic.dept_id`、附属部门 `user_department(user_id, dept_code)`；部门树 `department(id, pid, code)`；管理员 `account.admin_flag=1`
- 国家清单 `product/support/CountryCatalog`（ISO 3166-1，保存英文名，`canonicalName()` 校验）
- 刚完成的供应商变更 `enrich-supplier-basic-info` 已确立：独立表单页 / 详情页结构、编码自动生成、导出、批量删除、`BizException.of(errorCode, …)` 字段级报错的做法，本次沿用
- 开发库 `customer`、`customer_inquiry` 当前均无数据

## Goals / Non-Goals

**Goals:**
- 按 PRD V1.1 与原型交付外贸客户档案（含单证主体子表）、按负责人的数据权限、转移、查重、删除限制、导出
- 询盘侧（快速创建、选择器、名称回显、按客户名筛选）继续可用，行为变化只有查重和国家必填

**Non-Goals:**
- 把数据权限推广到询盘等其他业务数据（本次只做通用解析组件 + 客户接入）
- 联系人模块、等级自动计算、信用额度管控、单据生成、批量导入、与小满同步

## Decisions

### 1. 表结构

`customer` 表新增字段（迁移 `V1.2.9__customer_trade_profile.sql`，保留现有列名，`name` 即英文名称）：

| 字段 | 类型 | 说明 |
|---|---|---|
| customer_code | varchar(20) NOT NULL DEFAULT '' | 索引 |
| name_key | varchar(200) NOT NULL DEFAULT '' | 规范化名称，查重用；联合索引 (tenant_id, country, name_key) |
| name_cn / short_name | varchar(100) / varchar(50) | |
| customer_role | tinyint(2) DEFAULT 0 | 0 未设置、1 终端用户、2 系统集成商、3 经销商、4 贸易商、5 OEM 设备厂、6 维修服务商、7 其他 |
| industry | tinyint(2) DEFAULT 0 | 0 未设置、1 汽车制造 … 13 其他（13 项，见常量） |
| website | varchar(200) | |
| customer_grade | tinyint(2) DEFAULT 0 | 0 未分级、1 A、2 B、3 C |
| source_channel | tinyint(2) DEFAULT 0 | 0 未设置、1 阿里巴巴国际站 … 8 其他 |
| owner_id | bigint(20) DEFAULT 0 | 负责业务员 user_basic.id，0=未分配；索引。类型与 `customer_inquiry.owner_id` 一致 |
| external_ref | varchar(50) | 小满客户编号 |
| state / city / postcode / address | varchar(100/100/20/300) | |
| tax_id | varchar(50) | |
| timezone | varchar(64) | IANA 名，如 Europe/Berlin |
| contact_title / whatsapp / other_im | varchar(50/30/100) | contact_name 放宽到 100，contact_phone 保持 32 |
| currency | char(3) DEFAULT 'USD' | ISO 4217 |
| incoterm | varchar(3) DEFAULT '' | EXW/FCA/FOB/CFR/CIF/CPT/CIP/DAP/DPU/DDP |
| incoterm_place | varchar(100) | |
| payment_method | tinyint(2) DEFAULT 0 | 0 未设置、1 T/T 全额预付、2 T/T 定金+发货前尾款、3 T/T 定金+见提单副本尾款、4 L/C 即期、5 L/C 远期、6 D/P、7 D/A、8 O/A、9 其他 |
| deposit_ratio / payment_days | tinyint unsigned NULL / smallint NULL | |
| credit_limit | DECIMAL(18,2) NULL | 限额配置，不是交易金额：只存原币 + credit_currency，不存本位币（使用时按当日汇率折算，应收模块再做） |
| credit_currency | char(3) DEFAULT '' | |
| shipping_method | tinyint(2) DEFAULT 0 | 0 未设置、1 海运整柜、2 海运拼箱、3 空运、4 国际快递、5 铁路、6 陆运 |
| destination_port | varchar(100) | |
| remark | varchar(500) | |

新表 `customer_party`：id、tenant_id、customer_id（索引）、party_type tinyint（1 收货人 / 2 通知方 / 3 发票抬头）、company_name、country、state、city、postcode、address、contact_name、phone、email、tax_id、destination_port、is_default tinyint、remark、deleted_at 及系统字段。不加外键。

- 可空数值字段（deposit_ratio、payment_days、credit_limit）在 DO 上用 `@TableField(updateStrategy = ALWAYS)`，与供应商相同，`updateById` 只传完整查出的实体
- 按钮资源：110145 转移客户 `partner:customer:transfer`、110146 导出客户 `partner:customer:export`（pid 100061），加入标准版、旗舰版套餐
- 存量数据：编码按 `CUS` + 补零主键回填；`owner_id` 按 `create_by` 匹配 `user_basic.username` 回填，匹配不到为 0；`name_key` 由应用启动时的一次性补算任务填充（规范化规则在 Java 里，SQL 不好复刻），只处理 `name_key=''` 的行，幂等

### 2. 数据权限解析（`framework/security/DataScopeResolver`）

```
resolve(currentUsername) → DataScope { all: boolean, ownerIds: Set<Long> }

account.admin_flag = 1           → all
user_basic 不存在（非管理员）      → ownerIds = {}，什么都看不到
role.permission_scope = 1         → all
role.permission_scope = 2         → 部门集合 = role_org 的部门 + 其全部下级部门（department.pid 递归）
                                     ownerIds = 这些部门的用户（主部门或附属部门命中）∪ {自己}
role.permission_scope = 0 / 3 / 无角色 → ownerIds = {自己}
```

- 只在需要的请求里调用一次（不缓存到 Redis：角色和部门变更后要立即生效，查询量是几条小表查询）
- 「无权限」按「仅本人」处理：业务员至少能看到自己负责的客户，否则连自己建的客户都找不到。这一点与字面含义不同，写在注释里
- 通用组件放 `framework/security`，与 `PermissionChecker` 并列；客户服务用 `DataScope.apply(wrapper, column)` 追加 `owner_id IN (...)` 条件

### 3. 带权限与不带权限的两套查询

| 调用方 | 方法 / 接口 | 是否按数据权限过滤 |
|---|---|---|
| 询盘后端校验客户、按名称筛选询盘 | `CustomerService.getById`、`search`（保持现有签名） | 否 |
| 询盘列表回显客户名 | `GET /customers/{id}`，**改为只返回引用信息**（id、编码、名称、国家、状态） | 否 |
| 客户管理详情、编辑取数 | 新增 `GET /customers/{id}/detail` | 是 |
| 询盘录入的客户选择器 | `GET /customers?keyword=` | 是（业务员只能选自己范围内的客户） |
| 列表、导出、更新、删除、启停、转移 | 对应接口 | 是，越权统一返回「客户不存在或无权查看」 |

`GET /customers/{id}` 收窄返回字段，是为了在不做权限过滤的前提下不泄露联系人、交易条件等信息。

### 4. 查重

- `name_key = normalize(name)`：转小写 → 非字母数字替换为空格 → 反复去掉结尾的公司后缀词（co, company, ltd, limited, llc, inc, incorporated, corp, corporation, gmbh, ag, sa, sas, srl, bv, nv, pty, plc, llp, kg, oy, ab, as, spa）→ 去掉空格。例：`ABC Automation GmbH` → `abcautomation`
- 查重条件：同租户、未删除、同国家、同 name_key、排除自身；**不加数据权限条件**
- 命中返回错误码 `CUSTOMER_DUPLICATE`，message「该客户已存在，负责业务员：张伟」，detail 里带 `existingId`、`selectable`（已有客户是否在操作人范围内）。快速创建弹窗用这两个字段决定是否提供「使用该客户」
- 去掉原来的 `duplicate` / `force` 返回形态（**BREAKING**），`SaveCustomerRequest.force` 删除；快速创建弹窗同步改造

### 5. 单证主体保存

- 客户新增 / 更新请求带 `parties: [{id?, partyType, …, isDefault}]`，服务在同一事务内：按 id 比对 → 更新已有、插入新增、软删除请求里没有的；然后逐类型修正默认（多条默认 → 保留请求里最后一条标记的；无默认 → 最早创建的一条）
- 上限 20 条、英文字段、国家清单校验在服务端做

### 6. 转移

- `POST /customers/transfer {ids, ownerId, reason}`，权限 `partner:customer:transfer`
- 校验：每个 id 在操作人范围内；新负责人存在、在职（user_basic 状态启用）、在操作人范围内（范围为「全部」时任意本租户在职用户）
- 每个客户写一条操作日志：`LogService` 新增公共方法 `recordOperateLog(menu, operation, before, after)`（沿用 `recordForceLogoutOperateLog` 的写法，把私有实现提成公共方法），menu=「客户管理」，operation=「转移客户」，before={ownerId, ownerName}，after={ownerId, ownerName, reason}
- 新增客户时指定他人为负责人同样校验在范围内

### 7. 删除

- 被引用判定：`customer_inquiry` 或 `inquiry_order`（手动创建的询盘单也直接引用客户）中存在 `customer_id` 匹配且未删除的记录。用 `CustomerMapper` 上的一条 UNION 查询实现，避免客户模块依赖询盘模块；以后报价、订单模块上线时在同一处补充
- 批量删除：`POST /customers/batch-delete {ids}`，返回 `{deleted, referenced, missing}`

### 8. 时区

- 数据：npm 包 `countries-and-timezones`（MIT）的国家 → 时区列表，一次性转换为 `src/main/resources/masterdata/country-timezones.json`（键为 ISO 两位代码，值为 IANA 时区数组，第一个为默认），`GET /api/v1/masterdata/country-timezones` 返回
- 前端：选择国家后，若该国只有一个时区自动带出；多个时区时列出该国时区供选择。当地时间、时差用浏览器 `Intl.DateTimeFormat` 按 IANA 名计算
- 显示格式为「Europe/Berlin（UTC+01:00）」，不做时区中文名（原型里写的「UTC+01:00 柏林」需要维护一份中文城市名，收益不大）
- 服务端用 `ZoneId.of` 校验合法

### 9. 英文字段校验

PRD 原写"只允许 ASCII 可打印字符"，但原型示例的德国地址 `Industriestraße`、城市 `München` 都含变音字母，ASCII 校验会误伤欧洲客户的真实地址。这条规则要防的是业务员误填中文，所以实现为"不允许中日韩文字"（`\p{IsHan}`、平假名、片假名、韩文），拉丁字母的变音符号放行。

### 10. 枚举

角色、行业、等级、来源、付款方式、运输方式、贸易术语、币种都放后端 `CustomerConstants` 与前端 `constants.ts`，码值一致。系统虽有 `sys_currency_type` 字典类型，但没有任何字典项，本次不接入字典，等币种主数据时统一替换。

### 11. 前端

- `src/pages/partner/customer/`：`index.tsx`（列表）、`form.tsx`（新增 / 编辑）、`detail.tsx`、`components.tsx`（标签、卡片、单证卡片、转移弹窗、单证抽屉）、`constants.ts`、`service.ts`，结构同供应商
- 国家选择复用 `pages/product/components/useCountries`
- 负责业务员下拉：新增接口 `GET /api/v1/masterdata/customers/assignable-owners` 返回操作人范围内的在职用户（id、姓名、部门名），新增页与转移弹窗共用
- `assignable-owners` 同时返回操作人的范围类型（ALL / CUSTOM / SELF）；「负责业务员」筛选和列只在 ALL、CUSTOM 时显示
- `CustomerQuickCreateModal`：国家改为下拉必填，处理 `CUSTOMER_DUPLICATE`（可选用时显示「使用该客户」）
- `services/zhul/masterdata.ts`：`CustomerItem` 只加可选字段；`getCustomer` 注释说明只返回引用信息

## Risks / Trade-offs

- **[BREAKING] 查重与快速创建交互变化**：原来可以"仍然新建"同名客户，现在不行 → 快速创建弹窗同步改，提示里给出负责人，业务员线下协调；开发库无存量数据，不会出现历史重复
- **[BREAKING] 删除限制**：已有询盘的客户删不掉 → 提示改为禁用
- **[风险] 数据权限首次落地**：其他模块（询盘）仍全员可见，可能出现"询盘能看到但客户点不开"的情况 → 询盘列表只用 `GET /{id}` 取名字，不受影响；在交付说明里列为已知边界，后续按模块逐步接入
- **[风险] 「无权限」按「仅本人」处理**与字面含义不同 → 写入注释与交付说明，需要时可改为完全不可见
- **[权衡] 规范化查重可能误判**：如「ABC Trading Co」与「ABC Trading Company Ltd」会被视为同一客户 → 符合撞单防控的目的；真有不同公司同名同国时，业务员可在名称里加区分（如城市）
- **[权衡] 编辑页不能改负责人**：多一步操作，但保证每次换人都有转移记录
