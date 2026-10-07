import { calcDiscount, calcPiLine, calcPiTotals, type PiLine } from './calc';

// 与后端 PiCalculatorTest / spec sales/proforma-invoice「整单折扣 5%」一致
const USD = 7.15;

const totals = (
  lines: PiLine[],
  fees: number[],
  type: number,
  value: number | null,
) =>
  calcPiTotals(
    lines.map((line) => ({ line, result: calcPiLine(line, USD) })),
    fees,
    type,
    value,
    USD,
  );

describe('PI 合计与整单折扣（与后端 PiCalculator 同一口径）', () => {
  const four: PiLine[] = [
    { costPrice: 4500, quantity: 1, unitPrice: 740.44 },
    { costPrice: 1230, quantity: 2, unitPrice: 180 },
    { costPrice: 420, quantity: 2, unitPrice: 65.73 },
    { costPrice: 350, quantity: 2, unitPrice: 54.39 },
  ];

  it('整单折扣 5%', () => {
    const t = totals(four, [60, 0], 1, 5);
    expect(t.itemAmount).toBe(1340.68);
    expect(t.discountAmount).toBe(67.03);
    expect(t.totalAmount).toBe(1333.65);
    expect(t.totalAmountCny).toBe(9535.6);
    expect(t.netProfit).toBe(84.84);
    expect(t.netProfitCny).toBe(606.6);
    expect(t.marginRate).toBeCloseTo(6.7, 1);
  });

  it('没有折扣时净利润等于各行之和', () => {
    const t = totals(
      [{ costPrice: 520, quantity: 2, unitPrice: 85.56 }],
      [],
      0,
      null,
    );
    expect(t.discountAmount).toBe(0);
    expect(t.netProfit).toBe(25.67);
    expect(t.netProfitCny).toBe(183.51);
  });

  it('折扣规则', () => {
    expect(calcDiscount(2, 2000, 1340.68).error).toBe('折扣不能超过型号小计');
    expect(calcDiscount(1, 101, 10).error).toBe('折扣百分比需要在 0–100% 之间');
    expect(calcDiscount(2, -1, 10).error).toBe('折扣不能为负');
    expect(calcDiscount(2, 42.555, 100).amount).toBe(42.56);
    expect(calcDiscount(1, 100, 100)).toEqual({ amount: 100 });
  });

  it('没有采购成本的行不算利润', () => {
    const t = totals(
      [{ costPrice: null, quantity: 1, unitPrice: 120 }],
      [60],
      2,
      20,
    );
    expect(t.totalAmount).toBe(160);
    expect(t.netProfit).toBeNull();
    expect(t.marginRate).toBeNull();
  });
});
