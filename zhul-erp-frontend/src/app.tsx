import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import type { RequestConfig, RunTimeLayoutConfig } from '@umijs/max';
import { history, Link, request as umiRequest, useModel } from '@umijs/max';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import React from 'react';

// Initialize dayjs plugins globally
dayjs.extend(relativeTime);

import {
  DocLink,
  ErrorBoundary,
  Footer,
  LangDropdown,
  OfflineBanner,
} from '@/components';
import { AppLogo, SidebarUser, ThemeToggle, TopBar } from '@/components/Shell';
import { getMenuTree, type MenuItem } from '@/pages/system/menu/service';
import { AppThemeSync, useAppTheme } from '@/theme/AppTheme';
import { buildShellSettings } from '@/theme/shell';
import { getThemeMode } from '@/theme/store';
import { toProLayoutMenu } from '@/utils/menuOrder';
import defaultSettings from '../config/defaultSettings';
import { errorConfig } from './requestErrorConfig';

const isDev = process.env.NODE_ENV === 'development';

/** 主题切换时把外壳令牌（侧栏、顶栏、内容区）同步进 initialState.settings，ProLayout 从那里读 */
const ShellThemeSync: React.FC = () => {
  const { mode } = useAppTheme();
  const { setInitialState } = useModel('@@initialState');
  // setInitialState 的引用会随 initialState 变化，依赖它会死循环，只在 mode 变化时同步
  const setterRef = React.useRef(setInitialState);
  setterRef.current = setInitialState;
  React.useEffect(() => {
    setterRef.current((s) => ({
      ...s,
      settings: {
        ...s?.settings,
        ...buildShellSettings(mode),
      } as Partial<LayoutSettings>,
    }));
  }, [mode]);
  return null;
};

/** 外壳设置 = 项目默认设置 + 当前主题对应的令牌（侧栏、顶栏、内容区） */
const initialShellSettings = () =>
  ({
    ...defaultSettings,
    ...buildShellSettings(getThemeMode()),
  }) as Partial<LayoutSettings>;
const loginPath = '/login';
const publicPaths = [
  loginPath,
  '/user/register',
  '/user/register-result',
  '/forget-password/step1',
  '/forget-password/step2',
  '/forget-password/step3',
  '/forget-password/done',
];

/**
 * @see https://umijs.org/docs/api/runtime-config#getinitialstate
 * */
export async function getInitialState(): Promise<{
  settings?: Partial<LayoutSettings>;
  currentUser?: API.CurrentUser & {
    permissions?: string[];
    tenantName?: string;
  };
  loading?: boolean;
  fetchUserInfo?: () => Promise<
    | (API.CurrentUser & { permissions?: string[]; tenantName?: string })
    | undefined
  >;
  settingDrawerOpen?: boolean;
  // 侧边栏的顺序/名称/图标/层级全部来自「菜单管理」的原始树，而不是 routes.ts 里
  // 写死的书写顺序/英文 name——由 layout 里的 menu.request 直接转成 ProLayout 菜单数据
  menuTree?: MenuItem[];
}> {
  const fetchUserInfo = async () => {
    try {
      const userStr = localStorage.getItem('zhul_user');
      if (!userStr) {
        const { pathname, search, hash } = history.location;
        history.replace(
          `${loginPath}?redirect=${encodeURIComponent(pathname + search + hash)}`,
        );
        return undefined;
      }
      const currentUser = JSON.parse(userStr) as API.CurrentUser;

      // 权限列表统一由后端算好再给前端：已经把平台超管/租户套餐/角色三层限制都
      // 算进去了（管理员账号不再是前端直通的万能通行证，租户套餐没给的菜单/按钮，
      // 即使是租户自己的管理员登录也不会出现在这份列表里）。
      // 走统一的 request（而非裸 fetch），token 过期时才能命中刷新重放逻辑
      try {
        const permissions: string[] = await umiRequest('/api/v1/auth/menus', {
          method: 'GET',
        }).then((res) => res.data ?? []);
        return { ...currentUser, permissions };
      } catch {
        return { ...currentUser, permissions: [] };
      }
    } catch (_error) {
      const { pathname, search, hash } = history.location;
      history.replace(
        `${loginPath}?redirect=${encodeURIComponent(pathname + search + hash)}`,
      );
      return undefined;
    }
  };
  // 如果不是登录页面，执行
  const { location } = history;
  if (!publicPaths.includes(location.pathname)) {
    const currentUser = await fetchUserInfo();
    const menuTree = currentUser
      ? await getMenuTree().catch(() => [])
      : undefined;
    return {
      fetchUserInfo,
      currentUser,
      menuTree,
      settings: initialShellSettings(),
      settingDrawerOpen: false,
    };
  }
  return {
    fetchUserInfo,
    settings: initialShellSettings(),
    settingDrawerOpen: false,
  };
}

