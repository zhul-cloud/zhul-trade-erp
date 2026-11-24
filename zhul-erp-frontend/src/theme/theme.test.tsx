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

/** WCAG 对比度 */
const contrast = (a: string, b: string) => {
  const lum = (h: string) => {
    const [r, g, bl] = [0, 1, 2].map((i) => {
      const c = parseInt(h.slice(1 + i * 2, 3 + i * 2), 16) / 255;
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

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
    expect(p.canvas).toBe('#F4F6F8');
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

  it('状态原色浅色、深色相同，浅底为原色 16% 叠在各自的卡片色上（照参考模板，R14）', () => {
    const l = buildPalette('light');
    const d = buildPalette('dark');
    for (const [fg, bg] of [
      ['greenMain', 'greenSoft'],
      ['orangeMain', 'orangeSoft'],
      ['redMain', 'redSoft'],
      ['infoMain', 'infoSoft'],
    ] as const) {
      expect(l[fg]).toBe(d[fg]);
      expect(near(l[bg], mix(l[fg], l.card, 0.16))).toBe(true);
      expect(near(d[bg], mix(d[fg], d.card, 0.16))).toBe(true);
    }
  });

  it('状态文字色：绿、信息在浅色、深色都直接用原色（R18，2026-11-24 用户看过对比图后决定），中性标签字不低于 4.5:1', () => {
    for (const m of ['light', 'dark'] as const) {
      const p = buildPalette(m);
      expect(p.green).toBe(p.greenMain);
      expect(p.info).toBe(p.infoMain);
      expect(contrast(p.neutral, p.neutralSoft)).toBeGreaterThanOrEqual(4.5);
    }
    expect(buildPalette('light').red).toBe('#FF5630');
    // 橙文字不能和琥珀链接色混在一起（R19）
    expect(buildPalette('light').orange).not.toBe(buildPalette('light').orangeMain);
  });

  it('主题色实底上的文字：每种主题色在浅色下都不低于 4.5:1（琥珀用深色字）', () => {
    for (const k of PRESET_ORDER) {
      const p = buildPalette('light', k);
      expect(contrast(p.onPrimary, p.primary)).toBeGreaterThanOrEqual(
        k === 'amber' ? 4.5 : 3,
      );
    }
    // 琥珀用同色相最深一档（D54，对比图 R17），不用偏蓝的近黑
    expect(buildPalette('light', 'amber').onPrimary).toBe('#4A2E00');
    expect(buildPalette('light', 'royal').onPrimary).toBe('#FFFFFF');
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
    expect(chartSeries('dark', 'amber')[0]).toBe('#FDA92D');
    expect(chartSeries('light', 'sky')[0]).toBe('#078DEE');
    expect(chartSeries('light', 'red')).not.toContain('#E8664A');
    expect(chartSeries('dark', 'purple')[0]).toBe('#A77EE9');
  });

  it('图表第一个系列跟主题色走、与 chartSeries 一致；除浅色琥珀（用户选定的例外）外不低于 3:1', () => {
    for (const k of PRESET_ORDER) {
      for (const m of ['light', 'dark'] as const) {
        const p = buildPalette(m, k);
        expect(p.chart).toBe(p.link);
        expect(chartSeries(m, k)[0]).toBe(p.chart);
        if (!(m === 'light' && k === 'amber'))
          expect(contrast(p.chart, p.card)).toBeGreaterThanOrEqual(3);
      }
    }
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

  it('危险实底按钮的字：浅色照参考模板原色白字不低于 3:1，深色亮红底用深色字不低于 4.5:1', () => {
    for (const mode of ['light', 'dark'] as const) {
      const p = buildPalette(mode, 'amber');
      const b = buildAntdTheme(mode, 'amber').components?.Button;
      const fg = String(b?.dangerColor);
      const min = mode === 'light' ? 3 : 4.5;
      expect(contrast(fg, p.red)).toBeGreaterThanOrEqual(min);
      expect(contrast(fg, String(b?.colorErrorHover))).toBeGreaterThanOrEqual(
        min,
      );
    }
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
