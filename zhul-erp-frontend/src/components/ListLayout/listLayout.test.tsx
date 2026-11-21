import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React, { useState } from 'react';
import {
  FilterCard,
  FilterField,
  HelpTip,
  PageHeader,
  QuickFilters,
  Section,
  SummaryBar,
} from './index';

const h = React.createElement;

describe('汇总行', () => {
  it('金额按币种显示', () => {
    render(
      h(SummaryBar, {
        items: [
          { label: '本月实收人民币', value: 386420, currency: 'CNY' },
          { label: '美元在途', value: '12860.5', currency: 'USD' },
          { label: '本月订单', value: 26 },
        ],
      }),
    );
    expect(screen.getByText('CNY 386,420.00')).toBeTruthy();
    expect(screen.getByText('USD 12,860.50')).toBeTruthy();
    expect(screen.getByText('26')).toBeTruthy();
  });

  it('口径写在帮助图标里', () => {
    render(
      h(SummaryBar, {
        items: [{ label: '进行中', value: 18, help: '未完成也未取消的订单' }],
      }),
    );
    expect(
      screen.getByRole('img', { name: '未完成也未取消的订单' }),
    ).toBeTruthy();
  });

  it('宽度不够时只隐藏可隐藏的副文字，整段隐藏', () => {
    const sw = Object.getOwnPropertyDescriptor(
      HTMLElement.prototype,
      'scrollWidth',
    );
    const cw = Object.getOwnPropertyDescriptor(
      HTMLElement.prototype,
      'clientWidth',
    );
    Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
      configurable: true,
      get: () => 900,
    });
    Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
      configurable: true,
      get: () => 600,
    });
    try {
      render(
        h(SummaryBar, {
          items: [
            {
              label: '本月实收人民币',
              value: 386420,
              currency: 'CNY',
              sub: '线上 CNY 120,300.00 · 线下 CNY 266,120.00',
            },
            {
              label: '进行中',
              value: 18,
              sub: '待采购 5 · 已下单 7',
              subHideable: true,
            },
          ],
        }),
      );
      expect(screen.queryByText('待采购 5 · 已下单 7')).toBeNull();
      expect(
        screen.getByText('线上 CNY 120,300.00 · 线下 CNY 266,120.00'),
      ).toBeTruthy();
    } finally {
      if (sw) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', sw);
      if (cw) Object.defineProperty(HTMLElement.prototype, 'clientWidth', cw);
    }
  });
});

describe('搜索条件区', () => {
  it('更多筛选显示生效数量，点开后显示其余条件', () => {
    render(
      h(
        App,
        null,
        h(FilterCard, {
          onSearch: jest.fn(),
          onReset: jest.fn(),
          more: h(FilterField, { label: '品牌', children: h('input', null) }),
          moreCount: 2,
          children: h(FilterField, {
            label: '关键词',
            span: 2,
            children: h('input', null),
          }),
        }),
      ),
    );
    const btn = screen.getByRole('button', { name: /更多筛选/ });
    expect(btn.textContent).toContain('· 2');
    expect(screen.queryByText('品牌')).toBeNull();
    fireEvent.click(btn);
    expect(btn.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText('品牌')).toBeTruthy();
    fireEvent.click(btn);
    expect(screen.queryByText('品牌')).toBeNull();
  });

  it('没有生效的更多条件时不显示数量；没有更多条件时不显示按钮', () => {
    const { rerender } = render(
      h(
        App,
        null,
        h(FilterCard, {
          onSearch: jest.fn(),
          onReset: jest.fn(),
          more: h('span', null, 'x'),
          moreCount: 0,
          children: null,
        }),
      ),
    );
    expect(
      screen.getByRole('button', { name: /更多筛选/ }).textContent,
    ).not.toContain('·');
    rerender(
      h(
        App,
        null,
        h(FilterCard, {
          onSearch: jest.fn(),
          onReset: jest.fn(),
          children: null,
        }),
      ),
    );
    expect(screen.queryByRole('button', { name: /更多筛选/ })).toBeNull();
  });

  it('重置、查询各自回调', () => {
    const onSearch = jest.fn();
    const onReset = jest.fn();
    render(h(App, null, h(FilterCard, { onSearch, onReset, children: null })));
    fireEvent.click(screen.getByRole('button', { name: /重\s*置/ }));
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
    expect(onReset).toHaveBeenCalledTimes(1);
    expect(onSearch).toHaveBeenCalledTimes(1);
  });
});

describe('快捷筛选', () => {
  const Demo: React.FC = () => {
    const [v, setV] = useState<string[]>([]);
    return h(QuickFilters, {
      options: [
        { value: 'negative', label: '毛利为负' },
        { value: 'unassigned', label: '未指定采购员' },
      ],
      value: v,
      onChange: setV,
    });
  };

  it('点一下选中，再点取消，可多选', () => {
    render(h(Demo));
    const a = screen.getByRole('button', { name: '毛利为负' });
    const b = screen.getByRole('button', { name: '未指定采购员' });
    expect(a.getAttribute('aria-pressed')).toBe('false');
    fireEvent.click(a);
    fireEvent.click(b);
    expect(a.getAttribute('aria-pressed')).toBe('true');
    expect(b.getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(a);
    expect(a.getAttribute('aria-pressed')).toBe('false');
    expect(b.getAttribute('aria-pressed')).toBe('true');
  });
});

describe('帮助图标', () => {
  it('鼠标悬停时弹出说明', async () => {
    jest.useFakeTimers();
    try {
      render(h(HelpTip, { title: '按销售日期统计' }));
      fireEvent.mouseEnter(screen.getByRole('img', { name: '按销售日期统计' }));
      await act(async () => {
        jest.advanceTimersByTime(500);
      });
      expect(screen.getByRole('tooltip').textContent).toContain(
        '按销售日期统计',
      );
    } finally {
      jest.useRealTimers();
    }
  });
});

describe('页头与分区', () => {
  it('页头显示标题和主操作；分区显示标题、帮助和右侧操作', () => {
    render(
      h(
        'div',
        null,
        h(PageHeader, {
          title: '销售订单',
          extra: h('button', null, '新建销售订单'),
        }),
        h(Section, {
          title: '订单毛利',
          help: '收入减实际成本',
          extra: h('button', null, '编辑'),
          children: h('p', null, '内容'),
        }),
      ),
    );
    expect(
      screen.getByRole('heading', { level: 1, name: '销售订单' }),
    ).toBeTruthy();
    expect(
      screen.getByRole('heading', { level: 2, name: '订单毛利' }),
    ).toBeTruthy();
    expect(screen.getByRole('img', { name: '收入减实际成本' })).toBeTruthy();
    expect(screen.getByRole('button', { name: '编辑' })).toBeTruthy();
  });
});
