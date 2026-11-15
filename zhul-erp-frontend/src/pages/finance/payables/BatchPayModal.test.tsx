import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { BatchPayModal } from './BatchPayModal';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/payables', search: '' }),
  useModel: () => ({ initialState: {} }),
}));
// 付款截图换成一个按钮：点一下就有一张截图
jest.mock('@/pages/warehouse/components', () => {
  const { createElement } = jest.requireActual('react');
  return {
    AttachmentWall: ({ onChange }: { onChange: (f: unknown[]) => void }) =>
      createElement(
        'button',
        {
          type: 'button',
          onClick: () => onChange([{ id: 9, fileName: '截图.png' }]),
        },
        '加截图',
      ),
  };
});

const payable = {
  id: 1,
  payableNo: 'AP001',
  payeeType: 1,
  supplierId: 10,
  payeeName: '华强电子',
  currencyCode: 'USD',
  unpaidAmount: 1000,
} as never;

const bank = (id: number, accountType: number) => ({
  id,
  accountType,
  currencyCode: 'USD',
  bankName: `银行${id}`,
  accountName: `账户${id}`,
  accountNoMasked: `**${id}`,
  isDefault: true,
  enabled: true,
});

const batches: { groups: { payeeAccountId?: number }[] }[] = [];
beforeEach(() => {
  batches.length = 0;
  (request as jest.Mock).mockImplementation(
    (url: string, opts?: { data?: unknown }) => {
      if (url.includes('/bank-accounts/options')) {
        return Promise.resolve({ data: [bank(11, 1), bank(12, 2)] });
      }
      if (url.includes('/payee-accounts')) {
        // 供应商只有对公收款账户
        return Promise.resolve({
          data: [
            {
              id: 21,
              accountType: 1,
              accountName: '华强电子有限公司',
              bankName: '招商银行',
              accountNo: '6214000011112222',
              defaultAccount: false,
            },
          ],
        });
      }
      if (url.endsWith('/batch')) {
        batches.push(opts?.data as { groups: { payeeAccountId?: number }[] });
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: [] });
    },
  );
});

const open = () =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(BatchPayModal, {
        rows: [payable],
        open: true,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );

/** UI-074：切到私对私后，原先选的对公收款账户不再提交 */
it('切换付款方式后不提交已隐藏的对方账户', async () => {
  open();
  const payee = await screen.findByText(
    '华强电子有限公司',
    {},
    { timeout: 5000 },
  );
  await act(async () => {
    fireEvent.click(payee);
  });
  expect(
    screen.getByRole('radio', { checked: true, name: /华强电子有限公司/ }),
  ).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByText('私对私'));
  });
  fireEvent.change(screen.getAllByRole('spinbutton')[1], {
    target: { value: '7100' },
  });
  await act(async () => {
    fireEvent.click(screen.getByText('加截图'));
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /确认付款/ }));
  });
  expect(batches).toHaveLength(1);
  expect(batches[0].groups[0].payeeAccountId).toBeUndefined();
});

/** UI-075：外币应付的逐笔未付按该组币种显示 */
it('外币应付的逐笔未付金额标该组币种', async () => {
  open();
  const row = (await screen.findByText('AP001', {}, { timeout: 5000 }))
    .parentElement as HTMLElement;
  expect(row.textContent).toBe('AP001USD 1,000.00');
});
