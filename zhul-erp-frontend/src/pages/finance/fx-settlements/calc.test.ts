import { settleCny, settleRate } from './calc';

describe('结汇汇率换算', () => {
  test('金额 × 汇率四舍五入到分', () => {
    expect(settleCny(2500, 7.123456)).toBe(17808.64);
    expect(settleCny(2500, 7.12)).toBe(17800);
  });

  test('半分向上进位（HALF_UP），不受浮点误差影响', () => {
    // 2.01 × 0.5 = 1.005，浮点乘法得 1.00499…，直接 Math.round 会得到 1.00
    expect(settleCny(2.01, 0.5)).toBe(1.01);
    expect(settleCny(0.01, 0.5)).toBe(0.01);
    expect(settleCny(0.01, 0.499999)).toBe(0);
  });

  test('零值与超大金额', () => {
    expect(settleCny(0, 7.1)).toBe(0);
    expect(settleCny(99_999_999_999.99, 7.123456)).toBe(712_345_599_999.93);
  });

  test('按实际入账反算汇率保留 6 位', () => {
    expect(settleRate(2500, 17808.6)).toBe(7.12344);
    expect(settleRate(3, 10)).toBe(3.333333);
    expect(settleRate(3, 20)).toBe(6.666667);
    expect(settleRate(0, 10)).toBe(0);
  });
});
