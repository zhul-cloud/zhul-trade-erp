# UI-038 2026-11-03 产品负责人反馈的一批缺陷

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与本地验证）；产品负责人验收 |
| 关联 ID / 原专题 | UI-038、UI-039、UI-040、BUS-023、BUS-024。均为产品负责人 2026-11-03 反馈清单中的缺陷（原清单第 13、12、6、18、11 条），不在原审查范围内；已查重，与 UI-002（令牌刷新后重放绕过业务错误校验）不是同一问题 |
| 目标与影响范围 | 见下方「实施与验证结果」逐条说明。不包含同一反馈清单中的优化与新功能（另走 OpenSpec） |
| 起点 commit / 工作分支 | `b02789924cf143581f26a42d18f65d3ebd666714`（develop）/ `feat/feedback-batch-1103` |
| OpenSpec | 不适用：缺陷修复，不改变既有规格 |
| PR / 目标分支 | [#134](https://github.com/zhul-cloud/zhul-trade-erp/pull/134) → develop |
| 合并证据 | 2026-11-03 合并到 develop（`00eb2ff2`），随 v1.6.0 发版 |
| 目标版本 / 环境 | v1.6.0；全部租户 |

## 实施与验证结果

**UI-038 询盘管理列表点第 2 页无效**（反馈第 13 条）：表格的 `onChange` 在翻页时也会触发，原代码不分动作一律把页码重置为 1。改为只在排序动作（`extra.action === 'sort'`）时处理。销售订单列表有同样问题一并修复；报价单、PI 列表此前已有该判断。
- `zhul-erp-frontend/src/pages/inquiry/customer-inquiry/index.tsx`、`zhul-erp-frontend/src/pages/sales/orders/index.tsx`

**UI-039 新建询盘时客户名称过长把「来源」挤出对话框**（第 12 条）：客户与来源两列网格的第一列是 `1fr`，最小宽度等于内容宽度，长名称撑宽整行。改为 `minmax(0, 1fr)`，长名称在选择框内省略显示。
- `zhul-erp-frontend/src/pages/inquiry/customer-inquiry/NewInquiryModal.tsx`

**UI-040 登录过期后页面先提示「加载失败」再跳登录页**（第 6 条）：后端未登录返回 HTTP 200 + `code: 401`，前端刷新令牌失败后开始跳登录页，但把这个未登录响应当作成功结果交回页面，页面在跳转完成前显示加载失败或报错。改为开始跳转后请求不再返回（既不成功也不报错），多个同时过期的请求只跳一次；已在登录页时照常返回。
- `zhul-erp-frontend/src/requestErrorConfig.ts`；测试 `zhul-erp-frontend/src/requestErrorConfig.test.ts`「登录过期」两条

**BUS-023 应付的收款方不随供应商改名刷新**（第 18 条）：应付本来就按 `supplier_id` 关联供应商，另存了一份生成时的名称快照用于显示和搜索，改名后不更新。改为供应商显示名（有简称用简称，否则用名称）变化时发布 `SupplierRenamedEvent`，应付在同一事务内把收款方为供应商、货代且关联该供应商的应付名称刷新；迁移 V1.4.9 一次性刷新已过时的名称。已登记的付款记录保留当时的收款方名称，不改。
- `SupplierServiceImpl.update`、`masterdata/support/SupplierRenamedEvent.java`、`PayableSyncService.onSupplierRenamed`、`db/migration/V1.4.9__payable_payee_follow_supplier.sql`

**BUS-024 PI 导出的贸易术语地点和业务员显示中文**（第 11 条）：导出的业务员取的是「姓名」，改为优先取「昵称」（没填昵称才用姓名）。术语地点原样输出保存的文字；系统默认填英文国家名，中文来自手工输入等其他来源。改为导出时把中文国家名按国家清单换成英文（如 危地马拉 → Guatemala），术语地点字典的中文名换成字典英文名，其余原样。报价单导出同样处理。
- `QuotationRenderModels.sellerName / incoterm`、`PiRenderModels`、`document/support/PlaceNames.java`、`CountryCatalog.englishOf`、`QuotationDocumentServiceImpl`、`PiDocumentServiceImpl`

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `npx jest src/requestErrorConfig.test.ts` | 本地 Jest，工作区未提交 | 6 条通过 | 6 条通过 | 通过 |
| `npm run lint`（Biome + tsc） | 本地，工作区未提交 | 无错误 | 无错误（1 条既有提示，与本次无关） | 通过 |
| `mvn test -Dtest=CustomerDocTextTest,PiRenderTest,QuotationDocumentServiceTest,PiContractTest` | 本地隔离测试库，工作区未提交 | 全部通过 | 5 + 3 + 2 + 12 条通过 | 通过 |
| `mvn test -Dtest=SupplierServiceImplTest` | 本地 | 改名发事件、不改名不发 | 33 条通过 | 通过 |
| `mvn test -Dtest=PayableContractTest#supplierRenamed_payablePayeeFollows` | 本地隔离测试库 | 改名后应付收款方变为新名 | 通过 | 通过 |
| `npx antd lint ./src`、`npm test` | 本地，工作区未提交 | 无问题、全部通过 | 403 个文件无问题；22 组 164 条通过 | 通过 |
| 浏览器走查 UI-038：询盘管理列表点第 2 页 | 本地（走查账号，34 条询盘），工作区未提交 | 停在第 2 页 | 页码为 2，首行变为第 21 条 | 通过；销售订单本地仅 11 条不足两页，改法与询盘相同，由代码对照 |
| 浏览器走查 UI-039：新建询盘选长客户名 | 本地 | 来源在对话框内 | 对话框右边界 921，来源右边界 897；用超长名称对照，旧布局来源右边界到 1199 | 通过 |
| 浏览器走查 UI-040：删掉刷新令牌 Cookie、令牌改为无效后翻页 | 本地 | 直接到登录页，无任何错误提示 | 约 0.5 秒跳到登录页并提示「登录状态已过期，请重新登录」；跳转前记录到的提示为空。只改令牌、保留刷新 Cookie 时静默续期留在原页 | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| UI-038 | 询盘、销售订单列表翻页；排序仍回到第 1 页 | v1.6.0 已部署（[#134](https://github.com/zhul-cloud/zhul-trade-erp/pull/134) 合并 `00eb2ff2`；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814) 成功）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| UI-039 | 超长客户名时来源仍在对话框内 | v1.6.0 已部署（[#134](https://github.com/zhul-cloud/zhul-trade-erp/pull/134) 合并 `00eb2ff2`；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814) 成功）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| UI-040 | 登录过期停留页面后操作，直接到登录页且无错误提示 | v1.6.0 已部署（[#134](https://github.com/zhul-cloud/zhul-trade-erp/pull/134) 合并 `00eb2ff2`；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814) 成功）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-023 | 改供应商名后应付列表、详情、登记付款的收款方为新名 | v1.6.0 已部署（[#134](https://github.com/zhul-cloud/zhul-trade-erp/pull/134) 合并 `00eb2ff2`；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814) 成功）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |
| BUS-024 | 导出 PI / 报价单：DAP 中文国家名为英文，业务员为昵称 | v1.6.0 已部署（[#134](https://github.com/zhul-cloud/zhul-trade-erp/pull/134) 合并 `00eb2ff2`；[生产发版](https://github.com/zhul-cloud/zhul-trade-erp/actions/runs/41653028814) 成功）；本机验收通过（2026-11-14，见事件记录） | Ezio / 2026-11-14 | 已关闭 |

## 阻塞与下一步

- 提交并随反馈批次分支发 PR 到 develop（浏览器走查已完成）。
- 同批反馈第 7 条（管理员看不到待报销单据）原为现有设计（「待报销」只列本人垫付）。产品负责人确认：管理员要能看到所有人的待报销，并能代别人提交。已按确认的原型（报销与费用.op「B1b-报销单-待报销-全部人员」）实现，属于功能调整，不登记缺陷：有「报销付款」权限的人可切到「全部人员」，按垫付人筛选、给别人补传截图、代提交（多人的垫付每人一张报销单，报销人是垫付本人，提交人记在创建人上）；报销人或提交人都可以撤回；标签数字只算本人的。后端 `ReimbursementContractTest` 6 项通过（新增代提交用例），前端检查与测试通过；本地浏览器走查通过（全部人员切换、垫付人列与筛选、多人勾选提示「将生成 N 张报销单」、切回「我的」）。
- 同批反馈第 15 条（询盘列表加型号、品牌、品类列）属于功能调整，经产品负责人同意不走 OpenSpec，只在此记录：按确认的原型（询盘单.op「C2-询盘列表-型号品牌品类列」）实现。「型号数」列并入「型号」列（第一个型号 +「共 N 个 · 查看」，仍可按型号数排序，只有 1 个型号时不显示「查看」）；品牌、一级品类显示前两个，其余「+N」悬浮看全部；新增接口 `GET /api/v1/inquiry/customer-inquiries/{id}/models` 供弹窗查看全部型号（待确认时取 AI 识别结果）。不加品牌、品类筛选。`InquiryIntakeContractTest` 14 项、`InquiryModelSummaryTest` 2 项通过，前端 165 项测试通过，本地浏览器走查通过（本地数据没有多品牌询盘，「+N」只由测试覆盖）。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-03 / Claude | 认领并实施 5 项，单元与契约测试通过（见上表） | 浏览器走查后提交 |
| 2026-11-03 / Claude | 本地浏览器走查 UI-038/039/040 通过；本地库已执行迁移 V1.4.9 | 提交，随批次发 PR |
| 2026-11-03 / Claude | 第 7 条「待报销-全部人员 / 代提交」实现，契约测试 6 项、前端 165 项测试通过 | 本地走查后随批次提交 |
| 2026-11-03 / Claude | 第 15 条询盘列表型号、品牌、品类列实现并走查通过 | 随批次提交 |
| 2026-11-03 / Claude | 第 19 条登记付款选对方收款账户（OpenSpec `payment-payee-account`，已归档）实现：迁移 V1.5.0（`fin_payment` 加对方账户两列，可回退），`PayableContractTest` 9 项通过，前端 165 项测试通过；本地走查通过（公司对公 / 私对私 / 员工垫付切换、登记后付款记录与付款管理列表显示「付至 对公 · 招商银行 · … ****4343」）。走查在本地库生成了 FWPO20261103001 与付款 PY20261103001 | 随批次提交；迁移 V1.5.0 随发版执行 |
| 2026-11-03 / Claude | 第 4 条报销单批量登记已付（OpenSpec `reimbursement-batch-pay`，已归档）实现：新增 `POST /api/v1/finance/reimbursements/batch-pay`，每张报销单各一张报销付款单，截图挂到第一张、其余复制附件记录（共用存储文件）；整批一个事务。`ReimbursementContractTest` 7 项通过（新增批量用例：去重、无权限、共用截图、单张撤销、其中一张已撤回整批回滚、超过 50 张），前端 165 项测试通过；本地走查通过（代提交 2 张后与原有 1 张一起批量登记，生成 PY20261103002～004）。无迁移 | 随批次提交 |
| 2026-11-03 / Claude | 第 17 条询盘详情「货源店铺」列（OpenSpec `inquiry-detail-shop-column`，已归档）实现：只改前端，按 `inquiry:supplier:view` 显示，后端原本就按此权限返回渠道与店铺（供应商渠道存的是供应商名称）。前端检查与 165 项测试通过；本地走查（管理员）通过，询盘 20 显示「淘宝 汇丰科技公司 · 复制店铺名」。没有无权限的本地账号，无权限场景未在浏览器走查。业务员要看需管理员给角色勾选「查看货源信息」 | 随批次提交 |
| 2026-11-03 / Claude | 第 5 条联系人中文名（OpenSpec `contact-name-cn`，已归档）实现：迁移 V1.5.1（`customer_contact.name_cn`、`customer.contact_name_cn`，可回退）；联系人与客户表单可填中文名，留空时保存后由 AI 翻译回填（翻译 key `contact:联系人ID`，只写仍为空的，主联系人同步到客户）；只在新建、改英文名或清空中文名时提交翻译。`CustomerProfileContractTest` 8 项、`OpportunityApiContractTest` 8 项（登记商机的翻译任务数由 2 改为 4）及客户 / 商机 / AI 任务全部测试通过，前端 165 项测试通过；本地走查通过（新增联系人留空中文名提交了翻译任务；本机编排服务的订阅 CLI 不可用，任务失败，改用回调接口模拟成功结果回填「玛丽安娜·奥利维拉」；客户表单手填主联系人中文名后列表新列与联系人卡片都显示，未提交翻译）。走查时把本地客户 25772 的官网补了 https:// 前缀 | 随批次提交；迁移 V1.5.1 随发版执行 |
| 2026-11-03 / Claude | 第 9 条无货源型号可报价（OpenSpec `quote-no-source-line`，已归档）实现：迁移 V1.5.2（`quotation_item` 加无货原因与说明两列，存量无货行按有无替代型号回填，可回退；本地回填 38 行为「暂时缺货」、1 行为「停产有替代型号」）；还在询价的型号可手动选、带入为无货行（「未回价，按无货报」），按询盘报价仍只带已回价的；无货原因四种，选「停产有替代型号」须填替代型号、「其他」须填英文说明；整单无货可发送；文字报价与导出按原因输出，存量行输出与之前一致。询盘详情「去报价」在有型号还在询价时先弹出选型号。`QuotationContractTest` 18 项（新增原因与未回价用例）及报价 / 销售 / 单据 / 询盘 / 商机全部 232 项测试通过，前端 165 项测试通过；本地走查通过：询盘 IQ20261015003（5 个型号都在询价）勾 2 个生成 FWQT20261103001，一行改「其他」填说明，文字报价为「GL-VL1 Keyence 2 lead time 12 weeks, please confirm」，整单无货标为已发送成功（合计 USD 0.00），询价任务与型号状态不变 | 随批次提交；迁移 V1.5.2 随发版执行 |
| 2026-11-03 / Claude | 第 10 条先采购后收款（OpenSpec `order-before-payment`，已归档）实现：迁移 V1.5.3（`sales_order` 加先采购后收款、原因、操作人用户 ID、时间四列与索引，可回退）；PI 没有水单也没有到账时，转订单须勾选「先采购后收款」并填原因（≤200，接口加了 `@Valid`），有水单或到账时忽略；订单列表收款进度旁显示标记、收款状态筛选加「先采购后收款未收款」，详情顶部标记与催款提醒（有水单或到账后消失）；PI 详情「转成订单」不再因未付款置灰。`SalesOrderContractTest` 5 项（新增用例覆盖不勾 / 空原因 / 超长被拒、成功后采购需求生成、筛选、上传水单后退出筛选、有水单时忽略）及销售 / 采购 / 财务全部 165 项测试通过，前端 165 项测试通过；本地走查通过：由 FWQT20261010003 开出 FWPI20261103001 并标为已发送（未付款），勾选先采购后收款转成 SO20261103001，生成 6 条采购需求，详情提醒与列表筛选正常 | 随批次提交；迁移 V1.5.3 随发版执行 |
| 2026-11-11 / Claude | 台账核对：#134 已合并并随 v1.6.0 部署成功，5 项改为已发布待验收 | 待生产验收 |
| 2026-11-14 / Claude | 本机验收（用户确认本机与生产效果相同）：develop `6bdde1f7`（业务代码与生产 v1.22.1 一致）上后端完整 `mvn test` 1652 条、前端 `npm test` 340 条、AI 编排服务 139 条全部通过，含本记录各 ID 的回归测试。用户 Ezio 作为验收人确认关闭 BUS-023、BUS-024、UI-038、UI-039、UI-040 | 无 |
