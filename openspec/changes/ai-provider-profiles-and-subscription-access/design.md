# Design

## Context

动机见 proposal.md。当前 `sys_ai_config` 每租户一行，API Key、provider、mode、模型和用量共用，切换会串值。`AiConfigServiceImpl.fromRequest` 空 Key 会取该租户单行 Key，未按服务商限定。平台租户为 0，其他租户目前禁止订阅。

`AiTaskServiceImpl` 在请求线程解析配置，创建任务后通过 afterCommit 提交；输入和 Key 不落任务库。`requested_by` 是 user_basic.id，通用创建接口当前写 0；审计 create_by 是服务端已认证账号。编排服务收到请求后异步处理，订阅通过单实例非阻塞 semaphore 取得槽，繁忙时失败，没有新增持久排队系统。

权限由 `EffectivePermissionResolver` 直接查库：平台超管不限制资源范围，平台普通账号看角色；租户管理员看套餐 menu_ids，普通员工取套餐与角色交集。套餐写操作已有 requirePlatform。全局资源定义的五类写操作目前缺平台/维护权限守卫；PermissionChecker 按权限码取首条且未过滤 status，订阅必须防别名和停用绕过。

MybatisPlusConfig 只有分页拦截器，没有租户自动拦截；不得假设新 profile 会自动隔离。CLI 状态目前 bool，命令异常被当 false；本地登录信息不能证明远端凭据一定有效。

本分支起点为 release/1.3.0 的 5984df8，生产 tag v1.3.15。相较 e1c2cc2 增加 #64 历史迁移（V1.3.19/.20），未修改 AI 模块，但修改了询盘等共享业务服务；实现不得覆盖这些并发成果。

## Goals / Non-Goals

**Goals:**

- 按 tenant / mode / provider 隔离配置，订阅仅共享平台对应非敏感 profile，API 永不继承。
- 使用同一权限口径控制入口与真实调用，授权撤销对尚未开始调用生效。
- 添加兼容迁移和明确测试边界，先确认 OpenPencil 原型，再实现页面。

**Non-Goals:**

- 不增加独立租户订阅开关、套餐页面、账号池、登录代理、Key 下发或批量保存。
- 不新增 provider 粘性状态、持久任务队列、授权租约、token 撤销或强制终止已发给服务商的请求。
- 不重构通用鉴权或修复无关模块；共享资源写保护是本次订阅授权不可绕过的必要前置。
- 本轮只写规划与原型；不改产品代码、真实数据库、生产配置，不付费调用模型，不创建 PR 或部署。

## Decisions

### 1. 选择器与独立 profile 分表

保留 `sys_ai_config` 一租户一行作为选择器，保留 `mode`，新增 `api_provider`、`subscription_provider`。原 provider、api_key、模型、effort、搜索字段保留作为迁移前历史数据，新实现不再用作运行配置来源，不删除数据。

新增 `sys_ai_provider_config`（基础配置表，不物理删除）：

| 字段 | 类型/约束 | 用途 |
| --- | --- | --- |
| id | int，自增 | 主键 |
| tenant_id | int，索引 | API 所有者；订阅只能为 0 |
| mode | varchar(16) | api / subscription |
| provider | varchar(32) | 注册表稳定 ID，当前 claude / openai |
| api_key | varchar(512)，默认空 | API AES-GCM 密文；订阅必须空 |
| parse_model / translate_model | varchar(100)，默认空 | profile 模型 |
| parse_effort / translate_effort | varchar(16)，默认空 | profile 思考强度 |
| max_web_searches | int，默认 20 | 搜索上限 1–100 |
| status | tinyint(2)，默认 1，索引 | 基础记录状态，不新增产品开关 |
| create_time/by，update_time/by | 按项目规范 | 审计及北京时间 |

唯一索引 `(tenant_id,mode,provider)`，create_time 索引，不建数据库外键。所有字段含 COMMENT。新 profile Repository 按明确 tenant、mode、provider 限定；更新不能仅依赖外部传入 id。平台订阅只读路径明确允许 tenant 0，不泛化跨租户读取 API。

普通租户不存 subscription profile；从平台对应 profile 继承两个模型、两个 effort、搜索上限。平台未保存时构造该服务商默认订阅 profile，不增加启用审核或额外开关。selector 与当前可写 profile 在一个 Service 事务保存，任何验证失败全部不写；外部状态读取、模型查询不放在长写事务中。

