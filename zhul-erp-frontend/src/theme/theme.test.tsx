import { act, render, screen } from '@testing-library/react';
import React from 'react';
import { DefaultLogo } from '@/components/Shell/BrandLogo';
import { FAVICON_COLOR } from '@/utils/appearance';
import { buildAntdTheme } from './antd';
import {
  buildPalette,
  CHART_SERIES,
  chartSeries,
  mix,
  PRESET_ORDER,
  PRESETS,
} from './palette';
import { setThemeMode, setThemePreset } from './store';

jest.mock('@umijs/max', () => ({
  useModel: () => ({ initialState: {} }),
}));

/** 两个颜色每个通道相差不超过 1（取整误差） */
const near = (a: string, b: string) =>
  [0, 1, 2].every(
    (i) =>
      Math.abs(
        parseInt(a.slice(1 + i * 2, 3 + i * 2), 16) -
          parseInt(b.slice(1 + i * 2, 3 + i * 2), 16),
      ) <= 1,
  );

/** jsdom 把样式里的十六进制颜色读成 rgb() */
const rgb = (hex: string) =>
  `rgb(${[0, 1, 2].map((i) => parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16)).join(', ')})`;

afterEach(() => {
  act(() => {
    setThemeMode('light');
    setThemePreset('amber');
  });
});

describe('设计令牌', () => {
  it('浅色琥珀：主按钮、链接都是原色，选中浅底为原色 12%', () => {
    const p = buildPalette('light', 'amber');
    expect(p.primary).toBe('#FDA92D');
    expect(p.link).toBe('#FDA92D');
    expect(p.primarySoft).toBe('#FFF5E6');
    expect(p.canvas).toBe('#F9FAFB');
    expect(p.sidebar).toBe(p.card);
  });

  it('深色下链接用提亮的深色链接色，主按钮仍是原色', () => {
    const p = buildPalette('dark', 'royal');
    expect(p.primary).toBe('#2065D1');
    expect(p.link).toBe('#5E94E6');
    expect(p.primarySoft).toBe('#1B2637');
    expect(p.canvas).toBe('#0A0A0A');
    expect(p.card).toBe('#1A1A1A');
  });

  it('6 种主题色的浅底按规则叠出：浅色 12% 叠白，深色 16% 叠 #1A1A1A', () => {
    for (const k of PRESET_ORDER) {
      expect(PRESETS[k].lightSoft).toBe(
        mix(PRESETS[k].primary, '#FFFFFF', 0.12),
      );
      expect(PRESETS[k].darkSoft).toBe(
        mix(PRESETS[k].primary, '#1A1A1A', 0.16),
      );
    }
  });

  it('状态色浅色、深色相同，浅底分别叠在各自的卡片色上', () => {
    const l = buildPalette('light');
    const d = buildPalette('dark');
    for (const [fg, bg] of [
      ['green', 'greenSoft'],
      ['orange', 'orangeSoft'],
      ['red', 'redSoft'],
      ['info', 'infoSoft'],
    ] as const) {
      expect(l[fg]).toBe(d[fg]);
      expect(near(l[bg], mix(l[fg], l.card, 0.12))).toBe(true);
      expect(near(d[bg], mix(d[fg], d.card, 0.16))).toBe(true);
    }
  });

  it('不认识的主题色回到默认琥珀，原型链上的键也不认', () => {
    expect(buildPalette('light', 'nope' as any).primary).toBe('#FDA92D');
    expect(buildPalette('light', 'toString' as any).primary).toBe('#FDA92D');
    expect(chartSeries('light', 'constructor' as any)[0]).toBe('#FDA92D');
  });

  it('图表第一个系列为主题色，去掉色相最接近的系列色', () => {
    const s = chartSeries('light', 'amber');
    expect(s[0]).toBe('#FDA92D');
    expect(s).not.toContain('#B87A14');
    expect(s).toHaveLength(CHART_SERIES.length);
    expect(chartSeries('light', 'red')).not.toContain('#E8664A');
    expect(chartSeries('dark', 'purple')[0]).toBe('#A77EE9');
  });

  it('mix 计算正确', () => {
    expect(mix('#000000', '#FFFFFF', 0.5)).toBe('#808080');
    expect(mix('#FDA92D', '#FFFFFF', 0)).toBe('#FFFFFF');
    expect(mix('#FDA92D', '#FFFFFF', 1)).toBe('#FDA92D');
  });
});

describe('Ant Design 主题', () => {
  it('主色跟随主题色，深色用深色算法', () => {
    const t = buildAntdTheme('dark', 'purple');
    expect(t.token?.colorPrimary).toBe('#7635DC');
    expect(t.token?.colorLink).toBe('#A77EE9');
    expect(t.token?.borderRadius).toBe(8);
    expect(t.token?.borderRadiusLG).toBe(10);
    expect(t.components?.Input?.controlHeight).toBe(40);
    expect(t.token?.controlHeight).toBe(36);
  });
});

describe('主题色保存在本机', () => {
  it('切换后写入浏览器存储', () => {
    act(() => setThemePreset('teal'));
    expect(localStorage.getItem('zhul_theme_preset')).toBe('teal');
  });

  it('存储里是不认识的值时用琥珀', () => {
    localStorage.setItem('zhul_theme_preset', 'blue');
    jest.isolateModules(() => {
      const store = require('./store');
      expect(store.getThemePreset()).toBe('amber');
    });
  });

  it('读存储出错时用琥珀', () => {
    const real = globalThis.localStorage;
    const denied = {
      getItem: () => {
        throw new Error('denied');
      },
    };
    Object.defineProperty(globalThis, 'localStorage', {
      value: denied,
      configurable: true,
    });
    try {
      jest.isolateModules(() => {
        const store = require('./store');
        expect(store.getThemePreset()).toBe('amber');
      });
    } finally {
      Object.defineProperty(globalThis, 'localStorage', {
        value: real,
        configurable: true,
      });
    }
  });
});

describe('默认 Logo 与浏览器标签图标', () => {
  it('默认 Logo 跟随主题色，深色用深色链接色', () => {
    render(React.createElement(DefaultLogo, { siteName: '烛龙ERP', size: 32 }));
    const logo = () => screen.getByRole('img', { name: '烛龙ERP' });
    expect(logo().style.background).toBe(rgb('#FDA92D'));
    act(() => {
      setThemeMode('dark');
      setThemePreset('royal');
    });
    expect(logo().style.background).toBe(rgb('#5E94E6'));
    expect(logo().textContent).toBe('烛');
  });

  it('浏览器标签图标固定琥珀，不随主题色变化', () => {
    act(() => setThemePreset('purple'));
    expect(FAVICON_COLOR).toBe('#FDA92D');
  });
});
