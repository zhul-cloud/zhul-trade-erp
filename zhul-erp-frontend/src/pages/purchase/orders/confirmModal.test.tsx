import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ConfirmModal } from './detail';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
  useAccess: () => ({}),
  useParams: () => ({ id: '1' }),
  useLocation: () => ({ pathname: '/purchase/orders/1', search: '' }),
  useModel: () => ({ initialState: {} }),
  Link: (p: { children: unknown }) => p.children,
}));

const po = {
  id: 1,
  supplierName: '华强电子',
  items: [],
  expectedShipDate: null,
} as any;

const open = (defaultShipDate?: string | null) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(ConfirmModal, {
        open: true,
        po,
        total: 'CNY 100.00',
        missing: 0,
        zeroModels: [],
        defaultShipDate,
        onClose: () => undefined,
        onConfirm: () => Promise.resolve(),
      }),
    ),
  );

/** audit UI-100：页面上已填预计发货日期（还没保存）时，确认下单弹窗默认带出这个日期 */
it('确认下单默认带出页面上已填的预计发货日期', () => {
  open('2099-12-20');
  expect(
    (screen.getByLabelText('预计发货日期') as HTMLInputElement).value,
  ).toBe('2099-12-20');
});

it('页面上没填时预计发货日期为空', () => {
  open(null);
  expect(
    (screen.getByLabelText('预计发货日期') as HTMLInputElement).value,
  ).toBe('');
});
