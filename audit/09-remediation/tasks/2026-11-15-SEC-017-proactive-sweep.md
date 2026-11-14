# SEC-017 等：复审修复后的同类与全项目主动排查（2026-11-15）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（排查、实施与验证）；用户 2026-11-15 要求核实其他地方是否存在同类问题、不只修复审报告列出的位置，并主动查找项目问题、整理到台账 |
| 关联 ID / 原专题 | 新增：SEC-017、BUS-039、BUS-040、UI-055、UI-056、UI-057；修复不完整、继续处理：SEC-005、SEC-016、AI-005（[上一轮修复](2026-11-14-BUS-038-review-fixes.md)） |
| 目标与影响范围 | 后端对象数据范围、附件链接与挂接、资金金额校验；前端保活页面返回刷新、弹窗异步响应、未保存保护；AI 编排服务附件读取 |
| 起点 commit / 工作分支 | develop `4f933229` / `fix/sweep-1115` |
| OpenSpec | 缺陷修复，不新开提案；不新增界面 |
| PR / 目标分支 | 未创建 |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | 下一个发布版本 |

## 排查方法与覆盖

- **按 ID 入口的数据范围**：脚本列出所有使用数据范围的服务里，每个按 ID 的公开方法实际用的查找函数（只校验租户的 `require` / 带范围的 `visible` 等），再逐个核对对应控制器向哪些菜单开放；另外列出全部「按关联单据列出」的接口（`/order/{soId}`、`/of-order/{poId}`、`/by-source` 等），核对是否先校验关联单据的范围。
- **附件**：逐个附件类型对照所属单据详情的可见规则（含报销 / 费用 / 付款的「本人垫付」规则）；`attach`、`reassign`、`copy`、`sync` 的挂接校验。
- **金额**：脚本列出全部请求 DTO 里没有正数约束注解的金额字段，逐个核对服务层兜底；扫描「先判断大于 0、后按两位舍入」的写法。
- **前端**：统计按 `load` 取数却不在返回时刷新的页面；扫描弹窗 / 抽屉在打开时按对象取数、没有丢弃过期响应的写法；扫描页面内编辑区与整页表单的未保存登记。
- **规范项**：MyBatis `${}` 与 SQL 拼接、`JdbcTemplate` 手写 SQL 的租户条件、业务数据物理删除、浮点金额、除法精度、前端直接插入 HTML。
- **编排服务**：其他下载、线程池、长任务心跳与用量路径。

以下检查没有发现问题：退款单、客户背调、采购入库单、订单收款、付款单详情、PI / 报价按关联单据查询的范围；参考价、汇率、平台手续费、采购退货退款、余额直接转出的金额校验；SQL 拼接只用常量和框架条件片段；`JdbcTemplate` 手写 SQL 都带租户条件；物理删除只涉及映射表与基础表；没有浮点金额、无精度除法和直接插入 HTML；编排服务其他任务的期限（900 秒）短于后端空闲扫描（20 分钟），单线程任务的用量随回调上报。

## 发现

### SEC-005（上一轮修复不完整）· 报销、费用、付款类附件链接仍只校验租户

上一轮把付款、报销、费用、快递凭证、退货运费凭证归为「详情只校验租户」，与实际规则不符：[付款单详情](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/PaymentService.java)对只有采购订单菜单的人按采购员限定；[报销单](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/ReimbursementService.java)没有报销付款权限的人只看本人的，待报销项（快递、退货运费、垫付费用、垫付付款）按垫付人是本人。附件接口对这些菜单开放，同租户员工已知附件 ID 即可取得别人报销、垫付的凭证或别的采购员付款单附件的链接。

### SEC-016（上一轮修复不完整）· 询价回价附件的重新挂接不核对上传人

[`AttachmentService.reassign`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/attachment/service/AttachmentService.java)（我的询价保存回价时用）对还没挂上的附件只校验租户与类型，与上一轮 `attach` 的缺口相同。

### AI-005（上一轮修复不完整）· 旧的整份读取附件路径仍不受期限与大小约束

复审报告已指出「旧整份读取也有同类缺口」，上一轮只改了大附件流式下载。[`_read_attachment`](../../../scripts/ai-orchestrator/server.py) 一次 `resp.read()` 读完整个附件，只在建立连接时检查期限，没有 500MB 上限。当前询盘解析传入的都是已用流式下载读好的内容，这条路径暂时走不到，但仍是公开的调用方式。

### SEC-017 · P2 · 按关联单据列出的接口未校验关联单据的数据范围

