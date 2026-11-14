import { fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import { OrderFilesCard } from './OrderFiles';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/orders/1', search: '' }),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('@/components/PageTabs/store', () => ({
  tabsActions: { setDirty: jest.fn() },
}));

const { tabsActions } = jest.requireMock('@/components/PageTabs/store') as {
  tabsActions: { setDirty: jest.Mock };
};
const last = () => {
  const calls = tabsActions.setDirty.mock.calls;
  return calls.length ? calls[calls.length - 1][1] : undefined;
};

/** audit UI-057：销售订单备注卡的草稿与公共附件卡一样登记未保存，取消后撤销 */
it('订单备注改了未保存时登记到所属标签', () => {
  render(
    React.createElement(
      App,
      null,
      React.createElement(
        PaneContext.Provider,
        { value: { entryKey: 'e1', active: true } },
        React.createElement(OrderFilesCard, {
          order: { id: 1, remark: 'ORIGINAL', attachments: [] } as never,
          canEdit: true,
          onChanged: () => {},
        }),
      ),
    ),
  );
  fireEvent.click(screen.getByText('编辑'));
  fireEvent.change(screen.getByLabelText('备注'), {
    target: { value: 'ORIGINAL' },
  });
  expect(last()).not.toBe(true);
  fireEvent.change(screen.getByLabelText('备注'), {
    target: { value: '新备注' },
  });
  expect(last()).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  expect(last()).toBe(false);
});
