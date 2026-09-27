'use strict';
// 迁移规则：与后端保持一致的归一化、以及"疑似卖家承诺"识别。

/** 与后端 MpnNormalizer 一致：去首尾空格 → NFKC → 小写 → 去掉所有非字母数字 */
function normalizeMpn(raw) {
  return String(raw ?? '')
    .trim()
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
}

/**
 * 疑似卖家承诺（design.md 决策 12）：这类内容是福唯自己的承诺，不能进平台共享的 FAQ。
 * 每条规则同时给出名字，干跑报告里要列出命中的关键词，供人工复核误伤。
 */
const SELLER_RULES = [
  ['品牌名 Fouwell', /fouwell/i],
  ['邮箱', /[\w.+-]+@[\w-]+\.[\w.-]+/],
  ['电话', /(?:\+\d{1,3}[\s-]?)?\(?\d{2,4}\)?[\s-]\d{3,4}[\s-]\d{3,4}/],
  ['质保 warranty', /warrant/i],
  ['库存 in stock', /in[\s-]stock/i],
  ['发货 ship', /\bship(?:s|ped|ping|ment)?\b/i],
  ['交期 lead time', /lead[\s-]?time/i],
  ['起订量 MOQ', /\bMOQ\b/],
  ['价格 price / quote', /\b(?:price|prices|pricing|quote|quotes|quotation)\b/i],
  ['第一人称 we / our', /\b(?:we|we'll|we've|our)\b/i],
  ['第一人称 us（tell us / with us）', /\bus\b/], // 只匹配小写，避免误伤国家名 US
];

/** 返回命中的规则名列表（去重）；没命中返回空数组 */
function sellerHits(...texts) {
  const hits = new Set();
  for (const text of texts) {
    if (!text) continue;
    for (const [name, re] of SELLER_RULES) {
      if (re.test(text)) hits.add(name);
    }
  }
  return [...hits];
}

/** 规格标签 → 小写蛇形编码 */
function specKeyOf(label) {
  let key = String(label ?? '')
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  if (!key) key = 'spec';
  if (/^\d/.test(key)) key = `spec_${key}`;
  return key.slice(0, 60);
}

/** 像一个型号：无空格，只含字母数字和 . - _ / + ；用来区分"型号"与"一段描述" */
function looksLikeModel(text) {
  return /^[A-Za-z0-9][A-Za-z0-9.\-_/+]{2,63}$/.test(String(text ?? '').trim());
}

module.exports = { normalizeMpn, sellerHits, SELLER_RULES, specKeyOf, looksLikeModel };
