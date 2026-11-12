# 技术选型上下文

## 状态：已确认（2026-05-29），2026-11-12 按现行代码更新（DOC-001）

以下为当前实际使用的技术；版本以 `zhul-erp-frontend/package.json`、`zhul-erp-backend/pom.xml`、`docker-compose*.yaml` 为准。

## 技术栈

| 层级 | 选型 |
|------|------|
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 |
| 前端 | React 19 + Umi Max 4 + Ant Design 6 + ProComponents 3（最初来自 Ant Design Pro 模板，已不按模板升级） |
| 数据库 | MySQL 8.0 |
| 数据库迁移 | Flyway（2026-09-22 起，取代手动执行 SQL 文件） |
| 缓存与分布式锁 | Redis + Redisson |
| 消息队列 | 未引入（见下方「业财联动」） |
| AI 编排服务 | Python（`scripts/ai-orchestrator`） |
| API 文档 | Knife4j (Swagger3) |
| 构建工具 | Maven（后端）、npm（前端） |
| 部署 | Docker Compose（本机开发与生产）；生产镜像由 GitHub Actions 构建并推送 GHCR，服务器拉取部署，见 `docs/06-部署运维/08-生产发版.md` |

## 架构决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| 金额类型 | DECIMAL(18,2)，非 Float | 浮点精度丢失问题 |
| 软删除 | deleted_at 时间戳 | 财务数据不可物理删除 |
| 多币种 | 每笔交易存原币+汇率+本位币 | 支持事后汇率核对 |
| 业财联动 | 在业务操作的同一事务内同步执行（如应付同步 `PayableSyncService`），模块内少量用 Spring 事件 | 早期规划的 RabbitMQ 异步事件未实施；同步执行保证业务与财务数据一致。如以后确有异步需求再单独评估 |
| 多租户 | 共享数据库 + Row-level tenant_id | 实现成本低，行级隔离够用 |
| tenant_id 传递 | JWT Token 携带，后端 TenantContext 解析 | 安全，前端无需额外传参 |
| API 版本 | URL 路径：/api/v1/{resource} | 最直观，易维护 |
| 代码分层 | 标准三层：Controller / Service / Repository | 团队上手快，Spring Boot 默认风格 |
| 汇率更新 | 在系统里手动维护，每次修改记录日志 | 早期规划的定时自动拉取未实施，供应商见待确认项 |
| 时间存储 | 统一存北京时间（Asia/Shanghai），展示时不再换算；数据库与后端容器都设 `TZ=Asia/Shanghai`。客户所在地时间只在需要时由前端换算（`customer/time.ts`） | 与 `AGENTS.md`、`.claude/rules/coding.md` 一致。例：库里 `2026-11-12 14:30:00`，接口返回 `2026-11-12 14:30:00`，页面显示 `2026-11-12 14:30`，中间不做时区转换。早期「存 UTC、按用户时区展示」的设想已废弃 |
| Schema 版本管理 | Flyway 迁移文件（`zhul-erp-backend/src/main/resources/db/migration`），后端启动时自动执行；结构性种子数据（菜单/内置角色/字典）也在这里，示例账号等仅本地用的种子放 `db/dev-data`，只有 `application-dev.yml` 才加载 | 手动维护"一份完整 schema.sql"既没法覆盖已部署环境的增量升级，也没有机制防止忘记同步；Flyway 自动追踪已执行版本，新环境和已有环境都能正确处理，单一数据源不重复 |
| 前端状态 | 登录用户、菜单与权限放 Umi Max 的 `initialState`；页面状态用组件内 hooks | 没有使用 dva model |

## 待确认项

- [ ] 是否需要对接现有 ERP/财务系统
- [ ] 报表导出格式（Excel、PDF）
- [ ] 汇率 API 具体供应商（央行接口 / Open Exchange Rates）
