import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { ExpenseDrawer } from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/expenses', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

/** UI-018：事业部信息晚到时，只补默认事业部，不清空已经填写的名称 */
it('事业部信息晚到不重置已填写的费用单', async () => {
  let resolveUnits!: (v: unknown) => void;
  (request as jest.Mock).mockImplementation((url: string) => {
    if (url.includes('/system/business-units')) {
      return new Promise((r) => {
        resolveUnits = r;
      });
    }
    return Promise.resolve({ data: [] });
  });
  render(
    React.createElement(
      App,
      null,
      React.createElement(ExpenseDrawer, {
        open: true,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );
  const nameInput = await screen.findByPlaceholderText('如 补发运费');
  fireEvent.change(nameInput, { target: { value: 'AUDIT-DRAFT-NAME' } });
  expect((nameInput as HTMLInputElement).value).toBe('AUDIT-DRAFT-NAME');
  await act(async () => {
    resolveUnits({
      data: {
        enabled: true,
        myUnitId: 7,
        units: [{ id: 7, name: '阿里国际站' }],
      },
    });
  });
  expect(
    (screen.getByPlaceholderText('如 补发运费') as HTMLInputElement).value,
  ).toBe('AUDIT-DRAFT-NAME');
});

/** UI-026：自绘标签的字段要有可读名称，读屏能读出字段 */
it('费用单的输入框都有可读名称', async () => {
  (request as jest.Mock).mockResolvedValue({ data: [] });
  render(
    React.createElement(
      App,
      null,
      React.createElement(ExpenseDrawer, {
        open: true,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );
  expect(await screen.findByRole('textbox', { name: '名称' })).toBeTruthy();
  expect(screen.getByRole('spinbutton', { name: '金额' })).toBeTruthy();
  expect(screen.getByRole('textbox', { name: '说明' })).toBeTruthy();
  expect(screen.getByRole('combobox', { name: '币种' })).toBeTruthy();
  expect(screen.getByRole('combobox', { name: '费用类别' })).toBeTruthy();
  expect(screen.getByRole('textbox', { name: '收款方名称' })).toBeTruthy();
});

/** BUS-056：删除全部分摊后，不再提交已隐藏的关联售后单 */
it('删光分摊后保存不带关联售后单', async () => {
  const bodies: { afterSalesId?: number }[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opts?: { method?: string; data?: unknown }) => {
      if (url.includes('/sales/after-sales/order/')) {
        return Promise.resolve({
          data: [
            { id: 5, asNo: 'AS001', statusName: '处理中', symptom: '坏件' },
          ],
        });
      }
      if (opts?.method === 'PUT') {
        bodies.push(opts.data as { afterSalesId?: number });
        return Promise.resolve({ data: {} });
      }
      return Promise.resolve({ data: [] });
    },
  );
  const editing = {
    id: 9,
    expenseNo: 'FE001',
    afterSalesId: 5,
    name: '补寄运费',
    category: 'ORDER',
    amortizeMonths: 1,
    occurDate: '2026-11-01',
    payeeType: 4,
    payeeName: '顺丰',
    currencyCode: 'CNY',
    amount: 100,
    exchangeRate: 1,
    amountCny: 100,
    method: 1,
    status: 1,
    allocations: [
      { soId: 3, soItemId: 31, soNo: 'SO001', model: 'M-1', amount: 100 },
    ],
  };
  render(
    React.createElement(
      App,
      null,
      React.createElement(ExpenseDrawer, {
        open: true,
        editing: editing as never,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );
  expect(
    await screen.findByRole('combobox', { name: '关联售后单' }),
  ).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '删除这一行' }));
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  });
  expect(bodies).toHaveLength(1);
  expect(bodies[0].afterSalesId).toBeUndefined();
});
