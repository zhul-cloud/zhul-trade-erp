# OPS-012 独立站发布服务没有健康检查，v1.52.0 部署被判为失败

## 结论 / 下一步

v1.52.0 的服务实际都已切到新版本并正常运行，后端健康，迁移 V1.5.66 已执行。部署脚本要求每个容器都是 healthy，但 site-publisher 在 compose 里没有配健康检查，状态只有 running，所以被判为失败：没发成功通知邮件，也没清理旧镜像。hotfix/1.52.1 给 site-publisher 加了健康检查。下一步：hotfix 经用户确认后合到 main，确认「生产发版」成功。

## 范围与负责人

| 字段 | 内容 |
| --- | --- |
| 负责人 / 协作者 | Claude Code（本会话） |
| 关联 ID / 原专题 | OPS-012；原变更 `openspec/changes/website-publish` |
| 目标与影响范围 | 部署脚本的健康等待；只改 `docker-compose.yaml` 的 site-publisher 服务。不影响业务数据 |
| 起点 commit / 工作分支 | main `v1.52.0`；`hotfix/1.52.1` |
| OpenSpec | 属于 `website-publish` 的部署缺陷，不另开提案 |
| PR / 目标分支 | hotfix/1.52.1 → main，见下方事件记录 |
| 合并证据 | 未合并 |
| 目标版本 / 环境 | v1.52.1，生产 |

## 实施与验证结果

根因：`scripts/deploy/prod-deploy.sh` 的健康等待只接受 `healthy`，没有 `State.Health` 的容器取 `State.Status`（`running`），走到失败分支。site-publisher 镜像基于 Playwright，没有 curl，健康检查改用 node 自带的 `fetch` 访问 `/health`。

| 命令 / 操作与证据 | 环境、commit / 版本 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| 生产 `releases.log` | 生产 v1.52.0 | 部署成功 | `v1.52.0 失败 zhul-erp-site-publisher=running` | 复现 |
| `docker exec zhul-erp-site-publisher node -e "fetch('http://127.0.0.1:8910/health')…"` | 生产 v1.52.0，Node v24.13.0 | 返回 200、退出码 0 | 200、退出码 0 | 健康检查命令可用 |
| `docker compose -f docker-compose.yaml -f docker-compose.prod.yaml --profile app config --quiet` | 本机，hotfix/1.52.1 | 配置有效 | 通过 | 通过 |

## 合并、部署与逐 ID 验收

| ID | 修复 / 验收范围 | 部署与目标环境验收证据 | 验收人 / 日期 | 结论与剩余项 |
| --- | --- | --- | --- | --- |
| OPS-012 | v1.52.1 部署时所有容器 healthy，`releases.log` 记成功 | 未部署 | — | 待部署 |

## 阻塞与下一步

hotfix 合到 main 需要用户确认；上线后要合回 develop。

## 事件记录

| 日期 / 作者 | 事件与证据 | 下一步 / 交接 |
| --- | --- | --- |
| 2026-11-28 / Claude Code | v1.52.0 发版运行 46146424921 在「部署到生产服务器」失败；查明根因，建 hotfix/1.52.1 | 用户确认后合并 |
