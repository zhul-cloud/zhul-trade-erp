# Docker 部署指南

本项目只维护根目录的一份 `docker-compose.yaml`。MySQL、Redis、后端、前端分别运行在独立容器中；同一文件可以用于单机、前后端分服务器、云数据库以及本机开发。

> **默认不持久化。** MySQL、Redis、公开上传和私有上传目录均使用 `tmpfs` 临时存储，**停止、重启、删除或重建对应容器都会清空其中的数据**。生产使用前，先按第 7 节启用 `storage/` 下的持久化，或者为数据库配置云服务。云数据库不会替后端保存上传文件。
>
> 旧版 Compose 使用的 `zhul_erp_mysql_data` 卷不会被自动导入，也不应删除。已有数据请先备份并安排迁移，再启动新版容器。

## 1. 服务、版本与准备

| 服务名 | 容器名 | 运行内容 | 容器端口 |
| --- | --- | --- | --- |
| `mysql` | `zhul-erp-mysql` | MySQL 8.0 | 3306 |
| `redis` | `zhul-erp-redis` | Redis 7，启用 AOF | 6379 |
| `backend` | `zhul-erp-backend` | Java 17 / Spring Boot | 8080 |
| `frontend` | `zhul-erp-frontend` | Nginx 1.28 / 前端构建产物 | 80 |

需要 Docker Engine / Docker Desktop 和支持 profiles 的 Docker Compose V2 及以上。Windows 使用 Docker Desktop 的 **Linux containers**，不使用 Windows containers。首次构建要联网下载镜像、Maven 与 npm 依赖；构建工具已包含在多阶段镜像中，宿主机不必安装 Java、Maven 或 Node。

建议给 Docker 至少 6 GB 可用内存，并准备足够镜像磁盘空间。默认 tmpfs 也占用内存，适合临时验证；它不是磁盘数据卷。大数据量或生产环境先启用持久化。

在项目根目录执行本指南命令。根目录包含 `docker-compose.yaml`、`zhul-erp-backend/` 和 `zhul-erp-frontend/`。

Linux/macOS：

```bash
cp .env.example .env
docker version
docker compose version
```

Windows PowerShell：

```powershell
Copy-Item .env.example .env
docker version
docker compose version
```

不要覆盖已经配置好的 `.env`。实际 `.env` 和 `storage/` 运行数据均被 Git 忽略。

### 后端启动前填写密钥

仅启动 MySQL/Redis 或前端不需要填写后端密钥。启动后端前，在 `.env` 中分别填写：

```dotenv
JWT_SECRET=替换为独立随机值且至少32字符
AES_KEY=替换为另一个独立随机值
AI_TASK_CALLBACK_TOKEN=替换为另一个独立随机值
```

Linux/macOS 可以运行三次 `openssl rand -hex 32`；PowerShell 7 可以运行三次：

```powershell
[Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

把每次生成的不同值分别填入以上三项。即使暂不使用 AI 编排，也为回调令牌设置独立随机值。不要在已有数据上随意更换 `AES_KEY`，否则已有加密配置可能无法解密；更换 JWT 密钥会使原令牌失效。

`.env` 中的 `$` 可能参与 Compose 插值，含特殊字符的密码可使用单引号，例如 `DB_PASSWORD='包含$符号的实际密码'`。不要将凭据作为镜像构建参数。配置检查使用 `docker compose --profile app config --quiet`；完整 `config` 输出可能包含展开后的秘密，不要公开粘贴。

## 2. 启动方式速查

| 场景 | 在对应服务器的项目根目录执行 |
| --- | --- |
| 原有方式：只运行 MySQL、Redis | `docker compose up -d` |
| 单机运行四个服务 | `docker compose --profile app up -d --build` |
| 只部署前端 | `docker compose up -d --build frontend` |
| 后端与本地数据库一起部署 | `docker compose up -d --build backend mysql redis` |
| 只部署后端，连接云数据库 | `docker compose up -d --build backend` |
| 同机前后端，连接云数据库 | `docker compose up -d --build frontend backend` |

MySQL、Redis 没有 profile，默认启用；前后端使用 `app` profile。前后端都没有 `depends_on`，所以显式指定 `frontend` 或 `backend` 不会顺带启动数据库。不要设置 `COMPOSE_PROFILES=app`，否则会改变默认启动范围。

**`.env` 只决定连接目标，不决定启动范围。** 使用云数据库时执行指定服务的命令，不要运行启动全部服务的 `--profile app up`。

这些命令不会停止已经运行、但本次没有选择的服务。模式切换见第 9 节。

## 3. 单机全部部署

1. 准备 `.env`，填写三个后端密钥；生产先按第 7 节启用持久化。
2. 本地数据库使用以下连接配置；MySQL 初始化参数与后端访问参数应匹配。

```dotenv
BACKEND_URL=http://backend:8080
MYSQL_DATABASE=zhul_erp
DB_HOST=mysql
DB_PORT=3306
DB_NAME=zhul_erp
DB_USER=root
DB_PASSWORD=root
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_SSL_ENABLED=false
```

以上 `root` 仅为兼容原有本地体验的默认值。生产本地 MySQL 可使用 `MYSQL_USER` / `MYSQL_PASSWORD` 初始化应用账号，并将 `DB_USER` / `DB_PASSWORD` 设置为同一账号；`MYSQL_ROOT_PASSWORD` 单独配置。修改初始化变量不会修改已存在数据库里的账号或密码。

3. 构建并启动：

```bash
docker compose --profile app up -d --build
docker compose --profile app ps
docker compose logs --tail=100 backend
```

浏览器访问 `http://localhost:8000`。默认后端仅映射 `127.0.0.1:8080`；Nginx 使用 Docker 内网 `backend:8080`，不依赖宿主机映射端口。

