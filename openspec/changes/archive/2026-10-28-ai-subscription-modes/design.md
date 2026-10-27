## Context

`server.py` 的解析和翻译共用 `call_api`；既有 API 与 stub 已投入使用。个人订阅不能当成 API Key 或共享服务账号。

## Decisions

- 模式增加 subscription；服务商枚举保持原值，openai 订阅映射官方 Codex SDK。订阅 Claude 用原版 CLI，与官方个人认证流程一致；不实现 OAuth。
- 专用 HOME/CODEX_HOME/CLAUDE_CONFIG_DIR，过滤 API/OAuth 环境变量；不挂载宿主个人目录。默认 tmpfs，可按部署文档选持久目录。
- 一次只执行一个订阅任务，忙碌即失败；新临时 cwd + Linux 独立进程组，超时及任务退出均清理子进程。
- 禁 shell/写入/外部 MCP/hooks；Claude safe-mode/restricted 仅开放搜索，图片经 stream-json 直接输入；Codex只读沙箱且禁shell，LocalImageInput送图。PDF有限页数渲染，拒绝超限，不截断。
- Codex使用严格 schema 后删null，所有订阅结果用 JSON Schema 验证。观察完整完成事件才成功。保留回调契约，不扩展数据库事务。
- 操作事件按ID去重，达到搜索阈值立即终止失败；可能已有在途调用，不能视为服务商硬费用上限。翻译禁搜索。

## Risks / Trade-offs

个人订阅额度/模型权限/登录有效期依赖部署者账号。无账号的离线测试能验证接口、登录状态和线程构造，但不代表真实端到端模型效果。Codex内核沙箱不兼容时明确失败，不关闭防护。Windows使用Docker执行订阅模式。

## Rollout

API 默认不变，无新增必填 `.env` 键与迁移。部署新镜像后明确选择 subscription、清空API模型名、决定认证持久化，再用容器内原版CLI登录。切回api只需恢复原模式/Key/模型并重建。
