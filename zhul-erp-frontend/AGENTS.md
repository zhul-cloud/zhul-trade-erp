# AGENTS.md

本文件是前端目录的唯一指引正文（Claude Code 通过同目录 `CLAUDE.md` 的 `@AGENTS.md` 引用加载，Codex 直接读取），修改只改本文件。

烛龙ERP 前端：Umi Max 4 + antd 6 + ProComponents 3，构建用 utoopack。项目最初来自 Ant Design Pro 模板，模板的演示页面、接口与工具已清理，不再按模板升级。仓库级规则见仓库根目录的 `AGENTS.md`。

## 命令

- `npm start`：本地开发（加载 `src/pages/**/_mock.ts`）；`npm run dev` / `npm run start:no-mock`：不用 mock，接口代理到 `localhost:8080` 的后端（`config/proxy.ts`）
- `npm run build`：生产构建；`npm run preview`：预览构建结果
- `npm run lint`（Biome + tsc）、`npx antd lint ./src`（antd 专项）、`npm test`（Jest）——提交前都要通过
- `npm run biome`：自动修复格式；`npm run analyze`：分析打包体积；`npm run doctor`：React 代码体检

## 规则

- **只用 Biome**，不用 ESLint / Prettier
- **写 antd 代码前先 `npx antd info <组件>`**，不凭记忆写 API；已弃用的写法（如 Space `direction`、Modal/Drawer `destroyOnClose`）用新写法
- **Conventional commits**（commitlint 检查）· TypeScript strict · Node ≥ 20 · 只用 `package-lock.json`
- `src/.umi*` 是自动生成的，开发服务异常时删除后重启

## 架构要点

- **配置**：`config/config.ts`；路由 `config/routes.ts`。侧边栏菜单的顺序、名称、图标由后端「菜单管理」的菜单树决定（`getInitialState` 里的 `menuTree`），路由 `name` 只用于页面标题（`menu.xxx` 多语言 key）
- **登录与权限**：
  - 登录调 `/api/v1/auth/login`（`src/services/zhul/auth.ts`），令牌存储见 `src/utils/tokenStore.ts`（记住我存 localStorage，否则 sessionStorage），用户信息存 localStorage 的 `zhul_user`（类型 `CurrentUser`）
  - `getInitialState`（`src/app.tsx`）读 `zhul_user`，再从 `/api/v1/auth/menus` 取权限码；`src/access.ts` 按权限码控制页面与按钮
  - 401 时 `src/requestErrorConfig.ts` 自动刷新令牌并重放请求；登录前页面（登录、找回密码）的判断见 `isAuthPath`
- **请求**：用 `@umijs/max` 的 `request`；通用接口放 `src/services/zhul/`，页面专用接口放页面目录的 `service.ts`
- **外观**：系统名称、Logo 等由平台在系统参数「系统」分组统一设置，前端从 `/api/v1/public/appearance` 读取（`src/utils/appearance.ts`、`src/components/Shell/BrandLogo.tsx`）
- **样式**：主题 token 与 `createStyles`（`src/theme/`）为主；Tailwind CSS v4（`src/tailwind.css`，经 PostCSS 处理）在用，不能去掉；Less 只剩全局的 `src/global.less`
- **多语言**：`src/locales/` 8 种语言，只保留路由用到的菜单文案与 404 文案
- **mock**：只加载 `src/pages/**/_mock.ts`（`config.ts` 的 `mock.include`）

## 构建补丁

`patches/@utoo+pack+1.4.3.patch`（`postinstall` 时由 patch-package 自动应用）修复 utoopack 把 worker 分错执行池导致的构建偶发失败（`missing field source`）。上游修复 vercel/next.js#96761、#96810 合并并随 umi 升级后删除；`@utoo/pack` 版本变化时 patch-package 会提示补丁不匹配，需要重新评估。排查时设 `UTOO_WORKER_LOG=1` 可输出 worker 创建顺序。

## 页面组织

每个页面目录放 `index.tsx`，按需加 `service.ts`、`_mock.ts`、`data.d.ts`、样式文件，页面专用代码不外放。
