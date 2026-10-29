# Design

## Context

`sys_ai_config` 每租户一行，Key、服务商、运行方式、模型共用，切换即覆盖。订阅只允许平台租户。生产现有 2 行配置（平台与租户 1000，均为 api + claude）、1 个租户、1 个套餐。菜单写接口原先无权限校验，已由 #66 修复（本变更依赖它）。

## Goals / Non-Goals

**Goals:** 每个服务商的 API Key 设置独立保存；平台订阅可按套餐授权给公司并可收回；租户只看到可用与否；页面按 V3 原型实现。

**Non-Goals:** 不做订阅不可用时自动切换；不在编排服务启动订阅任务前回后端再查授权（订阅一次只跑一个、忙则直接失败、不排队，受理到启动几乎没有间隔）；不新增任务表字段；不改编排服务；不做按员工区分的订阅授权；不做动态服务商管理。

## Decisions

### 1. 数据结构：当前选择 + 连接方式设置

新表 `sys_ai_provider_config`（基础配置表，用 `status`，不加 `deleted_at`）：

| 字段 | 说明 |
| --- | --- |
| `tenant_id` | 所属租户；订阅设置只有平台（0）的行有效 |
| `mode` | `api` / `subscription` |
| `provider` | `claude` / `openai` |
| `api_key` | API 方式的 Key，AES-GCM 密文；订阅为空 |
| `parse_model`、`translate_model`、`parse_effort`、`translate_effort`、`max_web_searches` | 该连接方式的设置，空值按服务商默认 |
| `status` 与审计字段 | 按项目规范 |

唯一索引 `(tenant_id, mode, provider)`。`sys_ai_config` 保留 `mode`、`provider` 作为「当前选择」，其余旧列不再读写（COMMENT 标注已弃用，不删除）。

迁移：每行旧配置复制为一行新设置（同租户、同 mode、同 provider，密文原样复制不解密）；订阅行不复制 Key。

### 2. 订阅授权

按钮资源 110215「使用平台订阅」，pid 100118（AI 设置），权限码 `system:ai-config:use-subscription`。迁移只给平台内置 `ROLE_ADMIN`，不加入任何套餐。

判断规则（`AiSubscriptionAccess`）：平台租户始终可用；其他租户要求该资源 `status = 1`，且公司所属套餐的 `menu_ids` 包含 110215。按公司判断，不看员工角色——员工发起任务时结果一致。每次实时查库，不缓存。菜单写保护保证租户不能改资源本身。

### 3. 订阅可用性与状态接口

沿用编排服务 `GET /status`（Codex、Claude 是否已登录）。查询失败为「未知」。

- 平台：返回每家 `logged_in / logged_out / unknown` 与登录命令。
- 获授权租户：返回每家 `available: true / false / null`，不含命令与登录细节。
- 未授权租户：403。

### 4. 接口

沿用 `/api/v1/system/ai-config`：

| 接口 | 契约 |
| --- | --- |
| `GET` | `selected`（mode、provider，未配置为空）、`apiProfiles`（每家：hasKey、脱敏 Key、模型、思考强度、搜索次数）、`subscriptionProfiles`（每家平台订阅设置；仅平台或获授权租户返回）、`subscriptionAllowed`、`platform`、默认模型、选择的修改人与时间 |
| `PUT` | `mode`、`provider` 与该连接方式的设置：保存当前选择 + 这一种的设置，其他连接方式不变。API：Key 留空沿用该服务商已保存的 Key。订阅：平台可保存订阅设置；租户须获授权且该家可用，设置字段忽略 |
| `POST /test`、`POST /models` | API 用页面值，Key 留空用该服务商已保存的 Key；订阅须获授权，用平台订阅设置 |
| `GET /subscription-status` | 见决策 3 |

### 5. 创建任务前检查

`resolveForCurrentTenant()` 读当前选择：
- API：取本租户该服务商的设置，无 Key 视为未配置（原提示不变）。
- 订阅：租户须获授权且该家可用（查一次 `/status`）；平台租户不查授权。不可用时抛 `AiUnavailableException`，提示「AI 暂不可用，请在业务设置 → AI 设置 中改选其他方式」。配置取平台该家订阅设置。

`isConfiguredForCurrentTenant()`（商机保存顺带的音译）对订阅只查授权、不查登录状态（避免在业务保存里调编排服务）；登录失效时音译任务失败，不影响商机。

### 6. 页面

按 V3 原型：按服务商分组，每家两行（本公司 API Key、平台订阅；未授权不显示订阅行）。选中行决定使用方式；点「填写」「更换」选中该行并行内展开输入框（普通文本框，「取消」在框内）。右侧「模型与用量」随选中行变化：API 可编辑；租户的订阅只读（「由平台设置」）；平台的订阅可编辑。所有修改只通过有修改时出现的底部保存条保存。租户的订阅状态只显示可用 / 不可用 / 状态未知，顶部不可用时只提示改选其他方式。

## Risks / Trade-offs

- 套餐授权对同套餐所有公司同时生效 → 需要单独控制时给该公司单独套餐。
- 订阅共享可能不符合服务商个人订阅条款 → 用户已知悉并决定以权限控制；部署文档的个人订阅说明同步改为「平台按套餐授权使用」。
- 创建任务时多一次 `/status` 调用（只在租户使用订阅时）→ 10 秒超时，失败视为不可用。
- 订阅一次只跑一个任务，开放给多家公司后更容易遇到忙碌失败 → 维持现状，失败可重试。

## Migration Plan

1. `V1.3.21__ai_provider_profiles.sql`：建表、复制旧配置、给旧列加弃用注释。回退：删新表、恢复注释（旧列数据未动，旧版本可直接读取）。
2. `V1.3.22__ai_subscription_permission.sql`：新增资源 110215 并给平台 `ROLE_ADMIN`。回退：删除资源与关联。
3. 前后端同批发布；无新增环境变量；#66 须先于或随同本变更上线。
