import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PoolTab, PutIntoPoolModal } from './pool';
import { customerApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/customer/list', search: '' }),
  useModel: () => ({ initialState: {} }),
  Link: (p: { children: unknown }) => p.children,
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  customerApi: {
    ...jest.requireActual('./service').customerApi,
    putIntoPool: jest.fn(() => Promise.resolve({})),
    pool: jest.fn(),
    claim: jest.fn(() => Promise.resolve({})),
  },
}));

const wrap = (el: any) => render(React.createElement(App, null, el));

/** 放入公海原因必填；点常用原因后填进输入框（feedback-1116-batch2） */
it('放入公海原因必填', async () => {
  const onDone = jest.fn();
  wrap(
    React.createElement(PutIntoPoolModal, {
      open: true,
      customers: [{ id: 5, name: 'Delta Industrial', ownerName: '张伟' }],
      onClose: () => undefined,
      onDone,
    }),
  );
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /确认放入/ }));
  });
  expect((await screen.findAllByText('请填写放入原因')).length).toBeGreaterThan(
    0,
  );
  expect(customerApi.putIntoPool).not.toHaveBeenCalled();
  await act(async () => {
    fireEvent.click(screen.getByText('长期无回复'));
  });
  fireEvent.change(screen.getByRole('textbox'), {
    target: { value: '长期无回复：半年没回' },
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /确认放入/ }));
  });
  expect(customerApi.putIntoPool).toHaveBeenCalledWith(
    [5],
    '长期无回复：半年没回',
  );
  expect(onDone).toHaveBeenCalled();
});

it('公海列表点认领后调用认领', async () => {
  (customerApi.pool as jest.Mock).mockResolvedValue({
    total: 1,
    records: [
      {
        id: 7,
        displayName: 'Kurt Elektrik',
        name: 'Kurt Elektrik',
        customerCode: 'CUS01102',
        country: 'Turkey',
        inPool: true,
        poolReason: '业务员离职',
        lastOwnerName: '王强',
      },
    ],
  });
  wrap(React.createElement(PoolTab));
  expect(await screen.findByText('业务员离职')).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '认领' }));
  });
  await act(async () => {
    const buttons = await screen.findAllByRole('button', { name: /^认\s*领$/ });
    // 最后一个是确认框里的「认领」
    fireEvent.click(buttons[buttons.length - 1]);
  });
  expect(customerApi.claim).toHaveBeenCalledWith(7);
});
