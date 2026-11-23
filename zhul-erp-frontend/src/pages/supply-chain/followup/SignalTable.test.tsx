import { render, screen } from '@testing-library/react';
import React from 'react';
import { SignalTable } from './SignalTable';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  useLocation: () => ({ pathname: '/supply-chain/followup' }),
}));
// 销售跟进的样式文件在模块顶层写 JSX，测试环境用不到箭头，直接替换
jest.mock('@/pages/sales/followup/FlowChain', () => ({ Arrow: () => null }));

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

/** UI-093：待归类信号是分页业务列表，固定显示四个审计列，每页条数可改 */
it('UI-093：待归类信号显示「创建」「更新」两列，可改每页条数', async () => {
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
  const heads = screen
    .getAllByRole('columnheader')
    .map((h) => h.textContent ?? '');
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