健康检查：

```bash
curl http://localhost:8000/healthz
curl http://localhost:8000/api/v1/public/health
```

Windows PowerShell 使用 `curl.exe`，避免旧版 PowerShell 的同名别名。前端 `/healthz` 只检查 Nginx；后端健康接口在应用完成启动、MySQL 和 Redis 可访问后返回 `UP`，未就绪返回 HTTP 503。健康检查成功不等于已有登录账号。

### 首次登录：演示与生产分开

默认只执行正式 Flyway 迁移，创建表结构、菜单、角色、字典，**不创建可登录账号**。

仅本地演示，可在**空库第一次启动后端之前**设置：

```dotenv
FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/dev-data
```

迁移完成后使用 `platform / admin123` 登录。当前开发迁移最终删除了早期的 `admin` 示例租户账号，不应再使用 README 旧版本中的双账号说明。通过平台账号配置套餐、创建租户，再使用生成的租户管理员账号开展业务。

生产保持 `FLYWAY_LOCATIONS=classpath:db/migration`。现有工程没有公开注册或首次平台管理员初始化接口；首次生产平台账号需要由管理员显式初始化，不能直接导入演示固定口令。参考第 11 节。

不要把演示迁移追加到已经迁移完毕的生产库，也不要通过删除 Flyway 历史或开启乱序迁移来补账号。若临时验证时错过演示初始化，应仅在确认可丢弃的临时库上重新开始；不要对已有业务库这样操作。

## 4. 前后端分服务器部署

假设服务器 A 托管前端，服务器 B 的内网地址是 `10.0.0.20`。两台服务器分别获取同一版本代码、同一份 Compose，各自维护 `.env`，并分别执行命令。Compose 不会从 A 自动部署 B。

### A：只启动前端

```dotenv
WEB_PORT=8000
BACKEND_URL=http://10.0.0.20:8080
```

```bash
docker compose up -d --build frontend
```

A 不需要填写数据库密码、JWT 等后端变量。浏览器访问 A 的前端地址，`/api/` 和 `/uploads/` 由 Nginx 转发至 B，浏览器保持同源请求。

`BACKEND_URL` 只能是 `http(s)://主机[:端口]`，不能带用户名、密码、`/api`、其他路径、查询串或末尾斜杠。远端后端使用 HTTPS 时必须有有效且受信任的证书；不要通过关闭证书验证来绕过问题。

### B：后端与本地数据库

```dotenv
BACKEND_BIND_ADDRESS=10.0.0.20
BACKEND_HOST_PORT=8080
DB_HOST=mysql
REDIS_HOST=redis
```

填写密钥和数据库账号后启动：

```bash
docker compose up -d --build backend mysql redis
```

绑定地址必须实际属于 B，并且 A 能访问。通过防火墙或云安全组只允许需要的来源访问后端端口；MySQL、Redis 默认仅映射 B 的回环地址，无需向 A 开放。

跨服务器不能使用 Docker 服务名 `backend` 或容器 IP。没有互通内网时，需要事先配置可达的网络或受保护的 HTTPS 服务地址。

### HTTPS 入口与转发头

