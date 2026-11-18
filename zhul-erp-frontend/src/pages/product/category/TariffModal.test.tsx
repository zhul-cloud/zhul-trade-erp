import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { categoryApi } from '../service';
import TariffModal from './TariffModal';

const h = React.createElement;

jest.mock('@umijs/max', () => ({ request: jest.fn() }));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  categoryApi: {
    tariff: jest.fn(),
    saveTariff: jest.fn(),
    tariffAi: jest.fn(),
    tariffAiStatus: jest.fn(),
  },
}));

const rate = (countryCode: string, countryName: string, value = '') => ({
  countryCode,
  countryName,
  rate: value,
  note: '',
  source: '',
  checkedAt: value ? '2026-11-18' : null,
});

beforeEach(() => {
  jest.clearAllMocks();
  (categoryApi.tariff as jest.Mock).mockResolvedValue({
    hs6: '853650',
    categories: ['光电传感器'],
    rates: [rate('MX', '墨西哥', '0%'), rate('IN', '印度'), rate('IR', '伊朗')],
  });
});

afterEach(() => jest.useRealTimers());

/** openspec content-ai-batch-hs：AI 补全只填未填的国家，作为建议由人核对后保存 */
it('AI 补全：结果填进未填的国家，保存时一起提交', async () => {
  jest.useFakeTimers();
  (categoryApi.tariffAi as jest.Mock).mockResolvedValue({
    taskId: 7,
    status: 1,
    rates: [],
  });
  (categoryApi.tariffAiStatus as jest.Mock).mockResolvedValue({
    taskId: 7,
    status: 3,
    rates: [
      {
        ...rate('IN', '印度', '7.5%'),
        note: '另加附加税',
        source: 'https://example.org',
      },
      { ...rate('MX', '墨西哥', '9%') },
    ],
  });
  (categoryApi.saveTariff as jest.Mock).mockResolvedValue({});
  const onSaved = jest.fn();
  await act(async () => {
    render(
      h(
        App,
        null,
        h(TariffModal, {
          hs6: '853650',
          canEdit: true,
          onClose: jest.fn(),
          onSaved,
        }),
      ),
    );
  });
  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).getByText('光电传感器')).toBeTruthy();
  await act(async () => {
    fireEvent.click(
      within(dialog).getByRole('button', { name: /AI 补全未填（2 国）/ }),
    );
  });
  expect(categoryApi.tariffAi).toHaveBeenCalledWith('853650');
  await act(async () => {
    jest.advanceTimersByTime(4000);
  });
  expect(
    (within(dialog).getByLabelText('印度进口关税') as HTMLInputElement).value,
  ).toBe('7.5%');
  expect(
    (within(dialog).getByLabelText('墨西哥进口关税') as HTMLInputElement).value,
  ).toBe(
    '0%',
    // 已填的国家不被 AI 结果覆盖
  );
  expect(within(dialog).getByText('AI 建议')).toBeTruthy();
  await act(async () => {
    fireEvent.click(within(dialog).getByRole('button', { name: /保\s*存/ }));
  });
  const [hs6, rows] = (categoryApi.saveTariff as jest.Mock).mock.calls[0];
  expect(hs6).toBe('853650');
  expect(rows).toEqual([
    { countryCode: 'MX', rate: '0%', note: '', source: '' },
    {
      countryCode: 'IN',
      rate: '7.5%',
      note: '另加附加税',
      source: 'https://example.org',
    },
    { countryCode: 'IR', rate: '', note: '', source: '' },
  ]);
  expect(onSaved).toHaveBeenCalled();
});

it('没有维护权限：只能查看', async () => {
  await act(async () => {
    render(
      h(
        App,
        null,
        h(TariffModal, {
          hs6: '853650',
          canEdit: false,
          onClose: jest.fn(),
          onSaved: jest.fn(),
        }),
      ),
    );
  });
  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).queryByRole('button', { name: /保\s*存/ })).toBeNull();
  expect(within(dialog).queryByRole('button', { name: /AI 补全/ })).toBeNull();
  expect(
    (within(dialog).getByLabelText('墨西哥进口关税') as HTMLInputElement)
      .disabled,
  ).toBe(true);
});
