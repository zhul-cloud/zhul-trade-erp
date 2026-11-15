import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { AdjustModal } from './CreditModals';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/credits', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

/** BUS-057：启用事业部时，从页面顶部手工调整必须选事业部，不能落到无事业部账户 */
it('启用事业部时手工调整不选事业部不能提交', async () => {
  const adjusted: unknown[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opts?: { data?: unknown }) => {
      if (url.includes('/system/business-units')) {
        return Promise.resolve({
          data: { enabled: true, units: [{ id: 7, name: '阿里国际站' }] },
        });
      }
      if (url.includes('/page')) {
        return Promise.resolve({
          data: { records: [{ id: 1, name: 'ACME' }], total: 1 },
        });
      }
      if (url.endsWith('/adjust')) {
        adjusted.push(opts?.data);
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: [] });
    },
  );
  render(
    React.createElement(
      App,
      null,
      React.createElement(AdjustModal, {
        open: true,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );
  await screen.findByText('事业部');
  const [customerSelect] = screen.getAllByRole('combobox');
  await act(async () => {
    fireEvent.focus(customerSelect);
    fireEvent.mouseDown(customerSelect);
  });
  await act(async () => {
    fireEvent.click(await screen.findByText('ACME'));
  });
  fireEvent.change(screen.getByLabelText('调整金额'), {
    target: { value: '100' },
  });
  fireEvent.change(screen.getByLabelText('人民币价值'), {
    target: { value: '715' },
  });
  fireEvent.change(screen.getByPlaceholderText(/请说明为什么调整/), {
    target: { value: '旧 ERP 余额迁移' },
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /确认调整/ }));
  });
  expect(adjusted).toHaveLength(0);
  expect(await screen.findByText('请选择事业部')).toBeTruthy();
});
