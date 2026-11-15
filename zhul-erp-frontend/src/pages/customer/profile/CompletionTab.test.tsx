import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { completionApi } from '../background/service';
import CompletionTab from './CompletionTab';
import { profileApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(() => new Promise(() => undefined)),
  useAccess: () => ({ 'partner:customer:edit': true }),
}));
jest.mock('@/utils/dict', () => ({
  ...jest.requireActual('@/utils/dict'),
  useDictOptions: () => ({
    options: [
      { value: 1, label: '直接型' },
      { value: 2, label: '谨慎型' },
    ],
    labelOf: () => '',
  }),
}));
jest.mock('../background/components', () => ({
  BackgroundActions: () => null,
  PriorityPill: () => null,
  useBackgroundReports: () => ({
    reports: [],
    running: false,
    reload: jest.fn(),
  }),
}));
jest.mock('../background/precheck', () => ({
  useBackgroundPrecheck: () => ({
    data: undefined,
    reload: jest.fn(),
    open: false,
    setOpen: jest.fn(),
  }),
}));
jest.mock('../background/Suggestions', () => ({
  __esModule: true,
  default: () => null,
}));
jest.mock('../background/service', () => ({
  ...jest.requireActual('../background/service'),
  completionApi: { queue: jest.fn(), wizard: jest.fn() },
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  profileApi: {
    get: jest.fn(),
    update: jest.fn(),
    updateContact: jest.fn(),
    asked: jest.fn(),
  },
}));

const h = React.createElement;

const row = (customerId: number, name: string) => ({
  customerId,
  customerCode: `CU${customerId}`,
  name,
  country: 'AU',
  bizGrade: 0,
  potentialRating: 0,
  done: 2,
  missingCount: 3,
});

/** 第③步有一项联系人字段（沟通风格），可以直接点选 */
const wizard = (customerId: number, contactId: number) => ({
  customerId,
  customerCode: `CU${customerId}`,
  name: `客户${customerId}`,
  country: 'AU',
  done: 2,
  contactId,
  contactName: `联系人${contactId}`,
  steps: [
    {
      step: 3,
      name: '你自己就能判断',
      items: [
        {
          key: 'commStyle',
          label: '沟通风格',
          module: 4,
          contact: true,
          sourceKey: 'contact.commStyle',
        },
      ],
    },
    {
      step: 4,
      name: '只能问客户',
      items: [
        {
          key: 'companySize',
          label: '公司规模',
          module: 2,
          contact: false,
          sourceKey: 'companySize',
        },
      ],
    },
  ],
});

const profile = (customerId: number, contactId: number) => ({
  customerId,
  contacts: [{ id: contactId, name: `联系人${contactId}` }],
  currentBrands: [],
  derived: { topBrands: [] },
  completeness: { missing: [] },
});

type Pending = Map<
  string,
  { resolve: (v: unknown) => void; reject: (e: unknown) => void }
>;
let pending: Pending;
const defer = (key: string) =>
  new Promise((resolve, reject) => pending.set(key, { resolve, reject }));
const settle = async (key: string, value: unknown, fail = false) => {
  await act(async () => {
    const p = pending.get(key);
    if (fail) p?.reject(value);
    else p?.resolve(value);
  });
};

beforeEach(() => {
  jest.clearAllMocks();
  pending = new Map();
  (completionApi.queue as jest.Mock).mockResolvedValue({
    total: 2,
    todayDone: 0,
    records: [row(1, 'Alpha Ltd'), row(2, 'Beta Ltd')],
  });
  (completionApi.wizard as jest.Mock).mockImplementation((id: number) =>
    defer(`wizard-${id}`),
  );
  (profileApi.get as jest.Mock).mockImplementation((id: number) =>
    defer(`profile-${id}`),
  );
  (profileApi.update as jest.Mock).mockResolvedValue(undefined);
  (profileApi.updateContact as jest.Mock).mockResolvedValue(undefined);
  (profileApi.asked as jest.Mock).mockResolvedValue(undefined);
});

const renderTab = async () => {
  render(h(App, null, h(CompletionTab)));
  await act(async () => {});
};
const queueButton = (name: string) =>
  within(screen.getByRole('complementary', { name: '待补全的客户' }))
    .getByText(name)
    .closest('button') as HTMLButtonElement;

/** audit UI-070：首次加载失败后点「重试」，成功就显示队列并选中第一个客户 */
it('队列加载失败后重试成功：清掉错误并选中第一个', async () => {
  (completionApi.queue as jest.Mock)
    .mockRejectedValueOnce(new Error('网络超时'))
    .mockResolvedValue({
      total: 1,
      todayDone: 0,
      records: [row(1, 'Alpha Ltd')],
    });
  await renderTab();
  expect(screen.getByText('加载失败')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
  await act(async () => {});
  expect(screen.queryByText('加载失败')).toBeNull();
  expect(screen.getByText('Alpha Ltd')).toBeTruthy();
  expect(completionApi.wizard).toHaveBeenLastCalledWith(1);
});

/** audit UI-060：切换客户后上一个客户的档案迟到，联系人仍保存到当前客户的联系人 */
it('切换客户：丢弃上一个客户迟到的档案', async () => {
  await renderTab();
  await settle('wizard-1', wizard(1, 11));
  fireEvent.click(queueButton('Beta Ltd'));
  await settle('wizard-2', wizard(2, 21));
  await settle('profile-2', profile(2, 21));
  await settle('profile-1', profile(1, 11));
  fireEvent.click(screen.getByRole('button', { name: '直接型' }));
  fireEvent.click(screen.getByRole('button', { name: /^保\s*存$/ }));
  await act(async () => {});
  expect(profileApi.updateContact).toHaveBeenCalledWith(
    2,
    21,
    expect.objectContaining({ commStyle: 1 }),
  );
});

/** audit UI-060：档案取数失败时联系人项不能点选，不能保存了却没写入 */
it('档案取数失败：联系人项不能点选', async () => {
  await renderTab();
  await settle('wizard-1', wizard(1, 11));
  await settle('profile-1', new Error('失败'), true);
  const s3 = screen.getByRole('region', { name: '你自己就能判断' });
  expect(within(s3).queryByRole('button', { name: '直接型' })).toBeNull();
});

/** audit UI-069：勾「已经问了，等回复」刷新向导后，已点选还没保存的项保留 */
it('刷新向导：保留还没保存的点选', async () => {
  await renderTab();
  await settle('wizard-1', wizard(1, 11));
  await settle('profile-1', profile(1, 11));
  fireEvent.click(screen.getByRole('button', { name: '直接型' }));
  expect(screen.getByRole('button', { name: /保存，下一个客户/ })).toBeTruthy();
  fireEvent.click(screen.getByRole('checkbox', { name: '已经问了，等回复' }));
  await act(async () => {});
  await settle('wizard-1', wizard(1, 11));
  await settle('profile-1', profile(1, 11));
  expect(profileApi.asked).toHaveBeenCalled();
  expect(screen.getByRole('button', { name: /保存，下一个客户/ })).toBeTruthy();
});
