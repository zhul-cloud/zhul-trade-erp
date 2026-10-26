## Why

系统名称、Logo 等品牌元素散落在代码里：侧边栏的「烛」字方块、浏览器标签标题「烛龙ERP」、登录页副标题「烛龙外贸业财一体化中台」都是写死的，浏览器标签图标还是 Ant Design Pro 模板的默认图标。系统设置 PRD 早就约定由「外观设置」统一管理（`sys.site.name`、`sys.site.logo` 等），但这些内置配置从未写入数据库，系统参数页又禁止新建 `sys.` 开头的键，外观设置实际无法使用。另外，模板自带的 Google Analytics 仍在把线上访问数据发给模板作者的账号。

## What Changes

- **外观设置可用**：新增数据库迁移，写入平台级（`tenant_id=0`）内置外观配置，默认值等于当前显示内容，上线后外观不变：
  - `sys.site.name` 系统名称（烛龙ERP）
  - `sys.site.subtitle` 系统副标题（烛龙外贸业财一体化中台，**新增**）
  - `sys.site.logo` 系统 Logo（空）
  - `sys.login.background` 登录页背景（空）
  - `sys.login.footer` 登录页底部文字（© 2026 烛龙科技）
- **统一生效**：平台管理员在「系统参数 → 外观设置」修改后，登录页（Logo、名称、副标题、背景、底部文字）、侧边栏（Logo、名称）、浏览器标签（标题后缀、图标）全部取这套配置，刷新页面生效。
- **默认 Logo**：没有上传 Logo 时，登录页、侧边栏显示「系统名称第一个字」的渐变方块，浏览器标签图标按同样规则在页面上生成；页面脚本运行前先用静态的「烛」字图标。
- **平台统一，租户不可改**：外观配置对所有租户一致；租户账号在系统参数页看不到外观配置项，接口也拒绝租户修改（目前租户修改会直接覆盖平台配置，影响所有租户）。
- **模板残留清理**：去掉 Ant Design Pro 模板自带的 Google Analytics；`favicon.ico`、`logo.svg`、PWA 图标换成「烛」字图标。
- 不包含：按租户设置外观；登录前按租户子域名区分外观；邮件发件人名称（仍由 `.env` 的 `MAIL_FROM_NAME` 设置）。

## Capabilities

### New Capabilities

- `system/site-appearance`：平台统一的外观设置（系统名称、副标题、Logo、登录页背景与底部文字）及其在登录页、侧边栏、浏览器标签中的展示规则、默认值与修改权限。

### Modified Capabilities

（无）

## Impact

- 数据库：新增迁移，写入 5 条内置外观配置（只插入不存在的键）。
- 后端：`ConfigServiceImpl`（外观配置读取增加副标题；租户列表隐藏外观配置；租户修改外观配置时拒绝）、`AppearanceVO`。
- 前端：`AuthLayout`（副标题、默认 Logo 首字）、`AppLogo`（侧边栏）、`app.tsx`（登录后加载外观、浏览器标签标题与图标）、`services/zhul/public.ts`、`config/config.ts`（去掉 GA、声明图标）、`public/` 图标文件。
- 文档：系统设置 PRD 的内置配置清单加入副标题，并注明外观配置为平台统一、租户不可改。
- 无新增 `.env` 配置项。
