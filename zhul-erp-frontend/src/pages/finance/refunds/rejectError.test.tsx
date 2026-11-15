import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import RequestsTab from './RequestsTab';
import RefundRequestDetail from './request-detail';
import { refundRequestApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: ({ children }: { children?: unknown }) => children ?? null,
  request: jest.fn().mockResolvedValue({ data: [] }),
  useAccess: () => ({ 'finance:refund:edit': true }),
  useLocation: () => ({ pathname: '/finance/refunds', search: '' }),
  useModel: () => ({ initialState: {} }),
  useParams: () => ({ id: '7' }),
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  refundRequestApi: {
    page: jest.fn(),
    counts: jest.fn(),
    detail: jest.fn(),
    reject: jest.fn(),
    rejectApproval: jest.fn(),
  },
}));

const api = refundRequestApi as unknown as Record<string, jest.Mock>;
const row = {
  id: 7,
  requestNo: 'RA001',
  soId: 3,
  soNo: 'SO001',
  customerName: 'ACME',
  currencyCode: 'USD',
  amount: 100,
  reason: 'QUALITY',
  reasonName: '质量问题',
  method: 3,
  methodName: '转客户余额',
  status: 2,
  statusName: '待财务处理',
  items: [],
  attachments: [],
  logs: [],
};
const STALE = '退款申请已执行，不能这样操作';

beforeEach(() => {
  api.page.mockResolvedValue({ total: 1, records: [row] });
  api.counts.mockResolvedValue({
    pendingReceipt: 0,
    pendingApproval: 0,
    pendingFinance: 1,
    approvalRequired: 0,
  });
  api.detail.mockResolvedValue(row);
  api.reject.mockRejectedValue({ response: { data: { message: STALE } } });
});

const reject = async () => {
  const buttons = await screen.findAllByRole(
    'button',
    { name: /驳\s*回/ },
    { timeout: 5000 },
  );
  await act(async () => {
    fireEvent.click(buttons[0]);
  });
  fireEvent.change(await screen.findByPlaceholderText('请填写原因'), {
    target: { value: '客户改成换货' },
  });
  const ok = screen
    .getAllByRole('button', { name: /驳\s*回/ })
    .find((b) => b.closest('.ant-modal')) as HTMLElement;
  await act(async () => {
    fireEvent.click(ok);
  });
};

/** UI-077：申请已被别人处理时，列表里驳回失败要提示原因 */
it('列表驳回失败时提示后端原因', async () => {
  render(React.createElement(App, null, React.createElement(RequestsTab)));
  await reject();
  expect(await screen.findByText(STALE)).toBeTruthy();
});

/** UI-077：详情页驳回失败同样提示 */
it('详情驳回失败时提示后端原因', async () => {
  render(
    React.createElement(App, null, React.createElement(RefundRequestDetail)),
  );
  await reject();
  expect(await screen.findByText(STALE)).toBeTruthy();
});
