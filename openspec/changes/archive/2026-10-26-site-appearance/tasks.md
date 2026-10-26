## 1. 数据库：内置外观配置

- [x] 1.1 新增迁移 `V1.3.14__site_appearance.sql`：以 `INSERT … SELECT … WHERE NOT EXISTS` 写入 5 条 `tenant_id=0`、`is_builtin=1`、分组代码 `appearance`（显示为「外观」）的配置（名称、副标题、Logo、登录页背景、底部文字，默认值见规范，Logo 备注写「建议上传方形图片」），并在 `sql/build/rollback/` 附反向 SQL。验证：本机全新库启动后 5 行存在且取值正确；预先插入 `sys.site.name='福唯ERP'` 再执行迁移，值保持不变

## 2. 后端：外观读取与租户限制

- [x] 2.1 `GLOBAL_APPEARANCE_KEYS` 加入 `sys.site.subtitle` 并作为唯一清单；`getPublicAppearance()` 改读该清单，`AppearanceVO` 新增 `siteSubtitle`，各字段为空时返回规范中的默认值。验证：集成测试调用 `/api/v1/public/appearance`，无数据与有数据两种情况返回正确
- [x] 2.2 租户上下文下，`listAll`、`groupCounts` 过滤外观键；`updateValue` 修改外观键时拒绝并提示「外观由平台统一管理」。平台上下文行为不变。验证：集成测试覆盖租户列表不含外观键、租户修改被拒且平台值不变、平台修改成功
- [x] 2.3 更新系统设置 PRD：内置配置清单加入 `sys.site.subtitle`，注明外观配置平台统一、租户不可见不可改，Logo 作用于登录页、侧边栏与浏览器标签。验证：PRD 与规范内容一致

## 3. 前端：统一读取外观

- [x] 3.1 新增共享默认 Logo 组件（系统名称首字、渐变、圆角与字号按比例），`AuthLayout` 与侧边栏 `AppLogo` 共用；`services/zhul/public.ts` 增加 `siteSubtitle` 与默认值。验证：Jest 覆盖首字提取（中文、英文、空名称回退「烛」、代理对字符）
- [x] 3.2 `getInitialState` 并行加载外观并放入 `initialState.appearance`（失败用默认值）；侧边栏 `AppLogo` 显示外观 Logo 或默认 Logo 与系统名称，收起时只显示 Logo。验证：本机页面中修改外观后刷新，侧边栏随之变化
- [x] 3.3 浏览器标签：运行时 layout 标题取系统名称，标题格式为「页面名称 - 系统名称」；登录前页面设置 `document.title`；新增 `useFavicon`，有 Logo 用 Logo，没有用 canvas 生成默认 Logo 图标。验证：本机页面标签标题与图标随外观变化
- [x] 3.4 `AuthLayout` 副标题改读外观配置，去掉写死的品牌文字。验证：`grep` 前端源码中不再有写死的「烛龙外贸业财一体化中台」
- [x] 3.5 静态兜底图标：`public/favicon.ico`、`logo.svg`、`icons/*.png` 换成「烛」字默认 Logo（Noto Sans SC，SIL OFL 1.1），`config.ts` 声明 `favicons: ['/favicon.ico?v=2']`。验证：构建后的 `index.html` 含图标声明，`/favicon.ico` 为新图标
- [x] 3.6 删除 `config.ts` 中模板自带的 `analytics`（Google Analytics）。验证：构建后的 `index.html` 不含 `googletagmanager`
- [x] 3.7 `npm run lint`（Biome + tsc）、`npx antd lint ./src` 与相关 Jest 测试通过。验证：命令输出
- [x] 3.8 实施中补充（与「品牌元素只在一处设置」一致）：页面底部版权、登录页与找回密码各步骤的 Helmet 标签标题改读外观（新增 `useAppearance`、`usePageTitle`）；`AuthLayout` 改读全局初始状态，不再单独请求；系统参数页 Logo 与登录页背景允许留空（后端同步放开外观图片项的空值校验），清空后恢复默认样式。模板遗留且未挂路由的 `pages/user/login`、`pages/Welcome.tsx` 中的「烛龙ERP」未改动。验证：本机页面实测标签标题、底部版权；在系统参数页清空 Logo 后保存成功
- [x] 3.9 配置分组规范：分组一律存小写英文代码、页面显示 2 个字中文名。新增 `pages/system/config/groupLabel.ts`（代码 → 中文对照，未知原样显示），页签与新增配置的分组选项使用；自定义配置分组改存 `basic`/`security`/`mail`/`custom`（新增默认 `custom`），服务端只接受这 4 个；外观配置分组存 `appearance`（迁移与集成测试同步）；PRD 写入分组规范。验证：Jest 覆盖对照与 2 字长度；集成测试覆盖不合法分组被拒；本机页面分组页签全部为中文

## 4. 集成验证

- [x] 4.1 本机 Docker 重建后端与前端，平台账号在「系统参数 → 外观」修改名称、副标题、上传 Logo，登录页、侧边栏、浏览器标签全部生效；租户账号看不到外观配置。验证：页面实测记录（实测：平台账号改名「福唯ERP」后侧栏首字、标签标题与图标、页脚版权均更新；上传 Logo 后侧栏、标签图标、登录前页面使用该图片；清空 Logo、改回名称后恢复默认。本机库只有平台账号，租户不可见、不可改由后端集成测试 ConfigAppearanceIntegrationTest 验证，未做页面实测）
- [x] 4.2 `openspec validate site-appearance --strict` 通过；改动文件均为 LF（`scripts/docker-dev.ps1` 等按 `.gitattributes` 处理的除外）。验证：命令输出

## Workflow follow-up

- 合并到 main 前按 `08-生产发版.md` 检查：本变更无新增 `.env` 配置项，迁移为 `V1.3.14`。
- 上线后平台管理员在外观中核对 5 项配置；满足评审要求后归档本变更。
