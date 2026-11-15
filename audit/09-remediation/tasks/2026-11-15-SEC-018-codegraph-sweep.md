# SEC-018 等：用 CodeGraph 做全项目结构排查（2026-11-15）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（排查、登记、修复与本机验收）；用户 2026-11-15 要求用 CodeGraph 主动排查项目问题、整理到问题台账，随后要求「全部修复，本机验收后合并」 |
| 关联 ID / 原专题 | 新增：SEC-018、SEC-019、AI-011、UI-058，补充验收中新增 UI-059；修复不完整、重开：UI-055（[原修复](2026-11-15-SEC-017-proactive-sweep.md)） |
| 目标与影响范围 | 后端接口权限、事务与行锁、金额写法、AI 回调；前端保活页面返回刷新、页面跳转规范；最近合并未审查过的 #322（商品品类细分）与 #326（供应链三期） |
| 起点 commit / 工作分支 | 排查：develop `eec51e32` / `docs/audit-codegraph-sweep`（只改 `audit/`）；修复：develop `4190849d` / `fix/codegraph-sweep` |
| OpenSpec | 缺陷登记，不新开提案 |
| PR / 目标分支 | 排查记录：[#328](https://github.com/zhul-cloud/zhul-trade-erp/pull/328) → develop；修复：[#329](https://github.com/zhul-cloud/zhul-trade-erp/pull/329) `fix/codegraph-sweep` → develop；UI-058 兜底补修与 UI-059：`fix/tabs-backor-replace` → develop（PR 见事件记录） |
| 合并证据 | #328 合并到 develop `4190849d`；#329 合并到 develop `7cec009b`（2026-11-15，CI 后端两个分片全量、前端、AI 编排服务测试通过） |
| 目标版本 / 环境 | 待定 |

## 排查方法与覆盖

CodeGraph 1.6.2 索引（2583 个文件，节点 94296、边 214836，与 develop `eec51e32` 一致）。用它的节点、调用边和路由节点做结构查询，再回到源码逐个确认；只读访问索引库，没有改动项目代码。

| 检查 | 方法 | 结果 |
| --- | --- | --- |
| 同类内部调用代理注解方法 | 调用边中源、目标在同一类，目标带 `@Transactional` / `@Async` / `@Cacheable` 而调用方不带 | 9 处，8 处是名称解析把 `mapper.update` 当成同名服务方法的误报；`NotificationService.count/list` 调 `refreshOpen` 不经代理，但每条更新独立且幂等，不算问题 |
| 行锁与事务 | 项目不用 Redisson，加锁都是 `FOR UPDATE`；从 67 个含 `FOR UPDATE` 的方法沿调用边向上找到控制器 / 定时任务入口，看路径上有没有事务 | 写操作入口都在 `@Transactional` 内；剩余路径是带「是否加锁」参数的只读查询和预览，不影响正确性 |
| 接口权限 | 856 个路由节点对应的处理方法，没有方法级、类级 `@PreAuthorize` 的 102 个逐个看服务层兜底和前端调用方 | 发现 SEC-018、SEC-019；字典、选项、商品主数据、公共入口等为有意开放；供应商「快速新建」被询价、采购等 8 个页面共用，属设计 |
| 金额写法 | `BigDecimal.equals`、`doubleValue()`、`new BigDecimal(double)` | `doubleValue()` 只用于写 Excel 单元格，构造参数都是字符串，没有问题 |
| AI 回调 | 9 个 `AiTaskResultHandler` 实现对模型回传 key 的处理 | 发现 AI-011 |
| 新合并代码 | #322、#326 的服务、监听与前端页面 | 品类细分、需求品类补齐、履约异常回写的条件更新、事务边界和无租户上下文处理都正确；只有 AI-011 涉及 #322 |
| 前端返回刷新 | 会跳转到其他页面、按 `load` 取数却没有 `useOnReactivate` 的页面，逐个核对是否带草稿 | 发现 UI-055 漏改页面 |
| 页面跳转规范 | 地址里带 `from`、自写返回按钮 | 发现 UI-058；「返回列表」只出现在找不到记录的空状态里，符合规范 |

## 发现

### SEC-018 · P2 · 操作日志、系统参数、角色与菜单授权的读取接口只要求登录

以下接口没有菜单或功能权限校验，服务层也只按租户过滤，同租户任何登录用户（如本人数据范围的业务员）都能调用：

- 操作日志：[`LogController`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/controller/LogController.java#L44) 的列表、模块、详情、导出（第 44–64 行）。同一控制器的登录日志要求 `@perm.canAccessMenu('/system/log/login')`，菜单 `/system/log/operate` 也存在，只是后端没用。详情返回每次操作的前后内容；全项目 173 处写操作日志，含付款收款方与金额、退款、余额调整、费用、结汇等财务信息，业务员借此能看到本人数据范围以外的单据。
- 系统参数：[`ConfigController`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/controller/ConfigController.java#L35) 的列表与分组计数。加密项已脱敏为 `****`，未加密的配置值原样返回。
- 角色：[`RoleController`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/controller/RoleController.java#L36) 的列表、统计、角色数据范围部门（第 36、53、89 行）。
- 菜单：[`MenuController`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/controller/MenuController.java#L33) 的菜单树与角色菜单（第 33、85 行）。该类已定义 `@perm.canAccessMenu('/system/menu')` 常量，只用在写操作上。

前端只有对应的系统管理页面调用这些接口；例外是菜单树另被角色管理、租户套餐页面使用，`/roles/all` 被用户管理使用，修复时权限要覆盖这些页面。

> 修复时更正：菜单树 `GET /system/menus` 还是所有登录用户的侧边栏来源（`app.tsx` 的 `getInitialState` 读它再按个人权限过滤），只含菜单结构、不含业务数据，保持登录可读，不属于本问题；本机验收时在业务员侧边栏发现了这一点，详见「实施与验证结果」。

证据：CodeGraph 路由节点与源码静态核对，未在运行环境中实测。

### SEC-019 · P3 · 用户列表接口向所有登录用户返回同事的手机号和邮箱

[`UserController.list`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/controller/UserController.java#L36) 只要求登录，返回的 [`UserVO`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/dto/UserVO.java#L11) 含手机号、邮箱。销售订单、PI、报价、询价、商机、客户跟进、供应链等 10 个业务页面用它做负责人下拉，所以不能简单收紧权限；应给下拉提供只含 ID、姓名、部门的选项接口，完整列表留给用户管理菜单。编码规范把手机号、邮箱列为敏感数据。

证据：源码与前端调用方静态核对，未在运行环境中实测。

### AI-011 · P3 · AI 回调信任模型回传的 key，不核对是否属于本次提交

编排服务只在提示词里要求模型「key 原样返回」（[`server.py`](../../../scripts/ai-orchestrator/server.py#L724) 等 5 处），不过滤；后端回调按 key 直接定位数据，不对照任务自己的输入：

- 商品品类细分 [`CategoryRefineService.handle`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/refine/service/CategoryRefineService.java#L214)：任何解析得出的「商品 ID#品类 ID」，只要商品存在、当前品类与待细分状态对得上就写入建议。商品库是全局表、不走租户拦截，回调里也不校验维护权，模型把 ID 写错时可能写到本次未提交、甚至其他公司维护的商品上，这些商品随后出现在「待采纳」里。
- 商品候选 AI 补全 `CandidateAiFill.handle`、联系人名称翻译 `ContactNameTranslations`、型号描述翻译 `DescriptionTranslations`：同样按回传 key 定位；这些表走租户拦截，影响限于本租户内写错对象。

建议回调先从 `ai_task.input` 取出本次提交的 key 集合，只处理集合内的 key。

> 修复时更正：`ai_task` 按设计不存提交内容（`AiTaskServiceImpl.create` 注释），回调时取不回输入；改用自校验的 key，见「实施与验证结果」。

证据：源码静态核对，未构造模型错写 key 的回调实测。

### UI-055（修复不完整，重开）· 部分保活页面返回后仍显示旧数据

UI-055 给 78 个页面加了 `useOnReactivate(load)`，并有意排除了带草稿或就地编辑的页面（报价、PI、采购订单详情等）。本轮按「会跳转到其他页面、按 `load` 取数、没有草稿」筛出以下页面，既不在排除名单里也没有加：

- 客户详情、客户背调详情、运营商品详情、客户询价详情、询价分配看板
- 供应链的需求、覆盖、跟进、缺口项目页面
- 历史数据迁移页面

例如从客户详情打开订单或 PI 并改动后返回，客户详情仍是旧数据，要手动刷新。带草稿页面的取舍维持原决定；如要改善，可在没有未保存修改时才刷新。

证据：源码静态核对，未在浏览器中复现。

### UI-058 · P3 · 客户、供应商页面离开时压栈或在地址带 `from`，返回条指向已离开的页面

[`customer/detail.tsx`](../../../zhul-erp-frontend/src/pages/customer/detail.tsx#L425) 与 [`CompletionTab.tsx`](../../../zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx#L437) 跳到 `…/edit?from=detail`，[`customer/form.tsx`](../../../zhul-erp-frontend/src/pages/customer/form.tsx#L247) 据此在保存后 `history.push` 回详情。项目规范（2026-11-11）要求返回由标签页框架的返回条统一提供，不在地址里带 `from`。其他表单没有这种写法。

修复时补查：`history.push` 在标签页里是入栈，保存后再 push 回详情，栈变成「列表 → 详情 → 编辑 → 详情」，返回条显示「返回 编辑客户」，点了回到已保存的编辑页。同类写法还有供应商表单保存后、客户转移后、背调报告删除后、客户与供应商详情找不到记录时的「返回列表」，一并归入本 ID。

证据：源码静态核对。

### UI-059 · P3 · 不在菜单里的表单与迁移页没有登记标题，标签显示「上一页」或「××详情」（补充验收中发现）

标签与返回条的名称来自页面登记的标题；没有登记时，有菜单前缀的显示「菜单名详情」，没有的显示「上一页」。历史数据迁移页（`/tenant/history-migration`）的标签显示「上一页」；客户与供应商的新建 / 编辑、询盘确认、询价分配规则页显示「客户管理详情」「供应商库详情」等泛称。项目规范要求这类页面用 `usePageTitle` 登记标题。新建商品是独立全屏页（`layout: false`），不在标签框架里，不涉及。

证据：本机 BrowserOS 实测（迁移页标签「上一页」、供应商编辑页标签「供应商库详情」）；源码比对路由与菜单资源。

## 实施与验证结果

### 实施

- **SEC-018**：操作日志的列表、模块、详情、导出要求 `/system/log/operate` 菜单；系统参数的列表、分组计数要求 `/system/config`；角色的列表、统计、删除前校验、数据范围部门要求 `/system/role`，`/roles/all` 要求角色管理或用户管理；菜单的删除前校验要求 `/system/menu`，角色已分配菜单要求 `/system/role`。菜单树保持登录可读（见上方更正）。
- **SEC-019**：新增 `GET /api/v1/system/users/options`（登录可用，只返回 ID、姓名，可按姓名、状态筛选，最多 500 条），9 个负责人下拉与部门负责人搜索改用它（[`services/zhul/users.ts`](../../../zhul-erp-frontend/src/services/zhul/users.ts)）；完整用户列表与统计要求 `/system/user`。
- **AI-011**：[`AiTaskKeys`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/aitask/support/AiTaskKeys.java) 在发出的 key 末尾加 `~` 和 6 位 SHA-256 摘要，回调先校验，校验不过的条目丢弃；品类细分、商品候选、联系人名称翻译、型号描述翻译四处都用它。品类细分回调另外按 `ProductWriteGuard` 只写本公司能维护的商品。编排服务与提示词不变（key 仍原样返回）。上线时正在运行的这四类任务，回调里的旧 key 没有校验码会被丢弃，需要重新发起（这类任务一般几分钟内完成）。
- **UI-055**：上述 10 个页面加返回刷新；覆盖矩阵把取数抽成 `load`，切换条件时先清空，返回时保留当前矩阵直接重取；供应链跟进只在「我的」视图重取。
- **UI-058**：标签框架新增 [`tabsActions.backOr(fallback)`](../../../zhul-erp-frontend/src/components/PageTabs/store.ts)：退回本标签上一页，没有上一页时去兜底地址；客户表单、供应商表单、客户详情、客户背调详情、供应商详情的离开都改用它，去掉 `?from=detail`。

### 验证

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 缺陷见证：业务代码换回修复前（保留新增的校验工具以便测试编译），运行新增与改动的测试 | 本机 `mvn test`，MySQL 测试库 | 新测试失败 | [`SystemReadPermissionContractTest`](../../../zhul-erp-backend/src/test/java/com/zhul/erp/modules/system/SystemReadPermissionContractTest.java) 3 条失败（应 403 实为 200、选项接口不存在）；`CategoryRefineContractTest.callback_dropsMiscopiedKeysAndOtherCompanyProducts`、`CustomerProfileContractTest`（无校验码的 key 写入了「错写」）、`QuotationContractTest`（结果 2 条）失败 | 见证成立 |
| 修复后后端全量 `mvn test` | 本机，`fix/codegraph-sweep` | 全部通过 | 1697 条中 4 条失败：`NavigationContractTest` 3 条（这次运行时菜单树还被收紧，即上方更正的问题）、`OpportunityApiContractTest` 1 条（测试里的回调 key 没加校验码）；修正后这两组及本次改动的另外 3 组测试补跑全部通过 | 通过（以补跑为准，CI 再跑全量） |
| 前端 `npm run lint`、`npx antd lint ./src`、`npm test` | 本机 | 全部通过 | Biome、tsc、antd 检查通过；Jest 78 个文件 387 条通过（含新增 [`store.test.ts`](../../../zhul-erp-frontend/src/components/PageTabs/store.test.ts)、[`users.test.ts`](../../../zhul-erp-frontend/src/services/zhul/users.test.ts)） | 通过 |

### 本机验收（2026-11-15）

按用户「本机验收后合并」的要求，在本机用 `fix/codegraph-sweep` 重建后端、前端镜像，新建隔离租户（模板「业务员」角色账号、租户管理员、一个客户）。没有到生产，没有真实 AI 调用。

| 验收项 | 方式 | 结果 |
| --- | --- | --- |
| SEC-018 | HTTP | 业务员访问操作日志（列表、模块、导出）、系统参数（列表、分组计数）、角色（列表、下拉、统计、数据范围部门）、角色已分配菜单、用户列表与统计共 13 个接口均 403，管理员均 200；菜单树两者都 200 |
| SEC-018 | BrowserOS（管理员） | 用户管理、角色管理、系统设置、操作日志、部门管理页面正常加载，无报错 |
| SEC-019 | HTTP + BrowserOS | 业务员调用用户选项 200，只有 `id`、`name`，不含手机号、邮箱，姓名筛选与条数上限生效；业务员的侧边栏正常、销售订单页「业务员」下拉显示姓名；管理员在部门管理新增部门时搜索负责人，请求走选项接口并返回姓名 |
| UI-055 | BrowserOS（管理员） | 覆盖矩阵切到其他标签再切回，矩阵重新取数（请求 1 → 2），不闪骨架屏；客户详情返回后显示刚保存的简称 |
| UI-058 | 去掉 `from` 参数，保存后走标签页返回；没有上一页时替换当前页 | #329 已合并（`7cec009b`），兜底分支补修待合并；本机验收通过（见本机验收与补充验收） | Claude（用户 2026-11-15 指定本机验收） | 待合并（补修）；合并、发布后关闭 |
| UI-059 | 迁移页、客户与供应商表单、询盘确认、分配规则登记标题 | 待合并；本机验收通过（见补充验收） | Claude（用户 2026-11-15 指定本机验收） | 待合并；合并、发布后关闭 |
| AI-011 | 契约测试（真实 Spring 与 MySQL） | 抄错 ID、没有校验码的 key 丢弃；其他公司维护的商品不写建议；带校验码的正常写入。未做真实模型调用 |

验收中发现菜单树不能收紧（业务员侧边栏依赖它），已改回登录可读，并在测试里加断言防止再犯。

### 补充验收（2026-11-15，用户要求「本机点完剩下的页面」）

在本机重新启用验收租户，补背调报告、运营商品、客户询价、供应链需求与缺口项目数据，BrowserOS 逐页验证；用「打开页面 → 切到别的标签 → 切回」比较页面数据接口的请求次数。

| 验收项 | 结果 |
| --- | --- |
| UI-055 返回刷新 | 背调报告详情、运营商品详情、客户询价详情、询价分配看板、需求详情、缺口项目详情、历史数据迁移（平台账号）切回后数据接口请求 1 → 2；供应链跟进切回后重取概览与列表；连同首轮的客户详情、覆盖矩阵，10 个页面全部通过，无报错 |
| UI-058 退栈 | 供应商新建保存 → 回列表，栈只剩列表、新供应商出现；供应商「详情 → 编辑 → 保存」→ 回详情，栈「列表 → 详情」；客户「详情 → 转移」→ 回列表，栈只剩列表；背调报告删除 → 回客户详情，栈「列表 → 详情」 |
| UI-058 兜底分支（发现缺陷） | 直接打开不存在的供应商再点「返回列表」：栈变成「不存在的详情 → 列表」，返回条又指向刚离开的页面。原因是 `backOr` 没有上一页时用 `history.push` 去兜底地址。改为 `history.replace` 替换当前页后复测：供应商、客户两处栈都只剩列表，正常退栈路径（客户详情 → 编辑 → 保存）不受影响；[`store.test.ts`](../../../zhul-erp-frontend/src/components/PageTabs/store.test.ts) 新用例在旧代码上失败、修复后通过 |
| UI-059 标题 | 迁移页标签「历史迁移：验收租户-CG」、客户编辑「编辑客户」、供应商新建「新增供应商」、分配规则「分配规则」、询盘确认「确认询盘型号」 |

前端 Biome、tsc、antd 检查与 Jest 78 个文件 387 条通过。补充验收数据已清理：租户停用、账号停用、有效令牌 0，客户、供应商、询价、需求、缺口项目、背调报告、测试商品软删除，验收套餐停用；脚本与测试凭据已删除。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| SEC-018 | 操作日志、系统参数、角色、菜单授权的读取接口按对应菜单校验，不影响共用页面 | #329 已合并（`7cec009b`），未发布；本机验收通过（见本机验收） | Claude（用户 2026-11-15 指定本机验收） | 已合并待发布；发布后关闭 |
| SEC-019 | 负责人下拉改用不含手机号、邮箱的选项接口；完整用户列表按用户管理菜单校验 | #329 已合并（`7cec009b`），未发布；本机验收通过（见本机验收） | Claude（用户 2026-11-15 指定本机验收） | 已合并待发布；发布后关闭 |
| AI-011 | 四个回调只处理本次提交的 key | #329 已合并（`7cec009b`），未发布；契约测试通过 | Claude（用户 2026-11-15 指定本机验收） | 已合并待发布；发布后关闭 |
| UI-055 | 补上述页面的返回刷新 | #329 已合并（`7cec009b`），未发布；本机验收通过（见本机验收） | Claude（用户 2026-11-15 指定本机验收） | 已合并待发布；发布后关闭 |
| UI-058 | 去掉 `from` 参数，保存后走标签页返回 | #329 已合并（`7cec009b`），未发布；本机验收通过（见本机验收） | Claude（用户 2026-11-15 指定本机验收） | 已合并待发布；发布后关闭 |

## 阻塞与下一步

- SEC-018、SEC-019、AI-011、UI-055 已合并待发布；UI-058 补修与 UI-059 待合并；发布由用户决定，发布后关闭。
- 本机验收数据已清理：测试租户停用、账号停用、有效令牌 0，客户软删除，验收套餐停用；造数脚本与测试凭据已删除。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-15 / Claude | 按用户要求用 CodeGraph 在 develop `eec51e32` 上做结构排查（方法见上）；新增 SEC-018、SEC-019、AI-011、UI-058，UI-055 修复不完整、重开 | 等用户决定是否修复 |
| 2026-11-15 / Claude | 用户要求全部修复并本机验收后合并：在 `fix/codegraph-sweep` 实施修复（见上），缺陷见证、后端与前端测试、本机 HTTP 与真实页面验收完成；验收中发现菜单树不能收紧，已更正 | 创建修复 PR，CI 通过后合并到 develop |
| 2026-11-15 / Claude | [#329](https://github.com/zhul-cloud/zhul-trade-erp/pull/329) CI 全部通过，自动合并到 develop `7cec009b`；5 个 ID 改为已合并待发布 | 发布由用户决定，发布后关闭 |
| 2026-11-15 / Claude | 用户要求本机点完剩下的页面：补充验收 10 个返回刷新页面与 UI-058 的各条退栈路径全部通过；发现 `backOr` 兜底分支会留下刚离开的页面（UI-058 修复引入），改为替换当前页；另发现 UI-059 并修复；UI-058 改回待合并 | 在 `fix/tabs-backor-replace` 创建 PR，合并到 develop |
