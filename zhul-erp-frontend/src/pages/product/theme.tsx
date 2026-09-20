import { MoonOutlined, SunOutlined } from '@ant-design/icons';
import { App, theme as antdTheme, Button, ConfigProvider, Tooltip } from 'antd';
import { createStyles } from 'antd-style';
import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react';

/**
 * 商品主数据页面的主题：深色默认、浅色并存（方案 D：信任蓝 + 中性灰）。
 * 令牌取值见 .claude/context/ui-design-patterns.md「V3 方案 D」一节；
 * 本期只覆盖商品页，全站主题升级另立 change。
 */
export type ThemeMode = 'dark' | 'light';

const STORAGE_KEY = 'zhul_product_theme';

const readMode = (): ThemeMode => {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark';
  } catch {
    return 'dark';
  }
};

/** 设计令牌，页面里需要直接取色（进度条、渐变、胶囊）时用它，避免散落的十六进制 */
export const PALETTE = {
  dark: {
    canvas: '#0B1220',
    card: '#111A2C',
    inset: '#0D1524',
    hairline: '#1E293B',
    control: '#64748B',
    ink: '#F8FAFC',
    sub: '#CBD5E1',
    mute: '#94A3B8',
    link: '#60A5FA',
    accentSoft: '#182B4C',
    accentLine: '#234681',
    hover: '#16233A',
    green: '#4ADE80',
    greenSoft: '#183236',
    orange: '#FB923C',
    orangeSoft: '#2D282E',
    red: '#F87171',
    redSoft: '#2D2434',
    cyan: '#22D3EE',
  },
  light: {
    canvas: '#F8FAFC',
    card: '#FFFFFF',
    inset: '#F1F5F9',
    hairline: '#E2E8F0',
    control: '#8492A6',
    ink: '#0F172A',
    sub: '#475569',
    mute: '#64748B',
    link: '#1D4ED8',
    accentSoft: '#DBEAFE',
    accentLine: '#93C5FD',
    hover: '#F1F5F9',
    green: '#15803D',
    greenSoft: '#DCFCE7',
    orange: '#C2410C',
    orangeSoft: '#FFEDD5',
    red: '#B91C1C',
    redSoft: '#FEE2E2',
    cyan: '#0E7490',
  },
} as const;

export const PRIMARY_GRADIENT = 'linear-gradient(135deg, #2563EB, #4F46E5)';
export const ACCENT_GRADIENT =
  'linear-gradient(90deg, #BFDBFE, #60A5FA, #22D3EE)';

interface ThemeCtx {
  mode: ThemeMode;
  toggle: () => void;
  palette: (typeof PALETTE)[ThemeMode];
}

const Ctx = createContext<ThemeCtx>({
  mode: 'dark',
  toggle: () => {},
  palette: PALETTE.dark,
});

export const useProductTheme = () => useContext(Ctx);

const useStyles = createStyles(
  (
    { css },
    { p, fullPage }: { p: (typeof PALETTE)[ThemeMode]; fullPage: boolean },
  ) => ({
    frame: css`
    position: relative;
    min-height: ${fullPage ? '100vh' : 'calc(100vh - 140px)'};
    padding: ${fullPage ? '0' : '28px'};
    border-radius: ${fullPage ? '0' : '16px'};
    background: ${p.canvas};
    color: ${p.ink};
    font-variant-numeric: normal;

    /* 主按钮：蓝 → 靛渐变（一屏只放一个主操作） */
    .ant-btn-primary:not(:disabled):not(.ant-btn-dangerous) {
      background: ${PRIMARY_GRADIENT};
      border: none;
      box-shadow: 0 8px 26px rgba(59, 130, 246, 0.45);
    }

    /* 所有可交互元素都要有可见焦点 */
    a:focus-visible,
    button:focus-visible,
    [role='button']:focus-visible,
    [role='switch']:focus-visible,
    [tabindex]:focus-visible,
    .ant-btn:focus-visible,
    .ant-switch:focus-visible,
    .ant-input:focus-visible,
    .ant-checkbox-input:focus-visible + .ant-checkbox-inner {
      outline: 2px solid ${p.link};
      outline-offset: 2px;
    }

    /* 下拉框、日期框、带前缀的输入框：焦点在内部的 input 上，外框画出焦点环；表头排序键盘可达 */
    .ant-select:focus-within,
    .ant-picker:focus-within,
    .ant-input-affix-wrapper:focus-within,
    .ant-input-number:focus-within,
    .ant-table-thead th[tabindex]:focus-visible {
      outline: 2px solid ${p.link};
      outline-offset: 2px;
    }

    /* 输入框的清除按钮图标只有 12px，加大可点击区域到 24×24 */
    .ant-input-clear-icon,
    .ant-select-clear {
      padding: 6px;
      margin: -6px;
      box-sizing: content-box;
    }

    /* 表格里的数字、金额、汇率用等宽数字 */
    .num {
      font-variant-numeric: tabular-nums;
    }

    @media (prefers-reduced-motion: reduce) {
      *,
      *::before,
      *::after {
        transition: none !important;
        animation: none !important;
        scroll-behavior: auto !important;
      }
    }
  `,
    skip: css`
      position: absolute;
      left: 16px;
      top: 12px;
      z-index: 10;
      padding: 8px 16px;
      border-radius: 10px;
      background: ${p.card};
      color: ${p.link};
      border: 2px solid ${p.link};
      /* 平时只对读屏和键盘可见：获得焦点时才显示出来 */
      clip-path: inset(50%);
      width: 1px;
      height: 1px;
      overflow: hidden;
      white-space: nowrap;
      &:focus {
        clip-path: none;
        width: auto;
        height: auto;
        overflow: visible;
      }
    `,
  }),
);

