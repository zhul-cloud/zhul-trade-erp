import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { FlowStrip } from './index';

jest.mock('@umijs/max', () => ({
  Link: () => null,
  useLocation: () => ({ pathname: '/' }),
  useModel: () => ({ initialState: {} }),
}));

const h = React.createElement;

const renderStrip = (onSelect = jest.fn()) =>
  render(
    h(FlowStrip, {
      title: '跟进板块',
      loop: '复购回到第 2 步',
      active: 'today',
      onSelect,
      lead: {
        key: 'today',
        name: '今日待办',
        icon: null,
        value: 2,
        unit: '位客户',
        sub: '逾期 1 · 今天 1',
      },
      steps: [
        { key: '1', name: '主动开发', icon: null, step: 1, value: 0 },
        { key: '2', name: '商机成交', icon: null, step: 2, value: 3 },
        { key: '3', name: '订单履约', icon: null, step: 3 },
      ],
    }),
  );

describe('流程卡片条', () => {
  it('当前卡片标记为按下，其余未按下', () => {
    renderStrip();
    expect(
      screen
        .getByRole('button', { name: /今日待办/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      screen
        .getByRole('button', { name: /商机成交/ })
        .getAttribute('aria-pressed'),
    ).toBe('false');
  });

  it('没有数字的步骤显示「即将上线」且不能点', () => {
    const onSelect = jest.fn();
    renderStrip(onSelect);
    const card = screen.getByRole('button', { name: /订单履约/ });
    expect((card as HTMLButtonElement).disabled).toBe(true);
    expect(card.textContent).toContain('即将上线');
    fireEvent.click(card);
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('点卡片回传它的 key', () => {
    const onSelect = jest.fn();
    renderStrip(onSelect);
    fireEvent.click(screen.getByRole('button', { name: /商机成交/ }));
    expect(onSelect).toHaveBeenCalledWith('2');
  });

  it('箭头只画在步骤之间：入口卡与第 1 步之间、每行第一张不画', () => {
    const { container } = renderStrip();
    // 测试环境量不到宽度，四张排一行：第 2、3 步前各一个箭头
    expect(container.querySelectorAll('svg.lucide-chevron-right')).toHaveLength(
      2,
    );
    expect(screen.getByText('复购回到第 2 步')).toBeTruthy();
  });
});
