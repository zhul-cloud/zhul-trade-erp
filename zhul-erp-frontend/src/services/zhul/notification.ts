import { request } from '@umijs/max';

/** 站内通知（spec system/notification） */
export type NotificationItem = {
  id: number;
  node: string;
  nodeName: string;
  /** TODO-待办、INFO-知会 */
  kind: 'TODO' | 'INFO';
  title: string;
  content: string;
  bizNo: string;
  link: string;
  urgent: boolean;
  read: boolean;
  done: boolean;
  time: string;
};

export type NotificationCount = { todo: number; unread: number; total: number };

export type ReceiverPost = {
  /** PURCHASE_LEAD-采购负责人、RECEIVER-收货人、FINANCE-财务 */
  post: string;
  postName: string;
  /** 收货人跟采购订单的采购员走 */
  poBuyer: boolean;
  users: { id: number; name: string }[];
  /** 这个岗位接收的通知 */
  nodes: string[];
};

const quiet = { skipErrorHandler: true } as const;
const BASE = '/api/v1/notifications';
const RECEIVERS = '/api/v1/system/notification-receivers';

const get = <T>(url: string, params?: object) =>
  request<{ data: T }>(url, { method: 'GET', params, ...quiet }).then(
    (r) => r.data,
  );
const send = <T>(method: string, url: string, data?: unknown) =>
  request<{ data: T }>(url, { method, data, ...quiet }).then((r) => r.data);

export const notificationApi = {
  count: () => get<NotificationCount>(`${BASE}/count`),
  list: (tab: 'todo' | 'all', page: number, pageSize = 20) =>
    get<{ total: number; records: NotificationItem[] }>(BASE, {
      tab,
      page,
      pageSize,
    }),
  read: (id: number) => send<void>('POST', `${BASE}/${id}/read`),
  readAll: () => send<number>('POST', `${BASE}/read-all`),
};

export const receiverApi = {
  list: () => get<ReceiverPost[]>(RECEIVERS),
  candidates: () =>
    get<{ id: number; name: string }[]>(`${RECEIVERS}/candidates`),
  save: (post: string, body: { poBuyer: boolean; userIds: number[] }) =>
    send<ReceiverPost[]>('PUT', `${RECEIVERS}/${post}`, body),
};
