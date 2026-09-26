## 1. 数据库迁移

- [x] 1.1 新建 `V1.2.9__customer_trade_profile.sql`（版本号被占用则顺延）：`customer` 表按 design.md 第 1 节加字段、索引（customer_code、(tenant_id, country, name_key)、owner_id、customer_role、customer_grade、source_channel），contact_name 放宽到 100；每列带 COMMENT（枚举列出码值）；验证：应用启动后 `SHOW CREATE TABLE customer` 可见新列
- [x] 1.2 同一脚本新建 `customer_party` 表（含 tenant_id、customer_id、deleted_at 及索引）；验证：表存在且字段、索引与设计一致
- [x] 1.3 同一脚本回填存量编码（`CUS` + 补零主键，主键超过 5 位不截断）和 owner_id（按 create_by 匹配 user_basic.username）；插入按钮资源 110145 `partner:customer:transfer`、110146 `partner:customer:export` 并加入标准版、旗舰版套餐；验证：用临时数据核对回填 SQL，查询两个套餐 menu_ids 含新 id

## 2. 后端 - 基础组件

- [x] 2.1 `framework/security/DataScopeResolver` + `DataScope`：按 design.md 第 2 节解析（含下级部门、主部门 + 附属部门、管理员、无角色、无权限按仅本人）；验证：单测覆盖全部 / 自定义（含下级部门、附属部门）/ 仅本人 / 无权限 / 无角色 / 管理员 / 无 user_basic 七种情况
- [x] 2.2 `CustomerNameNormalizer`：小写、去标点空白、反复去结尾公司后缀；验证：单测覆盖 `ABC Automation GmbH`、`abc automation`、`ABC Trading Co., Ltd.`、`ABC Trading Company Limited`、只由后缀组成的名称（不能规范化为空串，保留原词）
- [x] 2.3 `CustomerConstants`（角色、行业、等级、来源、付款方式、运输方式、贸易术语、币种）；国家时区数据：从 `countries-and-timezones` 生成 `masterdata/country-timezones.json`，新增 `CountryTimezoneCatalog` 与 `GET /api/v1/masterdata/country-timezones`；验证：单测断言 DE → Europe/Berlin、US 有多个时区；带 Token 调接口返回数据、无 Token 返回 401
- [x] 2.4 `LogService` 新增公共方法 `recordOperateLog(menu, operation, before, after)`，强制下线日志改为复用它；验证：新增单测断言写入的 sys_log 字段（原先没有日志服务的测试可跑）；强制下线改为复用同一方法
- [x] 2.5 应用启动时补算 `name_key=''` 的客户（幂等）；验证：单测或集成测试造一条空 name_key 客户，启动任务后被填充

## 3. 后端 - 客户档案

- [x] 3.1 `CustomerDO`、`CustomerPartyDO` 及 Mapper；请求 / 响应 DTO：`AbstractCustomerRequest`（共用字段与 Bean Validation）、`SaveCustomerRequest`（去掉 force，含编码、负责人）、`UpdateCustomerRequest`（不含编码、负责人）、`CustomerPartyRequest`、`CustomerVO`（列表）、`CustomerDetailVO`（含单证、负责人姓名与部门）、`CustomerRefVO`（id、编码、名称、国家、状态）、`CustomerPageQuery`；验证：编译通过，契约测试覆盖非法入参 400
- [x] 3.2 服务端校验：国家清单、英文字段、交易条件联动（术语地点、定金比例、账期、信用额度成对）、不适用的联动字段置空、时区合法；验证：单测逐条覆盖 spec「交易条件字段联动校验」各场景
- [x] 3.3 新增：编码（手工唯一 / 自动生成撞号追加后缀）、查重（`CUSTOMER_DUPLICATE`，detail 带 existingId、selectable，不受数据权限限制）、负责人默认当前用户及指定他人时的范围校验、单证随客户保存；验证：单测覆盖留空生成、编码重复、后缀不同判重、同名不同国家放行、命中他人客户 selectable=false、指定范围外负责人被拒
- [x] 3.4 更新：忽略编码和负责人、改名改国家查重排除自身、单证按 id 比对增删改并修正默认、20 条上限；验证：单测覆盖 spec「每类单证主体有且仅有一条默认」三个场景与上限场景
- [x] 3.5 查询：分页（数据范围 + 八个筛选条件 + 名称同时匹配英文 / 中文 / 简称 + 更新时间倒序 + 负责人姓名批量回填）、`/detail`（范围外返回「客户不存在或无权查看」）、选择器 `search` 接口按范围过滤、`GET /{id}` 改为返回 `CustomerRefVO` 且不过滤；内部 `getById` / `search` 保持不过滤；验证：单测覆盖范围过滤、中文名匹配、越权详情；询盘相关现有测试全部通过
- [x] 3.6 删除与批量删除：被询盘引用拒绝删除；批量返回 deleted / referenced / missing；启停、更新、删除均校验范围；验证：单测覆盖被引用拒删、批量混合、越权操作被拒
- [x] 3.7 转移：`POST /transfer`（范围校验、在职校验、逐条写操作日志）；`GET /assignable-owners`（返回范围类型 + 可选用户）；验证：单测覆盖批量转移成功、转给范围外被拒、日志内容
- [x] 3.8 导出：按筛选 + 范围导出全部档案字段，上限 5000；验证：单测读回 Workbook 断言表头、行数、仅含范围内客户
- [x] 3.9 `CustomerController` 接好全部端点与权限码（新增不挂权限以兼容快速创建，其余按 add / edit / delete / status / transfer / export）；验证：契约测试——受限角色访问编辑取数、转移、导出返回 403，未登录返回 401，`@PreAuthorize` 权限码与迁移脚本逐一比对；`mvn test` 全部通过

