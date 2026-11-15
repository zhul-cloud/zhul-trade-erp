import { render } from '@testing-library/react';
import React from 'react';
import OrderCostCard from './CostCard';

/** audit UI-063：成本卡合计行的实际成本、退款、行收入、行毛利要落在各自的列下 */
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));

const line = {
  soItemId: 1,
  model: '6ES7214-1AG40-0XB0',
  quantity: 1,
  purchase: 100,
  purchaseSettled: true,
  invoice: 0,
  poFee: 0,
  claim: 0,
  returnFreight: 0,
  salesReturnCredit: 0,
  domestic: 0,
  international: 0,
  internationalSettled: true,
  expense: 0,
  receiptFee: 0,
  cost: 1111.11,
  revenue: 3333.33,
  refund: 222.22,
  profit: 1999.99,
  profitRate: 60,
  unreceivedQty: 0,
  unorderedQty: 0,
  missingCostPrice: false,
  sources: {},
};

const cost = {
  soId: 9,
  salesAmountCny: 3333.33,
  revenue: 3333.33,
  cost: 1111.11,
  receiptFee: 0,
  refund: 222.22,
  refunds: [],
  profit: 1999.99,
  profitRate: 60,
  settled: true,
  pending: [],
  missingCostCount: 0,
  lines: [line],
};

/** 合计行里包含 text 的单元格起始列号（按 colSpan 累加） */
const summaryColumn = (container: HTMLElement, text: string) => {
  const cells = Array.from(
    container.querySelectorAll<HTMLTableCellElement>(
      '.ant-table-summary tr td',
    ),
  );
  let col = 0;
  for (const c of cells) {
    if (c.textContent?.includes(text)) return col;
    col += c.colSpan || 1;
  }
  return -1;
};

const headerColumn = (container: HTMLElement, title: string) =>
  Array.from(container.querySelectorAll('.ant-table-thead th')).findIndex(
    (th) => th.textContent === title,
  );

it('普通订单：合计对齐实际成本、退款、行收入、行毛利', () => {
  const { container } = render(
    React.createElement(OrderCostCard, { cost: cost as never }),
  );
  expect(summaryColumn(container, '1,111.11')).toBe(
    headerColumn(container, '实际成本'),
  );
  expect(summaryColumn(container, '222.22')).toBe(
    headerColumn(container, '退款'),
  );
  expect(summaryColumn(container, '3,333.33')).toBe(
    headerColumn(container, '行收入'),
  );
  expect(summaryColumn(container, '1,999.99')).toBe(
    headerColumn(container, '行毛利'),
  );
});

it('售后补发订单：只有实际成本合计，也对齐', () => {
  const { container } = render(
    React.createElement(OrderCostCard, {
      cost: {
        ...cost,
        profit: null,
        replacementOfId: 3,
        replacementOfNo: 'SO20261101001',
      } as never,
    }),
  );
  expect(headerColumn(container, '行毛利')).toBe(-1);
  expect(summaryColumn(container, '1,111.11')).toBe(
    headerColumn(container, '实际成本'),
  );
});
