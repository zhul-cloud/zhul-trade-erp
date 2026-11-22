import dayjs from 'dayjs';
import type { ReceivableRow } from './service';

/** 到期筛选：已逾期、7 天内到期、本月到期（都不含已逾期）、没约定付款日 */
export type DueFilter = 'OVERDUE' | 'WEEK' | 'MONTH' | 'NONE';

export interface ReceivableFilters {
  keyword?: string;
  ownerId?: number;
  currency?: string;
  due?: DueFilter;
}

export const matchDue = (r: ReceivableRow, due: DueFilter, today = dayjs()) => {
  if (due === 'OVERDUE') return r.overdueDays != null;
  if (due === 'NONE') return !r.dueDate;
  if (r.dueInDays == null || !r.dueDate) return false;
  if (due === 'WEEK') return r.dueInDays <= 7;
  return !dayjs(r.dueDate).isAfter(today.endOf('month'), 'day');
};

export const filterRows = (
  rows: ReceivableRow[],
  f: ReceivableFilters,
  today = dayjs(),
) => {
  const kw = f.keyword?.trim().toLowerCase();
  return rows.filter(
    (r) =>
      (!kw ||
        (r.customerName ?? '').toLowerCase().includes(kw) ||
        r.soNo.toLowerCase().includes(kw) ||
        (r.piNo ?? '').toLowerCase().includes(kw)) &&
      (f.ownerId == null || r.ownerId === f.ownerId) &&
      (!f.currency || r.currencyCode === f.currency) &&
      (!f.due || matchDue(r, f.due, today)),
  );
};

export interface CustomerGroup {
  customerId: number;
  customerName?: string;
  customerCountry?: string;
  customerContact?: string;
  owners: string[];
  orders: ReceivableRow[];
  /** 各币种未收（原币），按金额从大到小 */
  remaining: { currency: string; amount: number }[];
  remainingCny: number;
  overdueCny: number;
  /** 最早约定付款日那张（订单已按到期排好，取第一张有日期的） */
  earliest?: ReceivableRow;
}

const round2 = (n: number) => Math.round(n * 100) / 100;

/** 按客户汇总：逾期金额大的在前，再按最早到期；订单保持后端的到期顺序 */
export const groupByCustomer = (rows: ReceivableRow[]): CustomerGroup[] => {
  const map = new Map<number, CustomerGroup>();
  for (const r of rows) {
    let g = map.get(r.customerId);
    if (!g) {
      g = {
        customerId: r.customerId,
        customerName: r.customerName,
        customerCountry: r.customerCountry,
        customerContact: r.customerContact,
        owners: [],
        orders: [],
        remaining: [],
        remainingCny: 0,
        overdueCny: 0,
      };
      map.set(r.customerId, g);
    }
    g.orders.push(r);
    if (r.ownerName && !g.owners.includes(r.ownerName))
      g.owners.push(r.ownerName);
    const c = g.remaining.find((x) => x.currency === r.currencyCode);
    if (c) c.amount = round2(c.amount + Number(r.remainingAmount));
    else
      g.remaining.push({
        currency: r.currencyCode,
        amount: Number(r.remainingAmount),
      });
    g.remainingCny = round2(g.remainingCny + Number(r.remainingCny));
    if (r.overdueDays != null)
      g.overdueCny = round2(g.overdueCny + Number(r.remainingCny));
    if (!g.earliest && r.dueDate) g.earliest = r;
  }
  const groups = [...map.values()];
  for (const g of groups) g.remaining.sort((a, b) => b.amount - a.amount);
  return groups.sort(
    (a, b) =>
      b.overdueCny - a.overdueCny ||
      (a.earliest?.dueDate ?? '9999').localeCompare(
        b.earliest?.dueDate ?? '9999',
      ) ||
      b.remainingCny - a.remainingCny,
  );
};
