import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { BackgroundActions, BackgroundCard } from './components';
import { PrecheckList } from './precheck';
import { backgroundApi } from './service';

/** spec crm/customer-background「背调前检查」：检查窗口、充足度文字、去补充、卡片摘要 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('@/components/PageTabs/hooks', () => ({ useTabActive: () => true }));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  backgroundApi: { research: jest.fn(), precheck: jest.fn(), list: jest.fn() },
}));

const item = (
  key: string,
  label: string,
  keyInfo: boolean,
  state: 'OK' | 'MISSING' | 'WARN',
  text: string,
  fixTarget?: string,
) => ({
  key,
  label,
  keyInfo,
  state,
  value: state === 'OK' ? text : undefined,
  reason: state === 'OK' ? undefined : text,
  fixTarget,
});

const LOW = {
  level: 'LOW' as const,
  keyCount: 0,
  items: [
    item('website', '官网', true, 'MISSING', '没填', 'profile:1'),
    item(
      'companyEmail',
      '企业邮箱',
      true,
      'WARN',
      '联系人用的是 gmail.com，找不到公司',
      'contact',
    ),
    item('inquiryText', '询盘原文', true, 'MISSING', '没有询盘原文'),
    item('basic', '名称、国家', false, 'OK', 'Pacific Controls · Australia'),
    item('linkedin', '领英', false, 'MISSING', '没填', 'profile:1'),
  ],
};
const MEDIUM = {
  ...LOW,
  level: 'MEDIUM' as const,
  keyCount: 1,
  items: [
    item('website', '官网', true, 'OK', 'pacificcontrols.com.au'),
    ...LOW.items.slice(1),
  ],
};

/** 页面持有检查窗口的开关（与 useBackgroundPrecheck 相同） */
const Actions = (props: {
  data: unknown;
  reload: unknown;
  onStarted: unknown;
}) => {
  const [open, setOpen] = React.useState(false);
  return React.createElement(BackgroundActions, {
    customerId: 7,
    customerName: 'Pacific Controls Pty Ltd',
    running: false,
    onStarted: props.onStarted as never,
    precheck: {
      data: props.data as never,
      reload: props.reload as never,
      open,
      setOpen,
    },
  });
};

const wrap = (el: unknown) =>
  render(React.createElement(App, null, el as never));

beforeEach(() => jest.clearAllMocks());

it('信息很少：检查窗口按钮为「仍要背调」，点了才发起背调', async () => {
  (backgroundApi.research as jest.Mock).mockResolvedValue({ id: 1 });
  const onStarted = jest.fn();
  const reload = jest.fn().mockResolvedValue(LOW);
  wrap(React.createElement(Actions, { data: LOW, reload, onStarted }));
  fireEvent.click(screen.getByRole('button', { name: /AI 背调/ }));
  expect(await screen.findByText('背调前检查')).toBeTruthy();
  expect(screen.getByText('信息：很少')).toBeTruthy();
  expect(screen.getByText('联系人用的是 gmail.com，找不到公司')).toBeTruthy();
  expect(backgroundApi.research).not.toHaveBeenCalled();
  // 没有编辑权限（没传 onFix）时不显示「去补充」
  expect(screen.queryByText('去补充')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '仍要背调' }));
  await waitFor(() =>
    expect(backgroundApi.research).toHaveBeenCalledWith(7, false),
  );
  expect(onStarted).toHaveBeenCalled();
});

it('信息一般：按钮为「直接背调」，「先去补充」只关闭窗口', async () => {
  const reload = jest.fn().mockResolvedValue(MEDIUM);
  wrap(
    React.createElement(Actions, {
      data: MEDIUM,
      reload,
      onStarted: jest.fn(),
    }),
  );
  fireEvent.click(screen.getByRole('button', { name: /AI 背调/ }));
  expect(await screen.findByText('信息：一般')).toBeTruthy();
  expect(screen.getByRole('button', { name: '直接背调' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '先去补充' }));
  expect(backgroundApi.research).not.toHaveBeenCalled();
});

it('去补充：只给有补充位置的缺失项，点了交给页面打开编辑', () => {
  const onFix = jest.fn();
  wrap(React.createElement(PrecheckList, { data: LOW, onFix }));
  const links = screen.getAllByText('去补充');
  // 官网、企业邮箱、领英有入口；询盘原文没有
  expect(links).toHaveLength(3);
  fireEvent.click(links[1]);
  expect(onFix).toHaveBeenCalledWith('contact');
  expect(screen.getByText('0 / 3 · 决定充足度')).toBeTruthy();
});

it('卡片：有报告时显示一行缺项，展开后看完整清单', () => {
  wrap(
    React.createElement(BackgroundCard, {
      customerId: 7,
      reports: [
        {
          id: 1,
          reportNo: 'BG20261112001',
          source: 1,
          status: 3,
          requestedByName: '张伟',
          createTime: '2026-11-12 10:20:00',
          failReason: '超时',
        } as never,
      ],
      precheck: MEDIUM,
    }),
  );
  expect(screen.getByText('缺 企业邮箱、询盘原文、领英')).toBeTruthy();
  expect(screen.queryByText('关键信息')).toBeNull();
  fireEvent.click(screen.getByText(/展开/));
  expect(screen.getByText('关键信息')).toBeTruthy();
});
