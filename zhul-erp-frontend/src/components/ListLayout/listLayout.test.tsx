import { act, fireEvent, render, screen } from '@testing-library/react';
import { App, Input } from 'antd';
import React, { useState } from 'react';
import {
  FilterCard,
  FilterField,
  HelpTip,
  MainCard,
  MoreActions,
  PageHeader,
  QuickFilters,
  Section,
  SummaryBar,
} from './index';

jest.mock('@umijs/max', () => ({
  Link: () => null,
  useLocation: () => ({ pathname: '/sales/orders' }),
  useModel: () => ({
    initialState: {
      menuTree: [
        {
          name: '销售管理',
          type: 1,
          children: [{ name: '销售订单', type: 2, path: '/sales/orders' }],
        },
      ],
    },
  }),
}));

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

  it('宽度够时副文字都显示；内容变短后重新测量，恢复显示', () => {
    let width = 500;
    const sw = jest
      .spyOn(Element.prototype, 'scrollWidth', 'get')
      .mockImplementation(() => width);
    const cw = jest
      .spyOn(Element.prototype, 'clientWidth', 'get')
      .mockReturnValue(600);
    const items = (sub: string) => [
      { label: '进行中', value: 18, sub, subHideable: true },
      { label: '本月订单', value: 26 },
    ];
    try {
      const { rerender } = render(h(SummaryBar, { items: items('待采购 5') }));
      expect(screen.queryByText('待采购 5')).toBeNull();
      width = 200;
      rerender(h(SummaryBar, { items: items('待采购 4') }));
      expect(screen.getByText('待采购 4')).toBeTruthy();
    } finally {
      sw.mockRestore();
      cw.mockRestore();
    }
  });

  it('宽度不够时只隐藏可隐藏的副文字，整段隐藏', () => {
    // jsdom 把这两个属性定义在 Element.prototype 上；用 spyOn 才能在用例结束后还原
    const sw = jest
      .spyOn(Element.prototype, 'scrollWidth', 'get')
      .mockReturnValue(500);
    const cw = jest
      .spyOn(Element.prototype, 'clientWidth', 'get')
      .mockReturnValue(600);
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
      sw.mockRestore();
      cw.mockRestore();
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
    const hidden = () =>
      (screen.getByText('品牌').closest('[id]') as HTMLElement).style.display;
    expect(hidden()).toBe('none');
    expect(btn.getAttribute('aria-controls')).toBeTruthy();
    fireEvent.click(btn);
    expect(btn.getAttribute('aria-expanded')).toBe('true');
    expect(hidden()).toBe('contents');
    fireEvent.click(btn);
    expect(hidden()).toBe('none');
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
  const Demo = () => {
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
  it('列表页的页头不传标题时取当前页面的菜单名称', () => {
    render(h(PageHeader, {}));
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(
      '销售订单',
    );
  });

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

describe('更多操作', () => {
  it('没有可显示的操作时不渲染「更多」按钮', () => {
    render(
      h(MoreActions, { items: [false, undefined, ''], onClick: jest.fn() }),
    );
    expect(screen.queryByRole('button', { name: '更多操作' })).toBeNull();
  });

  it('点开菜单：不可用的操作置灰并写明原因，可用的点击后回传 key', async () => {
    const onClick = jest.fn();
    render(
      h(
        App,
        null,
        h(MoreActions, {
          items: [
            { key: 'unclaim', label: '取消认领' },
            {
              key: 'void',
              label: '作废',
              danger: true,
              disabledReason: '已结汇，请先作废结汇单 JH001',
            },
          ],
          onClick,
        }),
      ),
    );
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '更多操作' }));
    });
    const voidItem = (
      await screen.findByText('已结汇，请先作废结汇单 JH001')
    ).closest('li') as HTMLElement;
    expect(voidItem.getAttribute('aria-disabled')).toBe('true');
    await act(async () => {
      fireEvent.click(voidItem);
    });
    expect(onClick).not.toHaveBeenCalled();
    await act(async () => {
      fireEvent.click(screen.getByText('取消认领'));
    });
    expect(onClick).toHaveBeenCalledWith('unclaim');
  });
});

it('更多菜单：危险操作前有普通操作时，用分隔线隔开', async () => {
  render(
    h(
      App,
      null,
      h(MoreActions, {
        items: [
          { key: 'unclaim', label: '取消认领' },
          { key: 'void', label: '作废', danger: true },
        ],
        onClick: jest.fn(),
      }),
    ),
  );
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '更多操作' }));
  });
  await screen.findByText('作废');
  const menu = document.querySelector('.ant-dropdown-menu') as HTMLElement;
  const kinds = [...menu.children].map((li) =>
    li.classList.contains('ant-dropdown-menu-item-divider')
      ? '—'
      : li.textContent,
  );
  expect(kinds).toEqual(['取消认领', '—', '作废']);
});

