# SEC-001 登录会话与 AI 输入紧急修复（批次 A）

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude（实施与验证）；用户 Ezio 决定合并、发布与验收 |
| 关联 ID / 原专题 | [SEC-001](../../07-security/02-身份会话与对象权限.md)、[SEC-004](../../07-security/02-身份会话与对象权限.md)、[SEC-008](../../07-security/03-全量平台与商品代码审查.md)、[AI-003](../../04-ai-and-operations/02-AI附件入口与权限.md) |
| 目标与影响范围 | 生产上同租户任意登录用户即可利用的会话与输入边界问题。不含 SEC-002/003/005/006/007（对象归属，另列批次 B）、AI 编排侧链接域名白名单、历史登录日志清理 |
| 起点 commit / 工作分支 | `main` `ccd6f340f087ab732a3056c05a246ca28d6a4361`（v1.5.0）；`hotfix/1.5.1`（`b1462eed`、`839f5995`）；合入 develop 用 `fix/batch-a-session-security`（从 `develop` `1c88104c` 拉出，版本号保留 develop 的值） |
| OpenSpec | 不适用：缺陷修复，不改变规格描述的业务行为 |
| PR / 目标分支 | 用户 2026-11-03 决定不单独发 hotfix：`fix/batch-a-session-security` → `develop`，随下一版（v1.6.0）发布；`hotfix/1.5.1` 不再向 `main` 发 PR |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | v1.6.0 / 生产 |

## 实施与验证结果

开工前在 `develop` `1c88104c`（含 v1.5.0）核对：四项问题在当前源码中仍然成立。

- **SEC-001**：登录日志 `content` 改记会话记录 `account_access_token.id`（`session_id`），不再存 Token；列表、导出加 `@perm.canAccessMenu('/system/log/login')`；响应字段 `tokenId` 改为 `sessionId`。是否在线按会话记录里最新的 AccessToken 查 Redis（刷新 Token 后也准确）。强制下线改为按会话 ID，并校验会话所属账号属于当前租户。旧日志里的 `token_id` 不再读取，这些 Token 最长 2 小时后自然过期，未做历史数据清理。涉及 `LogController`、`LogServiceImpl`、`LoginLogVO`、`ForceLogoutRequest`、`AuthServiceImpl`，前端 `system/log/login`。
- **SEC-004**：新增 `AccountSessionService`（删除 Redis AccessToken / RefreshToken 并标记会话记录失效）。`UserServiceImpl` 在停用（含编辑时改为停用）、删除、管理员重置密码后撤销该用户全部会话；强制下线复用同一组件。按用户要求扩展：邮箱找回密码（`AuthServiceImpl.resetPassword`）成功后同样撤销该用户全部会话。
- **SEC-008**：`PUT /api/v1/system/users/{userId}/password` 改为 JSON 请求体 `AdminResetPasswordRequest`，不再接受查询参数；前端同步改为 `data`。已核对没有切面或过滤器记录请求体。
- **AI-003**：删除无调用方的通用创建接口 `POST /api/v1/ai-tasks` 及 `CreateAiTaskRequest`（回调接口保留），任务只由业务模块在服务端拼输入创建。编排服务 `_read_attachment` 只接受 http/https 链接，不再接受 `attachmentPaths` / `rawAttachmentPath` 本地路径。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| `mvn test -Dtest=SessionSecurityContractTest`（[测试源码](../../../zhul-erp-backend/src/test/java/com/zhul/erp/modules/system/SessionSecurityContractTest.java)） | 本机 Docker MySQL/Redis 测试库；修复前 `main` `ccd6f340` 临时检出 | 修复前 4 个用例都失败 | 4 个失败：停用后会话仍有效、日志含 Token、强制下线契约不同、通用接口返回 200 | 缺陷见证成立 |
| 同上 | 本机；`hotfix/1.5.1` 工作区 | 4 个通过 | 4 个通过 | 通过 |
| `mvn test`（后端全量） | 本机；`hotfix/1.5.1` 工作区 | 无失败 | 1199 个，0 失败，6 个环境跳过（`b1462eed`）；加入邮箱找回密码撤销会话后 1200 个，0 失败，6 个跳过 | 通过 |
| `python -m unittest test_server test_subscription test_claude_stream`（新增 `AttachmentSourceTest` 3 个） | 编排容器内临时目录复制工作区脚本，跑完已删除 | 通过 | 58 个通过 | 通过 |
| `npm run lint`、`npx antd lint ./src`、`npx jest` | 本机；`hotfix/1.5.1` 工作区 | 无问题 | 通过；antd 388 文件无问题；21 套件 158 个通过 | 通过 |
| 浏览器操作登录日志页、重置密码 | — | — | 未执行；界面无变化，字段改名由类型检查覆盖 | 未执行，上线后验收 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| SEC-001 | 无菜单权限 403；日志与响应无 Token；会话 ID 下线；他租户会话不可下线 | 未部署 | — | 待合并；上线后在生产用登录日志页确认在线状态与强制下线正常 |
| SEC-004 | 停用、删除、管理员重置密码、邮箱找回密码后旧会话立即失效 | 未部署 | — | 待合并 |
| SEC-008 | 新密码只在请求体；查询参数方式被拒绝 | 未部署 | — | 待合并；历史日志已核查，无需清理（见下） |
| AI-003 | 通用创建接口关闭；编排服务拒绝非 http(s) 链接与本地路径 | 未部署 | — | 待合并；链接域名白名单经核实暂不做（见下） |

