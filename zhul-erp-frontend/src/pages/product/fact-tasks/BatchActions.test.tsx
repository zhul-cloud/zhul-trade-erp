import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { contentApi } from '../service';
import { BatchConfirmModal, BatchVerifyModal } from './BatchActions';

const h = React.createElement;

jest.mock('@umijs/max', () => ({ request: jest.fn() }));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  contentApi: { aiConfirmBatch: jest.fn(), verifyFactsBatch: jest.fn() },
}));

const row = (productId: number, mpn: string, aiStatus?: number) => ({
  productId,
  mpn,
  brandName: 'Siemens',
  status: 2,
  ai: aiStatus ? { id: productId * 10, status: aiStatus } : undefined,
});

beforeEach(() => jest.clearAllMocks());

/** openspec content-ai-batch-hs：只确认勾选里待确认的 AI 结果，写完逐个列出结果 */
it('批量确认 AI 结果：只提交待确认的，结果逐个列出', async () => {
  (contentApi.aiConfirmBatch as jest.Mock).mockResolvedValue({
    done: 1,
    partial: 1,
    failed: 0,
    approvedFaqs: 9,
    items: [
      {
        id: 10,
        label: '6ES7214',
        outcome: 'done',
        message: '资料包、营销包已写入',
      },
      {
        id: 20,
        label: 'ATV320',
        outcome: 'partial',
        message: '营销包：品牌型号对不上',
      },
    ],
  });
  const onDone = jest.fn();
  render(
    h(
      App,
      null,
      h(BatchConfirmModal, {
        rows: [
          row(1, '6ES7214', 3),
          row(2, 'ATV320', 3),
          row(3, 'E3Z', 4),
        ] as never,
        onClose: jest.fn(),
        onDone,
      }),
    ),
  );
  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).getByText('确认 2 个商品的 AI 结果？')).toBeTruthy();
  expect(within(dialog).getByText(/1 个不是待确认/)).toBeTruthy();
  await act(async () => {
    fireEvent.click(within(dialog).getByRole('button', { name: '确认写入' }));
  });
  expect(contentApi.aiConfirmBatch).toHaveBeenCalledWith([10, 20]);
  expect(within(dialog).getByText('营销包：品牌型号对不上')).toBeTruthy();
  expect(within(dialog).getByText('部分写入 1')).toBeTruthy();
  await act(async () => {
    fireEvent.click(within(dialog).getByRole('button', { name: /完\s*成/ }));
  });
  expect(onDone).toHaveBeenCalled();
});

it('批量资料已核：提交勾选的商品，跳过的说明原因', async () => {
  (contentApi.verifyFactsBatch as jest.Mock).mockResolvedValue({
    done: 1,
    partial: 0,
    failed: 1,
    approvedFaqs: 0,
    items: [
      { id: 1, label: '6ES7214', outcome: 'done', message: '资料已核' },
      {
        id: 2,
        label: 'ATV320',
        outcome: 'failed',
        message: '资料已核对，之后没有再导入资料包',
      },
    ],
  });
  render(
    h(
      App,
      null,
      h(BatchVerifyModal, {
        rows: [row(1, '6ES7214'), row(2, 'ATV320')] as never,
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).getByText('把 2 个商品标为资料已核？')).toBeTruthy();
  await act(async () => {
    fireEvent.click(within(dialog).getByRole('button', { name: '资料已核' }));
  });
  expect(contentApi.verifyFactsBatch).toHaveBeenCalledWith([1, 2]);
  expect(
    within(dialog).getByText('资料已核对，之后没有再导入资料包'),
  ).toBeTruthy();
});
