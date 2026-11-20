import { fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ModelQtyCell } from './DocPage';

/** openspec doc-list-unify：多个型号时「查看」弹出全部型号，单个型号不显示「查看」 */
const view = (el: React.ReactElement) =>
  render(React.createElement(App, null, el));

it('单个型号不显示查看', () => {
  view(
    React.createElement(ModelQtyCell, {
      items: [{ model: '1756-PB72', quantity: 1 }],
    }),
  );
  expect(screen.getByText('1 件')).toBeTruthy();
  expect(screen.queryByRole('button', { name: '查看' })).toBeNull();
});

it('多个型号点查看列出全部，能打开详情', () => {
  const open = jest.fn();
  view(
    React.createElement(ModelQtyCell, {
      items: [
        { model: '6ES7214-1AG40-0XB0', quantity: 10, brand: '西门子', category: 'PLC' },
        { model: '1756-PB72', quantity: 4, brand: 'AB', category: '电源' },
      ],
      docLabel: 'OB20261120001 · Arabian Automation LLC',
      onOpenDetail: open,
    }),
  );
  fireEvent.click(screen.getByRole('button', { name: '查看' }));
  expect(screen.getByText('2 个型号 · 14 件')).toBeTruthy();
  expect(screen.getByText('OB20261120001 · Arabian Automation LLC')).toBeTruthy();
  expect(screen.getByText('电源')).toBeTruthy();
  expect(screen.getAllByText('1756-PB72').length).toBeGreaterThan(0);
  fireEvent.click(screen.getByRole('button', { name: '打开详情' }));
  expect(open).toHaveBeenCalled();
});

it('退货类只有摘要时按摘要拆出型号', () => {
  view(
    React.createElement(ModelQtyCell, {
      summary: 'A-100 × 2、B-200 × 3',
      totalQty: 5,
    }),
  );
  expect(screen.getByText(/共 2 个型号 · 5 件/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '查看' }));
  expect(screen.getByText('B-200')).toBeTruthy();
  expect(screen.getByText('3')).toBeTruthy();
});
