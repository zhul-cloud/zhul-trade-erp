import { act, fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { buildPalette } from '@/theme/palette';
import { assignColumns } from './index';
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
  expect(screen.getByText('CNY 412,860.00')).toBeTruthy();
  expect(screen.getByText(/较上月 \+9\.6%/)).toBeTruthy();
  expect(screen.getByText('26')).toBeTruthy();
  expect(screen.getByText(/较上月 -4\.3%/)).toBeTruthy();
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

/** 规范「工作台两列按内容高度排列」：业务员看板（模板顺序照 V1.3.5）左列待办、过期 PI，右列业绩、询价进度、商机 */
it('业务员看板分两列：左列待办、过期 PI，右列业绩、询价进度、商机', () => {
  const half = (key: string) => ({ key, size: 'half' });
  const cols = assignColumns(
    [
      'my-todo',
      'my-performance',
      'quote-progress',
      'overdue-pi',
      'my-opportunities',
    ].map(half),
    2,
  );
  const left = cols.filter((c) => c.column === 0).map((c) => c.key);
  const right = cols.filter((c) => c.column === 1).map((c) => c.key);
  expect(left).toEqual(['my-todo', 'overdue-pi']);
  expect(right).toEqual([
    'my-performance',
    'quote-progress',
    'my-opportunities',
  ]);
  // 窄屏只有一列
  expect(assignColumns(['my-todo', 'overdue-pi'].map(half), 1)).toEqual([
    { key: 'my-todo', column: 0 },
    { key: 'overdue-pi', column: 0 },
  ]);
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
