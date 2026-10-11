## Context

- **菜单**：在 `resource` 表配置，套餐通过 `tenant_package.menu_ids` 授权，角色通过 `role_resource` 授权。前端 `access.ts` 用 `can(path)` 判断，后端用 `@perm.canAccessMenu(path)` 和 `@perm.has(permission)` 判断。
  - 现有「内容任务」：资源 100100，路径 `/product/content-tasks`，按钮 110202「商品内容维护」`product:content:edit`。
  - 已用到的最大资源 ID：菜单 100100，按钮 110202。
- **后端接口**：`ContentTaskController`（`/api/v1/product/content-tasks`）一个控制器包含全部接口：
  - 列表、计数、下载包、预览、确认、资料已核；
  - 平台设置、不发布、商品内容、上传记录。
  
  权限统一挂在内容任务菜单上。
- **阶段**：任务列表 SQL 的 `STAGE` 表达式给出 1 资料待补、2 资料待核、3 营销待生成、4 已完成，页签计数与筛选共用它。
- **前端**：
  - `pages/product/content-tasks`：列表与上传抽屉；
  - `products/cards/SeoCard.tsx`：营销卡片、平台标签、下载按钮等组件；
  - `ImportRecords.tsx`：上传记录。

## Goals / Non-Goals

**Goals**
- 资料与运营的入口、权限、页面彻底分开，现有账号的能力在迁移后不变。
- 尽量复用现有查询与组件，不改数据结构。

**Non-Goals**
- 不改营销内容的存储与格式（关键词总库、新字段放到 content-schema-v2）。
- 不做发布状态与渠道数据。

## Decisions

**1. 菜单与权限**（Flyway `V1.2.56`）

| 资源 | ID | 类型 | 路径 / 权限 | 说明 |
|---|---|---|---|---|
| 运营管理 | 100101 | 一级菜单 | `/operations` | 排在商品资料之后 |
| 商品运营 | 100102 | 二级菜单 | `/operations/products` | 列表与详情 |
| 营销内容维护 | 110203 | 按钮（挂在商品运营下） | `operations:marketing:edit` | 确认营销包、平台设置、不发布 |
| 资料任务 | 100100（改名） | 二级菜单 | `/product/fact-tasks`（改路径） | 原「内容任务」 |

- **授权迁移**：
  - 套餐里含 100100 的，补 100101、100102；含 110202 的，补 110203；
  - 角色里有 100100 的，补 100101、100102；有 110202 的，补 110203；
  - 内置管理员补全部。
- 前端旧路由 `/product/content-tasks` 重定向到 `/product/fact-tasks`；行内跳转（比如营销卡片里的「去上传」）一并改成新地址。

**2. 接口按入口分组，权限分别挂**

保留 `/api/v1/product/content-tasks` 前缀，按入口分组：

| 分组 | 接口 | 菜单 | 按钮 |
|---|---|---|---|
| 资料任务 | `POST /facts/page`、`POST /facts/counts`、`GET /facts/package`、`POST /facts/preview`、`POST /facts/confirm`、`POST /products/{id}/verify-facts` | 资料任务 | 确认、核对要「商品内容维护」 |
| 商品运营 | `POST /marketing/page`、`POST /marketing/counts`、`GET /marketing/package`、`POST /marketing/preview`、`POST /marketing/confirm`、平台设置、不发布 | 商品运营 | 确认、平台设置、不发布要「营销内容维护」 |
| 公共 | `GET /products/{id}`（商品内容）、`GET /products/{id}/imports?type=`、`GET /imports/{id}` | 登录即可 | — |

- 原来不分组的 `page`、`counts`、`package`、`preview`、`confirm` 去掉，前端同步改。
- **备选方案：** 一个接口加 `view` 参数，权限用 SpEL 按参数选菜单。这样权限表达式难读，接口文档也看不出边界，不采用。

**3. 列表与计数复用 `STAGE`，按入口映射页签**
- 资料任务：
  - 页签 1 资料待补、2 资料待核、`verified` 资料已核（`STAGE IN (3, 4)`）；
  - 计数返回 `all`、`1`、`2`、`verified`。
- 商品运营：
  - 页签 `unverified` 资料未核对（`STAGE IN (1, 2)`）、3 营销待生成、4 已完成；
  - 计数返回 `all`、`unverified`、`3`、`4`。
- Mapper 的筛选从 `status = #{status}` 改为 `status IN (...)`，由服务层把页签换成阶段列表。计数仍按 `STAGE` 分组，在服务层合并成页签。

**4. 预览与确认按入口限定包类型**
- `ContentImportService.analyze` 增加 `expectedType`。文件解析出的类型不符时，加文件级错误：
  - 资料入口收到营销包：「营销包请到运营管理 → 商品运营上传」；
  - 运营入口收到资料包或 wiki 文件：「资料包请到商品资料 → 资料任务上传」。
- 确认时同样校验，防止绕过前端。

**5. 上传记录按包类型过滤**
- `GET /products/{id}/imports` 增加 `type`（facts、marketing），可见范围规则不变。
- 商品详情、资料任务只查 facts，商品运营只查 marketing。

**6. 商品运营详情与关键词库**
- **页面**：新路由 `/operations/products/:id`。
  - 复用 `useProductContent`，以及平台页签、不发布开关、下载按钮等组件，从 `SeoCard.tsx` 拆出来放到 `pages/operations/products`；
  - 页头复用资料状态。
- **关键词库**：
  - `ProductContentVO` 增加 `keywordLibrary`：服务端把本公司该商品各平台的关键词合并，同一个词（不分大小写）合并成一行，档位取最高（数字最小），记录选用它的平台；
  - 按档位、首次出现顺序排序；
  - 只合并本公司启用平台的关键词。
  
  content-schema-v2 改成总库存储后，接口形状不变，前端不用改。

**7. 商品详情**
- 去掉 `MarketingCard`，新增 `OperationsSummary`：一行 `PlatformChips`，有商品运营菜单时显示「去商品运营」。
- 上传记录卡片只查资料包。

## Risks / Trade-offs

- **[旧地址与收藏]** 内容任务改了路径。→ 前端加重定向；接口路径改动会和前端一起发布。
- **[权限迁移遗漏]** 自定义角色可能没有内容任务菜单，却有其他组合。→ 迁移按「有 100100 就补运营菜单、有 110202 就补维护按钮」处理，覆盖现有的全部授权方式；迁移后用测试核对内置管理员和普通角色。
- **[两个页面看到同一商品]** 资料任务和商品运营都列出全部商品。这是有意的：两个角色各看各的阶段。

## Migration Plan

- Flyway `V1.2.56__split_operations_menu.sql`：
  1. 新增菜单和按钮资源；
  2. 改 100100 的名称和路径；
  3. 补套餐与角色授权。
- 只改菜单和权限数据，业务数据不动。
