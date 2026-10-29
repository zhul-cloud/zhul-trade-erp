'use strict';
// 发货跟踪的物流文本解析（规格「采购、发货、收款与退款迁移」）。
// 国际段 → 出运单（承运商、运单号、目的国、重量、运费），国内段与供应商段 → 快递单号列表。
// 解析不出的保留原文，由调用方写进备注并列入复核报告。
const { clean, mapCountry } = require('../rules');

const COUNTRIES = require('../../../zhul-erp-backend/src/main/resources/product/countries.json');
// 在一段文字里找国家名：先长后短，避免「多米尼加共和国」被「多米尼加」截断
const COUNTRY_WORDS = [...new Set([...COUNTRIES.map((c) => c.nameZh), '印尼', '沙特', '阿联酋', '孟加拉', '多米尼加', '香港'])]
  .sort((a, b) => b.length - a.length);

function findCountry(text) {
  let best = null;
  for (const w of COUNTRY_WORDS) {
    const i = text.indexOf(w);
    if (i >= 0 && (!best || i < best.i)) best = { i, w };
  }
  return best ? mapCountry(best.w) : null;
}

const INTL_CARRIERS = [['DHL', /\bDHL\b/i], ['FEDEX', /\bFED\s?EX\b/i], ['UPS', /\bUPS\b/i], ['TNT', /\bTNT\b/i], ['EMS', /\bEMS\b/i], ['ARAMEX', /\bARAMEX\b/i]];

/** 两位小数的金额字符串（输入是文本里抽出的数字） */
const money = (n) => (Math.round(Number(n) * 100) / 100).toFixed(2);

/**
 * 解析国际段，如「6800001110 卡塔尔 2KG，运费：392元，DHL」「7300009160 墨西哥单号请查收，DHL 费用 12 KG 975 超值 25」。
 * 返回 { carrier, carrierInferred, waybillNo, country, weightKg, baseFreight, surcharges, freight, raw }，
 * 文本为空返回 null。运费 = 基本运费 + 超值、偏远、超重等附加费（人民币）。
 */
function parseInternational(raw) {
  const text = clean(raw).replace(/\s+/g, ' ');
  if (!text) return null;
  const out = { carrier: '', carrierInferred: false, waybillNo: '', country: null, weightKg: null, baseFreight: null, surcharges: [], freight: null, raw: clean(raw) };

  for (const [name, re] of INTL_CARRIERS) if (re.test(text)) { out.carrier = name; break; }
  const wb = text.match(/(?<![0-9])(1Z[0-9A-Z]{16}|\d{10,15})(?!\d)/);
  if (wb) out.waybillNo = wb[1];
  else {
    // 国际段里填的是国内快递单号（如「KY4001100029831 跨越」）：照国内段取单号与承运商
    const d = parseDomestic(text)[0];
    if (d) { out.waybillNo = d.trackingNo; out.carrier = d.carrier; }
  }
  if (!out.carrier && out.waybillNo) {
    // 没写承运商时按单号格式推断：1Z 开头为 UPS，10 位数字为 DHL，12 位数字为 FedEx
    out.carrier = out.waybillNo.startsWith('1Z') ? 'UPS' : out.waybillNo.length === 10 ? 'DHL' : out.waybillNo.length === 12 ? 'FEDEX' : '';
    out.carrierInferred = !!out.carrier;
  }
  out.country = findCountry(text);

  const kg = text.match(/(\d+(?:\.\d+)?)\s*KG/i);
  if (kg) out.weightKg = kg[1];

  // 基本运费：「运费：392元」，或「费用 12 KG 975」「2.5 KG 220 超值」这种重量后面紧跟的数字
  const f1 = text.match(/运费[:：]?\s*(\d+(?:\.\d+)?)/);
  const f2 = text.match(/\d+(?:\.\d+)?\s*KG\s*[,，]?\s*(\d+(?:\.\d+)?)(?!\s*(?:KG|英寸))/i);
  if (f1) out.baseFreight = money(f1[1]);
  else if (f2) out.baseFreight = money(f2[1]);

  for (const m of text.matchAll(/(超值|偏远|超重)\s*\+?\s*(\d+(?:\.\d+)?)/g)) out.surcharges.push({ name: m[1], amount: money(m[2]) });
  if (out.baseFreight !== null) {
    const total = out.surcharges.reduce((s, x) => s + Math.round(Number(x.amount) * 100), Math.round(Number(out.baseFreight) * 100));
    out.freight = (total / 100).toFixed(2);
  }
  return out;
}

const DOMESTIC_CARRIERS = [
  ['顺丰', /顺丰|^SF/i], ['跨越', /跨越|^KY/i], ['京东', /京东|^JD/i], ['圆通', /圆通|^YT/i], ['中通', /中通/], ['申通', /申通/],
  ['韵达', /韵达/], ['联昊通', /联昊通/], ['信丰', /信丰|^XF/i], ['加运美', /加运美|^JYM/i], ['安能', /安能/], ['德邦', /德邦|^DPK/i], ['极兔', /极兔|^JT/i], ['EMS', /\bEMS\b|邮政/i],
];

/**
 * 解析国内段 / 供应商段：一个单元格可能有多个快递单号（换行分隔）。
 * 返回 [{ carrier, trackingNo, amount, raw }]；没有单号的行不返回（原文由调用方保留）。
 * 写成国际段格式的（如「888000001632 墨西哥 9KG,运费：845元」）也按快递单号处理，原文保留。
 */
function parseDomestic(raw) {
  const out = [];
  for (const line of clean(raw).split(/\n+/)) {
    const s = line.trim();
    const m = s.match(/(?<![A-Za-z0-9])((?:SF|KYE?|JD[A-Z]{1,2}|YT|DPK|JT|XF|JYM)\d{8,15}|\d{10,20})(?!\d)/i);
    if (!m) continue;
    const no = m[1].toUpperCase();
    let carrier = '';
    for (const [name, re] of DOMESTIC_CARRIERS) if (re.test(s) || re.test(no)) { carrier = name; break; }
    const amt = s.slice(m.index + m[1].length).match(/(\d+(?:\.\d+)?)\s*元?\s*$/);
    out.push({ carrier, trackingNo: no, amount: amt && Number(amt[1]) < 10000 ? money(amt[1]) : null, raw: s });
  }
  return out;
}

module.exports = { parseInternational, parseDomestic, findCountry };
