import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import AfterSalesDetail from './detail';
import { afterSalesApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  Link: () => null,
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/after-sales/7', search: '' }),
  useModel: () => ({ initialState: {} }),
  useParams: () => ({ id: '7' }),
}));
jest.mock('@/components/PageTabs/store', () => ({
  tabsActions: { setDirty: jest.fn(), setTitle: jest.fn() },
}));
jest.mock('@/pages/warehouse/components', () => ({
  AttachmentWall: () => null,
  DOC_PATHS: { expense: (id: number) => `/finance/expenses/${id}` },
  ReasonModal: () => null,
}));
jest.mock('@/pages/finance/expenses/AfterSalesExpense', () => () => null);
jest.mock('../returns/dialogs', () => ({ CreateReturnModal: () => null }));
jest.mock('./dialogs', () => {
  const marker = (name: string) => (p: { open: boolean }) =>
    p.open ? require('react').createElement('div', null, name) : null;
  return {
    PlanModal: marker('处理弹窗'),
    CloseModal: marker('结案弹窗'),
    ReplacementModal: marker('补发弹窗'),
    RefundRequestModal: marker('退款弹窗'),
  };
});
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  afterSalesApi: { detail: jest.fn() },
}));

const { tabsActions } = jest.requireMock('@/components/PageTabs/store') as {
  tabsActions: { setDirty: jest.Mock };
};
const lastDirty = () => {
  const calls = tabsActions.setDirty.mock.calls;
  return calls.length ? calls[calls.length - 1][1] : undefined;
};

const data = {
  id: 7,
  asNo: 'AS20261115001',
  customerName: 'Siam Automation',
  soId: 3,
  soNo: 'SO20261115001',
  levelCode: 'S3',
  levelName: 'S3 一般',
  status: 1,
  statusName: '处理中',
  reasonCode: 'INVESTIGATING',
  reasonName: '调查中',
  solutions: [],
  solutionNames: [],
  items: [],
  logs: [],
  createTime: '2026-11-15 10:00:00',
  receivedAt: '2026-11-15 10:00:00',
};

const page = (active: boolean) =>
  React.createElement(
    App,
    null,
    React.createElement(
      PaneContext.Provider,
      { value: { entryKey: 'as7', active } },
      React.createElement(AfterSalesDetail),
    ),
  );

beforeEach(() => {
  jest.clearAllMocks();
  (afterSalesApi.detail as jest.Mock).mockResolvedValue(data);
});

/** audit UI-065：弹窗开着时返回本页不重新取数，以免重置正在填写的处理、结案、补发内容 */
it('弹窗开着时返回不刷新，关着时返回刷新', async () => {
  const view = render(page(true));
  expect(await screen.findAllByText('AS20261115001')).not.toHaveLength(0);
  expect(afterSalesApi.detail).toHaveBeenCalledTimes(1);

  fireEvent.click(screen.getByRole('button', { name: /结案/ }));
  expect(screen.getByText('结案弹窗')).toBeTruthy();
  view.rerender(page(false));
  await act(async () => view.rerender(page(true)));
  expect(afterSalesApi.detail).toHaveBeenCalledTimes(1);
  expect(screen.getByText('结案弹窗')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: '编辑' }));
  expect(screen.getByText('处理弹窗')).toBeTruthy();
  view.rerender(page(false));
  await act(async () => view.rerender(page(true)));
  expect(afterSalesApi.detail).toHaveBeenCalledTimes(1);
});

it('没有弹窗时返回重新取数', async () => {
  const view = render(page(true));
  await screen.findAllByText('AS20261115001');
  view.rerender(page(false));
  await act(async () => view.rerender(page(true)));
  expect(afterSalesApi.detail).toHaveBeenCalledTimes(2);
});

/** audit UI-066：跟进记录写了内容还没保存时登记未保存，清空后撤销 */
it('跟进记录草稿登记未保存', async () => {
  render(page(true));
  const box = await screen.findByLabelText('跟进内容');
  expect(lastDirty()).not.toBe(true);
  fireEvent.change(box, { target: { value: '客户说换新后正常' } });
  expect(lastDirty()).toBe(true);
  fireEvent.change(box, { target: { value: '' } });
  expect(lastDirty()).toBe(false);
});
