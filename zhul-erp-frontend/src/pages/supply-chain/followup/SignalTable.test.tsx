import { render, screen } from '@testing-library/react';
import React from 'react';
import { SignalTable } from './SignalTable';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  useLocation: () => ({ pathname: '/supply-chain/followup' }),
}));

const signal = {
  id: 1,
  sourceType: 4,
  modelRaw: 'ABC-1',
  occurredAt: '2026-11-10 10:00:00',
  createTime: '2026-11-10 10:00:00',
  createBy: '张三',
  updateTime: '2026-11-11 09:30:00',
  updateBy: '李四',
};

const props = {
  rows: [signal],
  total: 30,
  page: 1,
  pageSize: 20,
  loading: false,
  picked: [],
  canEdit: true,
  onPick: jest.fn(),
  onPage: jest.fn(),
  onClassify: jest.fn(),
  onIgnore: jest.fn(),
};
const headers = () =>
  screen.getAllByRole('columnheader').map((h) => h.textContent ?? '');

afterEach(() => localStorage.clear());

/** ui-redesign D55：「创建」「更新」默认收进列设置，有「列」按钮可打开 */
it('「创建」「更新」默认收起，工具栏有列设置按钮', () => {
  render(React.createElement(SignalTable, props as never));
  expect(headers()).not.toContain('创建');
  expect(screen.getByRole('button', { name: /列设置/ })).toBeTruthy();
});

/** UI-093：待归类信号是分页业务列表，打开审计列后显示人名和时间，每页条数可改 */
it('UI-093：打开「创建」「更新」两列后显示在操作列之前，可改每页条数', async () => {
  localStorage.setItem(
    'zhul_cols:v1:anon:supply-chain-signals',
    JSON.stringify({ order: [], shown: ['create', 'update'], hidden: [] }),
  );
  render(
    React.createElement(SignalTable, {
      rows: [signal],
      total: 30,
      page: 1,
      pageSize: 20,
      loading: false,
      picked: [],
      canEdit: true,
      onPick: jest.fn(),
      onPage: jest.fn(),
      onClassify: jest.fn(),
      onIgnore: jest.fn(),
    } as never),
  );
  const heads = headers();
  const at = (t: string) => heads.indexOf(t);
  // ui-redesign D25：「创建」「更新」两列（人名 + 短时间，悬停看完整时间）
  expect(at('创建')).toBeGreaterThan(at('来源单号'));
  expect(at('更新')).toBe(at('创建') + 1);
  expect(at('操作')).toBe(at('更新') + 1);
  expect((await screen.findAllByText('张三')).length).toBeGreaterThan(0);
  expect(screen.getByText('11-11 09:30')).toBeTruthy();
  expect(
    document.querySelector('.ant-pagination-options-size-changer'),
  ).not.toBeNull();
});
