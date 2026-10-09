import { bargainOf, netPrice, rateOf, termsText, termsTotal } from './calc';

describe('purchase calc', () => {
  it('含税单价按不含税与目标价比较', () => {
    expect(netPrice(904, 1, true, 13)).toBeCloseTo(800, 6);
    expect(bargainOf(1000, 904, 60, 1, true, 13)).toBe(12000);
  });

  it('涨价为负', () => {
    const b = bargainOf(500, 587.6, 10, 1, true, 13);
    expect(b).toBe(-200);
    expect(rateOf(b, 5000)).toBe(-4);
  });

  it('整体砍价率按目标金额加权', () => {
    expect(rateOf(11800, 65000)).toBe(18.15);
  });

  it('没有目标价、没有单价、目标金额为 0', () => {
    expect(bargainOf(null, 100, 1, 1, false, 0)).toBeNull();
    expect(bargainOf(100, null, 1, 1, false, 0)).toBeNull();
    expect(rateOf(10, 0)).toBeNull();
  });

  it('外币先折人民币再去税', () => {
    expect(bargainOf(700, 100, 3, 7.1, true, 13)).toBe(215.04);
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
});
