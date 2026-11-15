import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import RecordsTab from './RecordsTab';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/receipts', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

/** UI-061：收款单翻页不能被表格的 onChange 重置回第 1 页 */
it('收款单点第 2 页后按第 2 页取数', async () => {
  const pages: number[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opts?: { data?: { page?: number } }) => {
      if (url.endsWith('/records')) {
        pages.push(opts?.data?.page ?? 0);
        return Promise.resolve({
          data: { records: [], total: 50, summary: [] },
        });
      }
      return Promise.resolve({ data: [] });
    },
  );
  render(React.createElement(App, null, React.createElement(RecordsTab)));
  const page2 = await screen.findByTitle('2');
  await act(async () => {
    fireEvent.click(page2);
  });
  expect(pages[pages.length - 1]).toBe(2);
  expect(
    screen.getByTitle('2').className.includes('ant-pagination-item-active'),
  ).toBe(true);
});
