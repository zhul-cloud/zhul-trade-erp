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
it('UI-093：待归类信号显示创建时间、创建人、更新时间、更新人，可改每页条数', () => {
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
  expect(at('创建时间')).toBeGreaterThan(at('来源单号'));
  expect(at('创建人')).toBe(at('创建时间') + 1);
  expect(at('更新时间')).toBe(at('创建人') + 1);
  expect(at('更新人')).toBe(at('更新时间') + 1);
  expect(at('操作')).toBe(at('更新人') + 1);
  expect(screen.getByText('张三')).toBeTruthy();
  expect(screen.getByText('2026-11-11 09:30:00')).toBeTruthy();
  expect(
    document.querySelector('.ant-pagination-options-size-changer'),
  ).not.toBeNull();
});
