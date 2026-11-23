import { act, render, screen } from '@testing-library/react';
import React from 'react';
import { DeltaText, deltaColors } from './delta';
import { buildPalette } from './palette';
import { setDeltaPref } from './store';

const h = React.createElement;
const rgb = (hex: string) =>
  `rgb(${[0, 1, 2].map((i) => parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16)).join(', ')})`;

afterEach(() => {
  act(() => setDeltaPref('redUp'));
});

describe('涨跌颜色', () => {
  const p = buildPalette('light');

  it('默认涨红跌绿：上涨、盈利为红，下跌、亏损为绿', () => {
    const c = deltaColors(p, 'redUp');
    expect(c.of(12.5)).toBe(p.red);
    expect(c.of(24256)).toBe(p.red);
    expect(c.of(-3.7)).toBe(p.green);
    expect(c.of(-860)).toBe(p.green);
  });

  it('涨绿跌红：方向反过来', () => {
    const c = deltaColors(p, 'greenUp');
    expect(c.of(6)).toBe(p.green);
    expect(c.of(-6)).toBe(p.red);
  });

  it('零值和空值不算涨跌，用正文色', () => {
    for (const pref of ['redUp', 'greenUp'] as const) {
      const c = deltaColors(p, pref);
      expect(c.of(0)).toBe(p.sub);
      expect(c.of(null)).toBe(p.sub);
      expect(c.of(undefined)).toBe(p.sub);
    }
  });

  it('深色下用深色的文字色', () => {
    const d = buildPalette('dark');
    expect(deltaColors(d, 'redUp').up).toBe(d.red);
    expect(d.red).toBe('#FF6A47');
  });

  it('变化配上下箭头，跟着设置换色', () => {
    const { container } = render(
      h(DeltaText, { value: 12.5 }, '较上月 +12.5%'),
    );
    const el = screen.getByText('较上月 +12.5%');
    expect(el.style.color).toBe(rgb(p.red));
    expect(container.querySelector('svg')).toBeTruthy();
    act(() => setDeltaPref('greenUp'));
    expect(screen.getByText('较上月 +12.5%').style.color).toBe(rgb(p.green));
  });

  it('没有变化时不显示箭头', () => {
    const { container } = render(h(DeltaText, { value: 0 }, '较上月 0%'));
    expect(container.querySelector('svg')).toBeNull();
  });
});
