## Context

- 编码：`SupplierServiceImpl.create` 已有自动生成（`SUP` + 5 位补零主键，冲突追加后缀），只在请求没带编码时启用；管理页目前强制手填
- 结算：`supplier.bank_name` / `bank_account` 两列，列表、详情、导出脱敏，编辑取数接口（需 `partner:supplier:edit`）返回明文
- 上传：`zhul.upload.dir`（默认 `./uploads`）整个目录经 `/uploads/**` 作为静态资源公开、免登录，询盘附件就放这里。营业执照、身份证类资料不能放进这个目录
- 权限：`@perm.has(code)` 只识别按钮资源（type=3）；供应商「查看」没有按钮权限，详情接口只要求登录
- 加密：`AesUtils`（密钥 `zhul.crypto.aes-key`）已用于系统配置加密存储

## Goals / Non-Goals

**Goals:** 见 proposal；另要求附件与身份证号在存储和访问两端都不暴露明文或公开地址。

**Non-Goals:** 附件迁移 OSS、在线预览 Office 文件、付款单按账户付款（后续采购 / 应付模块使用 `supplier_bank_account`）、清理上传后未保存的孤儿文件（见风险）。

## Decisions

### 1. 数据结构（迁移 V1.2.11）

```
supplier + wechat varchar(64) NOT NULL DEFAULT ''

supplier_bank_account（业务表：tenant_id、deleted_at）
  id, tenant_id, supplier_id,
  account_type tinyint   1-对公 2-对私
  account_name varchar(100)  户名 / 收款人姓名
  bank_name    varchar(100)
  account_no   varchar(30)   明文（与现有 bank_account 一致，脱敏在出参）
  payee_phone  varchar(20)   对私
  payee_id_no  varchar(128)  对私，AES 密文
  is_default   tinyint(1)
  sort_order   int
  索引：(tenant_id, supplier_id)、deleted_at

supplier_attachment（业务表）
  id, tenant_id, supplier_id,
  category tinyint  1-营业执照 2-开户许可证 3-资质证书 4-合同 5-其他
  file_name varchar(200)  原文件名
  file_key  varchar(200)  私有目录下的相对路径
  file_size bigint, content_type varchar(64)
  索引：(tenant_id, supplier_id)、category、deleted_at
```

存量迁移：`bank_account <> ''` 的未删除供应商插入一条对公默认账户（户名 = 供应商名称）；幂等条件为该供应商还没有任何账户。`bank_name` / `bank_account` 保留、不再写入（与 `main_brands` 相同处理）。

### 2. 保存语义：按 id 合并

供应商新增 / 更新请求加 `wechat`、`accounts`、`attachments`，去掉 `bankName` / `bankAccount`；`supplierCode` 服务端忽略。`accounts` / `attachments` 为 `null` 表示不修改，空列表表示清空。

不用主营产品那种「整体软删后重插」，而是按 id 合并：带 `id` 的更新该行（必须属于本供应商），不带的新增，已有但未出现的软删除。原因：身份证号从不回传明文，更新时 `payeeIdNo` 为 `null` 就沿用原密文、为空串清空、有值则加密覆盖——这要求能定位到原行。

默认账户：多个 `isDefault=true` 拒绝；一个都没有时第一个设为默认。

### 3. 私有文件存储

- 新配置 `zhul.upload.private-dir`（默认 `./private-uploads`，不在静态资源映射内），`.gitignore` 忽略
- `POST /api/v1/masterdata/suppliers/attachments`（multipart，需要新增或编辑供应商权限之一）：按文件头判断类型（PDF `%PDF`、PNG `89 50 4E 47`、JPG `FF D8 FF`），≤10MB；存为 `supplier/{tenantId}/{yyyyMM}/{uuid}.{ext}`，返回 `{fileKey, fileName, fileSize, contentType}`，此时还不属于任何供应商
- 保存供应商时新附件带 `fileKey`：服务端校验路径格式、租户段等于当前租户、文件确实存在，再从磁盘读大小与类型入库，杜绝伪造路径与越权引用
- `GET /api/v1/masterdata/suppliers/{id}/attachments/{attachmentId}`：校验附件属于本租户该供应商，`inline` 参数决定 `Content-Disposition`（inline / attachment，文件名按 RFC 5987 编码）

### 4. 下载权限：能访问供应商菜单

`PermissionChecker` 新增 `canAccessMenu(path)`：按路径找到菜单资源（type=2），判断是否在该账号的有效资源集合内（与 `has` 同一套 `EffectivePermissionResolver`）。下载接口要求 `@perm.canAccessMenu('/partner/suppliers')`。不新增按钮权限，避免已有角色升级后突然看不到附件。

### 5. 出参

- `SupplierVO` 加 `wechat`、`accounts`（账号、手机号、身份证号脱敏）、`attachments`（id、category、fileName、fileSize、contentType、上传人、上传时间，不含 fileKey）
- `SupplierFormVO` 的 `accounts` 账号与手机号为明文，身份证号仍为脱敏值
- 列表不带账户和附件；详情、编辑取数批量查询
- 导出：去掉「开户银行」「银行账号」两列，改为「微信」「收款账户」（多账户用 `；` 连接，形如 `[对公·默认] 户名 / 开户行 / 6222 **** **** 8888`）

### 6. 前端

- 基本信息：新增页编码框只读「保存后自动生成」，编辑页只读显示编码
- 联系信息：加「微信」
- 结算信息：账户卡片列表 + 「添加账户」；添加 / 编辑用弹窗（对公 / 对私分段切换、默认账户复选框），「设为默认」「删除」在卡片上直接操作；表单提交时整体带上
- 附件卡片：五个类型各一行，行内「上传」即调上传接口，得到 `fileKey` 后加入表单值；新上传未保存的文件用浏览器本地地址预览，已保存的调下载接口（带 token 取 blob）
- 详情：账户只读列表、附件分组、缺营业执照提示

## Risks / Trade-offs

- **[孤儿文件]** 上传后未保存的文件留在私有目录 → 量小，后续可加定时清理；本次不做
- **[BREAKING 接口]** 去掉 `bankName` / `bankAccount`、忽略 `supplierCode` → 仅本仓库前端调用，同步改完
- **[账号明文存储]** 与现状一致；身份证号更敏感所以加密。账号加密留待应付模块统一处理
- **[下载走接口]** 文件经应用转发 → 单个 ≤10MB，可接受；迁 OSS 后改签名地址
