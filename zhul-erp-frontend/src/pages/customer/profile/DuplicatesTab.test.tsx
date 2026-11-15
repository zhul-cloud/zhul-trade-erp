import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { MergeModal } from './DuplicatesTab';
import { profileApi } from './service';

const h = React.createElement;

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  profileApi: { mergePreview: jest.fn(), merge: jest.fn() },
}));

const member = (id: number, name: string) => ({
  id,
  customerCode: `CU${id}`,
  name,
  country: 'AU',
  ownerId: 1,
  ownerName: '张伟',
  profileDone: 2,
  inquiryCount: 0,
  orderCount: 0,
});

/** audit UI-056（补修）：对调保留方后，先发出的旧方向预览晚到，不能覆盖当前方向的预览 */
it('对调保留方：丢弃旧方向迟到的合并预览', async () => {
  const pending = new Map<string, (p: unknown) => void>();
  (profileApi.mergePreview as jest.Mock).mockImplementation(
    (targetId: number, sourceId: number) =>
      new Promise((resolve) => pending.set(`${targetId}<${sourceId}`, resolve)),
  );
  render(
    h(
      App,
      null,
      h(MergeModal, {
        group: {
          reason: 'MANUAL',
          reasonText: '',
          members: [member(1, 'Alpha Ltd'), member(2, 'Beta Ltd')],
        },
        index: 0,
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  fireEvent.click(await screen.findByLabelText('对调保留和被合并'));
  // 新方向（Beta 保留）先返回，旧方向（Alpha 保留）后到
  await act(async () => {
    pending.get('2<1')?.({
      documents: { 销售订单: 1 },
      contacts: [],
      filledFields: [],
    });
  });
  await act(async () => {
    pending.get('1<2')?.({
      documents: { 销售订单: 9 },
      contacts: [],
      filledFields: [],
    });
  });
  expect(screen.getByText(/1 张销售订单改挂到 Beta Ltd/)).toBeTruthy();
  expect(screen.queryByText(/9 张销售订单/)).toBeNull();
});
