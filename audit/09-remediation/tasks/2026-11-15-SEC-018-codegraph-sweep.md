# SEC-018 等：用 CodeGraph 做全项目结构排查（2026-11-15）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（排查与登记）；用户 2026-11-15 要求用 CodeGraph 主动排查项目问题、整理到问题台账。本轮只排查和登记，没有修复 |
| 关联 ID / 原专题 | 新增：SEC-018、SEC-019、AI-011、UI-058；修复不完整、重开：UI-055（[原修复](2026-11-15-SEC-017-proactive-sweep.md)） |
| 目标与影响范围 | 后端接口权限、事务与行锁、金额写法、AI 回调；前端保活页面返回刷新、页面跳转规范；最近合并未审查过的 #322（商品品类细分）与 #326（供应链三期） |
| 起点 commit / 工作分支 | develop `eec51e32` / 只改 `audit/`：`docs/audit-codegraph-sweep` |
| OpenSpec | 缺陷登记，不新开提案 |
| PR / 目标分支 | 本记录随文档 PR 合入 develop；修复 PR 未创建 |
| 合并证据 | 未合并修复 |
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

证据：CodeGraph 路由节点与源码静态核对，未在运行环境中实测。

### SEC-019 · P3 · 用户列表接口向所有登录用户返回同事的手机号和邮箱

[`UserController.list`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/controller/UserController.java#L36) 只要求登录，返回的 [`UserVO`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/system/dto/UserVO.java#L11) 含手机号、邮箱。销售订单、PI、报价、询价、商机、客户跟进、供应链等 10 个业务页面用它做负责人下拉，所以不能简单收紧权限；应给下拉提供只含 ID、姓名、部门的选项接口，完整列表留给用户管理菜单。编码规范把手机号、邮箱列为敏感数据。

证据：源码与前端调用方静态核对，未在运行环境中实测。

### AI-011 · P3 · AI 回调信任模型回传的 key，不核对是否属于本次提交

编排服务只在提示词里要求模型「key 原样返回」（[`server.py`](../../../scripts/ai-orchestrator/server.py#L724) 等 5 处），不过滤；后端回调按 key 直接定位数据，不对照任务自己的输入：

- 商品品类细分 [`CategoryRefineService.handle`](../../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/product/refine/service/CategoryRefineService.java#L214)：任何解析得出的「商品 ID#品类 ID」，只要商品存在、当前品类与待细分状态对得上就写入建议。商品库是全局表、不走租户拦截，回调里也不校验维护权，模型把 ID 写错时可能写到本次未提交、甚至其他公司维护的商品上，这些商品随后出现在「待采纳」里。
- 商品候选 AI 补全 `CandidateAiFill.handle`、联系人名称翻译 `ContactNameTranslations`、型号描述翻译 `DescriptionTranslations`：同样按回传 key 定位；这些表走租户拦截，影响限于本租户内写错对象。

建议回调先从 `ai_task.input` 取出本次提交的 key 集合，只处理集合内的 key。

证据：源码静态核对，未构造模型错写 key 的回调实测。

### UI-055（修复不完整，重开）· 部分保活页面返回后仍显示旧数据

UI-055 给 78 个页面加了 `useOnReactivate(load)`，并有意排除了带草稿或就地编辑的页面（报价、PI、采购订单详情等）。本轮按「会跳转到其他页面、按 `load` 取数、没有草稿」筛出以下页面，既不在排除名单里也没有加：

- 客户详情、客户背调详情、运营商品详情、客户询价详情、询价分配看板
- 供应链的需求、覆盖、跟进、缺口项目页面
- 历史数据迁移页面

例如从客户详情打开订单或 PI 并改动后返回，客户详情仍是旧数据，要手动刷新。带草稿页面的取舍维持原决定；如要改善，可在没有未保存修改时才刷新。

证据：源码静态核对，未在浏览器中复现。

### UI-058 · P3 · 客户详情进入编辑时在地址里带 `from`，违反标签页返回规范

[`customer/detail.tsx`](../../../zhul-erp-frontend/src/pages/customer/detail.tsx#L425) 与 [`CompletionTab.tsx`](../../../zhul-erp-frontend/src/pages/customer/profile/CompletionTab.tsx#L437) 跳到 `…/edit?from=detail`，[`customer/form.tsx`](../../../zhul-erp-frontend/src/pages/customer/form.tsx#L247) 据此在保存后 `history.push` 回详情。项目规范（2026-11-11）要求返回由标签页框架的返回条统一提供，不在地址里带 `from`。其他表单没有这种写法。

证据：源码静态核对。

## 实施与验证结果

本轮只排查、登记，未实施修复，未运行测试。上表各项结论来自 CodeGraph 结构查询加源码核对；所有发现都还没有在运行环境中复现，修复时须先补缺陷见证。CodeGraph 的调用边有少量同名方法误解析（见第一项检查），结论都以源码为准。

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| SEC-018 | 操作日志、系统参数、角色、菜单授权的读取接口按对应菜单校验，不影响共用页面 | 未修复 | — | 待认领 |
| SEC-019 | 负责人下拉改用不含手机号、邮箱的选项接口；完整用户列表按用户管理菜单校验 | 未修复 | — | 待认领 |
| AI-011 | 四个回调只处理本次提交的 key | 未修复 | — | 待认领 |
| UI-055 | 补上述页面的返回刷新 | 未修复 | — | 重开，待认领 |
| UI-058 | 去掉 `from` 参数，保存后走标签页返回 | 未修复 | — | 待认领 |

## 阻塞与下一步

- 是否修复、何时修复由用户决定；修复按原流程从 develop 拉 `fix/*`，先补缺陷见证再改。
- 本轮没有产生测试数据；扫描脚本放在系统临时目录，结束后已删除。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-15 / Claude | 按用户要求用 CodeGraph 在 develop `eec51e32` 上做结构排查（方法见上）；新增 SEC-018、SEC-019、AI-011、UI-058，UI-055 修复不完整、重开 | 等用户决定是否修复 |
