import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import LoginLogPage from './index';

/** 登录日志：ProTable 搜索表单换成统一搜索条件区（openspec ui-redesign 任务 7.7） */
jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/system/log/login', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const lastParams = () => {
  const calls = (request as jest.Mock).mock.calls.filter(
    ([url]) => url === '/api/v1/system/logs/login',
  );
  return calls[calls.length - 1][1].params;
};
const FMT = 'YYYY-MM-DD HH:mm:ss';
const weekStart = () => dayjs().subtract(6, 'day').startOf('day').format(FMT);

beforeEach(() => {
  (request as jest.Mock).mockReset();
  (request as jest.Mock).mockResolvedValue({
    data: { records: [], total: 0 },
  });
});

it('默认查最近 7 天；登录结果立即查询；IP 点查询才生效；重置回到最近 7 天', async () => {
  render(React.createElement(App, null, React.createElement(LoginLogPage)));
  await waitFor(() =>
    expect(lastParams()).toMatchObject({
      startTime: weekStart(),
      endTime: dayjs().endOf('day').format(FMT),
    }),
  );

  await act(async () => {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '登录结果' }));
  });
  await act(async () => {
    fireEvent.click(await screen.findByTitle('失败'));
  });
  await waitFor(() => expect(lastParams().result).toBe(0));

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /更多筛选/ }));
  });
  fireEvent.change(screen.getByLabelText('IP 地址'), {
    target: { value: '192.168' },
  });
  expect(lastParams().ip).toBeUndefined();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  });
  await waitFor(() =>
    expect(lastParams()).toMatchObject({ ip: '192.168', result: 0 }),
  );

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /重\s*置/ }));
  });
  await waitFor(() => {
    const q = lastParams();
    expect(q.ip).toBeUndefined();
    expect(q.result).toBeUndefined();
    expect(q.startTime).toBe(weekStart());
  });
});
