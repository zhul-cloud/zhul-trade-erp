'use strict';
// 清洗规则：金额、日期、型号归一化，以及旧字典值到新系统字典的对应。
// 对应关系见 openspec/changes/migrate-feishu-history 的规格「客户与商机合并」「询盘与询价记录迁移」「销售订单与询盘行配对」。
const fs = require('node:fs');
const path = require('node:path');

/** 与后端 MpnNormalizer 一致：去首尾空格 → NFKC → 小写 → 去掉所有非字母数字 */
function normalizeMpn(raw) {
  return String(raw ?? '').trim().normalize('NFKC').toLowerCase().replace(/[^a-z0-9]/g, '');
}

// 西里尔字母里与拉丁字母形状相同的（俄语询盘常混用，如「027Н120」里的 Н）
const CYRILLIC_LOOKALIKES = {
  А: 'A', В: 'B', Е: 'E', К: 'K', М: 'M', Н: 'H', О: 'O', Р: 'P', С: 'C', Т: 'T', Х: 'X', У: 'Y',
  а: 'a', е: 'e', к: 'k', м: 'm', о: 'o', р: 'p', с: 'c', т: 't', х: 'x', у: 'y',
};

/** 型号文本修正：西里尔形近字母换成拉丁字母、去首尾空白 */
function fixModelText(v) {
  return String(v ?? '').replace(/[АВЕКМНОРСТХУаекморстху]/g, (ch) => CYRILLIC_LOOKALIKES[ch]).trim();
}

/** 文本清理：去首尾空白；null/undefined 变成空串 */
function clean(v) {
  return String(v ?? '').trim();
}

/**
 * 金额文本 → 两位以内小数的字符串（不用浮点运算，原样保留精度）。
 * 识别 ¥ $ 千分位和负号（含「-¥12.00」）；空值返回 null；不是金额返回 undefined（由调用方记为异常）。
 */
function parseAmount(v) {
  const s = clean(v).replace(/[¥$￥,\s]/g, '');
  if (s === '') return null;
  if (!/^-?\d+(\.\d+)?$/.test(s)) return undefined;
  return s.replace(/^(-?)0+(?=\d)/, '$1');
}

/** 百分比文本 → 数字字符串（「12.03%」→「12.03」）；空值返回 null */
function parsePercent(v) {
  const s = clean(v).replace('%', '');
  if (s === '') return null;
  return /^-?\d+(\.\d+)?$/.test(s) ? s : undefined;
}

/** 'YYYY-MM-DD HH:mm:ss' / 'YYYY/MM/DD' → 'YYYY-MM-DD'；空值或无法识别返回 null */
function parseDate(v) {
  const m = clean(v).match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})/);
  if (!m) return null;
  return `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`;
}

/** 'YYYY-MM-DD HH:mm:ss' 原样返回（补齐秒）；只有日期的补 00:00:00；无法识别返回 null */
function parseDateTime(v) {
  const s = clean(v);
  const m = s.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/);
  if (!m) return null;
  const p = (x) => String(x ?? 0).padStart(2, '0');
  return `${m[1]}-${p(m[2])}-${p(m[3])} ${p(m[4])}:${p(m[5])}:${p(m[6])}`;
}

/** 「2026年9月」→「2026-09」；无法识别返回 null */
function parseMonth(v) {
  const m = clean(v).match(/^(\d{4})年(\d{1,2})月$/);
  return m ? `${m[1]}-${m[2].padStart(2, '0')}` : null;
}

// ---------- 国家 ----------

const COUNTRIES = JSON.parse(fs.readFileSync(
  path.join(__dirname, '../../zhul-erp-backend/src/main/resources/product/countries.json'), 'utf8'));

/** 旧数据里的写法 → 系统国家清单的中文名（清单里没有的简称、错别字、城市） */
const COUNTRY_ALIASES = {
  孟加拉: '孟加拉国', 孟家拉: '孟加拉国', 印尼: '印度尼西亚', 印度尼i西亚: '印度尼西亚', 印度尼西亚他: '印度尼西亚',
  沙特: '沙特阿拉伯', 阿联酋: '阿拉伯联合酋长国', 迪拜: '阿拉伯联合酋长国', 阿联酋迪拜: '阿拉伯联合酋长国',
  美: '美国', 波黑: '波斯尼亚和黑塞哥维那', 斯拉兰卡: '斯里兰卡', 塞加内尔: '塞内加尔', 埃塞尔比亚: '埃塞俄比亚',
  乌慈别克斯坦: '乌兹别克斯坦', 兹别克斯坦: '乌兹别克斯坦', 乌兹别克斯: '乌兹别克斯坦', 阿赛拜疆: '阿塞拜疆',
  西班亚: '西班牙', 斯洛文伐克: '斯洛伐克', 莫扎比克: '莫桑比克', 塔卡尔: '卡塔尔', 刚果金: '刚果（金）',
  '刚果（金）': '刚果（金）', '刚果（布）': '刚果（布）', '刚国 （布）': '刚果（布）', 刚果: '刚果（布）',
  多米尼加: '多米尼加共和国', 瓜德罗普岛: '瓜德罗普', 马恩岛: '马恩岛', 香港: '中国香港', HK: '中国香港',
  台湾: '中国台湾', 中国台湾: '中国台湾', 内蒙古: '中国', 中国人: '中国', iran: '伊朗', 巴勒斯坦: '巴勒斯坦',
};

