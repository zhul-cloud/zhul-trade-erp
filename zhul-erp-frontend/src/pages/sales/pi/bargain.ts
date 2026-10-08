/**
 * 议价测算（与后端 PiBargain 同一口径，改一边要同步另一边）：
 * 有采购成本价的行参与毛利，费用不计入；整单折扣按收入比例分摊到各行；
 * 整单红线 = 有成本行红线按收入加权（没有红线按 0）；
 * 总折扣上限 = 小计 × (1 − 成本 ÷ ((1 − 红线) × 有成本行收入 × 汇率))，还能再让 = 上限 − 当前折扣，
 * 金额向下取到分、点数（占小计的百分比）向下取到 0.1。
 */

export interface BargainLine {
  model: string;
  quantity: number;
  /** 采购成本单价（CNY），没有时不参与毛利 */
  costPrice?: number | null;
  /** 售价小计（原币） */
  amount: number;
  /** 红线（%） */
  floorMargin?: number | null;
}

export interface BargainSummary {
  itemAmount: number;
  costedRevenue: number;
  costTotal: number;
  /** 整单红线（%） */
  floorMargin: number;
  costed: boolean;
}

/** 浮点误差的余量：向下取整前先加上，避免 97.74 被算成 97.73999 */
const EPS = 1e-9;
const floorTo = (v: number, digits: number) => {
  const f = 10 ** digits;
  return Math.floor(v * f + EPS) / f;
};

export const summarize = (lines: BargainLine[]): BargainSummary => {
  let itemAmount = 0;
  let revenue = 0;
  let cost = 0;
  let floorWeighted = 0;
  for (const l of lines) {
    itemAmount += l.amount;
    if (l.costPrice != null) {
      revenue += l.amount;
      cost += l.costPrice * l.quantity;
      floorWeighted += l.amount * (l.floorMargin ?? 0);
    }
  }
  return {
    itemAmount,
    costedRevenue: revenue,
    costTotal: cost,
    floorMargin: revenue > 0 ? floorWeighted / revenue : 0,
    costed: revenue > 0,
  };
};

/** 折扣分摊后，有成本行的收入折合 CNY */
export const revenueCnyAfter = (
  s: BargainSummary,
  discount: number,
  rate: number,
) =>
  s.itemAmount === 0
    ? 0
    : (s.costedRevenue - (discount * s.costedRevenue) / s.itemAmount) * rate;

/** 折扣后的整单毛利率（%）；不能计算时为 null */
export const marginAfter = (
  s: BargainSummary,
  discount: number,
  rate: number,
): number | null => {
  const r = revenueCnyAfter(s, discount, rate);
  if (!s.costed || r <= 0) return null;
  return (1 - s.costTotal / r) * 100;
};

export const profitAfter = (
  s: BargainSummary,
  discount: number,
  rate: number,
) => revenueCnyAfter(s, discount, rate) - s.costTotal;

/** 在当前折扣基础上还能再让的金额（原币，向下取到分，不小于 0）；不能计算时为 null */
export const maxExtraDiscount = (
  s: BargainSummary,
  currentDiscount: number,
  rate: number,
): number | null => {
  if (!s.costed || s.itemAmount === 0 || rate <= 0) return null;
  const keep = 1 - s.floorMargin / 100;
  if (keep <= 0) return 0;
  const max =
    s.itemAmount * (1 - s.costTotal / (keep * s.costedRevenue * rate)) -
    currentDiscount;
  return max <= 0 ? 0 : floorTo(max, 2);
};

/** 金额占小计的点数，向下取到 0.1 */
export const points = (amount: number, itemAmount: number) =>
  itemAmount === 0 ? 0 : floorTo((amount * 100) / itemAmount, 1);

/** 单行在整单折扣分摊后的毛利（CNY）与毛利率（%） */
export const lineAfter = (
  l: BargainLine,
  s: BargainSummary,
  discount: number,
  rate: number,
) => {
  const share = s.itemAmount === 0 ? 0 : discount / s.itemAmount;
  const revenueCny = l.amount * (1 - share) * rate;
  if (l.costPrice == null) return { revenueCny, profit: null, margin: null };
  const cost = l.costPrice * l.quantity;
  return {
    revenueCny,
    profit: revenueCny - cost,
    margin: revenueCny > 0 ? (1 - cost / revenueCny) * 100 : null,
  };
};
