# Spec Delta

## MODIFIED Requirements

### Requirement: 按配置选择模型服务商
AI 编排服务 SHALL 按后端解析的配置选择已注册服务商，当前支持 `claude`、`openai`，默认 `claude`，可通过注册新服务商扩展。API 使用租户自己对应服务商 profile；订阅仅接受执行前授权关卡返回的有效服务商与平台订阅 profile。假数据模式不调用服务商、忽略模型配置；生产实例可同时服务不同租户与服务商。

#### Scenario: 未配置服务商时使用 Claude
- **WHEN** 合法 API 任务未指定服务商
- **THEN** 使用 Claude 对应 profile，并按原契约回调

#### Scenario: 切换到 OpenAI
- **WHEN** 租户选择已独立保存的 OpenAI API profile 后提交任务
- **THEN** 使用该租户 OpenAI Key 和模型，不使用 Claude Key，无须重启

#### Scenario: 假数据模式不受服务商影响
- **WHEN** 部署为 stub 且任务带 OpenAI、空 Key
- **THEN** 按既有假数据契约返回，不调用服务商；不得将生产授权校验伪装成已完成的真实调用验证

### Requirement: 按任务配置模型并提供推荐默认值
询盘解析 SHALL 使用解析模型，型号描述和联系人音译使用翻译模型。API 取当前租户对应 API profile；订阅取执行前后端确认的有效服务商对应平台订阅 profile。留空时 Claude 为 `claude-opus-5-5` / `claude-haiku-5-5`，OpenAI 为 `gpt-6.1-sol` / `gpt-6-luna`。显式模型名传给对应执行器，不偷偷更换模型。

#### Scenario: 留空使用推荐模型
- **WHEN** OpenAI 有效 profile 两模型留空
- **THEN** 解析用 `gpt-6.1-sol`，翻译用 `gpt-6-luna`

#### Scenario: 指定模型
- **WHEN** 有效 Claude profile 解析模型为 `claude-sonnet-5-5`
- **THEN** 解析用该值，翻译用该 profile 的显式值或推荐值

#### Scenario: 模型名与服务商不匹配
- **WHEN** OpenAI profile 显式使用 Claude 模型名
- **THEN** 服务商拒绝时任务失败，不自动改模型或切换 API/订阅方式

#### Scenario: 订阅模型默认值
- **WHEN** 获授权用户选订阅，但平台对应订阅 profile 未保存或模型为空
- **THEN** 使用对应服务商的平台推荐模型，不借用平台 API profile

#### Scenario: 订阅回退使用另一服务商平台配置
- **WHEN** Claude 订阅不可用、OpenAI 可用，且平台 OpenAI 订阅有自定义模型
- **THEN** 执行前有效选择 OpenAI，两个模型及强度、搜索上限均取平台 OpenAI 订阅 profile

### Requirement: 部署者个人订阅模式
获授权平台或普通租户任务 SHALL 在订阅方式下使用服务器官方 Codex SDK（OpenAI）或 Claude Code CLI；凭据由服务器官方 CLI 管理，不下发、复制给租户，不使用 API Key、不回退付费 API。每个任务在实际启动订阅 worker 前必须通过后端实时授权。保留专用 HOME 命名卷与现进程隔离，不新增账号池、登录代理或 token 接收界面。

#### Scenario: Codex 订阅无 API Key
- **WHEN** 获授权平台或普通租户通过执行前授权，容器内 Codex 以 ChatGPT 登录
- **THEN** 官方 SDK 执行并按原契约回调，api_key 为空

#### Scenario: 缺少个人订阅登录
- **WHEN** 未开始的订阅任务在执行前没有任何已登录服务商
- **THEN** 拒绝执行并安全失败回调；平台可见登录指引，普通租户不收到命令或凭据，不调用 API

#### Scenario: 登录状态持久保存
- **WHEN** 按默认 Compose 登录后停止、重启或重建容器
- **THEN** 认证仍保存在现专用命名卷（10001、0700），不进入 storage 备份，不复制宿主个人目录

## ADDED Requirements

### Requirement: 订阅服务商有效选择确定且不修改保存值
后端 SHALL 优先选择用户保存且已登录的订阅服务商；不可用时按注册表稳定顺序（当前 Claude、OpenAI）选择已登录服务商。未登录或未知不算可用，全部不可用则拒绝。有效选择与保存选择区分，不自动改写保存配置、不建立额外粘性状态；当前已发起的模型任务不因另一家状态变化而自动重放。

#### Scenario: 首选恢复后的新解析
- **WHEN** 保存 Claude，此前因其未登录有效选择 OpenAI，随后 Claude 恢复且新任务重新解析
- **THEN** 新任务可按保存首选使用 Claude；页面未刷新且未主动再选时可保留仍可用草稿

#### Scenario: 运行中认证失败
- **WHEN** 已启动任务收到明确的订阅认证失败
- **THEN** 该任务按原失败流程结束，不自动向另一服务商重放；之后未开始任务重新解析可用服务商

### Requirement: 订阅认证故障安全分类
订阅状态 SHALL 区分已登录、明确未登录与未知；本地登录标志不宣称远端有效性保证。真实执行中可识别的认证失效 SHALL 记录安全故障类别并影响之后可用性判断；额度不足、繁忙、模型不支持、超时或结构错误不得冒充认证失效。恢复必须有新的成功认证证据，不用旧本地标志覆盖已观察到的认证失败，不探读或回传凭据。

#### Scenario: 状态命令异常
- **WHEN** CLI 缺失、状态命令超时或返回无法解析
- **THEN** 状态为未知，禁止作为新任务可用订阅

#### Scenario: 非认证失败
- **WHEN** 任务因为繁忙、额度不足或不合法模型失败
- **THEN** 返回对应安全原因，不误把账号置为已退出，也不转用 API

#### Scenario: 已失效账号未重新认证
- **WHEN** 已观察到明确远端认证失败，而仅重复读取到此前的本地登录信息
- **THEN** 不将其重新宣称可用；需新的成功认证证据后恢复
