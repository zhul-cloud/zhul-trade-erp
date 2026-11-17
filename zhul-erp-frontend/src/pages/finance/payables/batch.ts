import { formatAmount } from '@/utils/format';
import { dueNowOfRows } from './dueNow';
import type { Payable } from './service';

/** 一次最多勾选的应付单数，与后端一致 */
export const MAX_BATCH_PAYABLES = 50;

const round2 = (n: number) => Math.round(n * 100) / 100;

export interface Group {
  key: string;
  payeeName: string;
  supplierId?: number;
  currency: string;
  /** 按到期日从早到晚，与后端核销顺序一致 */
  rows: Payable[];
  unpaid: number;
  /** 当期应付合计：每组的默认付款金额 */
  dueNow: number;
  /** 当期应付由哪几期组成（多张时带应付单号） */
  dueNowNote: string;
}

const byDue = (a: Payable, b: Payable) =>
  (a.nextDueDate ?? '9999').localeCompare(b.nextDueDate ?? '9999') ||
  a.payableNo.localeCompare(b.payableNo);

/** 按「收款方 + 币种」分组：每组生成一笔付款单 */
export const groupPayables = (rows: Payable[]): Group[] => {
  const map = new Map<string, Group>();
  for (const r of rows) {
    const key = `${r.payeeType}-${r.supplierId ?? r.payeeName}-${r.currencyCode}`;
    const g = map.get(key) ?? {
      key,
      payeeName: r.payeeName,
      supplierId: r.supplierId,
      currency: r.currencyCode,
      rows: [],
      unpaid: 0,
      dueNow: 0,
      dueNowNote: '',
    };
    g.rows.push(r);
    g.unpaid = round2(g.unpaid + r.unpaidAmount);
    map.set(key, g);
  }
  return [...map.values()].map((g) => {
    const rows = g.rows.sort(byDue);
    const now = dueNowOfRows(rows);
    return { ...g, rows, dueNow: now.amount, dueNowNote: now.note };
  });
};

/** 各组本次付款金额按币种合计（批量付款弹窗顶部），如「USD 3,180.00 · CNY 12,300.00」 */
export const amountTotals = (
  groups: Group[],
  amountOf: (g: Group) => number | null | undefined,
) => {
  const sum = new Map<string, number>();
  for (const g of groups)
    sum.set(g.currency, (sum.get(g.currency) ?? 0) + (amountOf(g) ?? 0));
  return [...sum.entries()]
    .map(([c, v]) => formatAmount(round2(v), c))
    .join(' · ');
};

/** 各币种合计，如「USD 4,860.00 · CNY 12,300.00」 */
export const currencyTotals = (rows: Payable[]) => {
  const sum = new Map<string, number>();
  for (const r of rows)
    sum.set(r.currencyCode, (sum.get(r.currencyCode) ?? 0) + r.unpaidAmount);
  return [...sum.entries()]
    .map(([c, v]) => formatAmount(round2(v), c))
    .join(' · ');
};
