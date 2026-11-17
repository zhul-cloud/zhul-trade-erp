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
import { TextQuoteModal } from './dialogs';
import {
  DEFAULT_TEXT_OPTIONS,
  LEAD_SHIFT,
  leadChoices,
  textQuery,
} from './textOptions';

/** spec quotation/quotation「文字报价」：报价货况默认全新原装，报价货期默认按采购货期顺延，改选后重新生成 */

jest.mock('@umijs/max', () => ({ request: jest.fn() }));

const mocked = request as unknown as jest.Mock;
const DICTS: Record<string, { itemName: string; itemValue: string }[]> = {
  inquiry_item_condition: [
    { itemName: '全新原装', itemValue: '1' },
    { itemName: '99新', itemValue: '2' },
  ],
  inquiry_lead_time: [
    { itemName: '现货', itemValue: '1' },
    { itemName: '1-2天', itemValue: '2' },
    { itemName: '1-2周', itemValue: '6' },
  ],
};

beforeEach(() => {
  mocked.mockReset();
  mocked.mockImplementation(
    (url: string, opts: { params?: Record<string, unknown> }) => {
      if (url === '/api/v1/system/dict-items') {
        const list = DICTS[opts.params?.dictType as string] ?? [];
        return Promise.resolve({
          data: list.map((d) => ({ ...d, status: 1 })),
        });
      }
      const p = opts.params ?? {};
      return Promise.resolve({
        data: {
          text: `A 1 $100 ${p.leadTime} ${p.condition}`,
          templateVersionNo: 2,
        },
      });
    },
  );
});

const textCalls = () =>
  mocked.mock.calls
    .filter(([url]) => String(url).endsWith('/text'))
    .map(([, opts]) => opts.params);

const open = () =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(TextQuoteModal, {
        quotation: { id: 7, quotationNo: 'QT1', status: 1, versionNo: 1 },
        open: true,
        onClose: jest.fn(),
        onMarkSent: jest.fn(),
      } as never),
    ),
  );

const pick = async (label: string, option: string) => {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: label }));
  const item = await screen.findByTitle(option);
  await act(async () => {
    fireEvent.click(item);
  });
};

describe('textOptions', () => {
  it('默认全新原装、按采购货期顺延；货期下拉第一项是顺延', () => {
    expect(textQuery(DEFAULT_TEXT_OPTIONS, 2)).toEqual({
      version: 2,
      condition: 1,
      leadTime: 'shift',
    });
    expect(textQuery({ condition: 3, leadTime: 6 })).toEqual({
      condition: 3,
      leadTime: '6',
    });
    expect(leadChoices([{ value: 1, label: '现货' }])).toEqual([
      { value: LEAD_SHIFT, label: '按采购货期顺延' },
      { value: 1, label: '现货' },
    ]);
  });
});

describe('TextQuoteModal', () => {
  it('打开时按默认生成，改选货期后重新生成', async () => {
    open();
    expect(await screen.findByDisplayValue('A 1 $100 shift 1')).toBeTruthy();
    expect(textCalls()[0]).toEqual({
      version: 1,
      condition: 1,
      leadTime: 'shift',
    });
    await pick('报价货期', '1-2周');
    await waitFor(() =>
      expect(textCalls().at(-1)).toEqual({
        version: 1,
        condition: 1,
        leadTime: '6',
      }),
    );
  });

  it('手改过文字时，改选前先确认', async () => {
    open();
    const box = await screen.findByDisplayValue('A 1 $100 shift 1');
    fireEvent.change(box, { target: { value: '我改过的文字' } });
    const before = textCalls().length;
    await pick('报价货况', '99新');
    expect(
      (await screen.findAllByText('重新生成会覆盖你改过的文字')).length,
    ).toBeGreaterThan(0);
    expect(textCalls().length).toBe(before);
    fireEvent.click(screen.getByRole('button', { name: '重新生成' }));
    await waitFor(() =>
      expect(textCalls().at(-1)).toMatchObject({ condition: 2 }),
    );
  });
});
