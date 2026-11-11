import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React, { useState } from 'react';
import { AccountListEditor, toAccountPayload } from './accounts';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useModel: () => ({ initialState: {} }),
}));

const ID_NO = '110101199001010011';

let latest: unknown[] = [];
const Host = ({ initial }: { initial: unknown[] }) => {
  const [rows, setRows] = useState(initial);
  latest = rows;
  return React.createElement(AccountListEditor, {
    value: rows as never,
    onChange: setRows as never,
    supplierName: '深圳华强',
  });
};

const editAndSave = async (fill: (dialog: HTMLElement) => void) => {
  await act(async () => {
    fireEvent.click(screen.getByText('编辑'));
  });
  const dialog = await screen.findByRole('dialog');
  fill(dialog);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  });
  await act(async () => {
    await new Promise((r) => setTimeout(r, 50));
  });
};

const input = (dialog: HTMLElement, label: string) =>
  dialog.querySelector(`#${label}`) as HTMLInputElement;

/** UI-027：对私账户先填身份证号，再只改开户行，供应商保存时仍带本轮填的号码 */
it.each([
  ['已有账户', 5],
  ['新账户', undefined],
])('%s二次编辑不丢身份证号', async (_, id) => {
  render(
    React.createElement(
      App,
      null,
      React.createElement(Host, {
        initial: [
          {
            key: 'a',
            id,
            accountType: 2,
            accountName: '张三',
            bankName: '招商银行',
            accountNo: '6214000000000001',
            payeePhone: '13800000000',
            payeeIdNoMasked: id ? '110101********0000' : '',
            defaultAccount: true,
          },
        ],
      }),
    ),
  );
  await editAndSave((d) => {
    fireEvent.change(input(d, 'payeeIdNo'), { target: { value: ID_NO } });
  });
  await editAndSave((d) => {
    fireEvent.change(input(d, 'bankName'), {
      target: { value: '招商银行上海张江支行' },
    });
  });
  const payload = toAccountPayload(latest as never);
  expect(payload?.[0].bankName).toBe('招商银行上海张江支行');
  expect(payload?.[0].payeeIdNo).toBe(ID_NO);
});