当前前端镜像默认监听 HTTP 80，宿主机默认映射 8000。公网生产访问应使用现有负载均衡/网关的 HTTPS 入口，或为 Nginx配置 TLS。若 TLS 在前置网关终止，需要按实际受信代理拓扑配置协议转发；当前 Nginx 使用自身 `$scheme`，不会无条件信任客户端传入的 `X-Forwarded-Proto`。验证登录、跳转、Cookie 和回调地址后再对外开放。

## 5. 后端使用云 MySQL / Redis

后端服务器 `.env` 示例（替换所有占位值）：

```dotenv
BACKEND_BIND_ADDRESS=10.0.0.20
DB_HOST=mysql-endpoint.example
DB_PORT=3306
DB_NAME=zhul_erp
DB_USER=实际应用账号
DB_PASSWORD='实际数据库密码'
REDIS_HOST=redis-endpoint.example
REDIS_PORT=6379
REDIS_USERNAME=实际ACL用户名
REDIS_PASSWORD='实际Redis密码'
REDIS_DATABASE=0
REDIS_SSL_ENABLED=true
```

端口、用户名和 TLS 开关以云实例要求为准；不使用 Redis ACL 用户名时留空。填写后端密钥后：

```bash
docker compose up -d --build backend
```

如果前后端同机、数据库在云上，保留 `BACKEND_URL=http://backend:8080`，执行：

```bash
docker compose up -d --build frontend backend
```

### MySQL JDBC URL 与 TLS

`DB_URL` 非空时完全覆盖 `DB_HOST` / `DB_PORT` / `DB_NAME` 拼接的地址，用户名密码仍由 `DB_USER` / `DB_PASSWORD` 提供。例如：

```dotenv
DB_URL='jdbc:mysql://mysql-endpoint.example:3306/zhul_erp?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=30000'
```

云服务的证书链、主机名必须匹配。默认拼接连接串不会强制关闭 TLS，也不会自动创建数据库；生产应明确按云服务要求配置 TLS 验证。

先在云服务侧创建 `zhul_erp` 数据库，并给应用/迁移账号适当权限。Flyway 启动迁移需要相应 DDL 权限，只有读写权限可能不够。数据库初始化环境变量 `MYSQL_*` 不会修改云服务。

### 私有 CA 证书

如云服务使用 JVM 不信任的私有 CA，按服务商要求准备 Java truststore，并放在项目 `storage/certs/` 下。在 backend 的 `volumes` 中额外添加只读文件挂载：

```yaml
volumes:
  - ./storage/certs/cloud-truststore.p12:/app/certs/cloud-truststore.p12:ro
```

这是修改同一份 Compose 的 backend 服务，不是增加第二份 Compose。如果已经启用上传持久化，把证书挂载追加到原有 `volumes` 列表，不能重复定义 `volumes` 键。证书目标与上传 tmpfs 不冲突。

然后配置：

```dotenv
JAVA_TOOL_OPTIONS='-Djavax.net.ssl.trustStore=/app/certs/cloud-truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=实际证书库密码'
```

自定义 truststore 要包含此次连接所需的全部受信证书。不要关闭主机名或证书校验。部署完成后从后端容器实际验证连接；仅在宿主机能连通不代表容器网络也可达。

当前配置面向 MySQL 兼容 JDBC 服务和 Redis 单端点服务。Redis Cluster、Sentinel 或云服务商的特殊代理模式需要按实际客户端拓扑进一步配置，本指南不宣称已经验证所有云产品。

## 6. 保留原有本机开发方式

```bash
docker compose up -d
```

仅运行 MySQL、Redis。后端和前端继续按根 README 的本机命令启动，Java 使用原来的 `application-dev.yml`，连接 `localhost:3306` / `localhost:6379`。

根 `.env` 不会自动被本机 Spring Boot 或前端开发服务器读取。如果更改 `MYSQL_HOST_PORT`、`REDIS_HOST_PORT` 或账号密码，请同步本机开发配置。

启动本机应用前先停止同端口的应用容器：

```bash
docker compose --profile app stop frontend backend
```

默认 MySQL tmpfs 停止后会变回空库；再次启动后需重启本机 Java，让 Flyway 重新建表。若希望开发数据跨重启保留，也应启用下一节的持久化。

## 7. 可选持久化：统一放在项目 storage/ 下

项目已提供 `storage/` 文件夹。启用后布局如下：

