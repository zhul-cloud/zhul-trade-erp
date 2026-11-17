import { amountTotals, currencyTotals, groupPayables } from './batch';
import type { Payable } from './service';

const ap = (p: Partial<Payable>) =>
  ({
    id: 1,
    payableNo: 'AP1',
    payeeType: 1,
    supplierId: 10,
    payeeName: '华强电子',
    currencyCode: 'USD',
    unpaidAmount: 100,
    ...p,
  }) as Payable;

describe('groupPayables', () => {
  it('按收款方 + 币种分组，组内按到期日从早到晚、无到期日排最后', () => {
    const groups = groupPayables([
      ap({
        id: 1,
        payableNo: 'AP3',
        nextDueDate: '2026-11-15',
        unpaidAmount: 1260,
      }),
      ap({ id: 2, payableNo: 'AP1', unpaidAmount: 50 }),
      ap({
        id: 3,
        payableNo: 'AP2',
        nextDueDate: '2026-11-12',
        unpaidAmount: 2400,
      }),
      ap({
        id: 4,
        supplierId: 11,
        payeeName: '顺丰',
        currencyCode: 'CNY',
        unpaidAmount: 9800,
      }),
      ap({ id: 5, currencyCode: 'CNY', unpaidAmount: 10 }),
    ]);
    expect(groups.map((g) => [g.payeeName, g.currency, g.unpaid])).toEqual([
      ['华强电子', 'USD', 3710],
      ['顺丰', 'CNY', 9800],
      ['华强电子', 'CNY', 10],
    ]);
    expect(groups[0].rows.map((r) => r.id)).toEqual([3, 1, 2]);
  });
});

describe('currencyTotals', () => {
  it('各币种合计', () => {
    expect(
      currencyTotals([
        ap({ unpaidAmount: 0.1 }),
        ap({ unpaidAmount: 0.2 }),
        ap({ currencyCode: 'CNY', unpaidAmount: 5 }),
      ]),
    ).toBe('USD 0.30 · CNY 5.00');
  });
});

describe('当期应付', () => {
  it('每组默认金额为各单当期应付之和，说明带应付单号；没有当期应付时取未付', () => {
    const [g] = groupPayables([
      ap({
        id: 1,
        payableNo: 'AP1',
        nextDueDate: '2026-11-12',
        unpaidAmount: 2400,
        dueNowAmount: 720,
        dueNowNote: '第 1 期 30% 下单后，11-12 到期',
      }),
      ap({
        id: 2,
        payableNo: 'AP2',
        nextDueDate: '2026-11-15',
        unpaidAmount: 1260,
      }),
    ]);
    expect(g.unpaid).toBe(3660);
    expect(g.dueNow).toBe(1980);
    expect(g.dueNowNote).toBe('AP1 第 1 期 30% 下单后，11-12 到期');
  });

  it('弹窗顶部按各组本次金额合计', () => {
    const groups = groupPayables([
      ap({ id: 1, unpaidAmount: 100 }),
      ap({ id: 2, supplierId: 11, currencyCode: 'CNY', unpaidAmount: 5 }),
    ]);
    expect(
      amountTotals(groups, (g) => (g.currency === 'USD' ? 30 : null)),
    ).toBe('USD 30.00 · CNY 0.00');
  });
});
