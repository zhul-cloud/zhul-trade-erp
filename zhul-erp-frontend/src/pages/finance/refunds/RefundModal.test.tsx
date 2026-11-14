import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import RefundModal from './RefundModal';
import { refundApi, refundRequestApi } from './service';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  history: { push: jest.fn() },
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  refundApi: { refundable: jest.fn(), create: jest.fn() },
  refundRequestApi: { execute: jest.fn() },
  afterSalesOptions: jest.fn().mockResolvedValue([]),
}));
jest.mock('@/utils/dict', () => ({
  useDictTexts: () => [{ code: 'QUALITY', name: '质量问题' }],
}));
// 凭证墙换成一个按钮：点一下就有一张凭证
jest.mock('@/pages/warehouse/components', () => {
  const { createElement } = jest.requireActual('react');
  return {
    AttachmentWall: ({ onChange }: { onChange: (f: unknown[]) => void }) =>
      createElement(
        'button',
        {
          type: 'button',
          onClick: () => onChange([{ id: 9, fileName: '回单.pdf' }]),
        },
        '加凭证',
      ),
  };
});

const refundable = refundApi.refundable as jest.Mock;
const execute = refundRequestApi.execute as jest.Mock;

const info = (
  soId: number,
  soNo: string,
  currencyCode: string,
  rate: number,
) => ({
  soId,
  soNo,
  currencyCode,
  received: 100,
  refunded: 0,
  refundable: 100,
  averageRate: rate,
  creditRate: rate,
  platformOnly: false,
});

const request = (id: number, soId: number, currencyCode: string): any => ({
  id,
  requestNo: `RA${id}`,
  soId,
  currencyCode,
  amount: 10,
  reason: 'QUALITY',
  reasonName: '质量问题',
  method: 2,
  methodName: '银行汇款',
  applicantName: '业务员',
});

/** 受控的请求：按测试指定的顺序返回 */
const deferred = <T,>() => {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((ok, fail) => {
    resolve = ok;
    reject = fail;
  });
  return { promise, resolve, reject };
};

const h = React.createElement;
const view = (req: any, open: boolean) =>
  h(
    App,
    null,
    h(RefundModal, {
      soId: req?.soId,
      open,
      request: req,
      onClose: () => {},
      onDone: () => {},
    }),
  );

beforeEach(() => {
  refundable.mockReset();
  execute.mockReset();
});

/** audit UI-054：申请 A 的查询迟到时丢弃，弹窗的币种、换算与提交都属于申请 B */
it('上一张申请的响应迟到，不覆盖当前申请的币种与人民币换算', async () => {
  const a = deferred<unknown>();
  const b = deferred<unknown>();
  refundable.mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise);
  const A = request(1, 101, 'EUR');
  const B = request(2, 202, 'GBP');
  const r = render(view(A, true));
  r.rerender(view(A, false));
  r.rerender(view(B, true));

  await act(async () => b.resolve(info(202, 'SO-B', 'GBP', 9)));
  await act(async () => a.resolve(info(101, 'SO-A', 'EUR', 7)));

  expect(await screen.findByText(/SO-B/)).toBeTruthy();
  expect(screen.queryByText(/SO-A/)).toBeNull();
  expect(
    (
      screen.getByRole('spinbutton', {
        name: '实际退出人民币',
      }) as HTMLInputElement
    ).value,
  ).toBe('90.00');

  execute.mockResolvedValue({ refundNo: 'TK1' });
  fireEvent.click(screen.getByRole('button', { name: '加凭证' }));
  fireEvent.click(screen.getByRole('button', { name: /执行并生成退款单/ }));
  await waitFor(() => expect(execute).toHaveBeenCalled());
  expect(execute).toHaveBeenCalledWith(
    2,
    expect.objectContaining({ amount: 10, amountCny: 90, method: 2 }),
  );
});

it('上一张申请的请求迟到失败，不提示错误', async () => {
  const a = deferred<unknown>();
  refundable
    .mockReturnValueOnce(a.promise)
    .mockResolvedValueOnce(info(202, 'SO-B', 'GBP', 9));
  const A = request(1, 101, 'EUR');
  const B = request(2, 202, 'GBP');
  const r = render(view(A, true));
  r.rerender(view(A, false));
  r.rerender(view(B, true));
  expect(await screen.findByText(/SO-B/)).toBeTruthy();
  await act(async () => a.reject(new Error('链接已过期')));
  expect(screen.queryByText('链接已过期')).toBeNull();
});
