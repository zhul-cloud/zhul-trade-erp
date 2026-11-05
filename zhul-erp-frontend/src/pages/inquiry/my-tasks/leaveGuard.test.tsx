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
import { myTaskApi } from '../shared/service';
import MyTasksPage from './index';

/** audit UI-003：有没保存的回价时切换任务、搜索、切页签，先问保存 / 放弃 / 取消 */
jest.mock('@umijs/max', () => ({
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/inquiry/my-tasks' }),
  useModel: () => ({ initialState: {} }),
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn().mockResolvedValue({ code: 0, data: [] }),
}));
jest.mock('../shared/TaskNameplates', () => ({
  useTaskNameplates: () => ({
    forItem: () => null,
    unmatched: null,
    node: null,
  }),
}));
jest.mock('../shared/service', () => ({
  ...jest.requireActual('../shared/service'),
  myTaskApi: {
    list: jest.fn(),
    donePage: jest.fn().mockResolvedValue({ total: 0, records: [] }),
    salesPending: jest.fn().mockResolvedValue({ total: 0, records: [] }),
    detail: jest.fn(),
    save: jest.fn().mockResolvedValue(undefined),
  },
}));

const task = (id: number, code: string) => ({
  id,
  taskCode: code,
  brand: 'Siemens',
  category: 'PLC',
  itemCount: 1,
  filledCount: 0,
  status: 1,
  urgent: false,
  timeout: false,
  editable: true,
});
const item = (id: number, model: string) => ({
  id,
  model,
  originalModel: model,
  quantity: 1,
  unit: 'pcs',
  description: '',
  lifecycle: 0,
  replacementModel: '',
  difficulty: 1,
  inquiryScript: '',
  searchKeywords: [],
  quotes: [],
});
const api = myTaskApi as unknown as Record<string, jest.Mock>;

beforeEach(() => {
  jest.clearAllMocks();
  window.history.pushState({}, '', '/inquiry/my-tasks?task=1');
  api.list.mockResolvedValue([task(1, 'TASK-A'), task(2, 'TASK-B')]);
  api.detail.mockImplementation(async (id: number) => ({
    task: task(id, id === 1 ? 'TASK-A' : 'TASK-B'),
    items: [item(id * 10, id === 1 ? 'MODEL-A' : 'MODEL-B')],
  }));
});

const open = async () => {
  render(React.createElement(App, null, React.createElement(MyTasksPage)));
  await screen.findByText('MODEL-A');
};
/** 确认框里的按钮：页面上也有同名的「保存草稿」 */
const dialogButton = async (name: string) => {
  await screen.findAllByText('回价还没保存');
  const dialog = document.querySelector('.ant-modal-confirm') as HTMLElement;
  return within(dialog).getByRole('button', { name });
};
const typeShop = () => {
  fireEvent.change(screen.getAllByPlaceholderText('店铺名称')[0], {
    target: { value: '华强北某店' },
  });
  fireEvent.change(screen.getAllByPlaceholderText('如 18.50')[0], {
    target: { value: '18.50' },
  });
};
const clickTask = (code: string) =>
  act(async () => {
    fireEvent.click(
      screen.getAllByText(code)[0].closest('button') as HTMLElement,
    );
  });

it('有没保存的修改时点另一个任务：先提示，取消后留在原任务、输入还在', async () => {
  await open();
  typeShop();
  await clickTask('TASK-B');
  expect((await screen.findAllByText('回价还没保存')).length).toBeGreaterThan(
    0,
  );
  fireEvent.click(screen.getByRole('button', { name: '取 消' }));
  await waitFor(() =>
    expect(screen.queryAllByText('回价还没保存')).toHaveLength(0),
  );
  expect(screen.queryByText('MODEL-B')).toBeNull();
  expect(screen.getByDisplayValue('华强北某店')).toBeTruthy();
});

it('选择放弃：切到另一个任务，不保存', async () => {
  await open();
  typeShop();
  await clickTask('TASK-B');
  fireEvent.click(await screen.findByRole('button', { name: '放弃修改' }));
  expect(await screen.findByText('MODEL-B')).toBeTruthy();
  expect(api.save).not.toHaveBeenCalled();
});

it('选择保存草稿：保存成功后才切换', async () => {
  await open();
  typeShop();
  await clickTask('TASK-B');
  fireEvent.click(await dialogButton('保存草稿'));
  expect(await screen.findByText('MODEL-B')).toBeTruthy();
  expect(api.save).toHaveBeenCalledWith(1, expect.anything(), false);
});

it('草稿不完整（没填单价）时保存不成功，留在原任务', async () => {
  await open();
  fireEvent.change(screen.getAllByPlaceholderText('店铺名称')[0], {
    target: { value: '华强北某店' },
  });
  await clickTask('TASK-B');
  fireEvent.click(await dialogButton('保存草稿'));
  await waitFor(() =>
    expect(screen.queryAllByText('回价还没保存')).toHaveLength(0),
  );
  expect(api.save).not.toHaveBeenCalled();
  expect(screen.queryByText('MODEL-B')).toBeNull();
  expect(screen.getByDisplayValue('华强北某店')).toBeTruthy();
});

it('保存失败时不离开原任务', async () => {
  api.save.mockRejectedValueOnce(new Error('网络错误'));
  await open();
  typeShop();
  await clickTask('TASK-B');
  fireEvent.click(await dialogButton('保存草稿'));
  await waitFor(() => expect(api.save).toHaveBeenCalled());
  expect(screen.queryByText('MODEL-B')).toBeNull();
  expect(screen.getByDisplayValue('华强北某店')).toBeTruthy();
});

it('切到已回价页签也先提示', async () => {
  await open();
  typeShop();
  await act(async () => {
    fireEvent.click(screen.getByText(/已回价/));
  });
  expect((await screen.findAllByText('回价还没保存')).length).toBeGreaterThan(
    0,
  );
});

it('没有修改时直接切换，不提示', async () => {
  await open();
  await clickTask('TASK-B');
  expect(await screen.findByText('MODEL-B')).toBeTruthy();
  expect(screen.queryAllByText('回价还没保存')).toHaveLength(0);
});
