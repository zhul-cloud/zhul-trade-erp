import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import PayableManagement from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({ 'finance:payment:register': true }),
  useLocation: () => ({ pathname: '/finance/payables', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const ap = (unpaidAmount: number, status: number) => ({
  id: 1,
  payableNo: 'AP001',
  payeeType: 1,
  supplierId: 10,
  payeeName: '华强电子',
  currencyCode: 'USD',
  amount: 1000,
  paidAmount: 1000 - unpaidAmount,
  unpaidAmount,
  status,
  statusName: status === 3 ? '已付清' : '未付',
});

/** UI-076：列表刷新后，已付清的应付从已选里剔除，已选合计按最新未付 */
it('刷新后已选的应付随列表更新', async () => {
  let current = ap(1000, 1);
  (request as jest.Mock).mockImplementation((url: string) => {
    if (url === '/api/v1/finance/payables') {
      return Promise.resolve({ data: { total: 1, records: [current] } });
    }
    return Promise.resolve({ data: url.endsWith('/stats') ? {} : [] });
  });
  render(
    React.createElement(App, null, React.createElement(PayableManagement)),
  );
  const no = await screen.findByText('AP001', {}, { timeout: 5000 });
  const rowBox = within(no.closest('tr') as HTMLElement).getByRole('checkbox');
  await act(async () => {
    fireEvent.click(rowBox);
  });
  expect(screen.getByText(/已选 1 张/)).toBeTruthy();
  // 别处把这张付清后刷新列表
  current = ap(0, 3);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  });
  await act(async () => {
    await new Promise((r) => setTimeout(r, 50));
  });
  expect(screen.queryByText(/已选 1 张/)).toBeNull();
});