- [应付按来源](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/PayableQueryService.java)（`GET /finance/payables/by-source`）对采购订单菜单开放，直接按来源 ID 返回应付单（金额、付款、核销），没有校验采购订单的采购员范围。
- [采购退货按采购订单](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/purchase/purchasereturn/service/PurchaseReturnService.java)（`GET /purchase/returns/of-order/{poId}`）对采购订单菜单开放，没有校验采购订单范围；采购退货详情本身按负责人限定。
- [售后单按订单](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/aftersales/service/AfterSalesService.java)（`GET /sales/after-sales/order/{soId}`）对销售订单、售后菜单开放，没有校验订单范围；售后单详情本身按负责人限定。
- 影响：同租户本人范围的采购员 / 业务员按别人单据的 ID 读到应付、采购退货、售后单信息。与 SEC-013、SEC-015 同属「入口绕过单据范围」，是不同接口的独立缺口。

### BUS-039 · P1 · 多收退回客户不校验实际转出人民币为正

[`CustomerCreditService.processOverpaid`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/CustomerCreditService.java) 退回客户时 `actualCny` 只校验不为空，负数、零或舍入为零都能生成余额提现退款单；汇兑损益按「流水人民币 − 实际转出人民币」计算，负数会把汇兑收益记大并生成其他收入。直接「转给客户」的请求有 `@DecimalMin`，这条入口没有。与 BUS-038 同类，资金入口不同。

### BUS-040 · P3 · 到账与订单费用先判断正数、后舍入，可登记 0 元单据

确认到账、平台收款（[`PaymentReceiptServiceImpl`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/PaymentReceiptServiceImpl.java)）、登记未认领到账（[`ReceiptLedgerServiceImpl`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/ReceiptLedgerServiceImpl.java)）、订单费用行（[`SalesOrderServiceImpl`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/sales/service/impl/SalesOrderServiceImpl.java)）先判断 `> 0` 再按两位 `HALF_UP` 舍入，`0.004` 通过检查后存为 0.00，形成金额为 0 的有效到账或费用。

### UI-055 · P2 · 保活页面从其他页面返回后不刷新（UI-053 只修了销售退货）

除销售退货列表外，约 80 个列表与详情页只在挂载或筛选变化时取数（`load` + `useEffect`），标签框架隐藏保活，返回时不重新挂载。在详情里改状态、或在关联单据上操作后返回，列表行、计数和原详情都显示旧数据，直到手动刷新。

### UI-056 · P2 · 多个弹窗切换对象时未丢弃迟到响应（UI-054 同类）

应付「登记付款」弹窗按应付单 `load(anchor)` 与收款账户取数、售后「申请退款」弹窗按订单取可退额度、PI 到账认领按 PI 取可认领到账、PI 抬头按 PI / 客户取候选等，打开时按对象异步取数，关闭或换对象后旧请求不作废。上一个对象的响应迟到会覆盖当前弹窗；登记付款的核销分配会落成上一张应付单的行。

### UI-057 · P2 · 未保存保护覆盖不全（UI-004 / UI-052 同类）

- [销售订单「备注与附件」卡](../../../zhul-erp-frontend/src/pages/sales/orders/OrderFiles.tsx)的备注草稿与 UI-052 写法相同，没有登记未保存。
- [采购订单详情](../../../zhul-erp-frontend/src/pages/purchase/orders/detail.tsx)草稿的整单编辑没有任何未保存保护。
- [客户表单](../../../zhul-erp-frontend/src/pages/customer/form.tsx)、[供应商表单](../../../zhul-erp-frontend/src/pages/supplier/form.tsx)只在点自己的「取消」时确认，关标签、点返回条、右键刷新都直接丢失。

## 实施与验证结果

### 实施

- **SEC-005 补全**：[`AttachmentOwnerScope`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/attachment/service/AttachmentOwnerScope.java) 不再把付款、报销、费用、快递凭证、退货运费凭证归为只校验租户。付款附件改用 [`PaymentService.attachmentVisible`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/finance/service/PaymentService.java)：付款与应付管理看全部；只有采购订单菜单的按核销到的采购订单采购员；运费账单菜单看核销到运费账单应付的；报销菜单看本人垫付的，有报销付款权限的看全部。报销单看申请人，费用看垫付人（费用菜单看全部），快递单看垫付人，退货运费凭证看垫付人或按采购退货负责人；有报销付款权限的看全部。仍只校验租户的类型只剩拍摄、素材、询价回价，以及只对财务菜单开放的其他收入、应付、结汇。
- **SEC-016 补全**：`reassign` 与 `attach` 一致，未挂附件只有上传人能挂。
- **SEC-017**：应付按来源对非应付管理用户校验采购订单采购员范围；采购退货按采购订单改用与详情相同的负责人范围；售后单按订单对非财务用户按「售后单负责人或订单负责人在范围内」过滤，与详情一致。
- **BUS-039**：余额转出的公共边界 `withdraw` 校验实际转出人民币舍入后大于 0，直接转出与退回多收都经过它。
- **BUS-040**：确认到账、平台收款、登记未认领到账、订单费用行改为按两位 `HALF_UP` 舍入后的金额判断。
- **AI-005 补全**：`_read_attachment` 改为复用流式下载 `lp.download`，与大附件同样受整任务期限和 500MB 约束。
- **UI-055**：78 个按 `load` 取数的列表、详情与工作台卡片加 `useOnReactivate(load)`，从其他页面返回时重新取数，筛选、分页、滚动保留。有草稿或就地编辑的页面不加（报价、PI、采购订单详情，商品详情的编辑卡片，AI 设置，汇率与运输时效的表格内编辑，国家资料），以免返回时冲掉未保存内容；费用、其他收入详情在修改弹窗开着时不刷新。
- **UI-056**：新增 [`liveRequests`](../../../zhul-erp-frontend/src/utils/liveRequests.ts)，effect 清理时作废，旧请求迟到的成功与失败都丢弃；用于售后申请退款与补发、PI 到账认领 / 抬头 / 选报价、新建 PI、余额流水、批量付款与登记付款的收款账户、出运单、发货抽屉、报价定价与报价文本、删除订单检查、选 PI、客户等级与合并候选、银行账户编辑、结汇。登记付款的 `load` 用请求序号丢弃过期结果。只加载全局下拉选项（负责人、品牌品类、取消原因等）的不改。
- **UI-057**：新增 `useUnsavedGuard`（登记标签未保存并提示浏览器离开），公共附件卡改用它；销售订单备注卡、采购订单草稿编辑（草稿与订单原值比较）、客户表单、供应商表单接入，保存成功或确认离开后清除。

