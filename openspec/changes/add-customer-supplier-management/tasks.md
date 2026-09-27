## 1. 数据库迁移

- [x] 1.1 在 `zhul-erp-backend/src/main/resources/db/migration/` 新建 `V1.2.7__partner_menu_resources.sql`（若该版本号已被占用，顺延到下一个可用版本号）：插入顶层菜单"客商管理"（`pid=0`，`sort=4`）+ 子菜单"客户管理"（`/partner/customers`）、"供应商管理"（`/partner/suppliers`），并插入对应的 8 个按钮权限资源（`type=3`：客户/供应商各 增/改/删/启停）；执行 `mvn flyway:migrate`（或应用启动自动迁移）后用 `SELECT * FROM resource WHERE pid IN (...)` 验证记录已插入
- [x] 1.2 同一迁移脚本里 `UPDATE tenant_package SET menu_ids = ...` 追加新增的菜单+按钮资源 id 到标准版、旗舰版两条记录；验证：查询这两条记录的 `menu_ids` 包含新增的所有 id

## 2. 后端 - 客户模块

- [x] 2.1 `CustomerService`/`CustomerServiceImpl` 新增分页查询方法（关键词/国家/状态过滤，返回含 total 的分页结果），`CustomerMapper` 补对应查询；`CustomerController` 新增 `GET /api/v1/masterdata/customers/page`；验证：`CustomerServiceImplTest` 新增用例覆盖"无筛选返回全部分页""关键词+国家+状态组合筛选"
- [x] 2.2 `CustomerService`/`CustomerServiceImpl` 新增更新方法（名称/国家/联系人/电话/邮箱），改名时复用现有判重逻辑但改为阻塞式校验（冲突直接返回错误，不做"是否复用"分支）；`CustomerController` 新增 `PUT /api/v1/masterdata/customers/{id}`；验证：单测覆盖"正常更新成功""改名冲突时返回错误且不落库"
- [x] 2.3 `CustomerService`/`CustomerServiceImpl` 新增状态切换方法；`CustomerController` 新增 `PUT /api/v1/masterdata/customers/{id}/status`；验证：单测覆盖"禁用后 search 接口默认不返回该客户""历史 customer_inquiry 关联查询不受影响"

## 3. 后端 - 供应商模块

- [x] 3.1 `SupplierService`/`SupplierServiceImpl` 新增分页查询方法（关键词/国家/状态/含 main_brands 展示），`SupplierMapper` 补对应查询；`SupplierController` 新增 `GET /api/v1/masterdata/suppliers/page`；验证：`SupplierServiceImplTest` 新增用例覆盖组合筛选场景
- [x] 3.2 `SupplierService`/`SupplierServiceImpl` 新增更新方法（名称/国家/联系人/电话/邮箱/主营品牌），改名判重同 2.2 的阻塞式校验；`SupplierController` 新增 `PUT /api/v1/masterdata/suppliers/{id}`；验证：单测覆盖正常更新与改名冲突
- [x] 3.3 `SupplierService`/`SupplierServiceImpl` 新增状态切换方法；`SupplierController` 新增 `PUT /api/v1/masterdata/suppliers/{id}/status`；验证：单测覆盖禁用后不可选、历史引用不受影响

## 4. 权限接入

- [x] 4.1 给 2.2/2.3/3.2/3.3 四个新端点 + 两个 delete 端点（共 6 个，不含 create——create 是询盘等场景已上线的内联创建入口复用的接口，保持不挂权限校验，避免破坏现有流程）挂上 `partner:customer:*`/`partner:supplier:*` 权限码。验证方式跟原计划不同：没有可用的"无权限测试账号"，改为核对 `@PreAuthorize` 里的权限码字符串与迁移脚本里 `resource.permission` 字段逐一比对（全部一致），并确认无 Token/假 Token 请求返回 401（证明安全链路确实包住了这些端点）。`has()`/`EffectivePermissionResolver` 机制本身已有本会话更早写的单测/集成测试覆盖，未重复造轮子。
- [ ] 4.1-补充：这次没有用真实的"受限角色账号"跑一遍 403，是遗留验证缺口，见 7.2 说明

## 5. 前端 - 客户管理页面

- [x] 5.1 `src/pages/partner/customer/service.ts`：封装分页列表/更新/状态切换/删除的请求函数
- [x] 5.2 `src/pages/partner/customer/index.tsx`：参考 `src/pages/system/position/index.tsx` 的单文件列表+弹窗结构（比 product/brand 更贴合本页需求，没有商品域主题包装），用 ProTable 实现列表（关键词/国家筛选）+ 新建/编辑弹窗（ProForm）+ 启停操作 + 删除；启停/编辑/删除按钮分别接 `access.ts` 的按钮级权限位；已用浏览器手动过一遍新建→重名冲突提示→改名成功→禁用的完整流程
- [x] 5.3 `config/routes.ts` 新增 `/partner` 分组及子路由 `/partner/customers`；父路由用 `partnerMenu`（两个子权限任一为真）而不是单一子权限，避免只有供应商权限、没有客户权限的角色被连带挡住
- [x] 5.4 `src/access.ts` 新增 `partnerCustomer`/`partnerSupplier`/`partnerMenu`、`partner:customer:add/edit/delete/status` 等权限位

## 6. 前端 - 供应商管理页面

- [x] 6.1 `src/pages/partner/supplier/service.ts`：封装分页列表/更新/状态切换/删除的请求函数
- [x] 6.2 `src/pages/partner/supplier/index.tsx`：结构同 5.2，多一个"主营品牌"字段（自由文本输入）；已用浏览器手动验证页面渲染与空列表态
- [x] 6.3 `config/routes.ts` 补 `/partner/suppliers` 子路由（`access: 'partnerSupplier'`）
- [x] 6.4 `src/access.ts` 新增 `partner:supplier:add/edit/delete/status` 等权限位

## 7. 端到端验证

- [x] 7.1 用平台管理员账号登录，确认侧边栏出现"客商管理"分组及两个子菜单，菜单动态渲染、图标正常；额外用 SQL 直接核对 `tenant_package.menu_ids`（标准版、旗舰版）已包含新增的全部 11 个资源 id，等价确认了"标准版租户能看到这两个菜单"这件事（没有现成的标准版租户账号可供登录测试）
- [ ] 7.2 遗留验证缺口：没有创建"只有客户权限、没有供应商权限"的测试账号做实际登录+403验证——dev 环境目前只有 3 个 admin_flag=1 账号（对权限完全无限制），没有受限角色账号可用。已通过静态比对权限码字符串、确认安全链路 401 生效、复用既有 resolver 单测覆盖来降低风险，但不等于端到端验证。如果需要更严格保证，建议后续建一个受限角色账号补测
- [x] 7.3 全量跑 `mvn test`（562 个测试全部通过，无回归）和 `npm run lint` + `npx antd lint ./src`（新增文件无问题；报出的 1 个 error/1 个 warning 均为预先存在、与本次改动无关的旧文件——`table-list` 模板残留代码和 `locales/zh-CN/menu.ts` 的重复 key）
