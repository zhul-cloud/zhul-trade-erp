import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { searchCustomers } from '@/services/zhul/masterdata';
import AfterSalesPage from './index';
import { afterSalesApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useLocation: () => ({ pathname: '/sales/after-sales', search: '' }),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams()],
}));
jest.mock('@/services/zhul/masterdata', () => ({ searchCustomers: jest.fn() }));
jest.mock('@/utils/dict', () => ({
  useDictTexts: (type: string) =>
    type === 'after_sales_reason'
      ? [
          { code: 'QUALITY', name: '产品质量' },
          { code: 'TRANSPORT', name: '运输包装' },
        ]
      : [],
}));
jest.mock('./dialogs', () => ({
  CreateAfterSalesModal: () => null,
  useOwnerOptions: () => [],
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  afterSalesApi: { page: jest.fn(), counts: jest.fn() },
}));

const lastQuery = () => {
  const calls = (afterSalesApi.page as jest.Mock).mock.calls;
  return calls[calls.length - 1][0];
};

/** audit UI-067：spec「售后管理页」的客户、原因筛选 */
it('按客户与原因筛选', async () => {
  (afterSalesApi.page as jest.Mock).mockResolvedValue({
    total: 0,
    records: [],
  });
  (afterSalesApi.counts as jest.Mock).mockResolvedValue({});
  (searchCustomers as jest.Mock).mockResolvedValue([
    { id: 5, name: 'Siam Automation' },
  ]);
  render(React.createElement(App, null, React.createElement(AfterSalesPage)));
  await waitFor(() => expect(afterSalesApi.page).toHaveBeenCalled());

  // ui-redesign 7.1 起：原因在「更多筛选」里，选好后点「查询」生效
  fireEvent.click(screen.getByRole('button', { name: /更多筛选/ }));
  fireEvent.mouseDown(await screen.findByLabelText('原因'));
  fireEvent.click(await screen.findByText('产品质量'));
  fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ reasonCode: 'QUALITY', page: 1 }),
  );

  const customer = screen.getByLabelText('客户');
  fireEvent.focus(customer);
  fireEvent.mouseDown(customer);
  fireEvent.click(await screen.findByText('Siam Automation'));
  fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ customerId: 5, reasonCode: 'QUALITY' }),
  );
});
