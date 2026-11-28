import { act, fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { buildPalette } from '@/theme/palette';
import { BoardContext, layoutRows } from './layout';
import { workbenchApi } from './service';
import { WIDGETS } from './widgets';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/dashboard', search: '' }),
  useModel: () => ({ initialState: { menuTree: [] } }),
}));

jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  workbenchApi: { widget: jest.fn() },
}));

const renderWidget = async (key: string, data: unknown) => {
  (workbenchApi.widget as jest.Mock).mockResolvedValue(data);
  const W = WIDGETS[key];
  await act(async () => {
    render(React.createElement(App, null, React.createElement(W)));
  });
};

beforeEach(() => jest.clearAllMocks());

/** ui-redesign 6.2：工作台只改外观，后端返回的数字、说明、跳转原样显示 */
it('我的待办：每项名称、说明、数量与跳转不变', async () => {
  await renderWidget('my-todo', {
    blocks: [
      {
        type: 'todos',
        todos: [
          {
            icon: 'inbox',
            tone: 'violet',
            title: '待确认的询盘',
            desc: 'AI 解析完成，等你核对型号',
            count: 3,
            alert: true,
            link: '/inquiry/customer-inquiries?status=3',
          },
          {
            icon: 'wallet',
            tone: 'orange',
            title: 'PI 待收款',
            desc: '已发给客户，还没付清',
            count: 4,
            link: '/sales/pi?receipt=open',
          },
        ],
      },
    ],
  });
  expect(screen.getByRole('heading', { name: '我的待办' })).toBeTruthy();
  const row = screen.getByRole('button', { name: /待确认的询盘/ });
  expect(row.textContent).toContain('AI 解析完成，等你核对型号');
  expect(row.textContent).toContain('3');
  fireEvent.click(row);
  expect(history.push).toHaveBeenCalledWith(
    '/inquiry/customer-inquiries?status=3',
  );
  expect(
    screen.getByRole('button', { name: /PI 待收款/ }).textContent,
  ).toContain('4');
});

it('我的业绩：金额、较上月与口径帮助', async () => {
  await renderWidget('my-performance', {
    blocks: [
      {
        type: 'metrics',
        metrics: [
          {
            label: '成交额',
            value: 412860,
            format: 'money',
            delta: 9.6,
            link: '/sales/orders',
          },
          { label: '成交订单', value: 26, format: 'count', delta: -4.3 },
          { label: '毛利率', value: null, format: 'percent', hint: '没有成交' },
        ],
      },
    ],
  });
  expect(screen.getByText('本月')).toBeTruthy();
  expect(
    screen.getByRole('img', { name: '按你的数据范围统计，口径同销售统计' }),
  ).toBeTruthy();
  // 数字卡：币种代码与数值分开显示，较上月为涨跌胶囊（ui-redesign C3）
  expect(screen.getByText('412,860.00')).toBeTruthy();
  expect(screen.getByText('+9.6%')).toBeTruthy();
  expect(screen.getByText('26')).toBeTruthy();
  expect(screen.getByText('−4.3%')).toBeTruthy();
  expect(screen.getAllByText('较上月')).toHaveLength(2);
  // 没有数据的指标显示「—」和后端给的说明
  expect(screen.getByText('—')).toBeTruthy();
  expect(screen.getByText('没有成交')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: /成交额/ }));
  expect(history.push).toHaveBeenCalledWith('/sales/orders');
});

it('表格没有数据时显示后端给的空状态文字', async () => {
  await renderWidget('rankings', {
    blocks: [
      {
        type: 'table',
        title: '业务员 · 本月成交额',
        table: {
          columns: [
            { title: '业务员', align: 'l' },
            { title: '成交额', align: 'r', format: 'money' },
          ],
          rows: [],
          empty: '本月还没有成交',
        },
      },
    ],
  });
  expect(screen.getByText('本月还没有成交')).toBeTruthy();
});