## 4. 前端 - 公共

- [x] 4.1 `pages/partner/customer/constants.ts`、`service.ts`（列表、详情、新增、更新、启停、删除、批量删除、转移、可选负责人、导出、国家时区）；`services/zhul/masterdata.ts` 类型补字段；验证：`npm run tsc` 无新增错误
- [x] 4.2 `config/routes.ts` 加 `/partner/customers/new`、`/:id`、`/:id/edit` 隐藏路由；`access.ts` 加 `partner:customer:transfer`、`partner:customer:export`；验证：直接访问三个地址进入对应页面

## 5. 前端 - 页面

- [x] 5.1 列表页按原型：两行筛选（负责业务员筛选按范围类型显示）、操作行（新增 / 导入置灰 / 导出）、勾选后批量转移与批量删除、表格列（名称英文 + 中文、角色与等级标签、主联系人、状态开关、时间与人员堆叠、查看 / 编辑 / 转移 / 删除）、空态与失败态；验证：浏览器走通筛选、分页、禁用确认、单条与批量删除（含被引用提示）、导出下载
- [x] 5.2 表单页（新增 / 编辑共用）按原型：五张卡片、字段联动显隐、国家选择后带出时区、英文字段提示、撞单报错挂到名称字段、编码与负责人在编辑态只读、错误汇总、离开确认、吸底操作栏；验证：浏览器新增完整客户、撞单提示、联动字段校验、编辑回显与保存
- [x] 5.3 单证信息：表单页按类型分组的卡片、设为默认 / 编辑 / 删除、右侧抽屉（类型切换、从注册信息复制、目的港仅收货人显示、默认开关）；验证：浏览器新增两条收货人并切换默认、删除默认后自动顺延、保存后详情一致
- [x] 5.4 详情页按原型：头部标签与操作、主栏五张卡片、侧栏负责与归属、客户当地时间（工作时间判定、与北京时差）、系统信息、不存在或无权时的提示；验证：浏览器从列表进入，数据与编辑页一致
- [x] 5.5 转移弹窗（单条 / 批量）；验证：浏览器批量转移后列表负责人更新，操作日志页可见转移记录
- [x] 5.6 `CustomerQuickCreateModal`：国家下拉必填，处理 `CUSTOMER_DUPLICATE`（可选用时「使用该客户」，否则只提示负责人）；验证：浏览器在询盘录入中快速创建成功并自动选中，重复时两种提示各走一次
- [x] 5.7 `npm run lint`、`npx antd lint ./src` 对新增与改动文件无新问题

## 6. 端到端验证

- [x] 6.1 建两个测试业务员（仅本人）和一个主管（自定义：其部门），分别登录：业务员只看到自己的客户、越权访问详情被拒；主管看到部门内客户并能转移
- [x] 6.2 询盘回归：询盘列表客户名正常回显（含主管看不到的客户）、按客户名筛选询盘正常、创建询盘选择客户正常
- [x] 6.3 深色、浅色主题下列表、表单、详情截图与原型对照

## 7. 规格归档准备

- [ ] 7.1 在本变更 specs 中把被取代的旧需求写清楚：归档顺序为 `add-inquiry-management` → `add-customer-supplier-management` → 本变更；本变更归档前，把 `master-data/customer` 中「创建客户记录」「同租户内客户名称重复提示」「客户记录仅支持软删除」改写为 MODIFIED（按本变更的新规则），并用 `openspec validate --strict` 验证
