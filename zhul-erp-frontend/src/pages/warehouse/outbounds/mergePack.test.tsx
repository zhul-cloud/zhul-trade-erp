import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { shortNos } from '@/pages/logistics/components';
import { outboundApi } from '@/pages/logistics/service';
import { PackDrawer } from './dialogs';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/warehouse/outbounds', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('@/pages/logistics/service', () => ({
  ...jest.requireActual('@/pages/logistics/service'),
  outboundApi: { detail: jest.fn(), pack: jest.fn(), mergePack: jest.fn() },
}));

const outbound = (
  id: number,
  obNo: string,
  model: string,
  quantity: number,
  extra: object = {},
) => ({
  id,
  obNo,
  soNo: `SO-${id}`,
  soId: id,
  customerId: 1,
  customerName: 'Hoorain HTF Trading',
  forwarderId: 1,
  forwarderName: '深圳捷运',
  source: 1,
  sourceName: '发货通知',
  status: 1,
  statusName: '待打包',
  totalQuantity: quantity,
  items: [{ id: id * 10, soItemId: id, model, quantity }],
  boxes: [],
  packingAttachments: [],
  ...extra,
});

const view = (props: { id?: number; mergeIds?: number[] }) =>
  React.createElement(
    App,
    null,
    React.createElement(PackDrawer, {
      ...props,
      onClose: () => undefined,
      onDone: () => undefined,
    }),
  );

const fill = (label: string, index: number, value: string) =>
  fireEvent.change(screen.getAllByLabelText(label)[index], {
    target: { value },
  });

beforeEach(() => jest.clearAllMocks());

it('单号简称取末尾最少 3 位且组内不重复', () => {
  const m = shortNos(['OB20261116001', 'OB20261116002']);
  expect(m.get('OB20261116001')).toBe('OB…001');
  expect(
    shortNos(['OB20261116001', 'OB20261117001']).get('OB20261116001'),
  ).toBe('OB…6001');
});

it('合并打包：型号按出库单分组，没装完提示带单号，两箱分装后一起提交', async () => {
  (outboundApi.detail as jest.Mock).mockImplementation((id: number) =>
    Promise.resolve(
      id === 1
        ? outbound(1, 'OB20261116001', 'MODEL-A', 10)
        : outbound(2, 'OB20261116002', 'MODEL-B', 6),
    ),
  );
  (outboundApi.mergePack as jest.Mock).mockResolvedValue([]);
  await act(async () => {
    render(view({ mergeIds: [1, 2] }));
  });

  expect(screen.getByText(/合并打包 · 2 张销售出库单/)).toBeTruthy();
  expect(screen.getByText('OB20261116001')).toBeTruthy();
  expect(screen.getByText('OB20261116002')).toBeTruthy();
  // 第 1 箱默认装 OB…001 的型号
  expect(screen.getAllByText('OB…001').length).toBeGreaterThan(0);

  fill('长', 0, '55');
  fill('宽', 0, '40');
  fill('高', 0, '30');
  fill('毛重', 0, '30');
  expect(
    screen.getByText('OB20261116002 的 MODEL-B 还差 6 个没装箱'),
  ).toBeTruthy();
  const done = screen.getByRole('button', { name: /完成合并打包/ });
  expect((done as HTMLButtonElement).disabled).toBe(true);

  // 添加一箱：自动带上还没装完的 OB…002 型号
  fireEvent.click(screen.getByRole('button', { name: /添加一箱/ }));
  fill('长', 1, '40');
  fill('宽', 1, '30');
  fill('高', 1, '30');
  fill('毛重', 1, '6');
  expect((done as HTMLButtonElement).disabled).toBe(false);

  await act(async () => {
    fireEvent.click(done);
  });
  expect(outboundApi.mergePack).toHaveBeenCalledTimes(1);
  const [ids, boxes] = (outboundApi.mergePack as jest.Mock).mock.calls[0];
  expect(ids).toEqual([1, 2]);
  expect(boxes.map((b: { items: unknown[] }) => b.items)).toEqual([
    [{ key: 10, quantity: 10 }],
    [{ key: 20, quantity: 6 }],
  ]);
  expect(outboundApi.pack).not.toHaveBeenCalled();
});

it('合并打包了的单修改装箱：同组的单一起打开，按整组提交', async () => {
  const groupBoxes = [
    {
      id: 7,
      boxNo: 1,
      length: 30,
      width: 30,
      height: 30,
      grossWeight: 5,
      chargeable: 5.4,
      items: [
        {
          outboundItemId: 10,
          outboundId: 1,
          obNo: 'OB20261116001',
          model: 'MODEL-A',
          quantity: 2,
        },
        {
          outboundItemId: 20,
          outboundId: 2,
          obNo: 'OB20261116002',
          model: 'MODEL-B',
          quantity: 3,
        },
      ],
    },
  ];
  (outboundApi.detail as jest.Mock).mockImplementation((id: number) =>
    Promise.resolve(
      id === 1
        ? outbound(1, 'OB20261116001', 'MODEL-A', 2, {
            status: 2,
            packId: 9,
            packMates: [{ id: 2, obNo: 'OB20261116002', status: 2 }],
            boxes: groupBoxes,
          })
        : outbound(2, 'OB20261116002', 'MODEL-B', 3, {
            status: 2,
            packId: 9,
            packMates: [{ id: 1, obNo: 'OB20261116001', status: 2 }],
            boxes: groupBoxes,
          }),
    ),
  );
  (outboundApi.pack as jest.Mock).mockResolvedValue({});
  await act(async () => {
    render(view({ id: 1 }));
  });

  expect(outboundApi.detail).toHaveBeenCalledWith(2);
  expect(screen.getByText(/修改装箱 · 合并打包 2 张/)).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /完成打包/ }));
  });
  const [id, boxes] = (outboundApi.pack as jest.Mock).mock.calls[0];
  expect(id).toBe(1);
  expect(boxes[0].items).toEqual([
    { key: 10, quantity: 2 },
    { key: 20, quantity: 3 },
  ]);
});