const Frame: React.FC<{ children: React.ReactNode; fullPage: boolean }> = ({
  children,
  fullPage,
}) => {
  const { palette } = useProductTheme();
  const { styles } = useStyles({ p: palette, fullPage });
  return (
    <div className={styles.frame}>
      <a className={styles.skip} href="#product-main">
        跳到主要内容
      </a>
      {children}
    </div>
  );
};

export const ProductThemeProvider: React.FC<{
  children: React.ReactNode;
  /** 整页模式：占满视口、无内边距，用于没有侧栏的新建向导 */
  fullPage?: boolean;
}> = ({ children, fullPage = false }) => {
  const [mode, setMode] = useState<ThemeMode>(readMode);
  const toggle = useCallback(() => {
    setMode((prev) => {
      const next = prev === 'dark' ? 'light' : 'dark';
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        /* 存不了就只在当前页面生效 */
      }
      return next;
    });
  }, []);
  const palette = PALETTE[mode];

  const themeConfig = useMemo(
    () => ({
      algorithm:
        mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      token: {
        colorPrimary: '#2563EB',
        colorLink: palette.link,
        colorBgLayout: palette.canvas,
        colorBgContainer: palette.card,
        colorBgElevated: palette.card,
        colorBorder: palette.control,
        colorBorderSecondary: palette.hairline,
        colorText: palette.ink,
        colorTextSecondary: palette.sub,
        colorTextTertiary: palette.mute,
        colorTextDescription: palette.mute,
        colorTextPlaceholder: palette.mute,
        colorSuccess: palette.green,
        colorWarning: palette.orange,
        colorError: palette.red,
        borderRadius: 10,
        borderRadiusLG: 16,
        controlHeight: 36,
        fontFamily:
          "'Noto Sans SC', AlibabaSans, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
      },
      components: {
        Table: {
          headerBg: palette.card,
          rowHoverBg: palette.hover,
          borderColor: palette.hairline,
        },
        Card: { headerFontSize: 16 },
        // 交互目标不小于 24×24：开关 44×24
        Switch: { trackHeight: 24, handleSize: 20, trackMinWidth: 44 },
      },
    }),
    [mode, palette],
  );

  const value = useMemo(
    () => ({ mode, toggle, palette }),
    [mode, toggle, palette],
  );

  return (
    <Ctx.Provider value={value}>
      <ConfigProvider theme={themeConfig}>
        <App>
          <Frame fullPage={fullPage}>{children}</Frame>
        </App>
      </ConfigProvider>
    </Ctx.Provider>
  );
};

/** 页面标题区：标题 + 一句话说明 + 右侧操作 + 主题切换 */
export const PageHeader: React.FC<{
  title: string;
  description?: string;
  actions?: React.ReactNode;
  eyebrow?: string;
}> = ({ title, description, actions, eyebrow }) => {
  const { mode, toggle, palette } = useProductTheme();
  return (
    <header
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: 16,
        marginBottom: 24,
        flexWrap: 'wrap',
      }}
    >
      <div>
        {eyebrow && (
          <div
            style={{
              fontFamily: "'IBM Plex Mono', ui-monospace, monospace",
              fontSize: 12,
              letterSpacing: 2,
              color: palette.link,
              marginBottom: 6,
            }}
          >
            {eyebrow}
          </div>
        )}
        <h1
          id="product-main"
          tabIndex={-1}
          style={{
            fontSize: 28,
            fontWeight: 700,
            margin: 0,
            color: palette.ink,
          }}
        >
          {title}
        </h1>
        {description && (
          <p style={{ margin: '6px 0 0', color: palette.sub, fontSize: 14 }}>
            {description}
          </p>
        )}
      </div>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        {actions}
        <Tooltip title={mode === 'dark' ? '切换到浅色' : '切换到深色'}>
          <Button
            aria-label={mode === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
            icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
            onClick={toggle}
          />
        </Tooltip>
      </div>
    </header>
  );
};
