'use strict';
// 清洗结果的公共结构（design.md 决策 2）：每条源记录给出且只给出一种处理结果。
const crypto = require('node:crypto');

const DISPOSITION = {
  MIGRATE: 'migrate', // 迁移
  MERGED: 'merged', // 合并到另一条（mergedInto 给出目标旧编号）
  SKIPPED: 'skipped', // 按规则跳过（reason 说明规则）
  MANUAL: 'manual', // 需人工处理（reason 说明原因）
};

/** 源记录内容哈希：只看业务字段（去掉 __ 开头的辅助字段），用于重复执行时判断源数据是否变化 */
function contentHash(row) {
  const pure = Object.fromEntries(Object.entries(row).filter(([k]) => !k.startsWith('__')).sort(([a], [b]) => a.localeCompare(b)));
  return crypto.createHash('sha1').update(JSON.stringify(pure)).digest('hex').slice(0, 16);
}

/** 源记录引用：表、文件、行号、旧编号、内容哈希 */
function srcOf(row) {
  return { ...row.__src, hash: contentHash(row) };
}

/** 一条清洗结果 */
function result(row, disposition, data = null, extra = {}) {
  return { src: srcOf(row), disposition, ...extra, data };
}

/**
 * 人民币分（整数）运算，避免浮点误差。金额文本 → 分；null 视为 0。
 * 只用于加减与比较；乘以汇率等用 mulRound。
 */
function cents(amount) {
  if (amount === null || amount === undefined || amount === '') return 0;
  const [i, f = ''] = String(amount).replace('-', '').split('.');
  const v = Number(i) * 100 + Number((f + '00').slice(0, 2)) + (f.length > 2 && Number(f[2]) >= 5 ? 1 : 0);
  return String(amount).startsWith('-') ? -v : v;
}
const yuan = (c) => (c < 0 ? '-' : '') + (Math.abs(c) / 100).toFixed(2);

/**
 * 金额 × 汇率，HALF_UP 保留两位。用十进制字符串转成整数后相乘，不经过浮点。
 * amount 最多两位小数，rate 最多六位小数。
 */
function mulRound(amount, rate) {
  const scale = (s, n) => { const [i, f = ''] = String(s).split('.'); return BigInt(i + (f + '0'.repeat(n)).slice(0, n)); };
  const neg = String(amount).startsWith('-') !== String(rate).startsWith('-');
  const p = scale(String(amount).replace('-', ''), 2) * scale(String(rate).replace('-', ''), 6); // 8 位小数
  const c = (p + 500000n) / 1000000n; // HALF_UP 到分
  const str = c.toString().padStart(3, '0');
  return (neg && c !== 0n ? '-' : '') + `${str.slice(0, -2)}.${str.slice(-2)}`;
}

/** 按权重分摊总额（分），HALF_UP，尾差放到权重最大的一项；权重全为 0 时平均分摊 */
function allocate(totalCents, weights) {
  const ws = weights.map((w) => Math.max(0, Number(w) || 0));
  const sum = ws.reduce((a, b) => a + b, 0);
  const base = sum > 0 ? ws : ws.map(() => 1);
  const bsum = base.reduce((a, b) => a + b, 0);
  const parts = base.map((w) => Math.round((totalCents * w) / bsum));
  const diff = totalCents - parts.reduce((a, b) => a + b, 0);
  let maxI = 0;
  base.forEach((w, i) => { if (w > base[maxI]) maxI = i; });
  parts[maxI] += diff;
  return parts;
}

module.exports = { DISPOSITION, contentHash, srcOf, result, cents, yuan, mulRound, allocate };
