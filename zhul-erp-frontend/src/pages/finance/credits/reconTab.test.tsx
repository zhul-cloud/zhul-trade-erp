import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { ReconTab } from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/credits', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const pair = { amount: 1, amountCny: 7 };
const recon = (name: string, currencyCode: string) => ({
  month: '2026-10',
  currencyCode,
  balanced: true,
  rows: [
    {
      customerId: 1,
      customerName: name,
      opening: pair,
      refundIn: pair,
      collectionIn: pair,
      overpaidIn: pair,
      adjust: pair,
      deduct: pair,
      withdraw: pair,
      closing: pair,
    },
  ],
  total: {
    customerName: '合计',
    opening: pair,
    refundIn: pair,
    collectionIn: pair,
    overpaidIn: pair,
    adjust: pair,
    deduct: pair,
    withdraw: pair,
    closing: pair,
  },
});

/** UI-078：切换币种后，迟到的旧币种结果不能覆盖新币种的数据 */
it('月度核对丢弃切换前迟到的响应', async () => {
  let resolveUsd!: (v: unknown) => void;
  (request as jest.Mock).mockImplementation(
    (url: string, opts?: { params?: { currencyCode?: string } }) => {
      if (url.endsWith('/reconciliation')) {
        if (opts?.params?.currencyCode === 'USD') {
          return new Promise((r) => {
            resolveUsd = r;
          });
        }
        return Promise.resolve({ data: recon('RUB 客户', 'RUB') });
      }
      return Promise.resolve({ data: [] });
    },
  );
  render(
    React.createElement(
      App,
      null,
      React.createElement(ReconTab, { unitsEnabled: false, units: [] }),
    ),
  );
  const [currencySelect] = screen.getAllByRole('combobox').slice(-1);
  await act(async () => {
    fireEvent.mouseDown(currencySelect);
  });
  await act(async () => {
    fireEvent.click(await screen.findByTitle('RUB'));
  });
  expect(await screen.findByText('RUB 客户')).toBeTruthy();
  await act(async () => {
    resolveUsd({ data: recon('USD 客户', 'USD') });
  });
  expect(screen.queryByText('USD 客户')).toBeNull();
  expect(screen.getByText('RUB 客户')).toBeTruthy();
});
