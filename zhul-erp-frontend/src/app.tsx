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
import { AppThemeSync, useAppTheme } from '@/theme/AppTheme';
import { buildShellSettings } from '@/theme/shell';
import { getThemeMode } from '@/theme/store';
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

      // 超级管理员拥有所有权限
      if (currentUser.access === 'admin') {
        return { ...currentUser, permissions: ['*'] };
      }

      // 普通用户从后端获取权限列表（菜单路径 + 按钮权限码混合）
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
    return {
      fetchUserInfo,
      currentUser,
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
    menuItemRender: (item, dom) => {
      if (item.path) {
        return (
          <Link to={item.path} prefetch>
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
