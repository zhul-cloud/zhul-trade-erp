import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { notificationApi } from '@/services/zhul/notification';
import { NotificationBell } from './NotificationBell';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  history: { push: jest.fn() },
}));
jest.mock('@/services/zhul/notification', () => ({
  notificationApi: {
    count: jest.fn(),
    list: jest.fn(),
    read: jest.fn(),
    readAll: jest.fn(),
  },
}));

const list = notificationApi.list as jest.Mock;
const count = notificationApi.count as jest.Mock;

const deferred = <T,>() => {
  let resolve!: (v: T) => void;
  const promise = new Promise<T>((ok) => {
    resolve = ok;
  });
  return { promise, resolve };
};

const item = (id: number, title: string): any => ({
  id,
  node: 'SO_CREATED',
  nodeName: '订单',
  kind: 'INFO',
  title,
  content: '',
  bizNo: '',
  link: '',
  urgent: false,
  read: true,
  done: false,
  time: '2026-11-15 10:00:00',
});

beforeEach(() => {
  list.mockReset();
  count.mockReset();
  count.mockResolvedValue({ todo: 0, unread: 0, total: 0 });
});

/** audit UI-082：「待处理」请求迟到时丢弃，列表、总数只反映当前的「全部」标签 */
it('切换标签后，上一个标签迟到的响应不覆盖当前列表', async () => {
  const todo = deferred<{ total: number; records: unknown[] }>();
  const all = deferred<{ total: number; records: unknown[] }>();
  list.mockImplementation((t: string) =>
    t === 'todo' ? todo.promise : all.promise,
  );
  render(React.createElement(App, null, React.createElement(NotificationBell)));

  fireEvent.click(screen.getByRole('button', { name: '通知' }));
  fireEvent.click(await screen.findByText('全部'));
  expect(list).toHaveBeenCalledWith('all', 1, 20);

  await act(async () =>
    all.resolve({ total: 1, records: [item(2, '全部标签的通知')] }),
  );
  await act(async () =>
    todo.resolve({
      total: 30,
      records: [item(1, '待处理标签的通知')],
    }),
  );

  expect(screen.getByText('全部标签的通知')).toBeTruthy();
  expect(screen.queryByText('待处理标签的通知')).toBeNull();
  expect(screen.queryByText('加载更多')).toBeNull();
});
