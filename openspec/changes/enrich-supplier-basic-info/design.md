## Context

- `supplier` 表（`V1.1__master_data_and_inquiry.sql`）目前只有名称、国家、联系人、电话、邮箱、主营品牌、状态。`add-customer-supplier-management`（已实现、未归档）刚加了分页、更新、启停接口和一个弹窗式管理页 `src/pages/partner/supplier/index.tsx`，菜单资源在 `V1.2.7__partner_menu_resources.sql`
- 供应商还被询盘模块用：`POST /suppliers` 内联创建（带同名"提示复用"语义）、`POST /suppliers/from-channel` 询价渠道转正式供应商、`GET /suppliers?keyword=` 选择器搜索、`GET /suppliers/{id}` 回显名称。前端调用方在 `src/services/zhul/masterdata.ts`
- 已有可参考的实现：Excel 导出见 `LogController` + `pages/system/log/operate/service.ts`（POI + blob 下载）；classpath JSON 字典见 `product/support/CountryCatalog` + `CountryController`；独立新建/详情页见 `pages/product/products/new.tsx`、`detail.tsx`
- 页面按 `ui-design-patterns.md` 的 V3 深色视觉语言，取色只走 `useAppTheme().palette` / CSS 变量

## Goals / Non-Goals

**Goals:**
- 按原型把供应商主数据扩展到 PRD 定义的全部字段，列表、新增、编辑、详情、删除确认五个界面对齐原型
- 询盘侧的内联创建、搜索、回显不需要任何改动就能继续工作

**Non-Goals:**
- 导入功能（按钮置灰，悬停提示「即将上线」）
- 删除时的采购业务关联校验（采购模块尚不存在）
- 菜单重组、供应商等级 / 分类页面
- 多银行账号、审核流程、母子公司关系（PRD 第七节默认假设维持现状）

## Decisions

### 1. 字段落库方式

新迁移 `V1.2.8__supplier_basic_info.sql`，`ALTER TABLE supplier ADD COLUMN`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `supplier_code` | `varchar(20) NOT NULL DEFAULT ''` | 业务编码，建索引 `idx_supplier_code` |
| `short_name` | `varchar(50) NOT NULL DEFAULT ''` | |
| `supplier_type` | `tinyint(2) NOT NULL DEFAULT 0` | 0-未设置、1-生产商、2-经销商、3-服务商、4-代理商、5-其他；建索引 |
| `industry` | `tinyint(2) NOT NULL DEFAULT 0` | 0-未设置、1-制造业、2-原材料、3-信息技术、4-物流运输、5-金融服务、6-其他 |
| `credit_code` | `varchar(18) NOT NULL DEFAULT ''` | 建索引 `idx_credit_code` |
| `legal_representative` | `varchar(50) NOT NULL DEFAULT ''` | |
| `registered_capital` | `DECIMAL(18,2) NULL` | 单位万元人民币；NULL 表示未填，区别于 0 |
| `established_date` | `date NULL` | |
| `region` | `varchar(100) NOT NULL DEFAULT ''` | 存「省/市/区」名称，用 `/` 分隔 |
| `address` | `varchar(200) NOT NULL DEFAULT ''` | |
| `bank_name` | `varchar(100) NOT NULL DEFAULT ''` | |
| `bank_account` | `varchar(30) NOT NULL DEFAULT ''` | 明文存储 |
| `remark` | `varchar(500) NOT NULL DEFAULT ''` | |

- 类型、行业用 `tinyint` 码值，和项目状态字段写法一致；0「未设置」专给存量数据和内联创建的记录用，管理页表单不提供这个选项、必须选一个真实类型
- **注册资本的币种**：PRD 标注「万元」，按国内营业执照惯例就是人民币，写进字段注释。它是工商登记信息而不是交易金额，不参与汇率换算，所以不加原币 / 本位币双字段
- `region` 存名称而不是区划代码：列表和详情直接可读，导出也不用再翻译；级联选择器回显时按名称在区划树里找回路径。代价是区划改名后老数据不跟着变，对供应商地址可以接受
- 存量数据编码：`UPDATE supplier SET supplier_code = CONCAT('SUP', LPAD(id, 5, '0')) WHERE supplier_code = ''`
- 不加唯一索引（软删除后同编码可以重新使用，数据库唯一索引表达不了"只在未删除记录里唯一"），唯一性由服务层按租户 + `deleted_at IS NULL` 校验，和现有名称判重一致

### 2. 编码生成与校验

- 管理页新增：前端必填；后端校验 `^[A-Za-z0-9]{1,20}$` 和租户内唯一，冲突抛业务异常，错误码 `SUPPLIER_CODE_DUPLICATE`，前端据此把错误挂到编码字段上
- 内联创建（`SaveSupplierRequest.supplierCode` 为空）：先插入拿主键，再生成 `SUP` + 5 位补零主键；若和别人手工录入的编码撞上，追加 `1`、`2`……直到唯一，然后回写。整个过程在同一个 `@Transactional` 里
- 更新接口不接受编码字段（`UpdateSupplierRequest` 没有这个属性），从根本上保证不可修改
- 管理页新增不走"同名提示复用"分支：新增页请求带 `force=true`，同名供应商允许存在（编码已经是唯一标识，PRD 也没有名称唯一的要求）。内联创建保留原有的同名提示。更新时的阻塞式同名校验保持现状

### 3. 信用代码校验