const byZh = new Map(COUNTRIES.map((c) => [c.nameZh, c]));
const byEn = new Map(COUNTRIES.map((c) => [c.nameEn.toLowerCase(), c]));

/**
 * 国家文本 → { nameEn, nameZh } 或 null。
 * 「美国、巴基斯坦」这类多个国家取第一个；城市、人名、「未知」「其他」等对不上的返回 null（由调用方列入复核报告）。
 */
function mapCountry(raw) {
  let s = clean(raw).split(/[、，,／/\n]/)[0].trim();
  if (!s) return null;
  if (COUNTRY_ALIASES[s]) s = COUNTRY_ALIASES[s];
  let hit = byZh.get(s) || byEn.get(s.toLowerCase());
  if (!hit) {
    // 「乌克兰 切尔卡瑟市…」这类：取开头连续的中文再试一次
    const head = (s.match(/^[一-龥]+/) || [])[0];
    hit = head && (byZh.get(COUNTRY_ALIASES[head] || head));
  }
  return hit ? { nameEn: hit.nameEn, nameZh: hit.nameZh } : null;
}

// ---------- 来源、等级 ----------

/** 客户来源 / 商机来源 → crm_source_channel（1-阿里巴巴国际站 … 8-其他）；空为 0 */
function mapSourceChannel(raw) {
  const s = clean(raw).toLowerCase();
  if (!s) return 0;
  if (['tm', 'rfq', '询盘'].includes(s)) return 1;
  if (s === '独立站') return 3;
  if (['whatsapp', 'facebook'].includes(s)) return 5;
  if (s === '公海') return 7;
  return 8; // 邮箱、微信及其他
}

/** 阿里等级：统一成 L0、L1、L1+、L2、L3、L4（「LO」「lo」是字母 O 的误写）；对不上返回 '' */
function mapAliLevel(raw) {
  const s = clean(raw).toUpperCase().replace(/^LO$/, 'L0');
  return ['L0', 'L1', 'L1+', 'L2', 'L3', 'L4'].includes(s) ? s : '';
}

/** 旧「客户类型」→ customer_role（1-终端用户、2-系统集成商、3-经销商、4-贸易商、6-维修服务商、7-其他） */
function mapCustomerRole(raw) {
  return {
    终端用户: 1, 终端工厂: 1, 系统集成商: 2, 本地分销商: 3, 贸易商: 4, 维修与备件采购: 6, 工程承包商: 7,
  }[clean(raw)] ?? 0;
}

// ---------- 商机阶段 ----------

/**
 * 旧跟进阶段 → { stage, closeReason, closeNote }。
 * 空阶段返回 null，由调用方按「有订单为赢单，否则为已报价」处理。
 */
function mapOpportunityStage(raw, lostReasonText = '') {
  const s = clean(raw);
  const code = (s.match(/^P(\d)/) || [])[1];
  switch (code) {
    case '0': return { stage: 'S1' };
    case '1': case '2': return { stage: 'S2' };
    case '3': return { stage: 'S4' };
    case '4': case '8': return { stage: 'S6' };
    case '5': return { stage: 'S7' };
    case '6': return { stage: 'WON' };
    case '9': return { stage: 'LOST', closeReason: mapLostReason(lostReasonText), closeNote: clean(lostReasonText) || s };
    case '7': return { stage: 'LOST', closeReason: 19, closeNote: '沉睡/流失' };
    default: return null;
  }
}

/** 输单原因文本 → close_reason（11-价格、12-交期、13-无货源、14-客户取消、15-竞争对手、19-其他） */
function mapLostReason(text) {
  const s = clean(text);
  if (/价格|价/.test(s)) return 11;
  if (/交期|货期/.test(s)) return 12;
  if (/无货|停产|找不到/.test(s)) return 13;
  if (/取消/.test(s)) return 14;
  if (/竞争|同行/.test(s)) return 15;
  return 19;
}

/** 阶段先后，用于同一客户多条商机合并时取最靠后的；结束状态排在进行中之后 */
const STAGE_ORDER = { S1: 1, S2: 2, S3: 3, S4: 4, S5: 5, S6: 6, S7: 7, LOST: 8, WON: 9 };

// ---------- 询盘 ----------

/** 询盘分级 → inquiry level（1-S、2-A、3-B、4-C）；空默认 B */
function mapInquiryLevel(raw) {
  return { S级: 1, A级: 2, B级: 3, C级: 4 }[clean(raw)] ?? 3;
}

/** 紧急程度 → urgent（高、紧急为 1） */
function mapUrgent(raw) {
  return ['高', '紧急'].includes(clean(raw)) ? 1 : 0;
}

