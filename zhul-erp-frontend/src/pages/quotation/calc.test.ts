import {
  calcLine,
  calcTotals,
  formatMargin,
  MODE_MARGIN,
  MODE_MARKUP,
  MODE_PRICE,
  round2,
} from './calc';

// 与后端 QuotationPricingTest / spec quotation/pricing-rule 的例子一致
const USD = 7.15;

describe('calcLine（与后端 QuotationPricing 同一口径）', () => {
  it('按毛利率定价', () => {
    const r = calcLine({
      costPrice: 2640,
      quantity: 1,
      rate: USD,
      mode: MODE_MARGIN,
      marginRate: 10,
    });
    expect(r.unitPriceCny).toBe(2933.33);
    expect(r.unitPrice).toBe(410.26);
    expect(r.netProfit).toBe(41.03);
    expect(r.netProfitCny).toBe(293.36);
    expect(r.costPriceForeign).toBe(369.23);
  });

  it('按加价定价', () => {
    const r = calcLine({
      costPrice: 200,
      quantity: 1,
      rate: USD,
      mode: MODE_MARKUP,
      markupAmount: 50,
    });
    expect(r.unitPriceCny).toBe(250);
    expect(r.unitPrice).toBe(34.97);
    expect(formatMargin(r.marginRate)).toBe('20.0%');
    expect(r.netProfit).toBe(7);
    expect(r.netProfitCny).toBe(50.04);
  });

  it('改售价反算毛利率', () => {
    const r = calcLine({
      costPrice: 2640,
      quantity: 1,
      rate: USD,
      mode: MODE_PRICE,
      unitPrice: 400,
    });
    expect(formatMargin(r.marginRate)).toBe('7.7%');
    expect(r.netProfit).toBe(30.77);
    expect(r.netProfitCny).toBe(220);
  });

  it('改毛利率 15%、数量 2', () => {
    const r = calcLine({
      costPrice: 520,
      quantity: 2,
      rate: USD,
      mode: MODE_MARGIN,
      marginRate: 15,
    });
    expect(r.unitPriceCny).toBe(611.76);
    expect(r.unitPrice).toBe(85.56);
    expect(r.amount).toBe(171.12);
    expect(r.netProfit).toBe(25.67);
    expect(r.netProfitCny).toBe(183.51);
  });

  it('没有采购成本价只能填售价，毛利率与净利润为空', () => {
    const r = calcLine({
      costPrice: null,
      quantity: 1,
      rate: USD,
      mode: MODE_MARGIN,
      marginRate: 10,
      unitPrice: 120,
    });
    expect(r.unitPrice).toBe(120);
    expect(r.marginRate).toBeNull();
    expect(r.netProfit).toBeNull();
  });

  it('非法毛利率给出说明', () => {
    expect(
      calcLine({
        costPrice: 100,
        quantity: 1,
        rate: USD,
        mode: MODE_MARGIN,
        marginRate: 100,
      }).error,
    ).toBeTruthy();
    expect(
      calcLine({
        costPrice: 100,
        quantity: 1,
        rate: USD,
        mode: MODE_MARKUP,
        markupAmount: -1,
      }).error,
    ).toBeTruthy();
  });
});

describe('calcTotals', () => {
  it('费用计入合计，不计入净利润与毛利率', () => {
    const r = calcLine({
      costPrice: 520,
      quantity: 2,
      rate: USD,
      mode: MODE_MARGIN,
      marginRate: 15,
    });
    const t = calcTotals(
      [{ costPrice: 520, quantity: 2, result: r }],
      [60],
      USD,
    );
    expect(t.totalAmount).toBe(231.12);
    expect(t.totalAmountCny).toBe(1652.51);
    expect(t.netProfit).toBe(25.67);
    expect(formatMargin(t.marginRate)).toBe('15.0%');
  });
});

describe('round2', () => {
  it('HALF_UP', () => {
    expect(round2(1.005)).toBe(1.01);
    expect(round2(-1.005)).toBe(-1.01);
    expect(round2(2.675)).toBe(2.68);
  });
});