```text
项目根目录/
├── docker-compose.yaml
├── .env
└── storage/
    ├── mysql/             # MySQL 数据
    ├── redis/             # Redis AOF 等数据
    ├── uploads/           # 后端公开上传
    ├── private-uploads/   # 后端私有上传
    └── certs/             # 可选云服务证书
```

这些路径是相对 Compose 文件解析的 bind mounts，实际数据存放在当前服务器的项目目录下。相同目录名不会让不同服务器共享数据。不要把 `.gitkeep` 或说明文件放进尚未初始化的 `storage/mysql/`；MySQL 首次初始化要求空数据目录。

### 切换方法

在根 `docker-compose.yaml` 中，针对需要持久化的服务：

1. 注释该服务的 `tmpfs` 配置。
2. 取消紧邻的 `volumes` 注释。
3. 创建目录并配置权限。
4. 重新创建对应容器，使挂载生效。

MySQL 示例，**不要同时挂载同一个目标目录的 tmpfs 和 bind mount**：

```yaml
# tmpfs:
#   - /var/lib/mysql
volumes:
  - ./storage/mysql:/var/lib/mysql
```

Redis 对应 `./storage/redis:/data`；后端对应 `./storage/uploads:/app/uploads` 和 `./storage/private-uploads:/app/private-uploads`。Redis 已启用 AOF，切到 bind mount 后才会跨重启保存这些文件。

Linux/macOS 创建目录：

```bash
mkdir -p storage/mysql storage/redis storage/uploads storage/private-uploads
```

Linux 上后端使用 UID/GID `10001:10001`，仅给它的两个上传目录授予权限：

```bash
sudo chown -R 10001:10001 storage/uploads storage/private-uploads
sudo chmod 750 storage/uploads storage/private-uploads
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force storage/mysql, storage/redis, storage/uploads, storage/private-uploads
```

Docker Desktop 需要允许访问项目所在磁盘。MySQL、Redis 官方入口会处理自己数据目录的权限；不要把它们统一改成后端 UID。

全套服务首次启用持久化后：

```bash
docker compose --profile app up -d --force-recreate
```

分服务器则只指定本机需要的服务，例如 `docker compose up -d --force-recreate backend`。

### 已有数据的处理

切换挂载不会把 tmpfs、旧命名卷或旧服务器的数据自动复制到 `storage/`。**重建前**先停止业务写入、完成数据库备份与上传文件备份；使用数据库工具恢复至新数据库，按原公开/私有目录恢复文件。

不要用 `down -v` 清理已有环境。旧版 `zhul_erp_mysql_data` 的实际卷名通常含 Compose 项目前缀；先核对 `docker volume ls` / 旧容器挂载再安排迁移，不要凭名字猜测和删除。更换 `.env` 连接云数据库也不会迁移旧数据。

临时 tmpfs 模式下，单独重启 MySQL 会清空数据库，而仍运行的后端不会自动重跑启动迁移。临时环境需要重置时，应同时重启后端：

```bash
docker compose restart mysql backend
```

只在确认数据可丢弃的临时环境使用此操作。启用持久化后的普通重启不会清空 bind mount 数据。

## 8. 常用配置与运行边界

| 变量 | 默认值/说明 |
| --- | --- |
| `WEB_BIND_ADDRESS` / `WEB_PORT` | `0.0.0.0` / `8000` |
| `BACKEND_URL` | `http://backend:8080`，由 Nginx 在运行时使用 |
| `BACKEND_BIND_ADDRESS` / `BACKEND_HOST_PORT` | `127.0.0.1` / `8080` |
| `MYSQL_BIND_ADDRESS` / `MYSQL_HOST_PORT` | `127.0.0.1` / `3306` |
| `REDIS_BIND_ADDRESS` / `REDIS_HOST_PORT` | `127.0.0.1` / `6379` |
| `DB_CONNECT_RETRIES` | `12`，Flyway 有限连接重试；不是无限等待 |
| `FLYWAY_LOCATIONS` | `classpath:db/migration`；映射为容器 `SPRING_FLYWAY_LOCATIONS` |
| `JAVA_TOOL_OPTIONS` | JVM 内存参数、truststore 参数等 |
| `AI_ORCHESTRATOR_BASE_URL` | 外部 AI 编排服务地址 |
| `AI_TASK_CALLBACK_BASE_URL` | 从 AI 编排服务可达的本项目后端地址 |

宿主机端口与容器内端口不同。例如 `MYSQL_HOST_PORT=3307` 时，本机 Java 连 `localhost:3307`，Docker 后端仍连 `mysql:3306`。