不使用 tenant 单行多组 JSON：现 ORM 与加密规则更适合有唯一键的 profile，能精确更新、测试并扩展服务商。provider 元数据放代码注册表（ID、显示名、API/订阅能力、推荐模型、logo），本轮不做动态服务商管理。前后端从相同协议定义支持值，不继续把 provider 写死为 DTO 正则两值。

### 2. 配置 API 与页面切换契约

沿用 `/api/v1/system/ai-config`；前后端同步升级分组响应，不保留会重新串值的旧全量覆盖行为。

| API | 契约 |
| --- | --- |
| GET 基础路径 | 返回 mode、apiProvider、subscriptionProvider、effectiveProvider、fallbackApplied、canUseSubscription、subscriptionAvailable、provider 元数据与各 profile；只返本人 API Key 脱敏值 |
| PUT 基础路径 | mode/provider + 当前 profile 参数，只保存当前组及该 mode 选择器，其他组不变；普通租户订阅只允许 mode/provider |
| POST /test | 当前 API profile 草稿可测试，空 Key 只读本人 api/provider；订阅重查使用权并取平台 profile |
| POST /models | 同上述主体边界；平台可查询订阅列表，普通租户只读平台配置，不能通过该接口传自选参数 |
| GET /subscription-status | 有菜单及使用权者可查 provider 三态与 checkedAt；loginCommands 仅平台 JSON 中存在 |

订阅非敏感字段也不能由普通租户覆盖：PUT/test/models 出现 apiKey、parseModel、translateModel、parseEffort、translateEffort、maxWebSearches 中任何字段（含空串/null）均返回参数/权限错误，不能仅静默忽略。使用专用入参或字段存在性校验，不能靠反序列化默认值掩盖越权。平台订阅配置不接受非空 Key，运行 payload 一律 api_key 为空。

无订阅使用权时基础配置仍可显示本人已保存 mode=subscription 与不可用原因，不能假装变成 api；返回 canUseSubscription=false，不暴露订阅入口、平台配置或命令。读状态接口也检查使用权。已有 AI 菜单和编辑按钮权限保留；订阅使用权限是额外条件，不用编辑权限替代运行权限。

页面先服务商卡片，后运行方式。普通租户只能选已登录服务商订阅；全部未知/未登录时订阅置灰。平台仍可选未登录服务商配置模型、获取命令，实际任务仍需可用登录。普通租户继承字段只读。顶部只显示方式、实际服务商与可用状态，不显示 Key；Key 脱敏仅在本人 API 配置区域。真实 logo 与 OpenPencil 原型由原型任务交付。保留原有有效用途、模型、计费与限制说明，仅调整因订阅共享与权限语义变化而不准确的部分；禁止无意义新增或重复说明，不以删减有效说明换取空洞。通过清晰分组、信息层次、间距和留白使排版优雅、阅读与操作顺畅。

原型微调约定（尚待用户确认）：服务商的已配置/未配置和登录状态放在服务商名称右侧，同排带状态点；品牌副标题单独放在下一排。运行方式卡恢复右侧 radio，整卡可点击，禁用时 radio 为灰色；服务商与运行方式采用相同样式的选择标记。按用户明确要求，仅删除顶部「点『测试连接』确认凭据和模型可用。」这行描述，保留测试连接按钮、配置状态和修改元信息，其他有效说明不删。租户只读「模型与用量」删除首行重复的「服务商 / Claude」等服务商信息，不通过补 logo 保留冗余；左侧已选服务商已足够，右侧保留平台配置标记、模型、思考强度、搜索次数及用途说明。
当前 profile 有未保存修改时，切换 mode/provider 先提供保存/放弃/取消，沿用现确认组件；保存只当前组，取消则不切换。未保存 Key 不自动落盘，也不因切换调用模型。不做多 profile 草稿缓存或全量保存。页面自动状态刷新不在用户尚未主动再选且当前选择可用时反复重置；整页刷新采用后端 effective 值。

### 3. 订阅权限与最小共享资源保护

新增固定资源计划 ID `110215`、code `RS3110215`、pid `100118`（AI 设置）、type 3、permission `system:ai-config:use-subscription`、status 1。实现前再次确认 ID 与最新 release 无冲突；若冲突统一重新分配并更新测试。

