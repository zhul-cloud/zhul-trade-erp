import {
  lineAfter,
  marginAfter,
  maxExtraDiscount,
  points,
  revenueCnyAfter,
  summarize,
} from './bargain';

/** 与后端 PiBargainTest、规格「议价测算」场景同一组数字：小计 USD 1,000、成本 CNY 5,400、红线 10%、汇率 6.65 */
const lines = [
  { model: 'A', quantity: 2, costPrice: 1500, amount: 600, floorMargin: 10 },
  { model: 'B', quantity: 1, costPrice: 2400, amount: 400, floorMargin: 10 },
];
const RATE = 6.65;

describe('议价测算', () => {
  it('最多还能让', () => {
    const s = summarize(lines);
    expect(s.costTotal).toBe(5400);
    expect(marginAfter(s, 0, RATE)?.toFixed(1)).toBe('18.8');
    const max = maxExtraDiscount(s, 0, RATE) as number;
    expect(max).toBe(97.74);
    expect(points(max, s.itemAmount)).toBe(9.7);
    expect(marginAfter(s, max, RATE) as number).toBeGreaterThanOrEqual(10);
    expect(revenueCnyAfter(s, max, RATE).toFixed(2)).toBe('6000.03');
  });

  it('按目标总价与点数试算', () => {
    const s = summarize(lines);
    expect(marginAfter(s, 50, RATE)?.toFixed(1)).toBe('14.5');
    expect((10 - (marginAfter(s, 120, RATE) as number)).toFixed(1)).toBe('2.3');
    expect(maxExtraDiscount(s, 50, RATE)).toBe(47.74);
    expect(maxExtraDiscount(s, 200, RATE)).toBe(0);
  });

  it('加权红线、没有成本的行不参与、单行分摊', () => {
    const s = summarize([
      {
        model: 'A',
        quantity: 1,
        costPrice: 3000,
        amount: 600,
        floorMargin: 10,
      },
      {
        model: 'B',
        quantity: 1,
        costPrice: 2000,
        amount: 400,
        floorMargin: 15,
      },
      { model: 'C', quantity: 1, costPrice: null, amount: 100 },
    ]);
    expect(s.floorMargin).toBe(12);
    expect(s.costedRevenue).toBe(1000);
    expect(maxExtraDiscount(s, 0, 6.6) as number).toBeLessThan(
      maxExtraDiscount(s, 0, RATE) as number,
    );
    expect(
      maxExtraDiscount(
        summarize([{ model: 'C', quantity: 1, amount: 100 }]),
        0,
        RATE,
      ),
    ).toBeNull();
    const l = lineAfter(lines[0], summarize(lines), 100, RATE);
    expect(l.revenueCny.toFixed(2)).toBe('3591.00');
    expect(l.margin?.toFixed(1)).toBe('16.5');
  });
});