固定容器名意味着同一 Docker Engine 上只能运行一套同名实例；不同服务器可以各自使用这些名称。不要只改 Compose 项目名后期望在同一台机器运行第二套同名容器。

后端镜像包含 LibreOffice Calc 和中文字体，用于已有单据转换。AI 编排服务不是本次四个容器之一；只有配置真实可达的编排地址与回调地址后，相关功能才能工作。Linux 上 `host.docker.internal` 不一定自动存在；不要把示例地址当作已部署的服务。

Nginx 的上传限制为 210 MB；私有上传只允许经后端鉴权接口访问，没有公开目录映射。日志输出到容器标准输出，由 Docker 的日志策略管理；`storage/` 不自动收集容器日志。

## 9. 更新、切换与停止

更新代码并构建单个应用：

```bash
docker compose up -d --build frontend
docker compose up -d --build backend
```

更新后端会重建容器；默认 tmpfs 中的上传文件因此丢失，生产先启用持久化。数据库服务不会因为上述命令而被自动重建。

仅更新 `.env` 中的连接地址/密钥，使用 `up -d` 重建对应容器即可，无需重新编译镜像；单纯 `restart` 不会加载新的 Compose 环境变量：

```bash
docker compose up -d frontend
docker compose up -d backend
```

从完整部署切回本机开发：

```bash
docker compose --profile app stop frontend backend
```

确认云数据库已就绪、数据已迁移且应用连接成功后，才停止不再需要的本地数据库：

```bash
docker compose stop mysql redis
```

停止全部四个服务：

```bash
docker compose --profile app stop
```

删除本 Compose 的容器与网络，但不删除已有 bind mount 数据：

```bash
docker compose --profile app down
```

默认 tmpfs 数据在 `stop` 时就已丢失；不能把“没有使用 `-v`”理解为默认模式数据安全。启用 `storage/` 持久化后，不要手工删除这些目录。

## 10. 检查与排障

```bash
docker compose --profile app config --quiet
docker compose --profile app ps -a
docker compose logs --tail=100 mysql redis backend frontend
```

| 现象 | 检查方向 |
| --- | --- |
| `Set JWT_SECRET/AES_KEY/... in .env` | 后端启动校验失败，填写对应变量再 `up -d backend` |
| 前端健康但接口 502 | `BACKEND_URL` 是否可从容器访问、B 是否开放正确端口、后端是否就绪 |
| `Unknown database` / 权限不足 | 云数据库是否预先创建，应用账号是否具有迁移权限 |
| 认证失败 | `.env` 后端访问凭据是否与实际实例一致；修改 `MYSQL_*` 不会修改旧密码 |
| Redis SSL/认证失败 | 云端端口、TLS、ACL 用户名与密码；本地 Redis 默认无密码 |
| 端口被占用 | 调整 `*_HOST_PORT` / `WEB_PORT`，同步本机或远端访问地址 |
| 停止后数据消失 | 默认 tmpfs 行为；需要保存数据时提前启用第 7 节配置 |
| MySQL 重启后业务表消失 | 临时库被重置，需要重启后端重新执行 Flyway |
| 健康正常但无法登录 | 正式迁移不创建登录账号；核对首次初始化或演示配置 |
| 上传目录 `Permission denied` | Linux bind mount 目录所有者是否为 `10001:10001` |
| 新增演示迁移报校验/未解析错误 | 不要改生产历史或开启乱序迁移，演示须从独立空库开始 |

健康状态变为 `unhealthy` 不会自行触发 Docker 重启。`restart: unless-stopped` 处理进程退出；数据库短暂不可用时检查网络和日志，而不是反复删除容器。

## 11. 首次生产平台账号的边界

工程当前没有现成的生产首次账号初始化接口/脚本。默认 Docker 部署不会补建固定口令账号，管理员应在维护窗口显式初始化第一个平台账号，再通过管理界面创建套餐和租户。

现有开发种子 `zhul-erp-backend/src/main/resources/db/dev-data/V1.2.0.2__dev_seed_platform_account.sql` 可作为表关联参考，但**不能原样用于生产**：它使用固定 ID、默认口令和旧角色 `ROLE_SYSADM`。全量正式迁移完成后，当前角色应使用 `ROLE_ADMIN`；平台管理员需要 `tenant_id=0` 和 `admin_flag=1`，并关联 `department`、`position`、`user_basic`、`account`、`account_local_auth`。

