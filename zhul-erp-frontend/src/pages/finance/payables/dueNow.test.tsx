import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { DueNowHint, dueNowOf } from './dueNow';

/** spec finance/supplier-payment「登记付款」：默认付当期应付，可一键付全部未付 */

const hint = (amount: number, onChange = jest.fn()) =>
  render(
    React.createElement(DueNowHint, {
      amount,
      due: 3000,
      unpaid: 10000,
      note: '第 1 期 30% 下单后，11-10 到期',
      currency: 'CNY',
      onChange,
    }),
  );

describe('DueNowHint', () => {
  it('默认显示组成，点「付全部未付」改为全部未付', () => {
    const onChange = jest.fn();
    hint(3000, onChange);
    expect(
      screen.getByText('当期应付：第 1 期 30% 下单后，11-10 到期'),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /付全部未付/ }));
    expect(onChange).toHaveBeenCalledWith(10000);
  });

  it('已是全部未付时可以改回当期应付', () => {
    const onChange = jest.fn();
    hint(10000, onChange);
    expect(screen.getByText('全部未付')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /改回当期应付/ }));
    expect(onChange).toHaveBeenCalledWith(3000);
  });

  it('没有付款计划且当期应付就是全部未付时不显示', () => {
    const { container } = render(
      React.createElement(DueNowHint, {
        amount: 500,
        due: 500,
        unpaid: 500,
        note: '',
        currency: 'CNY',
        onChange: jest.fn(),
      }),
    );
    expect(container.textContent).toBe('');
  });

  it('接口没带当期应付时退回未付', () => {
    expect(dueNowOf({ unpaidAmount: 88.5 } as never)).toBe(88.5);
    expect(dueNowOf({ unpaidAmount: 100, dueNowAmount: 30 } as never)).toBe(30);
  });
});
