import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import RecordFollowupModal from './RecordFollowupModal';
import { AI_STATUS, followupApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
// boardStyle.tsx 在模块顶层写了 JSX、没有引入 React（构建时自动注入），测试里先放到全局
jest.mock('./boardStyle', () => {
  (globalThis as { React?: unknown }).React = jest.requireActual('react');
  return jest.requireActual('./boardStyle');
});
jest.mock('@/pages/customer/service', () => ({
  customerApi: { page: jest.fn(() => Promise.resolve({ records: [] })) },
}));
jest.mock('@/pages/sales/after-sales/dialogs', () => ({
  CreateAfterSalesModal: () => null,
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  followupApi: {
    blockers: jest.fn(() => Promise.resolve([])),
    customerSummary: jest.fn(() => Promise.resolve({ currentItems: [] })),
    extract: jest.fn(),
    extractResult: jest.fn(),
  },
}));

const AI_NEXT = 'AI 建议：问客户下周能否下单';

const show = () =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(RecordFollowupModal, {
        open: true,
        target: { customerId: 1, customerName: 'Siam Automation' },
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );

/** 粘贴聊天后点 AI 整理，整理还在跑（第一次轮询前） */
const startAi = async () => {
  fireEvent.change(
    screen.getByPlaceholderText('把和客户的 WhatsApp 聊天或邮件直接粘贴进来'),
    { target: { value: '客户说下周再看' } },
  );
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /AI 整理/ }));
  });
};

/** 轮询拿到结果 */
const finishAi = async () => {
  await act(async () => {
    jest.advanceTimersByTime(2100);
  });
  await act(async () => {});
};

beforeEach(() => {
  jest.useFakeTimers();
  jest.clearAllMocks();
  (followupApi.extract as jest.Mock).mockResolvedValue({ taskId: 9 });
  (followupApi.extractResult as jest.Mock).mockResolvedValue({
    taskId: 9,
    status: AI_STATUS.DONE,
    result: {
      summary: '客户下周再看',
      nextActions: [{ text: AI_NEXT, date: '2099-01-05' }],
      profileSuggestions: [],
    },
  });
});

afterEach(() => jest.useRealTimers());

/** audit UI-064：AI 整理期间手填的下一步，整理完成后不被 AI 建议覆盖 */
it('整理期间手填的下一步不被覆盖', async () => {
  show();
  await startAi();
  const next = screen.getByPlaceholderText(
    '例如：发替代型号对比，问客户能否接受',
  ) as HTMLInputElement;
  fireEvent.change(next, { target: { value: '测试手填' } });
  await finishAi();
  expect(followupApi.extractResult).toHaveBeenCalled();
  expect(screen.getAllByText(AI_NEXT).length).toBeGreaterThan(0);
  expect(next.value).toBe('测试手填');
});

/** audit UI-064：AI 整理期间手选的日期，整理完成后不被 AI 建议的日期覆盖 */
it('整理期间手选的日期不被覆盖', async () => {
  show();
  await startAi();
  const pick = screen.getByRole('radio', { name: '3 个月后' });
  fireEvent.click(pick);
  expect(pick.getAttribute('aria-checked')).toBe('true');
  await finishAi();
  expect(followupApi.extractResult).toHaveBeenCalled();
  expect(
    (
      screen.getByPlaceholderText(
        '例如：发替代型号对比，问客户能否接受',
      ) as HTMLInputElement
    ).value,
  ).toBe(AI_NEXT);
  expect(
    screen
      .getByRole('radio', { name: '3 个月后' })
      .getAttribute('aria-checked'),
  ).toBe('true');
});
