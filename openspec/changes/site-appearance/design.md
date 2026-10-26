## Context

- 外观配置的读取已经存在：公开接口 `/api/v1/public/appearance`（免登录）由 `ConfigServiceImpl.getPublicAppearance()` 读取 `tenant_id=0` 的 `sys.site.name`、`sys.site.logo`、`sys.login.background`、`sys.login.footer`，前端只有 `AuthLayout`（登录前页面）在用。
- 这 4 个键从未写入数据库（各迁移脚本都没有插入），生产库里也没有；系统参数页新建配置时禁止 `sys.` 前缀，所以外观实际无法修改。
- `ConfigServiceImpl` 中，`GLOBAL_APPEARANCE_KEYS` 的修改始终写 `tenant_id=0`，不形成租户副本；而租户角色（如生产上租户 1000 的「总经理」）拥有 `system:config:edit`，修改时会直接改掉平台配置。
- 侧边栏 `AppLogo`、浏览器标题（`config.ts` 的 `title`、`defaultSettings.title`）、登录页副标题都写死；`public/favicon.ico`、`logo.svg`、`icons/*.png` 是 Ant Design Pro 模板默认图标；`config.ts` 开启了模板的 `analytics.ga_v2`。
- 平台账号判断：后端 `TenantContext.getTenantId()` 为空或 0 表示平台；前端有 `isPlatformAccount()`。

## Goals / Non-Goals

**Goals:**
- 品牌元素只在「系统参数 → 外观」一处维护，登录页、侧边栏、浏览器标签统一读取。
- 升级后外观与现在完全一致（默认值等于当前写死的内容）。
- 堵住租户修改平台外观的口子。

**Non-Goals:**
- 不做按租户的外观；不做登录前按域名区分租户。
- 不改系统参数页的整体交互（沿用现有图片上传字段）。
- 邮件发件人名称仍由 `MAIL_FROM_NAME` 配置，不接入外观。

## Decisions

### 1. 迁移只补缺失的键

新增迁移 `V1.3.14__site_appearance.sql`，用 `INSERT … SELECT … WHERE NOT EXISTS` 写入 5 条 `tenant_id=0`、`is_builtin=1`、`config_group='appearance'`（与其他模块的内置配置一样存英文代码，页面显示为「外观」） 的配置（名称、副标题为 STRING，Logo、背景为 URL，底部文字为 STRING），备注写明作用位置。已存在的键不覆盖，满足「升级时已有值」场景。按项目惯例在 `sql/build/rollback/V1.3.14__site_appearance_rollback.sql` 附反向 SQL：内置配置不走软删除，回退用 `DELETE` 删除本迁移插入的行（按 `create_by='sys'` 且键在清单内），注释写明仅限回退。

- 备选：在应用启动时用代码补齐。项目里其他内置配置都用迁移写入，保持一致。

### 2. 外观键集中定义，租户侧过滤与拒绝

`GLOBAL_APPEARANCE_KEYS` 加入 `sys.site.subtitle`，作为唯一的外观键清单，`getPublicAppearance()` 也改为读它。租户上下文（`effectiveTenantId() != 0`）下：
- `listAll`、`groupCounts` 过滤掉外观键；
- `updateValue` 遇到外观键抛业务异常「外观由平台统一管理」（`FORBIDDEN`，403）。
平台上下文保持现有行为。删除接口本来就禁止删除内置配置，不需改动。

- 备选：在权限层给外观单独加一个权限码。需要改资源表和角色授权，范围更大；外观键数量固定，按「平台 / 租户」判断已足够。

### 3. 前端：外观信息放进全局初始状态

`getInitialState` 并行请求当前用户与 `/public/appearance`，把结果放进 `initialState.appearance`，接口失败时用内置默认值（与迁移默认值一致）。各处读取：
- `AppLogo(collapsed)`：有 Logo 显示图片（32×32、`object-fit: contain`，宽 Logo 会缩小居中，文档建议上传方形图），没有则显示默认 Logo；展开时显示系统名称。
- 浏览器标题：运行时 `layout` 配置里设置 `title: appearance.siteName`，并用 `pageTitleRender` 生成「页面名称 - 系统名称」；登录前页面在 `AuthLayout` 中设置 `document.title`。
- 浏览器图标：新增 `useFavicon` 小工具，按外观替换 `<link rel="icon">`：有 Logo 用 Logo 地址；没有则用 canvas 画默认 Logo（64×64）生成 data URL。
- `AuthLayout`：副标题改读 `appearance.siteSubtitle`；默认 Logo 改用共享组件。

默认 Logo 抽成一个组件 `DefaultLogo({ name, size })`，字取 `Array.from(name.trim())[0] ?? '烛'`（按字符取，避免截断代理对），圆角与字号按现有比例（圆角 ≈ 0.31 × 边长、字号 0.5 × 边长）。登录页（48）、侧边栏（32）、canvas 图标共用同一个比例常量。

- 备选：每个页面各自请求外观接口。会重复请求且首屏闪动更明显，放进初始状态只请求一次。

### 4. 静态图标作为首屏兜底

`index.html` 在脚本运行前就会请求图标，因此 `public/favicon.ico`、`logo.svg`、`icons/*.png` 换成「烛」字默认 Logo（已用 Noto Sans SC，SIL OFL 1.1，字形转为路径或位图，不依赖访问者字体），`config.ts` 声明 `favicons: ['/favicon.ico?v=2']`。页面加载后由 `useFavicon` 替换为当前外观。改了系统名称但未上传 Logo 时，首屏会短暂显示「烛」，随后切换为新名称首字，可以接受。

### 5. 配置分组：存代码，显示 2 个字的中文名

实施中发现各模块内置配置的分组存的是英文代码（`inquiry`、`quotation` 等），页签直接显示代码，与 PRD 中的中文分组名不一致。外观配置也改存代码 `appearance`，系统参数页新增 `groupLabel` 对照表，把页签与新增配置的分组选项统一显示为 2 个字的中文名（用户要求去掉多余的「设置」后缀，自定义分组 `custom` 显示「其他」）。不迁移已有数据：生产库只有这 7 个英文代码分组的内置配置，没有自定义配置。新增自定义配置的分组选项去掉「外观」，避免出现两个同名页签。

- 备选：把所有分组改存中文。需要改其他模块的迁移数据，且代码更适合做稳定标识。

### 6. 去掉 Google Analytics

删除 `config.ts` 中的 `analytics` 配置。该 ID 不属于本项目，没有业务依赖。

## Risks / Trade-offs

- [宽幅 Logo（如横向文字标志）在侧边栏 32×32 中会很小] → `object-fit: contain` 保证不变形；系统参数页 Logo 的备注提示「建议上传方形图片」。
- [浏览器缓存旧图标] → 静态图标带版本参数；运行时替换 `<link>` 不受缓存影响。
- [外观接口失败] → 使用内置默认值，页面照常可用。
- [租户在升级前已经改过外观（写到了平台配置）] → 迁移不覆盖已有值；上线后由平台管理员在外观中核对。

## Migration Plan

1. 部署新版本：Flyway 执行 `V1.3.14`，补齐外观配置；没有新增 `.env` 配置项。
2. 上线后平台管理员打开「系统参数 → 外观」确认 5 项配置，按需修改。
3. 回退：重新部署上一版本即可，新增的配置行不影响旧版本；如需清除，执行迁移附带的反向 SQL。
