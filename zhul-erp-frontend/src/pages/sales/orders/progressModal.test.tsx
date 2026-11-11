import { render } from '@testing-library/react';
import React from 'react';
import { ProgressModal } from './dialogs';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/orders/1', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

/** 费用订单没有型号：详情页常驻的「更新进度」弹窗不能因为型号为空而让整页报错 */
it('费用订单（没有型号）渲染更新进度弹窗不报错', () => {
  const order = { id: 1, items: [], steps: [{ code: 'NEW', name: '新建' }] };
  expect(() =>
    render(
      React.createElement(ProgressModal, {
        order,
        itemIds: [],
        open: false,
        onClose: () => undefined,
        onDone: () => undefined,
      } as never),
    ),
  ).not.toThrow();
});
