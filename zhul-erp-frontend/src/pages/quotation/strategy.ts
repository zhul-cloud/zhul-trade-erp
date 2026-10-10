/**
 * 报价策略（spec quotation/pricing-rule「报价策略」）：只算出每行的外币售价，应用后各行按「直接填外币售价」保存。
 * 口径与 calc.ts 一致：售价(CNY) = 采购成本价 ÷ (1 − 毛利率)，外币售价 = 售价(CNY) ÷ 汇率（两位小数）。
 */
import { round2 } from './calc';

export interface StrategyLine {
  id: number;
  /** 采购成本价（CNY），参与策略的行一定有 */
  costPrice: number;
  quantity: number;
  /** 红线（%），没有时不限制 */
  floorMargin?: number | null;
}

export type Rounding = 'none' | 'integer' | 'p9' | 'p5' | 'five' | 'ten';

export const ROUNDING_OPTIONS: { value: Rounding; label: string }[] = [
  { value: 'none', label: '不取整' },
  { value: 'integer', label: '取整数' },
  { value: 'p9', label: '尾数 .9' },
  { value: 'p5', label: '尾数 .5' },
  { value: 'five', label: '按 5 进位' },
  { value: 'ten', label: '按 10 进位' },
];

export interface Tier {
  /** 采购成本价上限（CNY）；null 为以上全部 */
  maxCost: number | null;
  marginRate: number;
}

export const MAX_MARGIN = 95;
const EPS = 1e-9;

/** 售价取整：取整数四舍五入，其余向上取（不让取整吃掉利润） */
export const roundPrice = (price: number, rule: Rounding): number => {
  const p = round2(price);
  switch (rule) {
    case 'integer':
      return Math.round(p);
    case 'p9': {
      const base = Math.floor(p);
      const v = base + 0.9;
      return round2(v + EPS < p ? v + 1 : v);
    }
    case 'p5':
      return Math.ceil(p * 2 - EPS) / 2;
    case 'five':
      return Math.ceil(p / 5 - EPS) * 5;
    case 'ten':
      return Math.ceil(p / 10 - EPS) * 10;
    default:
      return p;
  }
};

/** 按毛利率算外币售价（未取整前两位小数） */
export const priceAt = (cost: number, margin: number, rate: number) =>
  round2(cost / (1 - Math.min(margin, MAX_MARGIN) / 100) / rate);

/** 人民币净利润（按报给客户的外币售价折算） */
export const profitCny = (
  lines: StrategyLine[],
  prices: Map<number, number>,
  rate: number,
) =>
  lines.reduce(
    (s, l) => s + ((prices.get(l.id) ?? 0) * rate - l.costPrice) * l.quantity,
    0,
  );

/** 可复现的随机数（mulberry32）：同一个种子得到同一组打散结果，预览与应用一致 */
export const seededRandom = (seed: number) => {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};

const byMargin = (
  lines: StrategyLine[],
  marginOf: (l: StrategyLine) => number,
  rate: number,
  rounding: Rounding,
) => {
  const prices = new Map<number, number>();
  for (const l of lines) {
    prices.set(
      l.id,
      roundPrice(priceAt(l.costPrice, marginOf(l), rate), rounding),
    );
  }
  return prices;
};

// ---------------------------------------------------------------- 统一毛利率 / 按金额分层

export const uniformMargin = (
  lines: StrategyLine[],
  margin: number,
  rate: number,
  rounding: Rounding,
) => byMargin(lines, () => margin, rate, rounding);

export const tierMargin = (cost: number, tiers: Tier[]) => {
  const tier =
    tiers.find((t) => t.maxCost != null && cost <= t.maxCost) ??
    tiers.find((t) => t.maxCost == null) ??
    tiers[tiers.length - 1];
  return tier ? tier.marginRate : 0;
};

export const tiered = (
  lines: StrategyLine[],
  tiers: Tier[],
  rate: number,
  rounding: Rounding,
) => byMargin(lines, (l) => tierMargin(l.costPrice, tiers), rate, rounding);

// ---------------------------------------------------------------- 目标利润 / 目标总价

export interface TargetOptions {
  rate: number;
  /** 打散幅度（毛利率点数，0 为不打散） */
  spread: number;
  seed: number;
  rounding: Rounding;
}

