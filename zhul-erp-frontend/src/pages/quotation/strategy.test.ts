import { calcLine, MODE_PRICE } from './calc';
import {
  costTotalForeign,
  itemTotal,
  profitCny,
  roundPrice,
  type StrategyLine,
  targetProfit,
  targetTotal,
  tiered,
  tierMargin,
  uniformMargin,
} from './strategy';

const RATE = 7.15;
const marginOf = (l: StrategyLine, price: number) =>
  calcLine({
    costPrice: l.costPrice,
    quantity: l.quantity,
    rate: RATE,
    mode: MODE_PRICE,
    unitPrice: price,
  }).marginRate as number;

const three: StrategyLine[] = [
  { id: 1, costPrice: 520, quantity: 1, floorMargin: 10 },
  { id: 2, costPrice: 2640, quantity: 1, floorMargin: 10 },
  { id: 3, costPrice: 85, quantity: 1, floorMargin: 10 },
];

describe('统一毛利率', () => {
  it('按 15% 定价（规格示例）', () => {
    const p = uniformMargin(three, 15, RATE, 'none');
    expect([p.get(1), p.get(2), p.get(3)]).toEqual([85.56, 434.39, 13.99]);
    expect(marginOf(three[0], 85.56)).toBeCloseTo(15, 1);
  });
  it('叠加尾数 .9', () => {
    expect(uniformMargin(three, 15, RATE, 'p9').get(1)).toBe(85.9);
  });
});

describe('尾数取整', () => {
  it.each([
    ['integer', 85.56, 86],
    ['integer', 85.44, 85],
    ['p9', 85.95, 86.9],
    ['p9', 85.9, 85.9],
    ['p5', 85.56, 86],
    ['p5', 85.2, 85.5],
    ['five', 81, 85],
    ['ten', 85.56, 90],
    ['none', 85.555, 85.56],
  ] as const)('%s %p → %p', (rule, price, expected) => {
    expect(roundPrice(price, rule)).toBe(expected);
  });
});

describe('按金额分层', () => {
  const tiers = [
    { maxCost: 300, marginRate: 35 },
    { maxCost: 3000, marginRate: 20 },
    { maxCost: null, marginRate: 12 },
  ];
  it('按采购成本价取档（规格示例）', () => {
    expect([85, 1200, 8000].map((c) => tierMargin(c, tiers))).toEqual([
      35, 20, 12,
    ]);
    expect(tierMargin(300, tiers)).toBe(35);
  });
  it('算出售价', () => {
    const p = tiered(
      [{ id: 1, costPrice: 1200, quantity: 2 }],
      tiers,
      RATE,
      'none',
    );
    expect(p.get(1)).toBe(209.79);
  });
});

describe('目标利润', () => {
  const lines: StrategyLine[] = [
    { id: 1, costPrice: 2640, quantity: 2, floorMargin: 10 },
    { id: 2, costPrice: 1680, quantity: 2, floorMargin: 10 },
    { id: 3, costPrice: 3100, quantity: 1, floorMargin: 15 },
    { id: 4, costPrice: 85, quantity: 10, floorMargin: 10 },
  ];
  const opts = { rate: RATE, spread: 3, seed: 7, rounding: 'none' as const };

  it('合计利润等于目标，每行不低于红线、毛利率被打散', () => {
    const r = targetProfit(lines, 5000, opts);
    expect(r.belowFloor).toBe(false);
    expect(Math.abs(profitCny(lines, r.prices, RATE) - 5000)).toBeLessThan(
      RATE,
    );
    const margins = lines.map((l) => marginOf(l, r.prices.get(l.id) as number));
    margins.forEach((m, i) => {
      expect(m).toBeGreaterThanOrEqual((lines[i].floorMargin as number) - 0.2);
    });
    expect(new Set(margins.map((m) => m.toFixed(1))).size).toBeGreaterThan(1);
  });

  it('换一组：种子不同结果不同，合计仍约等于目标', () => {
    const a = targetProfit(lines, 5000, opts);
    const b = targetProfit(lines, 5000, { ...opts, seed: 8 });
    expect([...a.prices.values()]).not.toEqual([...b.prices.values()]);
    expect(Math.abs(profitCny(lines, b.prices, RATE) - 5000)).toBeLessThan(
      RATE,
    );
  });

  it('同一个种子结果相同（预览与应用一致）', () => {
    expect([...targetProfit(lines, 5000, opts).prices.values()]).toEqual([
      ...targetProfit(lines, 5000, opts).prices.values(),
    ]);
  });

  it('目标低于按红线定价的利润：提示，且允许低于红线', () => {
    const r = targetProfit(lines, 500, opts);
    expect(r.belowFloor).toBe(true);
    expect(r.floorProfit).toBeGreaterThan(500);
    expect(Math.abs(profitCny(lines, r.prices, RATE) - 500)).toBeLessThan(RATE);
  });

  it('叠加取整：结果为整数，合计接近目标', () => {
    const r = targetProfit(lines, 5000, { ...opts, rounding: 'integer' });
    for (const p of r.prices.values()) expect(Number.isInteger(p)).toBe(true);
    expect(Math.abs(profitCny(lines, r.prices, RATE) - 5000)).toBeLessThan(200);
  });
});

describe('目标总价', () => {
  const lines: StrategyLine[] = [
    { id: 1, costPrice: 2640, quantity: 2, floorMargin: 10 },
    { id: 2, costPrice: 1680, quantity: 2, floorMargin: 10 },
    { id: 3, costPrice: 3100, quantity: 1, floorMargin: 10 },
  ];
  it('型号小计合计等于目标', () => {
    const p = targetTotal(lines, 5000, {
      rate: RATE,
      spread: 0,
      seed: 1,
      rounding: 'none',
    });
    expect(Math.abs(itemTotal(lines, p) - 5000)).toBeLessThan(0.005);
  });
  it('采购成本合计折外币', () => {
    expect(costTotalForeign(lines, RATE)).toBeCloseTo(
      (2640 * 2 + 1680 * 2 + 3100) / RATE,
      6,
    );
  });
});
