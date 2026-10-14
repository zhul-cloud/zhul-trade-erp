import {
  bargainOf,
  defaultPrice,
  invoiceCostOf,
  netPrice,
  rateOf,
  termsText,
  termsTotal,
} from './calc';

describe('purchase calc', () => {
  it('单价直接与目标价比较', () => {
    expect(netPrice(800, 1)).toBeCloseTo(800, 6);
    expect(bargainOf(1000, 800, 60, 1)).toBe(12000);
  });

  it('涨价为负', () => {
    const b = bargainOf(500, 520, 10, 1);
    expect(b).toBe(-200);
    expect(rateOf(b, 5000)).toBe(-4);
  });

  it('整体砍价率按目标金额加权', () => {
    expect(rateOf(11800, 65000)).toBe(18.15);
  });

  it('没有目标价、没有单价、目标金额为 0', () => {
    expect(bargainOf(null, 100, 1, 1)).toBeNull();
    expect(bargainOf(100, null, 1, 1)).toBeNull();
    expect(rateOf(10, 0)).toBeNull();
  });

  it('外币折人民币后比较', () => {
    expect(bargainOf(720, 100, 3, 7.1)).toBe(30);
  });

  it('开票成本 = 开票金额 × 税点', () => {
    expect(invoiceCostOf(16000, 3)).toBe(480);
    expect(invoiceCostOf(26767, 2.03)).toBe(543.37);
    expect(invoiceCostOf(16000, 0)).toBe(0);
  });

  it('付款条件文字与合计', () => {
    expect(termsText([{ percent: 100, trigger: 1 }])).toBe('全额预付');
    expect(termsText([{ percent: 100, trigger: 3, days: 0 }])).toBe('货到付款');
    expect(termsText([{ percent: 100, trigger: 3, days: 30 }])).toBe(
      '入库后 30 天',
    );
    expect(
      termsText([
        { percent: 30, trigger: 1 },
        { percent: 70, trigger: 3 },
      ]),
    ).toBe('30% 下单后 · 70% 入库后');
    expect(
      termsTotal([
        { percent: 30, trigger: 1 },
        { percent: 60, trigger: 3 },
      ]),
    ).toBe(90);
  });

  it('默认单价按汇率折算目标价', () => {
    expect(defaultPrice(1000, 1)).toBe(1000);
    expect(defaultPrice(700, 7.1)).toBe(98.59);
    expect(defaultPrice(null, 1)).toBeNull();
    expect(bargainOf(500, defaultPrice(500, 1), 3, 1)).toBe(0);
  });
});