it('统计页的搜索条件区不传查询、重置时不显示这两个按钮', () => {
  render(
    h(FilterCard, {
      children: h(FilterField, {
        label: '统计期间',
        children: h('input', { 'aria-label': '统计期间' }),
      }),
    }),
  );
  expect(screen.getByRole('region', { name: '搜索条件' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: /查\s*询/ })).toBeNull();
  expect(screen.queryByRole('button', { name: /重\s*置/ })).toBeNull();
});

describe('更多筛选的条件胶囊', () => {
  const Page = (props: {
    onSearch: (kw: string, code: string) => void;
    name: string;
  }) => {
    const [kw, setKw] = useState('');
    const [code, setCode] = useState('');
    return h(
      'div',
      null,
      h(FilterCard, {
        onSearch: () => props.onSearch(kw, code),
        more: h(FilterField, {
          label: '编码',
          children: h(Input, {
            'aria-label': `${props.name}编码`,
            value: code,
            onChange: (e: { target: { value: string } }) =>
              setCode(e.target.value),
          }),
        }),
        children: h(FilterField, {
          label: '关键词',
          children: h(Input, {
            'aria-label': `${props.name}关键词`,
            value: kw,
            onChange: (e: { target: { value: string } }) =>
              setKw(e.target.value),
          }),
        }),
      }),
      h(MainCard, { children: h('div', null, '表格') }),
    );
  };
  const toggleMore = (i = 0) =>
    fireEvent.click(screen.getAllByRole('button', { name: /更多筛选/ })[i]);
  const query = (i = 0) =>
    fireEvent.click(screen.getAllByRole('button', { name: /查\s*询/ })[i]);

  it('改了没查询不出胶囊；查询后收起显示，点 × 清空文本框并用新值查询', async () => {
    const onSearch = jest.fn();
    render(h(App, null, h(Page, { onSearch, name: '' })));
    toggleMore();
    fireEvent.change(screen.getByLabelText('编码'), {
      target: { value: 'A1' },
    });
    toggleMore();
    expect(screen.queryByText('编码：A1')).toBeNull();
    query();
    expect(await screen.findByText('编码：A1')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('关键词'), {
      target: { value: 'kw' },
    });
    onSearch.mockClear();
    fireEvent.click(screen.getByRole('button', { name: '去掉条件 编码：A1' }));
    await act(async () => {});
    expect(screen.queryByText('编码：A1')).toBeNull();
    // 用最新的关键词，不回到加胶囊时的旧值
    expect(onSearch).toHaveBeenLastCalledWith('kw', '');
  });

  it('同一页两组搜索条件区和主卡互不串', async () => {
    render(
      h(
        App,
        null,
        h('div', null, h(Page, { onSearch: () => {}, name: '甲' })),
        h('div', null, h(Page, { onSearch: () => {}, name: '乙' })),
      ),
    );
    toggleMore(1);
    fireEvent.change(screen.getByLabelText('乙编码'), {
      target: { value: 'B2' },
    });
    query(1);
    toggleMore(1);
    expect(await screen.findAllByText('编码：B2')).toHaveLength(1);
    const cards = document.querySelectorAll('[data-zhul-filter]');
    expect(cards[1].parentElement?.textContent).toContain('编码：B2');
    expect(cards[0].parentElement?.textContent).not.toContain('编码：B2');
  });
});

it('名称作前缀后，文本框不再重复名称、不写「全部」；关键词的搜索范围保留', () => {
  render(
    h(App, null, [
      h(FilterCard, {
        key: 'f',
        onSearch: () => {},
        children: [
          h(FilterField, {
            key: 'a',
            label: '客户编码',
            children: h(Input, { 'aria-label': 'a', placeholder: '客户编码' }),
          }),
          h(FilterField, {
            key: 'b',
            label: '主要联系人',
            children: h(Input, {
              'aria-label': 'b',
              placeholder: '联系人姓名',
            }),
          }),
          h(FilterField, {
            key: 'c',
            label: '角色编码',
            children: h(Input, { 'aria-label': 'c', placeholder: '全部' }),
          }),
          h(FilterField, {
            key: 'd',
            label: '关键词',
            children: h(Input, {
              'aria-label': 'd',
              placeholder: '客户、订单编号',
            }),
          }),
        ],
      }),
    ]),
  );
  for (const k of ['a', 'b', 'c'])
    expect(screen.getByLabelText(k).getAttribute('placeholder')).toBeNull();
  expect(screen.getByLabelText('d').getAttribute('placeholder')).toBe(
    '客户、订单编号',
  );
});