export interface TargetResult {
  prices: Map<number, number>;
  /** 全部按红线定价时的人民币净利润（目标利润低于它时部分型号会低于红线） */
  floorProfit: number;
  /** 目标低于按红线定价的利润 */
  belowFloor: boolean;
}

/** 二分求解：f 单调递增，返回使 f(x) ≈ target 的 x */
const solve = (
  f: (x: number) => number,
  target: number,
  lo: number,
  hi: number,
) => {
  let a = lo;
  let b = hi;
  for (let i = 0; i < 60; i++) {
    const mid = (a + b) / 2;
    if (f(mid) < target) a = mid;
    else b = mid;
  }
  return (a + b) / 2;
};

const clampMargin = (m: number, floor: number) =>
  Math.min(MAX_MARGIN, Math.max(floor, m));

/**
 * 每行毛利率 = 基础毛利率 + 本行随机偏移 + 统一修正量 d（不低于红线、不高于 95%）；
 * 先按不取整的价格求出使目标成立的 d，再按规则取整；不取整时把剩下的几分钱差额放在数量最少的一行。
 */
const shifted = (
  lines: StrategyLine[],
  opts: TargetOptions,
  respectFloor: boolean,
  measure: (prices: Map<number, number>) => number,
  target: number,
) => {
  const rnd = seededRandom(opts.seed);
  const offsets = new Map(
    lines.map((l) => [
      l.id,
      opts.spread > 0 ? (rnd() * 2 - 1) * opts.spread : 0,
    ]),
  );
  const floorOf = (l: StrategyLine) =>
    respectFloor ? (l.floorMargin ?? 0) : 0;
  const exact = (d: number) => {
    const prices = new Map<number, number>();
    for (const l of lines) {
      const m = clampMargin(d + (offsets.get(l.id) ?? 0), floorOf(l));
      prices.set(l.id, l.costPrice / (1 - m / 100) / opts.rate);
    }
    return prices;
  };
  const d = solve((x) => measure(exact(x)), target, -100, MAX_MARGIN + 100);
  const prices = new Map<number, number>();
  for (const [id, p] of exact(d)) prices.set(id, roundPrice(p, opts.rounding));
  if (opts.rounding === 'none' && lines.length > 0) {
    // 数量最少的行一分钱对合计的影响最小（数量相同取成本最大的），用它消化剩下的差额
    const biggest = lines.reduce((a, b) =>
      a.quantity < b.quantity ||
      (a.quantity === b.quantity && a.costPrice >= b.costPrice)
        ? a
        : b,
    );
    const diff = target - measure(prices);
    const unit =
      measure(new Map([[biggest.id, 1]])) - measure(new Map([[biggest.id, 0]]));
    if (unit > 0) {
      const adjusted = round2((prices.get(biggest.id) ?? 0) + diff / unit);
      if (adjusted > 0) prices.set(biggest.id, adjusted);
    }
  }
  return prices;
};

export const targetProfit = (
  lines: StrategyLine[],
  targetCny: number,
  opts: TargetOptions,
): TargetResult => {
  const floorPrices = byMargin(
    lines,
    (l) => l.floorMargin ?? 0,
    opts.rate,
    'none',
  );
  const floorProfit = round2(profitCny(lines, floorPrices, opts.rate));
  const belowFloor = targetCny < floorProfit - 0.005;
  const prices = shifted(
    lines,
    opts,
    !belowFloor,
    (p) => profitCny(lines, p, opts.rate),
    targetCny,
  );
  return { prices, floorProfit, belowFloor };
};

/** 型号小计合计（外币） */
export const itemTotal = (lines: StrategyLine[], prices: Map<number, number>) =>
  lines.reduce((s, l) => s + (prices.get(l.id) ?? 0) * l.quantity, 0);

/** 采购成本合计折外币：目标总价不能低于它 */
export const costTotalForeign = (lines: StrategyLine[], rate: number) =>
  lines.reduce((s, l) => s + (l.costPrice * l.quantity) / rate, 0);

export const targetTotal = (
  lines: StrategyLine[],
  targetForeign: number,
  opts: TargetOptions,
): Map<number, number> =>
  shifted(lines, opts, false, (p) => itemTotal(lines, p), targetForeign);
