import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ReviewDrawer } from './index';
import { candidateApi } from './service';

/**
 * spec product/product-candidate「候选审核」：已被订单引用的不能驳回；
 * 改型号后与已建档商品重复时先提示并入，确认后带 confirmMerge 再提交。
 */
jest.mock('@umijs/max', () => ({
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({ 'product:candidate:review': true }),
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  brandApi: {
    options: jest.fn(() => Promise.resolve([{ id: 8, brandName: 'Siemens' }])),
  },
  categoryApi: { options: jest.fn(() => Promise.resolve([])) },
  seriesApi: { options: jest.fn(() => Promise.resolve([])) },
  productApi: { search: jest.fn(() => Promise.resolve([])) },
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  candidateApi: { detail: jest.fn(), approve: jest.fn() },
}));

const candidate = {
  id: 5,
  brandId: 8,
  brandName: 'Siemens',
  brandText: 'Siemens',
  mpnRaw: '6ES7214-0XBO',
  categoryId: 3,
  categoryName: 'PLC',
  status: 1,
  statusName: '待审核',
  level: 3,
  levelName: '已成交',
  inquiryCount: 1,
  dealCount: 1,
  productId: 900,
  references: { 销售订单: 1 },
  usedByOrders: true,
  createTime: '2026-11-20 10:00:00',
};

const renderDrawer = (onDone = jest.fn()) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(ReviewDrawer, {
        id: 5,
        canReview: true,
        onClose: () => undefined,
        onDone,
      }),
    ),
  );

beforeEach(() => {
  (candidateApi.detail as jest.Mock).mockReset().mockResolvedValue(candidate);
  (candidateApi.approve as jest.Mock).mockReset();
});

it('已被订单引用：驳回不可用', async () => {
  renderDrawer();
  const reject = await screen.findByRole('button', { name: /驳\s*回/ });
  expect((reject as HTMLButtonElement).disabled).toBe(true);
});

it('改型号后与已建档商品重复：确认并入后带 confirmMerge 再提交', async () => {
  (candidateApi.approve as jest.Mock)
    .mockRejectedValueOnce({
      message: '商品库已有这个型号',
      info: {
        data: {
          errorCode: 'CANDIDATE_WILL_MERGE',
          detail: {
            targetId: 77,
            targetLabel: 'Siemens · 6ES7214-0XB0',
            references: { 销售订单: 1 },
          },
        },
      },
    })
    .mockResolvedValueOnce({});
  const onDone = jest.fn();
  renderDrawer(onDone);
  fireEvent.click(await screen.findByRole('button', { name: /通过建档/ }));
  expect(
    (await screen.findAllByText('商品库已有 Siemens · 6ES7214-0XB0')).length,
  ).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: /^并\s*入$/ }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect((candidateApi.approve as jest.Mock).mock.calls[1][1]).toMatchObject({
    confirmMerge: true,
  });
});
