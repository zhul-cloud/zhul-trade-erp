import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { contentApi } from '../service';
import { TaskList } from './TaskList';

/** 资料任务、营销任务：状态页签改为搜索条件区的状态下拉（openspec ui-redesign，product/product-content 增量） */
let mockSearch = '';
jest.mock('@umijs/max', () => ({
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/product/fact-tasks', search: '' }),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams(mockSearch), jest.fn()],
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  brandApi: { options: jest.fn().mockResolvedValue([]) },
  categoryApi: { options: jest.fn().mockResolvedValue([]) },
  contentApi: {
    page: jest.fn(),
    counts: jest.fn(),
    platforms: jest.fn().mockResolvedValue([]),
  },
}));

const lastTab = () => {
  const calls = (contentApi.page as jest.Mock).mock.calls;
  return calls[calls.length - 1][1].tab;
};

beforeEach(() => {
  jest.clearAllMocks();
  mockSearch = '';
  (contentApi.page as jest.Mock).mockResolvedValue({ total: 0, records: [] });
  (contentApi.counts as jest.Mock).mockResolvedValue({
    all: 5,
    '1': 3,
    '2': 0,
    verified: 2,
  });
});

const renderList = () =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(TaskList, { entry: 'facts' }),
    ),
  );

it('默认看资料待补；选项带数量，0 和「全部」只写名称；切换后立即查询，重置回默认', async () => {
  renderList();
  await waitFor(() => expect(lastTab()).toBe('1'));
  const status = screen.getByRole('combobox', { name: '状态' });
  await act(async () => {
    fireEvent.mouseDown(status);
  });
  const list = await screen.findByRole('listbox');
  const names = within(list.parentElement as HTMLElement)
    .getAllByRole('option')
    .map((o) => o.textContent);
  // antd 的虚拟列表会把选项文字放在 title 上；用页面上可见的选项名核对
  const titles = [...document.querySelectorAll('.ant-select-item-option')].map(
    (o) => o.getAttribute('title'),
  );
  expect(titles.length ? titles : names).toEqual([
    '全部',
    '资料待补 · 3',
    '资料待核',
    '资料已核 · 2',
  ]);
  await act(async () => {
    fireEvent.click(screen.getByTitle('资料已核 · 2'));
  });
  // 不用点「查询」
  await waitFor(() => expect(lastTab()).toBe('verified'));
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /重\s*置/ }));
  });
  await waitFor(() => expect(lastTab()).toBe('1'));
});

it('从别的单据带型号跳来时看全部状态', async () => {
  mockSearch = 'keyword=6ES7214';
  renderList();
  await waitFor(() =>
    expect(contentApi.page).toHaveBeenLastCalledWith(
      'facts',
      expect.objectContaining({ tab: undefined, keyword: '6ES7214' }),
    ),
  );
});