### 验证

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| [`RelatedDocScopeContractTest`](../../../zhul-erp-backend/src/test/java/com/zhul/erp/modules/attachment/RelatedDocScopeContractTest.java)（4 例：付款附件、报销类凭证、`reassign`、按关联单据列出） | 本机 MySQL；修复前 / 后 | 修复前失败、修复后通过 | 修复前 4 例全失败：别人垫付的凭证与别的采购员付款附件返回 200、`reassign` 挂上别人的未挂附件、按来源返回别人订单的应付；修复后全部通过，本人、财务、报销付款权限、应付管理正常 | 通过 |
| [`CustomerCreditContractTest`](../../../zhul-erp-backend/src/test/java/com/zhul/erp/modules/finance/CustomerCreditContractTest.java)「多收退回」补拒绝对照 | 同上 | 同上 | 修复前 `0.004` 到账登记成功（BUS-040）；单独还原 BUS-039 时实际转出人民币 `-1` 退回成功；修复后两者都拒绝，多收仍待处理、无汇兑收益，正常退回照旧 | 通过 |
| 后端完整 `mvn test` | 本机 MySQL | 通过 | 1687 条，0 失败、8 条跳过 | 通过 |
| 编排服务 `test_whole_read_path_also_streams_within_job_deadline` 与全部测试 | 编排服务镜像，2GB 容器；修复前 / 后 | 同上 | 修复前慢流整份读取不在期限停下；修复后 149 条全部通过 | 通过 |
| 前端：[`liveRequests.test.ts`](../../../zhul-erp-frontend/src/utils/liveRequests.test.ts)、[`deleteOrder.test.tsx`](../../../zhul-erp-frontend/src/pages/sales/orders/deleteOrder.test.tsx) 迟到用例、[`orderFilesUnsaved.test.tsx`](../../../zhul-erp-frontend/src/pages/sales/orders/orderFilesUnsaved.test.tsx)、`hooks.test.tsx` 浏览器离开用例 | Jest；修复前 / 后 | 同上 | 还原订单备注卡与删除订单弹窗时 2 个新用例失败；修复后通过 | 通过 |
| `npm run lint`、`npx antd lint ./src`、`npm test` | 本机 | 通过 | 无错误；545 个文件无问题；74 组 377 条通过 | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| SEC-005 | 付款、报销、费用、快递与退货运费凭证附件按所属单据规则 | 未发布 | — | 处理中 |
| SEC-016 | `reassign` 未挂附件只有上传人能挂 | 未发布 | — | 处理中 |
| AI-005 | 旧整份读取路径受期限与大小约束 | 未发布 | — | 处理中 |
| SEC-017 | 三个按关联单据列出的接口校验关联单据范围 | 未发布 | — | 处理中 |
| BUS-039 | 多收退回客户拒绝非正的实际转出人民币 | 未发布 | — | 处理中 |
| BUS-040 | 到账与订单费用按舍入后的金额判断 | 未发布 | — | 处理中 |
| UI-055 | 保活页面返回后刷新 | 未发布 | — | 处理中 |
| UI-056 | 弹窗丢弃过期响应 | 未发布 | — | 处理中 |
| UI-057 | 销售订单备注卡、采购订单草稿编辑、客户与供应商表单登记未保存 | 未发布 | — | 处理中 |

## 阻塞与下一步

- 实施修复，补修复前失败、修复后通过的测试，本机复测后创建 PR。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-15 / Claude | 按用户要求在 develop `4f933229` 做同类与全项目排查（方法见上）；新增 SEC-017、BUS-039、BUS-040、UI-055、UI-056、UI-057，SEC-005、SEC-016、AI-005 上一轮修复不完整，状态改回处理中 | 实施修复 |
