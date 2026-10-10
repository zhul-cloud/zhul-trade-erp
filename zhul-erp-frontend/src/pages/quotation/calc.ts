/**
 * 报价计算（与后端 QuotationPricing / QuotationCalculator 同一口径，改一边要同步另一边）：
 * 毛利率按售价计算：售价(CNY) = 采购成本价 ÷ (1 − 毛利率)；按加价：售价(CNY) = 采购成本价 + 加价；
 * 直接填外币售价：售价(CNY) = 外币售价 × 汇率。外币售价 = 售价(CNY) ÷ 汇率（两位小数）；
 * 毛利率 = 1 − 采购成本价 ÷ (外币售价 × 汇率)；小计 = 外币售价 × 数量；
 * 净利润(外币) = 小计 − 采购成本价 × 数量 ÷ 汇率；净利润(CNY) = 小计 × 汇率 − 采购成本价 × 数量。
 * 金额只在显示时舍入到 2 位（HALF_UP）。这里只用于编辑时即时显示，保存后以后端结果为准。
 */

export const MODE_MARGIN = 1;
export const MODE_MARKUP = 2;
export const MODE_PRICE = 3;

/** 两位小数 HALF_UP（加一个极小量抵消二进制浮点误差，如 1.005） */
export const round2 = (v: number) => {
  const sign = v < 0 ? -1 : 1;
  const abs = Math.abs(v);
  return (sign * Math.round(abs * 100 * (1 + Number.EPSILON * 4) + 1e-9)) / 100;
};

export interface LineInput {
  costPrice?: number | null;
  quantity: number;
  rate: number;
  mode: number;
  marginRate?: number | null;
  markupAmount?: number | null;
  unitPrice?: number | null;
}

export interface LineResult {
  costPriceForeign: number | null;
  unitPriceCny: number;
  unitPrice: number;
  /** 百分数，两位小数；没有成本或售价为 0 时为 null */
  marginRate: number | null;
  amount: number;
  amountCny: number;
  netProfit: number | null;
  netProfitCny: number | null;
  /** 输入不完整或不合法时的说明（如毛利率 ≥ 100%） */
  error?: string;
}

export const calcLine = (input: LineInput): LineResult => {
  const { costPrice: cost, quantity, rate } = input;
  const qty = quantity > 0 ? quantity : 0;
  const mode = cost == null ? MODE_PRICE : input.mode;
  let priceCny = 0;
  let unitPrice = 0;
  let error: string | undefined;
  if (mode === MODE_MARGIN && cost != null) {
    const m = input.marginRate;
    if (m == null) {
      error = '请填写毛利率';
    } else if (m < 0 || m >= 100) {
      error = '毛利率需要在 0–100% 之间';
    } else {
      priceCny = cost / (1 - m / 100);
      unitPrice = round2(priceCny / rate);
    }
  } else if (mode === MODE_MARKUP && cost != null) {
    const markup = input.markupAmount;
    if (markup == null || markup < 0) {
      error = '加价金额不能为负';
    } else {
      priceCny = cost + markup;
      unitPrice = round2(priceCny / rate);
    }
  } else {
    unitPrice = round2(input.unitPrice ?? 0);
    priceCny = unitPrice * rate;
  }
  const amount = round2(unitPrice * qty);
  const amountCnyExact = unitPrice * qty * rate;
  let marginRate: number | null = null;
  let netProfit: number | null = null;
  let netProfitCny: number | null = null;
  if (cost != null && !error) {
    netProfit = round2(unitPrice * qty - (cost * qty) / rate);
    netProfitCny = round2(amountCnyExact - cost * qty);
    if (unitPrice > 0) {
      marginRate =
        mode === MODE_MARGIN && input.marginRate != null
          ? round2(input.marginRate)
          : round2((1 - cost / (unitPrice * rate)) * 100);
    }
  }
  return {
    costPriceForeign: cost != null ? round2(cost / rate) : null,
    unitPriceCny: round2(priceCny),
    unitPrice,
    marginRate,
    amount,
    amountCny: round2(amountCnyExact),
    netProfit,
    netProfitCny,
    error,
  };
};

export interface TotalsResult {
  itemAmount: number;
  feeAmount: number;
  totalAmount: number;
  totalAmountCny: number;
  netProfit: number | null;
  netProfitCny: number | null;
  marginRate: number | null;
}

/** 整单合计：净利润、毛利率只按有采购成本价的行，费用不参与 */
export const calcTotals = (
  lines: { costPrice?: number | null; quantity: number; result: LineResult }[],
  fees: number[],
  rate: number,
): TotalsResult => {
  let itemAmount = 0;
  let netProfit: number | null = null;
  let netProfitCny: number | null = null;
  let cost = 0;
  let revenueCny = 0;
  for (const l of lines) {
    itemAmount += l.result.amount;
    if (l.costPrice != null && l.result.netProfit != null) {
      netProfit = (netProfit ?? 0) + l.result.netProfit;
      netProfitCny = (netProfitCny ?? 0) + (l.result.netProfitCny ?? 0);
      cost += l.costPrice * l.quantity;
      revenueCny += l.result.amount * rate;
    }
  }
  const feeAmount = fees.reduce((s, f) => s + round2(f || 0), 0);
  const total = itemAmount + feeAmount;
  return {
    itemAmount: round2(itemAmount),
    feeAmount: round2(feeAmount),
    totalAmount: round2(total),
    totalAmountCny: round2(total * rate),
    netProfit: netProfit == null ? null : round2(netProfit),
    netProfitCny: netProfitCny == null ? null : round2(netProfitCny),
    marginRate: revenueCny > 0 ? round2((1 - cost / revenueCny) * 100) : null,
  };
};

/** 毛利率显示：一位小数加 %，空值「—」 */
export const formatMargin = (v?: number | null) =>
  v == null ? '—' : `${(Math.round(v * 10 + 1e-9) / 10).toFixed(1)}%`;