/** 规范「工作台按行排列、同一行等高」（ui-redesign D18、D19）：按模板顺序装进 12 栏的行 */
it('业务员看板按行：待办与过期 PI 一行，业绩整行，询价进度与商机一行', () => {
  const rows = layoutRows([
    { key: 'my-todo', size: 'half' },
    { key: 'overdue-pi', size: 'half' },
    { key: 'my-performance', size: 'full' },
    { key: 'quote-progress', size: 'half' },
    { key: 'my-opportunities', size: 'half' },
  ]);
  expect(rows.map((r) => r.map((w) => `${w.key}:${w.span}`))).toEqual([
    ['my-todo:6', 'overdue-pi:6'],
    ['my-performance:12'],
    ['quote-progress:5', 'my-opportunities:7'],
  ]);
});

it('老板看板：有主角卡时经营概览半宽，没有趋势权限时经营概览整行', () => {
  const half = (key: string) => ({ key, size: 'half' });
  expect(
    layoutRows(
      [
        'revenue-trend',
        'biz-overview',
        'cash',
        'risk-alerts',
        'sales-funnel',
        'rankings',
      ].map(half),
    ).map((r) => r.map((w) => w.span)),
  ).toEqual([
    [6, 6],
    [6, 6],
    [4, 8],
  ]);
  expect(
    layoutRows(['biz-overview', 'cash', 'risk-alerts'].map(half)).map((r) =>
      r.map((w) => `${w.key}:${w.span}`),
    ),
  ).toEqual([['biz-overview:12'], ['cash:6', 'risk-alerts:6']]);
});

/** UI-108：其他岗位看板按原型 I1–I4 装行（模板顺序见后端 V1.5.65 与 WorkbenchContractTest） */
it('业务负责人、采购负责人、运营负责人、采购员看板按原型装行', () => {
  const rowsOf = (keys: string[]) =>
    layoutRows(keys.map((key) => ({ key }))).map((r) =>
      r.map((w) => `${w.key}:${w.span}`),
    );
  expect(
    rowsOf([
      'team-performance',
      'follow-ups',
      'quote-conversion',
      'sales-compare',
      'opportunity-stats',
    ]),
  ).toEqual([
    ['team-performance:12'],
    ['follow-ups:6', 'quote-conversion:6'],
    ['sales-compare:12'],
    ['opportunity-stats:12'],
  ]);
  expect(
    rowsOf([
      'sourcing-overview',
      'buyer-efficiency',
      'purchase-execution',
      'bargain-result',
      'payables',
    ]),
  ).toEqual([
    ['sourcing-overview:12'],
    ['buyer-efficiency:6', 'purchase-execution:6'],
    ['bargain-result:6', 'payables:6'],
  ]);
  expect(
    rowsOf([
      'content-pipeline',
      'platform-progress',
      'media-overview',
      'monthly-output',
    ]),
  ).toEqual([
    ['content-pipeline:12'],
    ['platform-progress:6', 'media-overview:6'],
    ['monthly-output:12'],
  ]);
  expect(
    rowsOf(['my-inquiry', 'quote-stats', 'quote-trend', 'my-purchase']),
  ).toEqual([
    ['my-inquiry:12'],
    ['quote-stats:4', 'quote-trend:8'],
    ['my-purchase:12'],
  ]);
});

it('落单的组件补满整行，不留空洞', () => {
  // 没有风险提醒权限时，资金单独一行占满
  expect(
    layoutRows([
      { key: 'cash' },
      { key: 'sales-funnel' },
      { key: 'rankings' },
    ]).map((r) => r.map((w) => `${w.key}:${w.span}`)),
  ).toEqual([['cash:12'], ['sales-funnel:4', 'rankings:8']]);
});

const rgb = (hex: string) => {
  const n = Number.parseInt(hex.slice(1), 16);
  return `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`;
};
const LIGHT = buildPalette('light');

const alarmTodo = (count: number) => ({
  blocks: [
    {
      type: 'todos',
      todos: [
        {
          icon: 'alarm-clock',
          tone: 'red',
          title: '售后要更新客户',
          desc: '已逾期或 1 小时内到期的售后单',
          count,
          alert: true,
          link: '/sales/after-sales',
        },
      ],
    },
  ],
});

const chipColor = () =>
  (
    screen
      .getByRole('button', { name: /售后要更新客户/ })
      .querySelector('span') as HTMLElement
  ).style.color;

/** 规范「没有告警时图标不标红」 */
it('数量为 0 的告警待办图标为中性色', async () => {
  await renderWidget('my-todo', alarmTodo(0));
  expect(chipColor()).toBe(rgb(LIGHT.sub));
});