初始化前核对当前表结构与用户名冲突，使用数据库生成的新 ID，提供真实资料和独立生成的强口令 BCrypt 哈希，在同一事务中写入关联记录。完成后验证平台登录及租户创建。不要通过开启开发种子、复用演示密码或删除 Flyway 记录绕过该步骤。

当前找回密码的邮件发送仍有模拟实现，不能将邮件重置当作已经可用的生产初始化流程。

以下人工模板仅适用于**正式迁移已完成、尚无任何用户的全新生产库**。先用可信的本地工具生成强密码的 BCrypt 哈希（60 字符），不要使用在线密码生成/哈希网站，不要把明文密码填进 SQL。数据库客户端应连接正确的 ERP 库，并在维护窗口的同一连接中执行；账号表没有用户名唯一约束，因此不能并发或重复初始化。

先填写并单独执行参数与预检。字符串中的引号必须正确转义：

```sql
SET NAMES utf8mb4;
SET @bootstrap_username = '<实际登录用户名，最多100字符>';
SET @bootstrap_name = '<真实姓名，最多16字符>';
SET @bootstrap_phone = '<真实手机号，最多16字符>';
SET @bootstrap_email = '<真实邮箱，最多100字符>';
SET @bootstrap_bcrypt = '<独立强密码生成的60字符BCrypt哈希>';

SELECT
  (SELECT COUNT(*) FROM user_basic) AS user_count,
  (SELECT COUNT(*) FROM account) AS account_count,
  (SELECT COUNT(*) FROM account_local_auth) AS auth_count,
  (SELECT COUNT(*) FROM role
   WHERE tenant_id = 0 AND code = 'ROLE_ADMIN' AND status = 1) AS role_count;
```

前三项必须全为 `0`，`role_count` 必须为 `1`。否则停止，核对已有初始化状态，不要继续套用模板。确认输入无误后执行事务；任何语句失败立即 `ROLLBACK`：

```sql
START TRANSACTION;

INSERT INTO department
  (tenant_id, pid, name, all_name, status, create_by, update_by)
VALUES (0, 0, '平台管理', '平台管理', 1, 'bootstrap', 'bootstrap');
SET @bootstrap_dept_id = LAST_INSERT_ID();
UPDATE department SET code = CONCAT('DP', @bootstrap_dept_id)
WHERE id = @bootstrap_dept_id;

INSERT INTO position (tenant_id, name, status, create_by, update_by)
VALUES (0, '平台管理员', 1, 'bootstrap', 'bootstrap');
SET @bootstrap_position_id = LAST_INSERT_ID();
UPDATE position SET code = CONCAT('POS', @bootstrap_position_id)
WHERE id = @bootstrap_position_id;

INSERT INTO user_basic
  (tenant_id, pid, name, type, username, phone, dept_id, position_id,
   role_code, email, nickname, status, create_by, update_by)
VALUES
  (0, 0, @bootstrap_name, 1, @bootstrap_username, @bootstrap_phone,
   @bootstrap_dept_id, @bootstrap_position_id, 'ROLE_ADMIN',
   @bootstrap_email, @bootstrap_name, 1, 'bootstrap', 'bootstrap');
SET @bootstrap_user_id = LAST_INSERT_ID();

INSERT INTO account
  (tenant_id, user_id, username, phone, email, admin_flag, login_status,
   status, create_by, update_by)
VALUES
  (0, @bootstrap_user_id, @bootstrap_username, @bootstrap_phone,
   @bootstrap_email, 1, 0, 1, 'bootstrap', 'bootstrap');
SET @bootstrap_account_id = LAST_INSERT_ID();

INSERT INTO account_local_auth
  (account_id, username, password, salt, create_by, update_by)
VALUES
  (@bootstrap_account_id, @bootstrap_username, @bootstrap_bcrypt,
   '', 'bootstrap', 'bootstrap');

SELECT a.id AS account_id, a.username, a.tenant_id, a.admin_flag,
       u.id AS user_id, u.role_code, u.dept_id, u.position_id
FROM account a
JOIN user_basic u ON u.id = a.user_id
JOIN account_local_auth la ON la.account_id = a.id
WHERE a.id = @bootstrap_account_id;
```

查询应返回一行，`tenant_id=0`、`admin_flag=1`、`role_code=ROLE_ADMIN`。确认后**单独执行 `COMMIT`**；不符合预期执行 `ROLLBACK`。模板不自动提交，不创建额外的 `tenant_id=0` 租户记录。提交后验证新账号登录、平台权限与租户创建，再结束维护窗口。

