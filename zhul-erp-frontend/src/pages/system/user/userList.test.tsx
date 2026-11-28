import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import UserPage from './index';

/** 用户管理：ProTable 搜索表单换成统一搜索条件区，启用、禁用数量并入状态下拉（openspec ui-redesign 任务 7.7） */
jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/system/user', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const listCalls = () =>
  (request as jest.Mock).mock.calls.filter(
    ([url]) => url === '/api/v1/system/users',
  );
const lastParams = () => {
  const calls = listCalls();
  return calls[calls.length - 1][1].params;
};

beforeEach(() => {
  (request as jest.Mock).mockReset();
  (request as jest.Mock).mockImplementation((url: string) => {
    if (url === '/api/v1/system/users/stats')
      return Promise.resolve({
        data: { total: 5, enabled: 4, disabled: 1, newThisMonth: 2 },
      });
    if (url === '/api/v1/system/users')
      return Promise.resolve({ data: { records: [], total: 0 } });
    return Promise.resolve({ data: [] });
  });
});

it('状态下拉带启用、禁用数量，切换后立即查询；姓名点「查询」才生效', async () => {
  render(React.createElement(App, null, React.createElement(UserPage)));
  await waitFor(() => expect(listCalls().length).toBeGreaterThan(0));
  expect(await screen.findByText('本月新增')).toBeTruthy();

  await act(async () => {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '状态' }));
  });
  expect(await screen.findByTitle('启用 · 4')).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByTitle('禁用 · 1'));
  });
  await waitFor(() => expect(lastParams().status).toBe(0));

  fireEvent.change(screen.getByLabelText('用户'), {
    target: { value: '张三' },
  });
  expect(lastParams().name).toBeUndefined();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  });
  await waitFor(() =>
    expect(lastParams()).toMatchObject({ name: '张三', status: 0 }),
  );
});