- 统一转大写后校验 `^[0-9A-Z]{18}$`，只做长度和字符集检查，不做 GB 32100 校验位计算（PRD 只要求 18 位格式；校验位算法容易误伤手工录入的历史数据）
- 唯一性：租户内未删除记录，排除自身；冲突信息带出占用方名称

### 4. 银行账号脱敏与编辑取数

- 工具方法 `SensitiveDataMasker.maskBankAccount`：长度大于 8 输出「前 4 + ` **** **** ` + 后 4」；5 到 8 位输出「`**** ` + 后 4」；4 位及以下原样输出。原型图和 PRD 文字有出入（PRD 写"后 8 位打码"，原型是前后各留 4 位），以原型为准
- `SupplierVO.bankAccount` 永远是脱敏值——列表、详情、选择器搜索、导出都走它，不会漏
- 新增 `GET /suppliers/{id}/form`，要求 `partner:supplier:edit` 权限，返回 `SupplierFormVO`（`SupplierVO` 的字段加明文账号），只给编辑页用

### 5. 接口清单

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/v1/masterdata/suppliers` | 不挂（内联创建在用） | 请求体新增字段均为可选；管理页新增时前端再按 `partner:supplier:add` 控制按钮 |
| GET | `/suppliers/page` | 不挂（同现状） | 新增 `supplierCode`、`name`、`creditCode`、`supplierType` 参数；保留 `keyword`、`country`；排序改为 `update_time DESC` |
| GET | `/suppliers/{id}` | 不挂 | 返回完整 VO（账号脱敏），含 `createBy`/`updateBy`/`updateTime` |
| GET | `/suppliers/{id}/form` | `partner:supplier:edit` | 编辑取数，明文账号 |
| PUT | `/suppliers/{id}` | `partner:supplier:edit` | 请求体扩展全部字段（不含编码） |
| POST | `/suppliers/batch-delete` | `partner:supplier:delete` | 体 `{ids: []}`，返回 `{deleted, skipped}` |
| GET | `/suppliers/export` | `partner:supplier:export` | 参数同分页查询（无分页），返回 xlsx |
| GET | `/api/v1/masterdata/regions` | 登录即可 | 省市区树 |

- 新增按钮权限资源 `partner:supplier:export`（id 110155，挂在 100062 下），追加进标准版、旗舰版套餐的 `menu_ids`，与 V1.2.7 做法一致
- 导出上限 5000 行，超出时提示缩小筛选范围（防止一次拉全表拖垮内存），并按项目规范对导出接口不额外加限流——现有日志导出也没有限流基础设施，这里记为风险
- 批量删除走 `POST` 而不是带 body 的 `DELETE`，避开部分代理丢弃 DELETE 请求体的问题

### 6. 行政区划数据

- 数据来源：npm 包 `china-division` 2.7.0（WTFPL 协议，可自由使用）的 `pca-code.json`，一次性转换后放到 `zhul-erp-backend/src/main/resources/masterdata/regions.json`，不作为前端依赖引入
- 转换时把北京、天津、上海、重庆的第二级（原始数据叫「市辖区」「县」）合并为一个与省同名的节点，使路径读作「上海市/上海市/浦东新区」，与原型一致；「省直辖县级行政区划」保持原样
- 不含港澳台，需要时再补
- 新增 `RegionCatalog`（照搬 `CountryCatalog` 的启动时加载写法）和 `RegionController`，接口返回 `[{code, name, children}]`
- 前端照 `useCountries` 的写法做模块级缓存，一个会话只拉一次（项目里没有在用 react-query）

### 7. 前端页面结构

- `src/pages/partner/supplier/index.tsx`：列表页重写。ProTable 自带筛选区换成原型的筛选卡片（5 项、两行网格）；`rowSelection` + 顶部「已选 N 条 / 批量删除」条；状态列用 `Switch`，禁用时 `Modal.confirm`；时间列「创建时间 / 创建人」「更新时间 / 更新人」两列上下堆叠（原型样式，同时满足列表统一规范要求的四个字段）
- `src/pages/partner/supplier/form.tsx`：新增和编辑共用，按路由参数区分；四张卡片、双列布局、底部吸底操作栏；取消时有修改则弹「确认离开？当前填写内容将不会保存。」
- `src/pages/partner/supplier/detail.tsx`：五张只读卡片，右上角「返回」「编辑」
- 删除确认用 `Modal.confirm`（danger 按钮「确认删除」），文案按原型
- 类型、行业的枚举和标签配色放 `src/pages/partner/supplier/constants.ts`，前后端码值保持一致
- `src/services/zhul/masterdata.ts` 的 `SupplierItem` 只加可选字段，不改已有字段

## Risks / Trade-offs

- **[风险] 存量供应商没有类型**：迁移后类型显示为「未设置」，编辑保存时被强制补选 → 列表里用灰色标签「未设置」提示，不阻塞查看
- **[风险] 导出无限流**：大租户频繁导出会占 CPU / 内存 → 用 5000 行上限兜底，后续统一引入限流组件时再补
- **[权衡] 编码只在应用层保证唯一**：极端并发下两个请求同时创建相同手工编码可能都通过校验 → 管理页新增是低频人工操作，接受；如果后续出现问题再加 Redisson 锁 `zhul:erp:lock:supplier-code:{tenantId}:{code}`
- **[权衡] 账号明文存库**：PRD 没有加密存储要求，本次只做展示层脱敏；如需落库加密需要另立变更（牵涉密钥管理）
- **[兼容] `/suppliers/page` 排序从创建时间改为更新时间**：只有旧弹窗页在用，本次一起替换，无外部影响
