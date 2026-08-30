# 技术选型上下文

## 状态：已确认（2026-05-29）

## 技术栈

| 层级 | 选型 |
|------|------|
| 后端 | Java 17 + Spring Boot 3.x |
| 前端 | React 18 + Ant Design Pro 6.x |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis |
| 消息队列 | RabbitMQ（业财联动异步事件） |
| API 文档 | Knife4j (Swagger3) |
| 构建工具 | Maven |
| 部署 | Docker Compose（开发）/ 阿里云或腾讯云（生产） |

## 架构决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| 金额类型 | DECIMAL(18,2)，非 Float | 浮点精度丢失问题 |
| 软删除 | deleted_at 时间戳 | 财务数据不可物理删除 |
| 多币种 | 每笔交易存原币+汇率+本位币 | 支持事后汇率核对 |
| 业财联动 | 事件驱动（RabbitMQ） | 解耦，可审计，不阻塞业务主流程 |
| 多租户 | 共享数据库 + Row-level tenant_id | 实现成本低，行级隔离够用 |
| tenant_id 传递 | JWT Token 携带，后端 TenantContext 解析 | 安全，前端无需额外传参 |
| API 版本 | URL 路径：/api/v1/{resource} | 最直观，易维护 |
| 代码分层 | 标准三层：Controller / Service / Repository | 团队上手快，Spring Boot 默认风格 |
| 汇率更新 | 定时任务自动拉取，每日更新 | 减少人工维护 |
| 时间存储 | UTC，展示时转用户时区 | 多地区业务标准 |
| 前端状态 | Ant Design Pro umi model（dva） | 与 ADP 深度集成，开箱即用 |

## 待确认项

- [ ] 是否需要对接现有 ERP/财务系统
- [ ] 报表导出格式（Excel、PDF）
- [ ] 汇率 API 具体供应商（央行接口 / Open Exchange Rates）
