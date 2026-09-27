## Context

见 proposal.md - Why/What Changes。当前状态：`masterdata` 模块（`CustomerController`/`SupplierController`）只有创建、非分页关键词搜索、按ID查询、软删除；前端没有独立页面，只在询盘录入/询价弹窗里内联使用。数据库表 `customer`/`supplier` 字段已经够用，本次不改表结构。

菜单/导航目前是动态渲染机制：侧边栏完全由 `resource` 表驱动（`menuDataRender` 直接读后端菜单树 + 当前用户有效权限生成，参见 `zhul-erp-frontend/src/app.tsx`），新增菜单只需要在 `resource` 表插入记录，前端不用手改 `routes.ts` 的展示逐层（但仍需要在 `routes.ts` 里声明真实路由和 `access` 门禁，两者是独立的两套机制：`resource` 决定侧边栏长什么样，`routes.ts`+`access.ts` 决定路由能不能进）。

## Goals / Non-Goals

**Goals:**
- 客户、供应商各有一个独立的分页列表管理页，支持搜索、编辑、启停
- 沿用现有的软删除、同名重复提示语义，不引入新的业务规则
- 新菜单挂到侧边栏，两个租户套餐（标准版、旗舰版）都能看到（已跟用户确认：客户/供应商是基础主数据，不做套餐分层）

**Non-Goals:**
- 不做客户分层、地址明细、商机来源等字段扩展（依赖 `add-opportunity-management` 的结论）
- 不做供应商 `main_brands` 的结构化校验（关联品牌主数据）
- 不改动现有的非分页 `search` 接口行为（询盘录入等场景继续用它做选择器）

## Decisions

### 1. 导航归属：新建一个顶层菜单分组"客商管理"，不拆分到 CRM/采购两个域

proposal.md 里留了这个问题给 design 决定。虽然 CLAUDE.md 把"客户与商机管理（CRM）"和"采购与供应商管理"列成两个不同业务域，但目前系统里这两个域都还没有任何其他页面（商机管理还在探索阶段、采购单模块还没做），为这两个只有"一个主数据页面"的域各开一个顶层菜单是过度设计。

决定：新增一个顶层菜单**"客商管理"**（中文 ERP 里"客商"是"客户+供应商"的惯用合称，避免跟子菜单"客户管理"/"供应商管理"重名），下挂两个子菜单：
- 客户管理 → `/partner/customers`
- 供应商管理 → `/partner/suppliers`

`sort` 值定为 4（现有顶层：工作台=1、询盘管理=2、商品管理=3、系统管理=98、租户管理=99，插在商品管理之后、系统管理之前，不需要重排其他菜单的 sort）。

等商机管理、采购管理真正落地、各自域下有多个页面时，再考虑要不要把客户管理挪到 CRM 域、供应商管理挪到采购域——现在挪没有实际收益，只是徒增一层菜单结构。

**Alternatives considered**：直接挂在现有"商品管理"分组下当第 5 个子菜单——否决，客户/供应商是往来单位主数据，跟商品主数据是不同维度的东西，混在一起会让"商品管理"这个分组名字失焦。

### 2. 分页列表用新端点，不复用/不改造现有 `search`

现有 `GET /api/v1/masterdata/customers?keyword=` 返回不分页的 `List<CustomerVO>`，被询盘录入等场景的选择器直接依赖。如果给同一个端点加 `page`/`page_size` 参数、根据参数是否存在切换返回“列表”还是“分页对象”两种响应形状，前端调用方需要自己判断，容易出错。

决定：新增 `GET /api/v1/masterdata/customers/page`（供应商同理 `/suppliers/page`），入参 `page`/`page_size`/`keyword`/`country`/`status`，响应遵循项目分页规范（含 `total`）。原 `GET /api/v1/masterdata/customers` 保持原样不动。

### 3. 更新接口允许修改名称，重复检查逻辑复用现有创建时的判重规则

创建时已有"同租户内名称完全重复→提示是否复用已有记录"的规则（`master-data/customer` spec 里的既有需求）。更新时如果也允许改名称，需要同样判重，但语义略有不同：创建时"提示复用"是让用户在两条记录里选一条，更新时用户已经在编辑一条具体记录，不存在"复用"选项，只能提示冲突、阻止保存。

决定：更新时把名称判重做成**阻塞式校验**（返回错误，不落库），不做"是否复用"的分支交互，比创建时更简单。

### 4. 启用/禁用只影响"新增引用时可选"，不影响历史数据

参照系统管理里其它主数据（角色、字典）的禁用语义：禁用 = 新建/选择场景里不可选，但历史引用不受影响、不级联处理已有数据。

### 5. 按钮权限命名沿用 `<模块>:<资源>:<动作>` 惯例

- `partner:customer:add` / `edit` / `delete` / `status`
- `partner:supplier:add` / `edit` / `delete` / `status`

### 6. 套餐关联：新菜单加进标准版和旗舰版两个现有套餐

已跟用户确认。迁移脚本里把新增的菜单资源 id 追加进 `tenant_package.menu_ids`（两个套餐都追加），不新建套餐、不做按钮级别的套餐差异化（按钮权限继续只受角色约束，跟其它模块一致）。

## Risks / Trade-offs

- **[风险] 新增独立管理页后，业务员可能直接在这里改客户联系方式，但询盘详情页展示的客户信息是否需要实时联动？** → 缓解：`customer_inquiry` 只存 `customer_id`，展示时是关联查询而非快照，改了客户资料会自动在询盘详情里体现，不需要额外同步逻辑（沿用现有关联查询方式即可）
- **[权衡] "客商管理"这个顶层分组现在只有 2 个子菜单，商机/采购模块落地前显得单薄** → 接受，比过度拆分成两个只有一页的顶层菜单更简洁；后续按第 1 条决策的说明再迁移

## Migration Plan

1. 后端：`CustomerController`/`SupplierController` 新增 3 个端点（分页列表、更新、状态切换）；`CustomerService`/`SupplierService` 及实现类、`CustomerMapper`/`SupplierMapper` 同步扩展分页查询能力
2. Flyway 迁移（`db/migration`，非 dev-data，因为菜单资源是正式种子数据，参考 `V1.2.6__inquiry_menu_resources.sql` 的先例）：新建 `V1.2.7__partner_menu_resources.sql`
   - 插入顶层菜单"客商管理"（`pid=0`，`sort=4`）+ 两个子菜单"客户管理"（`/partner/customers`）、"供应商管理"（`/partner/suppliers`）
   - 插入对应按钮权限资源（`type=3`）：客户/供应商各 4 个（增/改/删/启停）
   - 更新 `tenant_package` 表标准版、旗舰版两条记录的 `menu_ids`，追加新增的菜单+按钮资源 id
   - 注：实际实现时如果 `V1.2.7` 已被其它并行开发占用，需要顺延到下一个可用版本号（这个项目里发生过好几次，Flyway 严格校验顺序）
3. 前端：`config/routes.ts` 新增 `/partner` 路由分组（含 `access` 门禁）；`access.ts` 补 `partnerCustomer`/`partnerSupplier` 等权限位；新增 `/partner/customers`、`/partner/suppliers` 两个页面（ProTable 列表 + 编辑弹窗 + 启停操作），按 `ui-design-patterns.md` 的列表页统一规范
4. 回滚策略：纯新增（无既有表结构变更、无既有接口行为变更），出问题直接回退代码+撤销那条 Flyway 迁移即可，不影响 `customer_inquiry`/`inquiry_order` 等下游数据