订阅检查定位该固定 ID，验证 tenant_id=0、type=3、pid=100118、权限码一致且 status=1，父菜单身份与启用状态正确，然后以现 resolver 的有效 ID 集合判断；平台超管的 null 范围可通过范围判断，但不能豁免资源已停用/身份不合法。不能通过同权限码的另一个资源授权。新资源仅加入平台 tenant0 的内置 ROLE_ADMIN，不自动加入任何普通租户套餐。租户 admin 始终受套餐限制；套餐与普通角色交集口径不变。

平台在现套餐资源树中勾选/移除即可开通/撤权。同套餐全部关联租户共同生效；独立租户控制用独立套餐，不承诺现界面有按租户开关。无需缓存权限，沿用每次查询数据库，旧页面、旧 JWT 或旧保存配置不构成持续授权。

必要前置：MenuService 共享资源五类写入口检查当前 tenant=0，Controller/Service 使用现维护权限：新增 `system:menu:add`（110021），修改、启停、排序 `system:menu:edit`（110022），删除 `system:menu:delete`（110023），菜单 `/system/menu`（100013）。平台超管按现范围规则操作，普通平台角色须有对应权限。不给普通租户写全局资源，否则其可篡改 permission/status 恢复订阅。只补这五类保护，不做通用 PermissionChecker 重构，不新增菜单 UI。

### 4. 有效 provider 解析和三态

纯解析器优先保存的 subscription_provider，若其非 logged_in 则按注册顺序 claude、openai 取首个 logged_in；均不可用则拒绝。GET 只展示 effectiveProvider/fallbackApplied，不写保存配置。无额外 ConcurrentMap/Redis 粘性系统。用户显式保存才改变首选，首选恢复后新解析可重新使用它。每次回退必须换成对应平台订阅 profile，不能沿用失效服务商模型。

编排 `/status` 按 provider 返回 state、checked_at。明确 CLI 未登录为 logged_out；超时、缺失、无法解析或服务不可达为 unknown，不能把 unknown 报红灯未登录。状态查询不是远端 token 有效性证明，真实执行返回明确 auth 错误时标不可用并提供安全 error_code；额度、繁忙、超时、模型或 schema 错误分开。恢复须出现新的成功认证证据，不允许普通轮询的旧本地 true 清掉真实认证失败。实现前对锁定 CLI/SDK 版本确认官方可用的非推理认证/重新登录信号，以 mock 覆盖；不读取、解析或下发真实凭据，不花费模型额度做探测。

运行中的任务认证失败按原流程失败，不自动切 provider 重放。后续任务重新解析，不转付费 API。状态轮询是页面主动/有界查询，不增加后台自动化或持续任务。

### 5. 持久任务主体及实际启动前关卡

`ai_task` 增加 `execution_mode varchar(16) NOT NULL DEFAULT ''`（注释列举空为历史未知、api、subscription），用于证明发起时方式；旧未知任务不能获订阅授权。复用已有 tenant_id、requested_by、create_by，不存 Key 或 CLI token。新任务在认证请求线程从真实账号解析 user_id，补齐通用接口当前的 requestedBy=0，并核对业务传入主体；订阅主体缺失、tenant 不匹配或 sys/0 均拒绝。缺 TenantContext 不再默认为平台。

授权查持久 task，用 create_by 账号名 + tenant_id + requested_by 匹配当前 account/user_basic，核对账号有效和权限，不仅凭 user_id（现 account.user_id 非唯一）。显式主体查询复用 EffectivePermissionResolver，不向线程写入别人的 SecurityContext/TenantContext。不扩大 JWT/token 生命周期机制。

关卡顺序：

1. 用户保存/订阅测试/订阅模型列表重查当前权限；创建任务前解析并检查。
2. 任务及业务事务正常提交；Java afterCommit 真正提交订阅请求前，再按该持久主体查权限，无权则沿现提交失败分发。
3. 编排带 task_id 接受任务。取得现串行执行槽后，启动订阅 worker 前调用内部 `POST /api/v1/ai-tasks/{taskId}/subscription-authorization`。
4. 该端点不要求用户 JWT，精确白名单并复用现 `X-AI-Callback-Token` / `AI_TASK_CALLBACK_TOKEN` 验证；无令牌或错误返回 401，不新增环境变量。请求不接受 allowed、tenantId、requestedBy 作为可信授权。
5. 后端加载未删除、未完成且原方式为 subscription 的真实 task，按持久主体重查资源/套餐/角色、租户当前 mode 仍为 subscription、当前登录状态，再解析有效 provider 与平台最新订阅 profile。
6. 成功响应 mode=subscription、provider、parse_model、translate_model、parse_effort、translate_effort、max_web_searches、api_key=''；无账号、登录命令或真实 Key。编排必须用此配置覆盖入队 ai 参数，先解析响应合法性再启动 worker。
7. 拒绝、网络错误、无效响应或状态未知，均不 spawn、不调用模型，通过既有失败回调/分发结束任务；不因为租户已改 API 就转换收费方式。

