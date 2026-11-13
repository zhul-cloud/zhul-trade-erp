import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { myTaskApi } from '../shared/service';
import Modal from './PasteQuoteModal';

/** change sourcing-ai-quote-fill：AI 识别、识别中取消、失败提示、预览与跳到其他任务 */
jest.mock('@umijs/max', () => ({
  request: jest.fn().mockResolvedValue({ data: [] }),
}));
jest.mock('../shared/components', () => ({
  ...jest.requireActual('../shared/components'),
  useQuoteDicts: () => ({
    conditionOptions: [{ value: 1, label: '全新原装' }],
    leadTimeOptions: [{ value: 1, label: '现货' }],
  }),
}));
jest.mock('../shared/service', () => ({
  ...jest.requireActual('../shared/service'),
  myTaskApi: {
    pastePreview: jest.fn(),
    pasteAiEnabled: jest.fn(),
    pasteAi: jest.fn(),
    pasteAiResult: jest.fn(),
    pasteAiCancel: jest.fn().mockResolvedValue(undefined),
  },
}));

const items = [
  { id: 101, model: 'MR-J4-70B' },
  { id: 102, model: 'HG-KR73' },
];

const api = myTaskApi as unknown as Record<string, jest.Mock>;

const setup = (aiOn: boolean) => {
  api.pasteAiEnabled.mockResolvedValue(aiOn);
  const onOpenTask = jest.fn();
  const onFill = jest.fn();
  render(
    React.createElement(
      App,
      null,
      React.createElement(Modal, {
        open: true,
        taskId: 7,
        taskCode: 'IQ1-A',
        items,
        onClose: jest.fn(),
        onFill,
        onOpenTask,
      }),
    ),
  );
  return { api, onOpenTask, onFill };
};

const typeIn = () => {
  fireEvent.change(screen.getByLabelText('店铺名称'), {
    target: { value: '恒拓工控' },
  });
  fireEvent.change(screen.getByLabelText('店家回复原文'), {
    target: { value: 'MR-J4-70B 驱动 含税 2850 现货\nFR-E740 780' },
  });
};

beforeEach(() => jest.useFakeTimers());
afterEach(() => jest.useRealTimers());

it('没有开启 AI 时只有按规则识别', async () => {
  setup(false);
  await act(async () => {});
  expect(screen.queryByRole('button', { name: /AI 识别/ })).toBeNull();
  expect(screen.getByRole('button', { name: /识\s*别/ })).toBeTruthy();
});

it('AI 识别：识别中可取消，完成后预览请核对与其他任务，点任务带上原文', async () => {
  const { api, onOpenTask, onFill } = setup(true);
  await act(async () => {});
  typeIn();
  api.pasteAi.mockResolvedValue({ aiTaskId: 55, status: 'RUNNING' });
  api.pasteAiResult.mockResolvedValueOnce({ aiTaskId: 55, status: 'RUNNING' });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /AI 识别/ }));
  });
  expect(screen.getByText(/AI 识别中/)).toBeTruthy();

  api.pasteAiResult.mockResolvedValue({
    aiTaskId: 55,
    status: 'DONE',
    preview: {
      rows: [
        {
          raw: 'MR-J4-70B 驱动 含税 2850 现货',
          itemId: 101,
          model: 'MR-J4-70B',
          unitPrice: 2850,
          unitPriceExTax: 2522.12,
          taxIncluded: true,
          taxRate: 13,
          itemCondition: 1,
          leadTime: 1,
          note: '',
          status: 'MATCHED',
          locked: false,
          confidence: 'CHECK',
        },
      ],
      common: '',
      otherTasks: [
        {
          taskId: 9,
          taskCode: 'IQ2-B',
          brand: '三菱',
          category: '变频器',
          modelCount: 12,
        },
      ],
      unknownModels: ['E2E-X5ME1'],
    },
  });
  await act(async () => {
    jest.advanceTimersByTime(2000);
  });
  await act(async () => {
    jest.advanceTimersByTime(2000);
  });
  expect(screen.getByText('请核对')).toBeTruthy();
  expect(screen.getByText('含税 2850.00 · 13%')).toBeTruthy();
  expect(
    screen.getByText('本任务 2 个型号找到 1 个 · 1 个请核对'),
  ).toBeTruthy();
  expect(screen.getByText(/不在你的任务里 1 个/)).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: '打开并带上原文' }));
  expect(onOpenTask).toHaveBeenCalledWith(
    expect.objectContaining({
      taskId: 9,
      shopName: '恒拓工控',
      text: 'MR-J4-70B 驱动 含税 2850 现货\nFR-E740 780',
    }),
  );

  fireEvent.click(screen.getByRole('button', { name: /填入 1 条/ }));
  expect(onFill.mock.calls[0][0][0]).toEqual(
    expect.objectContaining({
      itemId: 101,
      entry: expect.objectContaining({
        unitPrice: 2850,
        taxIncluded: true,
        taxRate: 13,
      }),
    }),
  );
});

it('取消识别后提示失败可改用按规则识别', async () => {
  const { api } = setup(true);
  await act(async () => {});
  typeIn();
  api.pasteAi.mockResolvedValue({ aiTaskId: 56, status: 'RUNNING' });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /AI 识别/ }));
  });
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  expect(api.pasteAiCancel).toHaveBeenCalledWith(7, 56);
  expect(screen.queryByText(/AI 识别中/)).toBeNull();

  api.pasteAi.mockResolvedValue({ aiTaskId: 57, status: 'RUNNING' });
  api.pasteAiResult.mockResolvedValue({
    aiTaskId: 57,
    status: 'FAILED',
    errorMessage: 'AI 识别超时',
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /AI 识别/ }));
  });
  await act(async () => {
    jest.advanceTimersByTime(2000);
  });
  expect(
    screen.getByText('AI 识别超时，可以重试或点「按规则识别」'),
  ).toBeTruthy();
});
