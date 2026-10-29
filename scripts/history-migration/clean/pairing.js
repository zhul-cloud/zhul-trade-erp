'use strict';
// 订单行配对询盘行，以及订单行、采购行的金额分摊（规格「销售订单与询盘行配对」，design.md 决策 6、7）。
const { allocate, yuan, cents } = require('./common');

const DAY = 86400000;
const daysBetween = (a, b) => Math.round((Date.parse(b) - Date.parse(a)) / DAY);

/**
 * 配对。
 *   inquiryLines：[{ id, customer, owner, modelKey, date }]   id 如 'XP2026101010700#2'，customer 为客户分组 ID
 *   orderLines：  [{ id, customer, owner, modelKey, date }]   id 如 'SO2026102200695#1'
 * 规则（只在还没配上的订单行之间进行）：
 *   第 1 轮：同一客户 + 同一归一化型号 + 询盘日期 ≤ 销售日期，取日期最近的一条；
 *   第 2 轮：同一业务员 + 同一归一化型号 + 销售日期前 180 天内，取最近的一条；
 *   第 3 轮：归一化型号一方是另一方的前缀（≥ 6 位），只作为建议，不关联。
 * 一条询盘行可以被多张订单配对（客户复购）。
 * 返回 { pairs: Map(订单行ID → { inquiryLine, round }), suggestions: [{ orderLine, inquiryLine }], unmatched: [订单行ID] }。
 */
function pairOrderLines(inquiryLines, orderLines) {
  const byModel = new Map();
  for (const q of inquiryLines) {
    if (!q.modelKey) continue;
    (byModel.get(q.modelKey) || byModel.set(q.modelKey, []).get(q.modelKey)).push(q);
  }
  const nearestBefore = (cands, date, maxDays) => {
    let best = null;
    for (const q of cands) {
      if (!q.date || q.date > date) continue;
      if (maxDays !== undefined && daysBetween(q.date, date) > maxDays) continue;
      if (!best || q.date > best.date || (q.date === best.date && q.id > best.id)) best = q;
    }
    return best;
  };

  const pairs = new Map();
  for (const round of [1, 2]) {
    for (const o of orderLines) {
      if (pairs.has(o.id) || !o.modelKey) continue;
      const cands = (byModel.get(o.modelKey) || []).filter((q) => (round === 1 ? q.customer === o.customer : q.owner === o.owner));
      const hit = nearestBefore(cands, o.date, round === 2 ? 180 : undefined);
      if (hit) pairs.set(o.id, { inquiryLine: hit.id, round });
    }
  }

  const suggestions = [];
  const keys = [...byModel.keys()];
  const unmatched = [];
  for (const o of orderLines) {
    if (pairs.has(o.id)) continue;
    unmatched.push(o.id);
    if (!o.modelKey || o.modelKey.length < 6) continue;
    const near = keys.filter((k) => k.length >= 6 && k !== o.modelKey && (k.startsWith(o.modelKey) || o.modelKey.startsWith(k)));
    for (const k of near) {
      const hit = nearestBefore(byModel.get(k).filter((q) => q.customer === o.customer), o.date);
      if (hit) suggestions.push({ orderLine: o.id, inquiryLine: hit.id });
    }
  }
  return { pairs, suggestions, unmatched };
}

/**
 * 订单行金额分摊：订单总额（原币，两位小数字符串）分到各行。
 *   lines：[{ quantity, unitPrice?, costCny? }]，unitPrice 取自 PI/INV 附件（原币），costCny 为该行采购成本（人民币）
 * 优先级：各行都有附件单价且合计等于订单总额 → 直接用；否则按采购成本比例；都没有成本按数量比例。
 * 返回 { amounts: [两位小数字符串], unitPrices: [两位小数字符串], basis: 'pi' | 'cost' | 'quantity' }。
 */
function allocateOrderLines(total, lines) {
  const totalCents = cents(total);
  const qty = lines.map((l) => Math.max(1, Number(l.quantity) || 1));
  const piAmounts = lines.map((l, i) => (l.unitPrice != null ? Math.round(cents(l.unitPrice) * qty[i]) : null));
  let parts; let basis;
  if (piAmounts.every((x) => x !== null) && piAmounts.reduce((a, b) => a + b, 0) === totalCents) {
    parts = piAmounts; basis = 'pi';
  } else if (lines.some((l) => cents(l.costCny ?? 0) > 0)) {
    parts = allocate(totalCents, lines.map((l) => cents(l.costCny ?? 0))); basis = 'cost';
  } else {
    parts = allocate(totalCents, qty); basis = 'quantity';
  }
  return {
    amounts: parts.map(yuan),
    // 单价 = 行金额 ÷ 数量，HALF_UP 两位；单价 × 数量与行金额的分差保留在行金额上
    unitPrices: parts.map((p, i) => yuan(Math.round(p / qty[i]))),
    basis,
  };
}

module.exports = { pairOrderLines, allocateOrderLines };
