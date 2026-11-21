import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { hiddenCount } from '@/components/PageTabs';
import {
  getDeltaPref,
  getModePref,
  getThemeMode,
  getThemePreset,
  setDeltaPref,
  setModePref,
  setThemePreset,
} from '@/theme/store';
import { AppLogo } from './AppLogo';
import { ThemeSettingsPanel } from './ThemeSettings';
import { UserMenu } from './UserMenu';

const mockSetInitialState = jest.fn();
const mockState: any = {
  appearance: { siteName: '烛龙ERP' },
  currentUser: {
    name: '王敏',
    userid: 'wm',
    access: 'admin',
    tenantName: '示例贸易有限公司',
  },
};

jest.mock('@umijs/max', () => ({
  useModel: () => ({
    initialState: mockState,
    setInitialState: mockSetInitialState,
  }),
  history: { replace: jest.fn() },
}));

const h = React.createElement;

afterEach(() => {
  act(() => {
    setModePref('light');
    setThemePreset('amber');
    setDeltaPref('redUp');
  });
  mockSetInitialState.mockReset();
});

describe('主题设置', () => {
  it('三项都写入本机存储，模式、主题色、涨跌颜色立即生效', () => {
    render(h(ThemeSettingsPanel));
    fireEvent.click(screen.getByLabelText(/深色/));
    expect(getModePref()).toBe('dark');
    expect(getThemeMode()).toBe('dark');
    expect(localStorage.getItem('zhul_theme')).toBe('dark');

    fireEvent.click(screen.getByLabelText('宝蓝'));
    expect(getThemePreset()).toBe('royal');
    expect(localStorage.getItem('zhul_theme_preset')).toBe('royal');

    fireEvent.click(screen.getByLabelText(/涨绿跌红/));
    expect(getDeltaPref()).toBe('greenUp');
    expect(localStorage.getItem('zhul_delta_color')).toBe('greenUp');
  });

  it('涨红跌绿是默认，排在前面', () => {
    render(h(ThemeSettingsPanel));
    const radios = screen
      .getAllByRole('radio')
      .filter((r) => (r as HTMLInputElement).name === 'theme-delta');
    expect((radios[0] as HTMLInputElement).value).toBe('redUp');
    expect((radios[0] as HTMLInputElement).checked).toBe(true);
  });

  it('打开时焦点落到选中项，按 Esc 关闭', () => {
    const onClose = jest.fn();
    render(h(ThemeSettingsPanel, { onClose }));
    expect(document.activeElement).toBe(screen.getByLabelText(/浅色/));
    fireEvent.keyDown(document.activeElement as Element, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('重新打开页面后恢复上次的选择', () => {
    localStorage.setItem('zhul_theme', 'dark');
    localStorage.setItem('zhul_theme_preset', 'teal');
    localStorage.setItem('zhul_delta_color', 'greenUp');
    jest.isolateModules(() => {
      const s = require('@/theme/store');
      expect(s.getModePref()).toBe('dark');
      expect(s.getThemePreset()).toBe('teal');
      expect(s.getDeltaPref()).toBe('greenUp');
    });
  });

  it('跟随系统时按操作系统的深浅色，系统切换后跟着变', () => {
    localStorage.setItem('zhul_theme', 'system');
    let listener: ((e: { matches: boolean }) => void) | undefined;
    const real = window.matchMedia;
    window.matchMedia = jest.fn().mockReturnValue({
      matches: true,
      addEventListener: (_: string, l: any) => {
        listener = l;
      },
    }) as any;
    try {
      jest.isolateModules(() => {
        const s = require('@/theme/store');
        expect(s.getThemeMode()).toBe('dark');
        listener?.({ matches: false });
        expect(s.getThemeMode()).toBe('light');
      });
    } finally {
      window.matchMedia = real;
    }
  });

  it('没有选过时按浅色、琥珀、涨红跌绿', () => {
    localStorage.removeItem('zhul_theme');
    localStorage.removeItem('zhul_product_theme');
    localStorage.removeItem('zhul_theme_preset');
    localStorage.removeItem('zhul_delta_color');
    jest.isolateModules(() => {
      const s = require('@/theme/store');
      expect(s.getThemeMode()).toBe('light');
      expect(s.getThemePreset()).toBe('amber');
      expect(s.getDeltaPref()).toBe('redUp');
    });
  });
});

describe('侧栏顶部', () => {
  it('显示站点名和公司名，点收起按钮收起侧栏', () => {
    render(h(AppLogo, { collapsed: false }));
    expect(screen.getByText('烛龙ERP')).toBeTruthy();
    expect(screen.getByText('示例贸易有限公司')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '收起侧边栏' }));
    const update = mockSetInitialState.mock.calls[0][0];
    expect(update({ collapsed: false }).collapsed).toBe(true);
  });

  it('收起后只留 Logo 和展开按钮', () => {
    render(h(AppLogo, { collapsed: true }));
    expect(screen.queryByText('示例贸易有限公司')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '展开侧边栏' }));
    const update = mockSetInitialState.mock.calls[0][0];
    expect(update({ collapsed: true }).collapsed).toBe(false);
  });
});

describe('全部标签', () => {
  it('超出可见区域的标签才计入数量', () => {
    const box = { left: 0, right: 500 };
    expect(
      hiddenCount(box, [
        { left: 0, right: 100 },
        { left: 104, right: 480 },
        { left: 484, right: 600 },
        { left: 604, right: 700 },
      ]),
    ).toBe(2);
    expect(hiddenCount(box, [{ left: -0.5, right: 500.5 }])).toBe(0);
  });
});

describe('头像菜单', () => {
  it('显示姓名、角色、公司名和退出登录', async () => {
    render(h(App, null, h(UserMenu)));
    fireEvent.click(
      screen.getByRole('button', { name: '当前用户 王敏，打开菜单' }),
    );
    expect(await screen.findByText('退出登录')).toBeTruthy();
    expect(screen.getByText('管理员')).toBeTruthy();
    expect(screen.getAllByText('示例贸易有限公司').length).toBeGreaterThan(0);
  });
});