it('数量大于 0 的告警待办图标为错误色', async () => {
  await renderWidget('my-todo', alarmTodo(2));
  expect(chipColor()).toBe(rgb(LIGHT.red));
});

/** ui-redesign D16：漏斗比例超过 100% 不显示 */
it('销售漏斗：比例超过 100% 不显示，底部一句结论', async () => {
  await renderWidget('sales-funnel', {
    blocks: [
      {
        type: 'funnel',
        steps: [
          { label: '询盘', value: 6, rate: null },
          { label: '报价', value: 12, rate: 200 },
          { label: '成交订单', value: 3, rate: 25 },
        ],
      },
    ],
  });
  expect(screen.queryByText('→ 200%')).toBeNull();
  expect(screen.getByText('→ 25%')).toBeTruthy();
  expect(screen.getByText(/本月询盘 6，成交订单 3/)).toBeTruthy();
});

const risk = (counts: number[]) => ({
  blocks: [
    {
      type: 'todos',
      todos: [
        '过期未收款 PI',
        '超期未到货',
        '未处理的到货差异',
        '毛利为负的订单',
      ].map((title, i) => ({
        icon: 'warning',
        tone: 'red',
        title,
        desc: '',
        count: counts[i],
        alert: true,
        link: '/x',
      })),
    },
  ],
});

/** ui-redesign D20：全为 0 时四格保留，标题旁「全部正常」；有风险时写需要处理的项数 */
it('风险提醒：全为 0 显示全部正常，有风险时写项数，四格都在', async () => {
  await renderWidget('risk-alerts', risk([0, 0, 0, 0]));
  expect(screen.getByText(/全部正常 · 截至/)).toBeTruthy();
  expect(screen.getAllByRole('button')).toHaveLength(4);
});

it('风险提醒：有 2 项时写「2 项需要处理」', async () => {
  await renderWidget('risk-alerts', risk([2, 1, 0, 0]));
  expect(screen.getByText(/2 项需要处理 · 截至/)).toBeTruthy();
});

/** ui-redesign D28：同一看板有主角卡时，经营概览不重复显示成交额 */
it('经营概览：看板上有主角卡时去掉成交额', async () => {
  (workbenchApi.widget as jest.Mock).mockResolvedValue({
    blocks: [
      {
        type: 'metrics',
        metrics: [
          { label: '成交额', value: 100, format: 'money' },
          { label: '毛利', value: 20, format: 'money' },
        ],
      },
    ],
  });
  const W = WIDGETS['biz-overview'];
  await act(async () => {
    render(
      React.createElement(
        BoardContext.Provider,
        { value: new Set(['revenue-trend', 'biz-overview']) },
        React.createElement(W),
      ),
    );
  });
  expect(screen.queryByText('成交额')).toBeNull();
  expect(screen.getByText('毛利')).toBeTruthy();
});

/** 设计评审（9.1 第 3 批）：近 12 个月没有成交时不画「1 / 1 / 0」的刻度，写一句说明 */
it('主角卡：近 12 个月都没有成交时只留 0 基线并说明', async () => {
  await renderWidget('revenue-trend', {
    blocks: [
      {
        type: 'metrics',
        metrics: [{ label: '成交额', value: 0, format: 'money' }],
      },
      {
        type: 'bars',
        bars: { labels: ['10月', '11月'], values: [0, 0], format: 'money' },
        text: '近 12 个月合计 CNY 0.00',
      },
    ],
  });
  expect(screen.getByText('近 12 个月还没有成交')).toBeTruthy();
  expect(screen.queryByText('1')).toBeNull();
});

/** 档位固定：出错时用一行的空状态加「重试」，不撑高卡片 */
it('组件加载失败时一行提示加重试，只影响自己', async () => {
  (workbenchApi.widget as jest.Mock).mockRejectedValue(new Error('服务不可用'));
  const W = WIDGETS.cash;
  await act(async () => {
    render(React.createElement(App, null, React.createElement(W)));
  });
  expect(screen.getByRole('alert').textContent).toContain('加载失败');
  expect(screen.getByRole('button', { name: /重\s*试/ })).toBeTruthy();
});
