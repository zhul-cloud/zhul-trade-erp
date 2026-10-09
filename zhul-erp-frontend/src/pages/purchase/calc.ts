/**
 * 采购价与砍价的即时预览（spec purchase/purchase-order「价格、税与砍价」），口径与后端 PurchaseCalc 一致，保存后以后端为准：
 * 不含税单价(CNY) = 单价 × 汇率 ÷ (1 + 税率)；砍价 = (目标价 − 不含税单价) × 数量；砍价率 = 砍价 ÷ 目标金额。
 */
import type { PaymentTerm } from './service';

export const round2 = (v: number) =>
  Math.round((v + Number.EPSILON) * 100) / 100;

export const netPrice = (
  unitPrice: number | null | undefined,
  rate: number,
  taxIncluded: boolean,
  taxRate: number,
) => {
  if (unitPrice == null) return null;
  const cny = unitPrice * rate;
  return taxIncluded && taxRate ? cny / (1 + taxRate / 100) : cny;
};

export const bargainOf = (
  target: number | null | undefined,
  unitPrice: number | null | undefined,
  quantity: number,
  rate: number,
  taxIncluded: boolean,
  taxRate: number,
) => {
  const net = netPrice(unitPrice, rate, taxIncluded, taxRate);
  if (target == null || net == null) return null;
  return round2((target - net) * quantity);
};

export const rateOf = (
  bargain: number | null | undefined,
  targetAmount: number,
) =>
  bargain == null || !targetAmount
    ? null
    : round2((bargain * 100) / targetAmount);

export const TRIGGERS: Record<number, string> = {
  1: '下单后',
  2: '发货前',
  3: '入库后',
};

/** 付款条件文字：与后端 PaymentTerms.text 一致 */
export const termsText = (terms: PaymentTerm[]) => {
  if (!terms.length) return '';
  if (terms.length === 1) {
    const t = terms[0];
    if (t.trigger === 1) return '全额预付';
    if (t.trigger === 3) return t.days ? `入库后 ${t.days} 天` : '货到付款';
    return '发货前付全款';
  }
  return terms
    .map((t) => {
      const when =
        t.trigger === 3 && t.days ? `入库后 ${t.days} 天` : TRIGGERS[t.trigger];
      return `${Number(t.percent)}% ${when}`;
    })
    .join(' · ');
};

export const termsTotal = (terms: PaymentTerm[]) =>
  round2(terms.reduce((s, t) => s + (Number(t.percent) || 0), 0));
