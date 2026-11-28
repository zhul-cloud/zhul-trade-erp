# 独立站发布服务（site-publisher）

后端通过它对自动发布的站点执行检测连接、导入、比对、发布（OpenSpec 变更 `website-publish`，设计第 1、3、7 条）。只在 Compose 内网提供服务，不映射端口；每个请求带请求头 `X-Publisher-Token`，值与后端的 `SITE_PUBLISHER_TOKEN` 相同。

站点这边的约定（`site-data.json`、`erp/build.sh`、`erp/verify.js`、`erp/export-existing.js`）见 `docs/00-总体架构/独立站站点数据约定.md`。

## 接口

| 接口 | 说明 |
| --- | --- |
| `POST /check` | 同步执行，请求体为 `{site, secrets}`，返回 `{items: [{key, ok, message}]}`；`key` 依次为 `repo`、`adapter`、`deploy`、`url` |
| `POST /jobs` | 异步执行，返回 202。`type` 为 `import`、`compare` 或 `publish`；同一站点已有任务在执行时返回 409 |
| `GET /jobs/:jobId` | 最近 100 个任务的状态，不含凭据 |
| `GET /health` | 健康检查 |

`site`：`{code, repoUrl, branch, url, deploy: {host, port, user, path}}`。`repoUrl` 必须是不带账号的 https 地址。

`secrets`：`{repoToken, deployKey, indexNowKey}`，都可选。部署私钥必须不带密码。

发布任务另外需要：
- `release`：发布单号 `WP…`；
- `siteData`：完整的站点数据；
- `checks`：本地检查和线上检查共用的检查清单；
- `indexNowUrls`：可选，要推送收录的链接；
- `timeouts`：可选，按步骤覆盖默认超时，例如 `{build: 600000}`，单位毫秒。

从部署一步重试时，传 `startFrom: "deploy"` 和上次失败时返回的 `commit`。

### 回调

每一步开始和结束时，以及任务结束时，都会 POST 到 `callbackUrl`，请求头带同一个令牌：

```json
{"jobId": "…", "event": "step", "step": "build", "status": "running|success|failed|warning", "startedAt": "…", "endedAt": "…", "message": "…", "output": "最后 50 行"}
{"jobId": "…", "event": "done", "status": "success|failed|deploy_failed", "failedStep": "…", "error": "…", "output": "…", "result": {…}}
```

**发布的步骤**：`pull` → `write` → `build` → `local_check` → `commit` → `deploy` → `online_check` → `push` → `indexnow`。

| 失败在哪一步 | 结果 |
| --- | --- |
| `commit` 及之前（第 1～5 步） | `failed`，本地仓库丢弃改动回到远程状态，线上不变 |
| `deploy` | `deploy_failed`，结果里带 `commit`，可以从部署一步重试 |
| `online_check`、`push`、`indexnow` | 不算失败，记为 `warning`，并写入 `result.warnings` |

推送没成功的提交，会在下一个任务的 `pull` 一步先补推。

**各任务的结果 `result`**：
- `publish`：`commit`、`local`、`online`（每页的检查结果）、`warnings`；
- `import`：`exported`（站点数据格式的全部商品）、`exportWarnings`；
- `compare`：`same`、`differences`（`[{model, field, site, erp}]`）、`onlyOnSite`、`onlyInErp`。

**超时**：构建和部署各 10 分钟，其他步骤 3 分钟。超时会结束整个进程组。

## 凭据

- **仓库令牌**：通过 `GIT_ASKPASS` 从子进程的环境变量读取，不写进 `.git/config`。
- **部署私钥**：写入临时目录（权限 0700，文件 0600），任务结束就删除。
- **输出脱敏**：所有命令输出、回调和日志都先把凭据替换成 `***`，私钥的每一行也会单独替换。日志只记任务号、类型和结果，不记录请求体。

## 运行与测试

```bash
npm test                      # Node 22+，需要 git、rsync、ssh-keygen
docker build -t zhul-erp-site-publisher:local .
```

测试用一个本机的裸仓库代替站点仓库，再用 `test/fake-ssh.sh` 让 rsync 部署到本地目录，不连接任何外部服务。

环境变量：

| 变量 | 说明 |
| --- | --- |
| `SITE_PUBLISHER_TOKEN` | 必填，没有配置时所有请求返回 503 |
| `SITE_PUBLISHER_WORK` | 默认 `/work`，挂载命名卷 `site-publisher-work` |
| `SITE_PUBLISHER_PORT` | 默认 `8910` |
| `PUBLISHER_GIT_NAME`、`PUBLISHER_GIT_EMAIL` | 发布提交的提交人 |

另外还有几个只给测试用的变量：`PUBLISHER_SSH_BIN`、`PUBLISHER_ALLOW_FILE_REPOS`、`PUBLISHER_INDEXNOW_ENDPOINT`，以及各种重试间隔。
