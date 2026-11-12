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