// ProLayout 支持的api https://procomponents.ant.design/components/layout
export const layout: RunTimeLayoutConfig = ({ initialState }) => {
  return {
    siderWidth: 240,
    // 侧边栏菜单树完全由「菜单管理」的数据 + 当前用户的有效权限决定，不再从 umi 由
    // routes.ts 派生出的 menuData 上打补丁（那棵树里混着重定向占位节点，容易把只用
    // 于路由跳转、本该隐藏的节点误当成真实菜单项渲染出来），也直接忽略传入的
    // menuData 参数、完全用后端数据重新生成一份。改了 sort/name/icon 或套餐/角色
    // 权限，刷新页面即生效。
    // 用同步的 menuDataRender 而不是 menu.request：initialState.menuTree 在
    // getInitialState 里已经 await 过了，这里不需要再异步请求一次——ProLayout 的
    // menu.request 是异步的，会让菜单树先以空数组挂载、请求成功后才二次渲染，这次
    // 排查发现这个额外的异步重渲染会把每个菜单项的宽度算错（图标+文字被压缩到只有
    // 几十像素，文字被省略号截断到只剩第一个字）。同步渲染没有这个问题。
    menuDataRender: () => {
      const allowed = new Set(initialState?.currentUser?.permissions ?? []);
      return toProLayoutMenu(initialState?.menuTree ?? [], allowed);
    },
    menu: { locale: false },
    menuItemRender: (item, dom) => {
      if (item.path) {
        return (
          // display:block + width:100%：<a> 默认是 inline，百分比宽度的子元素
          // （ProComponents 菜单项内部布局靠 width:100% 撑满）在 inline 容器里没有
          // 明确的包含块可以撑，会直接退化成按内容收缩——图标+文字被压缩到几十像素，
          // 文字用省略号只剩第一个字。这是这次改动之外一直存在的旧问题，只是之前
          // 只用无障碍树核对过文字内容，没有真的用截图看过侧边栏才没发现。
          <Link
            to={item.path}
            prefetch
            style={{ display: 'block', width: '100%' }}
          >
            {dom}
          </Link>
        );
      }
      return dom;
    },
    // 当前用户卡放在侧栏底部（menuFooterRender），顶栏不再重复放头像
    menuHeaderRender: (_logo, _title, props) => (
      <AppLogo collapsed={props?.collapsed} />
    ),
    menuFooterRender: (props) => <SidebarUser collapsed={props?.collapsed} />,
    footerRender: () => <Footer />,
    onPageChange: () => {
      const { location } = history;
      // 如果没有登录，重定向到 login
      if (!initialState?.currentUser && location.pathname !== loginPath) {
        history.replace(
          `${loginPath}?redirect=${encodeURIComponent(location.pathname + location.search + location.hash)}`,
        );
      }
    },
    bgLayoutImgList: [],
    links: [],
    // Replace ProLayout's default ErrorBoundary with our offline-aware version,
    // so chunk load errors show friendly messages instead of "Something went wrong."
    ErrorBoundary,
    // 自定义 403 页面
    // unAccessible: <div>unAccessible</div>,
    // 增加一个 loading 的状态
    childrenRender: (children) => (
      <>
        <ShellThemeSync />
        <TopBar>
          <ThemeToggle />
          <DocLink />
          <LangDropdown />
        </TopBar>
        {children}
      </>
    ),
    ...initialState?.settings,
  };
};

/**
 * @name request 配置，可以配置错误处理
 * 它基于 axios 提供了一套统一的网络请求和错误处理方案。
 * @doc https://umijs.org/docs/max/request#配置
 */
export const request: RequestConfig = {
  baseURL: isDev ? '' : 'https://pro-api.ant-design-demo.workers.dev',
  withCredentials: true,
  ...errorConfig,
};

/** 主题同步放在 innerProvider：它在 antd 的 ConfigProvider 之内，所有页面（含没有外壳的向导页）都被覆盖 */
export function innerProvider(container: React.ReactNode) {
  return <AppThemeSync>{container}</AppThemeSync>;
}

export function rootContainer(container: React.ReactNode) {
  return (
    <>
      <OfflineBanner />
      <ErrorBoundary>{container}</ErrorBoundary>
    </>
  );
}
