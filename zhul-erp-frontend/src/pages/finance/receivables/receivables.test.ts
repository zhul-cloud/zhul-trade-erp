import dayjs from 'dayjs';
import { filterRows, groupByCustomer } from './receivables';
import type { ReceivableRow } from './service';

const row = (p: Partial<ReceivableRow>): ReceivableRow => ({
  orderId: 1,
  soNo: 'SO1',
  customerId: 1,
  customerName: 'Acme',
  ownerId: 9,
  ownerName: '李娜',
  currencyCode: 'USD',
  totalAmount: 100,
  receivedAmount: 0,
  remainingAmount: 100,
  remainingCny: 720,
  ...p,
});

const today = dayjs('2026-11-22');
const rows = [
  row({ orderId: 1, soNo: 'SO1', dueDate: '2026-11-10', overdueDays: 12 }),
  row({ orderId: 2, soNo: 'SO2', dueDate: '2026-11-25', dueInDays: 3 }),
  row({
    orderId: 3,
    soNo: 'SO3',
    dueDate: '2026-12-05',
    dueInDays: 13,
    currencyCode: 'EUR',
    remainingAmount: 50,
    remainingCny: 390,
  }),
  row({
    orderId: 4,
    soNo: 'SO4',
    customerId: 2,
    customerName: 'Bolt',
    ownerId: 8,
    ownerName: '王磊',
  }),
];

describe('应收管理筛选与按客户汇总', () => {
  it('到期筛选：逾期、7 天内、本月、未约定互不混淆', () => {
    const ids = (due: 'OVERDUE' | 'WEEK' | 'MONTH' | 'NONE') =>
      filterRows(rows, { due }, today).map((r) => r.orderId);
    expect(ids('OVERDUE')).toEqual([1]);
    expect(ids('WEEK')).toEqual([2]);
    expect(ids('MONTH')).toEqual([2]);
    expect(ids('NONE')).toEqual([4]);
  });

  it('关键词、业务员、币种叠加筛选', () => {
    expect(
      filterRows(rows, { keyword: 'bolt' }, today).map((r) => r.orderId),
    ).toEqual([4]);
    expect(
      filterRows(rows, { ownerId: 9, currency: 'EUR' }, today).map(
        (r) => r.orderId,
      ),
    ).toEqual([3]);
  });

  it('按客户汇总：分币种未收、折人民币、逾期金额，逾期客户在前', () => {
    const g = groupByCustomer(rows);
    expect(g.map((x) => x.customerId)).toEqual([1, 2]);
    expect(g[0].remaining).toEqual([
      { currency: 'USD', amount: 200 },
      { currency: 'EUR', amount: 50 },
    ]);
    expect(g[0].remainingCny).toBe(1830);
    expect(g[0].overdueCny).toBe(720);
    expect(g[0].earliest?.soNo).toBe('SO1');
    expect(g[1].earliest).toBeUndefined();
  });
});