/**
 * 询盘状态 → customer_inquiry.status（7-已报价、8-已成交、9-未成交）。
 * hasDeal：该询盘有型号行配对上了订单。
 */
function mapInquiryStatus(raw, hasDeal) {
  const s = clean(raw);
  if (s === '询价完成') return hasDeal ? 8 : 7;
  // 无法报价、未询价、询价中都为未成交（不用「已取消」：统计的询盘数不含已取消，要与旧报表一致）
  return 9;
}

// ---------- 货况、货期 ----------

/**
 * 货况文本 → { code, exact }，code 为 inquiry_item_condition 的值（0-未填）。
 * exact=false 表示是近似归类，调用方要把原文写进备注。
 */
function mapCondition(raw) {
  const s = clean(raw);
  if (!s) return { code: 0, exact: true };
  const exact = { 全新原装: 1, '99新': 2, 翻新: 3, 二手: 4, 拆机件: 5, 国产替代: 6 }[s];
  if (exact) return { code: exact, exact: true };
  let code = 0;
  if (/翻新|充新/.test(s)) code = 3;
  else if (/^99%?新$/.test(s)) code = 2;
  else if (/全新|库存/.test(s)) code = 1;
  else if (/成新|\d+新|二手/.test(s)) code = 4;
  else if (/国产|高仿|台版|组装|定制/.test(s)) code = 6;
  return { code, exact: false };
}

/** 一个时长文本里的最大天数（「4-6周」→ 42，「3个月」→ 90）；识别不了返回 null */
function maxDays(s) {
  const t = s.replace(/工作周/g, '周').replace(/个工作日/g, '天').replace(/个?月/g, '月');
  const m = t.match(/(\d+(?:\.\d+)?)(?:\s*-\s*(\d+(?:\.\d+)?))?\s*(天|周|月)?/);
  if (!m) return null;
  const n = Number(m[2] ?? m[1]);
  const unit = m[3] || '周'; // 「8-10」这种没写单位的，旧系统里都是周
  return n * ({ 天: 1, 周: 7, 月: 30 }[unit]);
}

/**
 * 货期文本 → { code, exact }，code 为 inquiry_lead_time 的值（0-未填）。按上限归档：
 * 现货 1；≤2 天 2；≤3 天 3；≤5 天 4；≤7 天 5；≤2 周 6；≤4 周 7；≤8 周 8；更长 9。
 */
function mapLeadTime(raw) {
  const s = clean(raw);
  if (!s) return { code: 0, exact: true };
  if (s === '现货') return { code: 1, exact: true };
  const days = maxDays(s);
  if (days === null) return { code: 0, exact: false };
  const code = days <= 2 ? 2 : days <= 3 ? 3 : days <= 5 ? 4 : days <= 7 ? 5 : days <= 14 ? 6 : days <= 28 ? 7 : days <= 56 ? 8 : 9;
  const exactTexts = { 2: '1-2天', 3: '2-3天', 4: '3-5天', 5: '5-7天', 6: '1-2周', 7: '2-4周', 8: '4-8周' };
  return { code, exact: exactTexts[code] === s };
}

// ---------- 订单 ----------

/** 付款方式 → payment_method 字典编码 */
function mapPaymentMethod(raw) {
  return { 信保订单: 'ALIBABA_TA', 银行转账: 'TT', 支付宝: 'ALIPAY', 微信: 'WECHAT', paypal: 'PAYPAL' }[clean(raw).toLowerCase()]
    ?? { 信保订单: 'ALIBABA_TA', 银行转账: 'TT', 支付宝: 'ALIPAY', 微信: 'WECHAT' }[clean(raw)] ?? null;
}

/** 订单状态 → sales_order_status 进度编码；已退款的订单保持有效，进度为已完成 */
function mapOrderProgress(raw) {
  return {
    待采购: 'PENDING_PURCHASE', 采购中: 'ORDERED', 已发货: 'RECEIVED', 货代已发货: 'TO_FORWARDER',
    货代已签收: 'SHIPPED', 客户已签收: 'COMPLETED', 已完成: 'COMPLETED', 已退款: 'COMPLETED',
  }[clean(raw)] ?? null;
}

/** 现货 / 期货 → 1 / 2 */
function mapStockType(raw) {
  return clean(raw) === '期货' ? 2 : 1;
}

/** 新老客户 → 1 / 2 */
function mapCustomerType(raw) {
  return clean(raw) === '老客户' ? 2 : 1;
}

module.exports = {
  normalizeMpn, fixModelText, clean, parseAmount, parsePercent, parseDate, parseDateTime, parseMonth,
  mapCountry, mapSourceChannel, mapAliLevel, mapCustomerRole,
  mapOpportunityStage, mapLostReason, STAGE_ORDER,
  mapInquiryLevel, mapUrgent, mapInquiryStatus,
  mapCondition, mapLeadTime,
  mapPaymentMethod, mapOrderProgress, mapStockType, mapCustomerType,
};
