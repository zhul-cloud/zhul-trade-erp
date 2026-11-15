import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { SupplyConfirmModal, supplyRisks } from './dialogs';

/** 供应链第三期 spec quotation「发送前确认供应」：有供应提示时先确认，勾选后才能发送 */

jest.mock('@umijs/max', () => ({ request: jest.fn() }));

const item = (id: number, model: string, supplyHints: string[] = []) => ({
  id,
  model,
  brand: 'Siemens',
  hints: [],
  supplyHints,
});

const quotation = (over: Record<string, unknown> = {}) =>
  ({
    id: 1,
    status: 1,
    versionNo: 1,
    currentVersionNo: 1,
    editingVersionNo: undefined,
    items: [
      item(10, '6ES7214-1AG40-0XB0', ['成本 45 天前']),
      item(11, '1756-L72'),
      item(12, 'MR-J4-70A', ['成本 38 天前', '货源未验证']),
    ],
    ...over,
  }) as never;

describe('supplyRisks', () => {
  it('草稿与修改中的新版本：列出有供应提示的行', () => {
    expect(supplyRisks(quotation()).map((i) => i.id)).toEqual([10, 12]);
    expect(
      supplyRisks(quotation({ status: 2, editingVersionNo: 2, versionNo: 2 }))
        .length,
    ).toBe(2);
  });

  it('没有提示直接发送；已发送版本「记一次发送」不再确认', () => {
    expect(supplyRisks(quotation({ items: [item(11, '1756-L72')] }))).toEqual(
      [],
    );
    expect(supplyRisks(quotation({ status: 2 }))).toEqual([]);
  });
});

describe('SupplyConfirmModal', () => {
  it('逐行列出型号与提示，勾选后才能确认并发送', async () => {
    const onConfirm = jest.fn(() => Promise.resolve());
    render(
      React.createElement(SupplyConfirmModal, {
        quotation: quotation(),
        channel: 3,
        onClose: jest.fn(),
        onConfirm,
      }),
    );
    expect(await screen.findByText('6ES7214-1AG40-0XB0')).toBeTruthy();
    expect(screen.getByText('成本 38 天前 · 货源未验证')).toBeTruthy();
    expect(screen.queryByText('1756-L72')).toBeNull();
    const send = screen.getByRole('button', { name: /确认并发送/ });
    expect(send.hasAttribute('disabled')).toBe(true);
    fireEvent.click(
      screen.getByRole('checkbox', {
        name: '已向供应商确认这些型号的最新成本和交期',
      }),
    );
    expect(send.hasAttribute('disabled')).toBe(false);
    fireEvent.click(send);
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith(3));
  });
});