## 生产核查（2026-11-03，只读）

- **SEC-008 历史日志**：生产有两处访问日志。主机 Nginx `/var/log/nginx/access.log*` 共 13 份，最早到 2026-10-21，会记录查询串（6570 行接口请求带查询串）；前端容器 Nginx 输出到 Docker 日志。两处统计 `newPassword=` 均为 0 行，`PUT /api/v1/system/users/{id}/password` 也为 0 行；后端日志无 `newPassword`。保留期内没有发生过管理员重置密码，**无需清理，未删除任何日志**。10-21 之前的日志已被轮转删除，无法核查。只统计行数，未输出日志内容。
- **AI-003 生产附件链接**：生产 `STORAGE_TYPE=oss`，询盘附件 42 个（普通 38、铭牌 4）全部存在 OSS，没有本地存储文件。后端发给编排服务的是 `FileStorageService.link` 生成的私有桶 `zhul-erp-prod-private`（`oss-cn-hangzhou.aliyuncs.com`）签名 https 链接，有效期 60 分钟；本地存储时则是拼接 `AI_TASK_CALLBACK_BASE_URL`（`http://backend:8080`）的相对链接。
- **AI-003 域名白名单判断**：通用创建接口删除后，附件链接只由后端服务端代码生成，编排服务的接口又要求回调令牌，用户已无法注入自选链接；本次的 http(s) 限制挡住了 `file://` 和本地路径。再加域名白名单需要给编排服务新增配置项（OSS 私有桶域名、后端地址），并在生产 `.env` 补齐，边际收益低，**暂不做**。以后新增由用户提供链接的 AI 入口时，再补白名单。

## 阻塞与下一步

- 合并到 `develop` 后，随 v1.6.0 发版上线；上线前生产 v1.5.0 上四项问题仍然存在（用户已知悉并决定）。
- 上线后在生产验收：登录日志页在线状态与强制下线、重置密码、停用用户后旧会话失效、询盘附件解析正常。
- `hotfix/1.5.1` 远程分支保留未用，确认不再需要后删除。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-03 / Claude | 认领批次 A，在当前源码复核四项仍成立；在 `hotfix/1.5.1` 实施并完成上表验证（`b1462eed`） | 等用户审阅后建 PR |
| 2026-11-03 / Claude | 按用户要求补做：邮箱找回密码撤销会话；只读核查生产访问日志（无需清理）与生产附件链接（OSS 签名链接，白名单暂不做）；DTO 改名 `AdminResetPasswordRequest` 避免与认证模块同名类混淆 | 等用户确认后建 PR |
| 2026-11-03 / Claude | 用户决定先合到 develop、以后一起发版：从 `develop` 拉 `fix/batch-a-session-security` 合入 `hotfix/1.5.1`（只有三个版本号文件冲突，保留 develop 值），回归 17 个用例通过 | 发 PR 到 `develop` 并合并 |
