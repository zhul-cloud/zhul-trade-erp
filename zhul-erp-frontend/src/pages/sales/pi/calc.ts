/**
 * PI 计算（与后端 PiCalculator 同一口径，改一边要同步另一边）：
 * 型号行按「直接填外币售价」计算（复用报价单 calcLine），成本取快照；
 * 整单折扣按百分比时 = 型号小计 × 百分比（两位 HALF_UP），按金额时直接取值，都不能超过型号小计；
 * 合计 = 型号小计 + 费用 − 折扣；合计净利润 = Σ有成本行净利润 − 折扣按收入比例分摊到有成本行的部分；
 * 合计毛利率 = 1 − Σ成本 ÷ ((有成本行收入 − 分摊折扣) × 汇率)。只用于编辑时即时显示，保存后以后端结果为准。
 */
import {
  calcLine,
  type LineResult,
  MODE_PRICE,
  round2,
} from '@/pages/quotation/calc';
import { DISCOUNT } from '../components';

export interface PiLine {
  costPrice?: number | null;
  quantity: number;
  unitPrice: number | null;
}

export const calcPiLine = (l: PiLine, rate: number): LineResult =>
  calcLine({
    costPrice: l.costPrice,
    quantity: l.quantity,
    rate,
    mode: MODE_PRICE,
    unitPrice: l.unitPrice,
  });

/** 折扣金额（正数）与错误提示 */
export const calcDiscount = (
  type: number,
  value: number | null | undefined,
  itemAmount: number,
): { amount: number; error?: string } => {
  if (type === DISCOUNT.NONE || !value) return { amount: 0 };
  if (value < 0) return { amount: 0, error: '折扣不能为负' };
  let amount = 0;
  if (type === DISCOUNT.PERCENT) {
    if (value > 100)
      return { amount: 0, error: '折扣百分比需要在 0–100% 之间' };
    amount = round2((itemAmount * value) / 100);
  } else {
    amount = round2(value);
  }
  if (amount > round2(itemAmount)) {
    return { amount, error: '折扣不能超过型号小计' };
  }
  return { amount };
};

export interface PiTotals {
  itemAmount: number;
  feeAmount: number;
  discountAmount: number;
  discountError?: string;
  totalAmount: number;
  totalAmountCny: number;
  netProfit: number | null;
  netProfitCny: number | null;
  marginRate: number | null;
}

export const calcPiTotals = (
  lines: { line: PiLine; result: LineResult }[],
  fees: number[],
  discountType: number,
  discountValue: number | null | undefined,
  rate: number,
): PiTotals => {
  let itemAmount = 0;
  let costedRevenue = 0;
  let cost = 0;
  let profit: number | null = null;
  let profitCny: number | null = null;
  for (const { line, result } of lines) {
    itemAmount += result.amount;
    if (line.costPrice != null && result.netProfit != null) {
      costedRevenue += result.amount;
      cost += line.costPrice * line.quantity;
      profit = (profit ?? 0) + result.netProfit;
      profitCny = (profitCny ?? 0) + (result.netProfitCny ?? 0);
    }
  }
  const feeAmount = fees.reduce((s, f) => s + round2(f || 0), 0);
  const d = calcDiscount(discountType, discountValue, itemAmount);
  const total = itemAmount + feeAmount - d.amount;
  let netProfit: number | null = null;
  let netProfitCny: number | null = null;
  let marginRate: number | null = null;
  if (profit != null && itemAmount > 0) {
    const allocated = (d.amount * costedRevenue) / itemAmount;
    const revenueCny = (costedRevenue - allocated) * rate;
    netProfit = round2(costedRevenue - allocated - cost / rate);
    netProfitCny = round2(revenueCny - cost);
    marginRate = revenueCny > 0 ? round2((1 - cost / revenueCny) * 100) : null;
  }
  return {
    itemAmount: round2(itemAmount),
    feeAmount: round2(feeAmount),
    discountAmount: d.amount,
    discountError: d.error,
    totalAmount: round2(total),
    totalAmountCny: round2(total * rate),
    netProfit,
    netProfitCny,
    marginRate,
  };
};