当前订阅槽繁忙直接失败，保留行为；不为“排队撤权”另加队列。若线程在附件准备等过程中延迟，关卡仍放在真实 spawn 前，避免取得旧授权后再长时间等待。附带音译用可用性结果跳过，不让预检竞态的无权异常导致父业务事务回滚；业务主动操作保留明确拒绝。

API 发起时快照语义保留。订阅在执行前重取平台 profile；开始后使用该次快照。授权查询不与远端模型请求组成数据库事务：最后关卡之后的撤权无法撤回已发送请求，不宣称即时断电，不新增在途终止。

## Risks / Trade-offs

- 历史单行曾跨 provider 沿用 Key，无法从密文推断真实归属 → 只按已保存 API provider 保持现行为；订阅残留 Key 不自动迁移激活，保留旧密文。
- 本地 CLI 状态不能证明远端 token 永久有效 → 三态、运行期安全 auth 分类、下次调用前重查，明确认证恢复证据，禁止付费探测或窥读凭据。
- 套餐授权影响全部关联租户 → 在原型/说明准确标注，通过独立套餐实现独立控制。
- 授权服务不可用会让订阅任务失败 → fail closed，统一失败分发，不回退 API；保留现任务超时兜底。
- 旧客户端仍发送扁平全量 subscription 参数 → 前后端同批部署，服务端严格拒绝越权，不静默吞字段。
- 共享菜单写保护会阻止过去未受控的租户直接写操作 → 此为阻止自授权限的必要行为；平台合法维护做回归。
- 角色/资源读取现有逻辑的其他状态规则不完善 → 本次订阅守卫验证固定资源状态与真实主体，通用权限重构另行处理。

## Migration Plan

规划两份新增 Flyway 迁移：`V1.3.21__ai_provider_profiles.sql`（选择器/profile/执行方式）与 `V1.3.22__ai_subscription_permission.sql`（新资源和平台内置角色）。当前仓库最高为 V1.3.20；实现前 fetch 并核对，冲突必须改为新的未发布版本，不能改已执行迁移。

1. 创建 profile 表和选择器列，原字段保留。旧 mode=api：按该行 tenant/provider 复制原加密 Key、模型、effort、搜索、状态及审计，不解密不重加密，api_provider 指向原 provider。历史错配保持而不猜测纠正。
2. 旧平台 mode=subscription：subscription_provider 指向原 provider，模型、effort、搜索复制到平台订阅 profile；原 api_key 无法证明属于该 provider，仅保留旧列，不迁入新 API profile，不返回其为当前 Key。API 新组为空 Key/默认模型。
3. 其他 provider 不从旧行猜配置；未保存 subscription profile 用平台默认。普通租户若历史出现不合规 subscription，不迁移其私有订阅 profile，保留选择但必须获新授权才可用。保留现平台 tenant0 已保存订阅选择，不自动改 mode。
4. 新权限仅赋平台内置 ROLE_ADMIN；不遍历所有套餐赠送，不把使用权等同编辑权。不迁移旧任务为可授权订阅任务。
5. 同批发布新版后端、编排、前端（实施阶段遵守 release PR 流程），无需新增环境变量；发布验证只用测试凭据/模拟服务，真实登录由平台管理员在既有容器 CLI 完成。
6. 回滚先停止新订阅受理、保留全部 profile/旧列/权限数据。旧后端不支持新权限及 profile，不能直接降级并继续接受订阅，否则恢复旁路；仅在平台确保订阅调用停用并评估配置映射后协调回退服务。本设计不提供自动破坏性 down migration，不删除新配置或业务任务。PR 必须列最终迁移号、此回退限制和无新增 env。
