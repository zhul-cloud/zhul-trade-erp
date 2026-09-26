## 1. 数据库迁移

- [x] 1.1 新建 `zhul-erp-backend/src/main/resources/db/migration/V1.2.8__supplier_basic_info.sql`（版本号被占用则顺延）：`supplier` 表新增 design.md 第 1 节的 13 个字段及 `idx_supplier_code`、`idx_credit_code`、`idx_supplier_type` 索引，每个字段带 COMMENT（枚举列出码值）；存量记录回填 `SUP` + 5 位补零主键编码；验证：应用启动后 `SHOW CREATE TABLE supplier` 可见新字段，`SELECT COUNT(*) FROM supplier WHERE supplier_code = ''` 为 0
- [x] 1.2 同一脚本插入按钮资源 `partner:supplier:export`（id 110155，pid 100062），并追加进标准版、旗舰版 `tenant_package.menu_ids`；验证：SQL 查询两个套餐的 `menu_ids` 含 110155

## 2. 后端 - 基础组件

- [x] 2.1 新增 `SensitiveDataMasker.maskBankAccount`（放 `common` 下的工具包），按 design.md 第 4 节规则脱敏；验证：单测覆盖空值、4 位、8 位、19 位、30 位账号
- [x] 2.2 新增供应商类型、行业常量（`masterdata/constants/SupplierConstants`），码值与迁移注释一致；验证：编译通过，单测中引用
- [x] 2.3 从 `china-division` 的 `pca-code.json` 生成 `src/main/resources/masterdata/regions.json`；新增 `RegionCatalog`、`RegionVO`、`RegionController`（`GET /api/v1/masterdata/regions`）；验证：`RegionCatalogTest` 断言能找到「上海市 → 上海市 → 浦东新区」路径，带 Token 调接口返回树、无 Token 返回 401

## 3. 后端 - 供应商模块

- [x] 3.1 `SupplierDO`、`SupplierVO`（含 `createBy`/`updateTime`/`updateBy`，账号脱敏）、新增 `SupplierFormVO`（明文账号）、`SaveSupplierRequest`、`UpdateSupplierRequest`（不含编码）按 design.md 扩展字段和 Bean Validation 注解（长度、正则、`@PastOrPresent`、`@DecimalMin("0")`、`@Digits(fraction = 2)`）；验证：编译通过，契约测试覆盖非法入参返回 400
- [x] 3.2 `create` 支持编码：手工编码校验格式 + 租户内唯一（`SUPPLIER_CODE_DUPLICATE`），为空时插入后按 `SUP` + 补零主键生成、冲突追加数字后缀；信用代码转大写 + 唯一校验（`SUPPLIER_CREDIT_CODE_DUPLICATE`，消息带占用方名称）；验证：`SupplierServiceImplTest` 覆盖手工编码成功、编码重复、自动生成、自动生成撞号追加后缀、信用代码重复、信用代码为空不校验
- [x] 3.3 `update` 写入全部新字段（不改编码），信用代码唯一校验排除自身；新增 `getFormById`；验证：单测覆盖更新成功、改为他人信用代码被拒、保留自身信用代码可保存、请求里带编码也不改变原编码
- [x] 3.4 `page` 增加编码 / 名称 / 信用代码 / 类型筛选，排序改为 `update_time DESC`，保留 `keyword`、`country`；验证：单测覆盖组合筛选
- [x] 3.5 新增 `batchDelete(ids)` 返回 `{deleted, skipped}`；验证：单测覆盖全部成功、部分已删除被跳过、空列表报参数错误
- [x] 3.6 新增 `export(query)` 生成 xlsx（照 `LogController` 的写法），导出全部基础信息字段（比列表列更全，方便线下核对）、账号脱敏、上限 5000 行；验证：单测读回生成的 Workbook，断言表头、行数和账号列为脱敏值
- [x] 3.7 `SupplierController` 新增 `GET /{id}/form`、`POST /batch-delete`、`GET /export`，按 design.md 第 5 节挂权限；验证：`@PreAuthorize` 权限码与迁移脚本逐一比对一致；`mvn test` 全部通过

## 4. 前端 - 公共

- [x] 4.1 `src/pages/partner/supplier/constants.ts`：类型、行业选项与标签颜色；`service.ts` 扩展为列表 / 详情 / 编辑取数 / 新增 / 更新 / 状态 / 删除 / 批量删除 / 导出 / 区划请求；`src/services/zhul/masterdata.ts` 的 `SupplierItem` 补可选字段；验证：`npm run tsc` 通过
- [x] 4.2 `config/routes.ts` 增加 `/partner/suppliers/new`、`/partner/suppliers/:id`、`/partner/suppliers/:id/edit` 隐藏路由（`access: 'partnerSupplier'`）；`access.ts` 增加 `partner:supplier:export`；验证：直接访问三个地址能进入对应页面

## 5. 前端 - 页面

- [x] 5.1 列表页 `index.tsx` 按原型重写：标题 + 说明、两行筛选卡片、「共 N 条记录」+ 新增 / 导入（置灰）/ 导出、勾选后的批量删除条、表格列（序号、编码、名称、简称、类型标签、信用代码、联系人、电话、状态开关、创建时间 / 创建人、更新时间 / 更新人、查看 / 编辑 / 删除）、空态文案与按钮、加载失败可重试；验证：浏览器走通筛选、分页切换、禁用确认与取消、单条删除、批量删除、导出下载
- [x] 5.2 表单页 `form.tsx`（新增 / 编辑共用）：四张卡片双列布局、字段校验与原型 placeholder、地区级联、编辑态编码置灰加锁并提示、银行账号明文回显、底部创建人 / 时间只读行、吸底操作栏、取消的离开确认、服务端编码 / 信用代码冲突挂到对应字段；验证：浏览器新增一条完整供应商、编码重复提示、信用代码格式与重复提示、编辑回显并保存成功
- [x] 5.3 详情页 `detail.tsx`：五张只读卡片、类型与状态标签、账号脱敏、空值显示「—」、返回与编辑按钮（按权限）、不存在时的提示；验证：浏览器从列表「查看」进入，数据与编辑页一致
- [x] 5.4 `npm run lint` 与 `npx antd lint ./src` 对新增 / 改动文件无新问题

## 6. 端到端验证

- [x] 6.1 询盘单「添加供应商」内联创建一个新供应商，确认创建成功、自动获得编码，并在供应商管理列表中可见；已有询盘单的供应商名称回显正常。实际验证方式：开发库没有任何询盘单，未走询盘界面；在已登录的浏览器里按 `SupplierQuickCreateModal` 完全相同的字段调用 `POST /suppliers`（得到 `SUP00003`、类型「未设置」）、同名再提交（返回 duplicate 提示）、`GET /suppliers?keyword=`（已禁用供应商不出现），再在管理页编辑这条记录（强制补选类型，country、main_brands、境外电话保持不变）
- [x] 6.2 浅色、深色两种主题下检查列表、表单、详情三个页面的对比度和布局，截图与原型对照
