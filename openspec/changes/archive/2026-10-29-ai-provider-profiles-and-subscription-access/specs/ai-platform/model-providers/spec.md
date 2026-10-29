## MODIFIED Requirements

### Requirement: 部署者个人订阅模式
编排服务 SHALL 在任务携带的运行方式为 `subscription` 时，按携带的服务商使用官方 Codex Python SDK（`openai`）或原版 Claude Code CLI（`claude`）执行。订阅方式用于平台租户以及获平台授权使用订阅的公司的任务（授权与可用性由后端在创建任务前检查，编排服务按任务携带的配置执行），不使用 API Key，不自动回退 API 或改用其他服务商；部署者本人通过容器内官方 CLI 登录，项目不代收 token，不向租户下发任何凭据。

#### Scenario: Codex 订阅无 API Key
- **WHEN** 平台租户或获授权公司的任务携带订阅方式、服务商 `openai`，容器内已由部署者完成 ChatGPT 登录
- **THEN** 编排服务通过官方 Codex SDK 执行任务，并使用原回调契约

#### Scenario: 缺少个人订阅登录
- **WHEN** 任务携带订阅方式但容器内对应 CLI 未登录
- **THEN** 任务失败回调明确提示在容器内通过官方 CLI 登录，不转用 API

#### Scenario: 登录状态持久保存
- **WHEN** Docker 按默认 Compose 运行订阅服务，部署者在容器内完成官方 CLI 登录后停止、重启、重建容器或重新构建镜像
- **THEN** 认证保存在专用 HOME 的 Docker 命名卷中（属主 10001、权限 0700，不在 `storage/` 备份范围内），无需重新登录；删除该卷或执行官方退出命令后才需重新登录；不挂载或复制宿主个人目录
