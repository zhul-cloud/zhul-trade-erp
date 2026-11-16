# BUS-084 11-16 反馈第二批（货代独立、公海客户）合并后审查

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（审查、实施与验证）；用户（验收） |
| 关联 ID / 原专题 | BUS-084、BUS-085、BUS-086、SEC-024。审查对象是 OpenSpec `feedback-1116-batch2`（[#351](https://github.com/zhul-cloud/zhul-trade-erp/pull/351)）的全部改动，属于审查范围外的新发现。已与台账核对，没有重复的既有 ID。 |
| 目标与影响范围 | 采购登记直发货代、费用单收款方选货代、公海客户的新建单据与详情读取。不含已有单据的归属、PI / 报价沿已有链路的流转（见下文「边界」）。 |
| 起点 commit / 工作分支 | `9d521824`（develop，#351 合并）/ `fix/batch2-review` |
| OpenSpec | 不适用：按 `feedback-1116-batch2` 已定规则修缺陷，不改规范 |
| PR / 目标分支 | [#353](https://github.com/zhul-cloud/zhul-trade-erp/pull/353) → `develop` |
| 合并证据 | develop `4cdca928`；main `a77a6146`（v1.28.0） |
| 目标版本 / 环境 | v1.28.0 / 生产 |

## 审查方法

- 用 `/code-review high` 审查 #351，覆盖逐行、删除行为、跨文件调用方、复用、简化、效率、层次、规范八个角度。
- 调用方追踪用 CodeGraph（`codegraph_explore`）。追踪对象：`requireForwarder`、`requireInScope` / `getActiveInScope`、`SupplierPicker`、`/logistics/forwarders` 的权限。
- 结论：4 个缺陷（下表）和 1 个效率问题。效率问题是 `ForwarderService.counts` 重复查询历史占位货代，不算缺陷，未编号，已顺手改掉。

## 现象与根因

| ID | 现象 | 根因 |
| --- | --- | --- |
| BUS-084 | 采购登记发货选「直发货代」时，下拉里有其他客户的指定货代，保存也成功。之后这批直发货会进入本客户的出运单，用的却是别的客户的货代。 | 规则「其他客户的指定货代不能用」只加在发货通知和出运单上。`ShipmentService.direct` 仍调用不带客户的 `requireForwarder(id)`，前端 `ShipmentDrawer` 取的也是全部货代。 |
| BUS-085 | 财务角色只有「费用管理」菜单时，新建费用单选收款方「货代」，下拉为空，提示去货代管理新建。 | 第二批把费用单的货代选择从供应商搜索（没有权限限制）改成了 `/logistics/forwarders`。这个接口只放行销售订单、销售出库、出运、发货跟踪、运费账单菜单，不包括费用管理，所以返回 403。 |
| BUS-086 | 公海客户认领前，仍可经接口新建手动销售订单、提交询盘。 | 「公海客户认领后才能操作」只在 `CustomerServiceImpl.getActiveInScope` 里拦截。手动订单 `SalesOrderServiceImpl.create` 和询盘 `CustomerInquiryServiceImpl.submit` 直接按 ID 取客户，不经过它，只靠前端客户选择器排除公海客户。 |
| SEC-024 | 没有客户管理菜单的用户（如仓库人员）直接请求客户详情接口，能读到公海客户的完整档案和联系方式。 | 客户详情接口没有菜单注解，原来靠数据范围限制。第二批改用 `requireReadable` 后，公海客户对所有登录用户放行；公海列表本身要求客户管理菜单。 |

## 实施与验证结果

### 实施

- **BUS-084**
  - `ShipmentService.requireOneCustomer`：确定直发型号所属客户后，用 `requireForwarder(id, 客户)` 校验。没有客户时，只能用公司货代。
  - 发货表单 `ShipmentFormVO.Line` 新增 `customerId`。`ForwarderVO` 新增 `customerId`（归属客户）。
  - 前端 `ShipmentDrawer`：按勾选型号的客户过滤，只列公司货代和这个客户的指定货代，客户指定的排在前面并标「客户指定」。
- **BUS-085**：`OutboundController` 的 `GET /logistics/forwarders` 增加放行条件 `canAccessMenu('/finance/expenses')`。
- **BUS-086**
  - 新增 `masterdata/support/CustomerPool.requireClaimed`，`getActiveInScope`、手动销售订单、询盘提交三处共用。
  - 商机从「主动开发」创建前已经经过 `requireInScope`，会被拦住，不用改。
- **SEC-024**：`requireReadable` 只对有客户管理菜单的用户放行公海客户；没有菜单的仍按数据范围判断，公海客户读不到。
- **效率**：`ForwarderService.counts` 只取一次占位货代列表。

**边界（不改）**：报价转 PI、PI 转订单沿已有单据链路进行，不按客户是否在公海拦截。这与第二批「已有单据归属不变」一致。

### 验证

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 缺陷见证：暂时撤下 `src/main` 的修复，运行新测试 `OutboundShippingContractTest#directToForwarder_rejectsOtherCustomersForwarder` | 本机隔离库，`fix/batch2-review` 测试 + `9d521824` 主代码 | 失败 | 用其他客户的指定货代登记直发成功（生成 SD 单） | BUS-084 见证成立 |
| 缺陷见证：同上，运行 `ForwarderContractTest#expensesMenuCanListForwarders` | 同上 | 失败 | 只有费用管理菜单的用户取货代列表返回 403 | BUS-085 见证成立 |
| 缺陷见证：同上，运行 `CustomerPoolContractTest#poolCustomer_newDocumentsRejected` | 同上 | 失败 | 公海客户的手动订单越过客户校验，一直走到「请至少添加一个型号」 | BUS-086 见证成立 |
| 缺陷见证：同上，运行 `CustomerPoolContractTest#poolDetailNeedsCustomerMenu` | 同上 | 失败 | 没有客户管理菜单的用户取到公海客户详情（code 0） | SEC-024 见证成立 |
| 修复后运行 `CustomerPoolContractTest`、`ForwarderContractTest`、`OutboundShippingContractTest`、`CustomerServiceImplTest` | 本机隔离库，`fix/batch2-review` | 全部通过 | 4、2、15、37 条全部通过 | 通过 |
| 前端 `npm run lint`、`npx antd lint ./src`、`npx jest` | 本机，`fix/batch2-review` | 全部通过 | lint 通过；antd 596 个文件无问题；Jest 119 个文件 / 482 条通过 | 通过 |
| 后端全量 `mvn test` | 本机隔离库，`fix/batch2-review` | 除本机已知的连接数上限报错外全部通过 | 1786 条，失败 0；53 个报错全在 `ProductCoreIntegrationTest`、`AfterSalesReturnConcurrencyContractTest`、`SupplyAuditFixContractTest`（Too many connections），单独重跑 42、3、8 条全部通过 | 通过 |
| 本机实测 BUS-084：验收租户的采购单（型号属于客户 1）点「登记发货」，选「直发货代」 | 本机 Docker，前后端按 `fix/batch2-review` 重建，浏览器 | 只列公司货代和客户 1 的启用中的指定货代 | 下拉为「验收 Acro Freight 181036（客户指定）」、两家公司货代；客户 2 的两家和已停用的不出现；选指定货代登记成功（SD20261116001）；接口提交客户 2 的货代返回「是其他客户指定的货代」 | 通过 |
| 本机实测 BUS-085：只有「费用管理」菜单（含按钮）的财务账号新建费用单，收款方选「货代」 | 同上 | 能取到货代列表并选择 | 侧边栏只有费用管理；`/logistics/forwarders` 返回 200，下拉列出全部启用的货代 | 通过 |
| 本机实测 BUS-086：管理员用接口给公海客户（客户 2）新建手动订单、提交询盘 | 同上 | 拒绝 | 两个都返回「在公海，认领后才能操作」 | 通过 |
| 本机实测 SEC-024：同一公海客户的详情接口 | 同上 | 财务账号（没有客户菜单）被拒绝，管理员正常 | 财务账号返回「在公海，认领后才能操作」；管理员 code 0 | 通过 |
| BrowserOS neo 界面复测（按规则改用 BrowserOS neo，截图确认） | 本机 Docker，`fix/batch2-review` 重建，BrowserOS neo | 同上四项 | BUS-084 直发货代下拉为「客户指定」+ 两家公司货代；BUS-086 手动创建订单的客户框搜不到公海客户；SEC-024 财务账号打开公海客户详情页显示 403；BUS-085 财务账号费用单选「货代」列出全部启用的货代 | 通过 |

实测数据（验收租户 1001 里的采购单 PO-ACC-B2、财务角色和账号、发货单）留在本机库；验收后租户与套餐已停用，浏览器登录信息与临时脚本已删除。

### 第二批验收中发现、已在 #351 合并前修复的问题（未编号）

以下问题都在 #351 合并前、本机验收时发现并修好，从未进入 develop，所以不新增 ID：
- 前端 `access.ts` 漏加货代按钮权限 key，货代管理页没有新增、编辑、停用按钮。
- 公海客户详情加载完之前就请求跟进汇总，弹出「认领后才能操作」报错。
- 放入公海只点了快捷原因时，原因末尾多一个「：」。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| BUS-084 | 直发货代只能选公司货代或本客户的指定货代；其他客户的被拒绝 | v1.28.0 已部署（运行 43857633362 成功） | 用户 / 2026-11-17 | 已关闭：用户决定按本机验收关闭，未做生产核对 |
| BUS-085 | 只有费用管理菜单的财务能在费用单里选货代 | v1.28.0 已部署 | 用户 / 2026-11-17 | 已关闭：用户决定按本机验收关闭，未做生产核对 |
| BUS-086 | 公海客户认领前不能新建手动订单、提交询盘 | v1.28.0 已部署 | 用户 / 2026-11-17 | 已关闭：用户决定按本机验收关闭，未做生产核对 |
| SEC-024 | 没有客户管理菜单的用户读不到公海客户详情，有菜单的能读 | v1.28.0 已部署 | 用户 / 2026-11-17 | 已关闭：用户决定按本机验收关闭，未做生产核对 |

## 阻塞与下一步

- 已随 v1.28.0 上线；待用业务租户账号在生产核对四项后关闭。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-16 / Claude | 用户要求按台账审查第二批新增的问题；#351 合并后审查，新增 BUS-084～086、SEC-024，在 `fix/batch2-review` 修复，缺陷见证与回归测试见上表 | 本机实测 |
| 2026-11-16 / Claude | 后端全量测试通过（连接数报错的三个类单独重跑通过）；本机重建前后端，浏览器与接口实测四项全部通过，见上表 | 提交 PR，随 v1.28.0 发版 |
| 2026-11-16 / Claude | 用户指出界面实测应按规则用 BrowserOS neo（之前误用了 Claude 内置浏览器），四项改用 BrowserOS neo 重做，全部通过（见上表）。#353 合并到 develop（`4cdca928`）。 | 随 v1.28.0 发版 |
| 2026-11-16 / Claude | v1.28.0 上线：发版 PR #354 合并到 main（`a77a6146`），生产发版运行 43857633362 成功，标签 v1.28.0；4 个 ID 改为「已发布待验收」。用户授权后在生产用 BrowserOS neo 只读核对（平台超级管理员账号，没有业务数据）：货代管理菜单与页面正常；客户管理有「公海客户」页签；供应商类型筛选已没有「服务商」。没有保存或修改任何数据。 | 用业务租户账号在生产核对四项（直发货代下拉、费用单选货代、公海客户新建单据、无客户菜单读公海详情） |
| 2026-11-17 / 用户、Claude | 用户决定按本机验收关闭 BUS-084、BUS-085、BUS-086、SEC-024，改为「已关闭」，未做生产核对 | — |
