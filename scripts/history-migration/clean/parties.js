'use strict';
// 客户与商机合并（规格「客户与商机合并」，design.md 决策 5）。
// 旧客户表与入选商机的联系人一起分组：强证据自动合并，只有姓名相同的列为疑似重复，等人工结论。
const r = require('../rules');
const { DISPOSITION, result } = require('./common');

// ---------- 比较键：与后端 ContactKeys、CustomerNameNormalizer 一致 ----------

function emailKey(v) {
  const s = r.clean(v).replace(/\s/g, '').toLowerCase();
  return /^[^@]+@[^@]+\.[^@]+$/.test(s) ? s : '';
}

function phoneKey(v) {
  const d = r.clean(v).replace(/[^0-9]/g, '').replace(/^00/, '');
  return d.length < 6 ? '' : d;
}

const NAME_SUFFIXES = new Set(['co', 'company', 'ltd', 'limited', 'llc', 'inc', 'incorporated', 'corp', 'corporation',
  'gmbh', 'ag', 'sa', 'sas', 'srl', 'bv', 'nv', 'pty', 'plc', 'llp', 'kg', 'oy', 'ab', 'as', 'spa']);

function nameKey(v) {
  const words = r.clean(v).toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  const kept = [...words];
  while (kept.length && NAME_SUFFIXES.has(kept[kept.length - 1])) kept.pop();
  return (kept.length ? kept : words).join('');
}

/** 不能当作身份依据的联系人名（占位、无意义） */
const GENERIC_CONTACTS = new Set(['', 'username', 'none', 'null', 'na', 'customer', 'buyer', 'sir', 'mr', 'test', '私人']);
function contactKey(v) {
  const k = nameKey(v);
  return GENERIC_CONTACTS.has(k) || k.length < 2 ? '' : k;
}

/** 旧客户名称里不是公司名的值（作为客户名称会误导） */
const JUNK_NAMES = /^(私人|已注销|无货源|无|个人|未知|不明|-+)$/;

// ---------- 并查集 ----------

class UnionFind {
  constructor() { this.p = new Map(); }
  find(x) {
    if (!this.p.has(x)) this.p.set(x, x);
    let root = x;
    while (this.p.get(root) !== root) root = this.p.get(root);
    while (this.p.get(x) !== root) { const n = this.p.get(x); this.p.set(x, root); x = n; }
    return root;
  }
  union(a, b) { const ra = this.find(a); const rb = this.find(b); if (ra !== rb) this.p.set(rb, ra); return ra !== rb; }
}

/**
 * 参与分组的「人」：旧客户（key C…）与入选商机（key SJ…）。
 * 每人带比较键与国家；国家对不上时为 null。
 */
function personOfCustomer(row) {
  const country = r.mapCountry(row['国家']);
  return {
    code: row.__src.code, kind: 'customer', row,
    contact: r.clean(row['联系人']), country,
    emails: [emailKey(row['邮箱'])].filter(Boolean),
    phones: [phoneKey(row['手机号']), phoneKey(row.Whatsapp)].filter(Boolean),
    nameKey: contactKey(row['联系人']),
  };
}

function personOfOpportunity(row) {
  const country = r.mapCountry(row['国家/地区']);
  return {
    code: row.__src.code, kind: 'opportunity', row,
    contact: r.clean(row['联系人']), country,
    emails: [emailKey(row['邮箱地址'])].filter(Boolean),
    phones: [phoneKey(row['联系方式'])].filter(Boolean),
    nameKey: contactKey(row['联系人']),
  };
}

/**
 * 合并。输入旧客户、商机、询盘（取「客户 ↔ 商机」的直接关联），以及人工结论：
 *   decisions.samePerson    [[旧编号, 旧编号], …]  强制合并
 *   decisions.notSamePerson [[旧编号, 旧编号], …]  禁止按姓名合并
 * 返回 { customerResults, opportunityResults, groups, suspects, unreliableOpps }：
 *   groups：每组一个新客户，{ id, customerCodes, opportunityCodes, primary }
 *   suspects：疑似重复 [{ a, b, reason }]（还没有人工结论的）
 *   unreliableOpps：被多个客户的询盘引用、引用不可信的商机 [{ code, customers }]
 */
function mergeParties({ customers, opportunities, inquiries }, decisions = {}) {
  // 0. 一个商机被多个不同客户的询盘引用（旧系统批量回填时填错），这些引用不可信：不作为合并依据，也不让商机因此入选
  const customersOfOpp = new Map();
  for (const q of inquiries) {
    const o = r.clean(q['商机信息']); const c = r.clean(q['客户']);
    if (o && c) (customersOfOpp.get(o) || customersOfOpp.set(o, new Set()).get(o)).add(c);
  }
  const unreliableOpps = new Map([...customersOfOpp].filter(([, cs]) => cs.size > 1).map(([o, cs]) => [o, cs.size]));
  const trustedLink = (q) => !unreliableOpps.has(r.clean(q['商机信息'])) || !r.clean(q['客户']);

  // 1. 入选商机：有跟进阶段或有（可信的）询盘
  const oppWithInquiry = new Set(inquiries.filter(trustedLink).map((q) => r.clean(q['商机信息'])).filter(Boolean));
  const opportunityResults = [];
  const selected = [];
  for (const row of opportunities) {
    const code = row.__src.code;
    if (r.clean(row['跟进阶段']) || oppWithInquiry.has(code)) selected.push(row);
    else opportunityResults.push(result(row, DISPOSITION.SKIPPED, null, { reason: '没有跟进阶段也没有询盘' }));
  }

  const people = [...customers.map(personOfCustomer), ...selected.map(personOfOpportunity)];
  const byCode = new Map(people.map((p) => [p.code, p]));
  const uf = new UnionFind();
  people.forEach((p) => uf.find(p.code));
  const reasons = new Map(); // 合并依据，写进复核报告
  const link = (a, b, why) => { if (byCode.has(a) && byCode.has(b) && uf.union(a, b)) reasons.set(`${a}|${b}`, why); };

  const pairKey = (a, b) => [a, b].sort().join('|');
  const notSame = new Set((decisions.notSamePerson || []).map(([a, b]) => pairKey(a, b)));
  const same = (decisions.samePerson || []);

  // 2. 强证据：询盘同时填了客户和商机；邮箱、电话或 WhatsApp 相同
  for (const q of inquiries) {
    const c = r.clean(q['客户']); const o = r.clean(q['商机信息']);
    if (c && o && trustedLink(q)) link(c, o, `询盘 ${q.__src.code} 同时关联`);
  }
  for (const field of ['emails', 'phones']) {
    const seen = new Map();
    for (const p of people) for (const k of p[field]) {
      if (seen.has(k)) link(seen.get(k), p.code, `${field === 'emails' ? '邮箱' : '电话'}相同：${k}`);
      else seen.set(k, p.code);
    }
  }
  for (const [a, b] of same) link(a, b, '人工确认为同一人');

  // 3. 姓名 + 国家相同，且联系方式不矛盾 → 自动合并；姓名相同但国家不同或缺失 → 疑似重复
  const conflict = (a, b) => (a.emails.length && b.emails.length && !a.emails.some((e) => b.emails.includes(e)))
    || (a.phones.length && b.phones.length && !a.phones.some((x) => b.phones.includes(x)));
  const byName = new Map();
  for (const p of people) if (p.nameKey) (byName.get(p.nameKey) || byName.set(p.nameKey, []).get(p.nameKey)).push(p);
  const suspects = [];
  for (const list of byName.values()) {
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        const a = list[i]; const b = list[j];
        if (uf.find(a.code) === uf.find(b.code) || notSame.has(pairKey(a.code, b.code))) continue;
        const sameCountry = a.country && b.country && a.country.nameEn === b.country.nameEn;
        if (sameCountry && !conflict(a, b)) link(a.code, b.code, `联系人与国家相同：${a.contact}（${a.country.nameZh}）`);
        else suspects.push({ a: a.code, b: b.code, reason: sameCountry ? '联系人与国家相同，但联系方式不同' : `联系人相同，国家不同或缺失（${a.country?.nameZh || '空'} / ${b.country?.nameZh || '空'}）` });
      }
    }
  }
  // 后面的合并可能让早先的疑似已经在同一组里了
  const openSuspects = suspects.filter((s) => uf.find(s.a) !== uf.find(s.b));

  // 4. 分组，选出每组的主记录：优先旧客户（创建最早的），没有旧客户取最早的商机
  const groups = new Map();
  for (const p of people) {
    const root = uf.find(p.code);
    if (!groups.has(root)) groups.set(root, []);
    groups.get(root).push(p);
  }
  const createdAt = (p) => r.parseDateTime(p.row['创建时间']) || '9999';
  const out = [];
  const customerResults = [];
  for (const members of groups.values()) {
    const cs = members.filter((p) => p.kind === 'customer').sort((a, b) => createdAt(a).localeCompare(createdAt(b)));
    const os = members.filter((p) => p.kind === 'opportunity').sort((a, b) => createdAt(a).localeCompare(createdAt(b)));
    const primary = cs[0] || os[0];
    const id = primary.code;
    out.push({ id, customerCodes: cs.map((p) => p.code), opportunityCodes: os.map((p) => p.code), customer: buildCustomer(cs, os) });
    for (const p of cs) {
      if (p === primary) customerResults.push(result(p.row, DISPOSITION.MIGRATE, { groupId: id }));
      else customerResults.push(result(p.row, DISPOSITION.MERGED, null, { mergedInto: id, reason: whyMerged(reasons, p.code, members) }));
    }
    for (const p of os) {
      opportunityResults.push(result(p.row, DISPOSITION.MIGRATE, { groupId: id }, p === primary ? {} : { reason: whyMerged(reasons, p.code, members) }));
    }
  }
  return { customerResults, opportunityResults, groups: out, suspects: openSuspects, unreliableOpps: [...unreliableOpps].map(([code, n]) => ({ code, customers: n })) };
}

function whyMerged(reasons, code, members) {
  for (const [k, why] of reasons) if (k.split('|').includes(code)) return why;
  return `与 ${members[0].code} 同组`;
}

/** 一组 → 新客户的字段。旧客户的值优先，商机补空缺。 */
function buildCustomer(cs, os) {
  const first = (vals) => vals.map(r.clean).find(Boolean) || '';
  const cRows = cs.map((p) => p.row);
  const oRows = os.map((p) => p.row);
  const latestOpp = oRows[oRows.length - 1];
  const contact = first([...cRows.map((x) => x['联系人']), ...oRows.map((x) => x['联系人'])]);
  const companyNames = [...cRows.map((x) => x['客户名称']), ...oRows.map((x) => x['客户名称'])].map(r.clean).filter((n) => n && !JUNK_NAMES.test(n));
  const country = [...cs, ...os].map((p) => p.country).find(Boolean) || null;
  const sourceRaw = first([...cRows.map((x) => x['客户来源']), ...oRows.map((x) => x['来源'])]);
  const source = r.mapSourceChannel(sourceRaw);
  const aliLevel = source === 1 ? (r.mapAliLevel(first([...cRows.map((x) => x['客户等级']), ...oRows.map((x) => x['阿里等级'])]))) : '';
  const countryRaw = first([...cRows.map((x) => x['国家']), ...oRows.map((x) => x['国家/地区'])]);
  return {
    name: companyNames[0] || contact,
    contactName: contact,
    country: country ? country.nameEn : '',
    countryRaw: country ? '' : countryRaw, // 对不上清单的原文，写进备注
    sourceChannel: source,
    aliLevel,
    customerRole: r.mapCustomerRole(first(oRows.map((x) => x['客户类型']))),
    email: first([...cRows.map((x) => x['邮箱']), ...oRows.map((x) => x['邮箱地址'])].map((v) => (emailKey(v) ? v : ''))),
    phone: first([...cRows.map((x) => x['手机号']), ...oRows.map((x) => x['联系方式'])].map((v) => (phoneKey(v) ? v : ''))),
    whatsapp: first(cRows.map((x) => x.Whatsapp)),
    address: first([...cRows.map((x) => x['公司地址']), ...cRows.map((x) => x['地址']), ...oRows.map((x) => x['地址'])]),
    owner: first([latestOpp && latestOpp['业务员'], ...cRows.map((x) => x['创建人'])]),
    createdBy: first([...cRows.map((x) => x['创建人']), ...oRows.map((x) => x['创建人'])]),
    createTime: [...cRows, ...oRows].map((x) => r.parseDateTime(x['创建时间'])).filter(Boolean).sort()[0] || null,
    otherNames: companyNames.slice(1),
  };
}

/**
 * 一组的多条商机合并为一条（新系统一个客户最多一条商机）：
 * 首次接触日期取最早的，阶段取最靠后的；所有阶段都为空时，hasDeal 为真取赢单，否则为已报价。
 * 其余商机的内容写入需求摘要。oppRows 为该组的旧商机行（任意顺序）。
 */
function resolveOpportunity(oppRows, hasDeal) {
  if (!oppRows.length) return null;
  const rows = [...oppRows].sort((a, b) => (r.parseDate(a['商机日期']) || '').localeCompare(r.parseDate(b['商机日期']) || ''));
  let best = null;
  for (const row of rows) {
    const m = r.mapOpportunityStage(row['跟进阶段'], row['输单原因']);
    if (m && (!best || r.STAGE_ORDER[m.stage] >= r.STAGE_ORDER[best.stage])) best = { ...m, row };
  }
  if (hasDeal && (!best || best.stage !== 'WON')) best = { stage: 'WON', row: (best && best.row) || rows[rows.length - 1], byDeal: true };
  if (!best) best = { stage: 'S4', row: rows[rows.length - 1], byDefault: true };
  const latest = rows[rows.length - 1];
  const parts = [];
  for (const row of rows) {
    const text = ['商机内容', '沟通内容摘要', '后续计划', '对客户的思考', '推进卡点', '客户反馈', '备注']
      .map((k) => r.clean(row[k])).filter(Boolean).join('；');
    if (text) parts.push(rows.length > 1 ? `[${r.parseDate(row['商机日期'])}] ${text}` : text);
  }
  return {
    sourceCodes: rows.map((x) => x.__src.code),
    firstContactDate: r.parseDate(rows[0]['商机日期']),
    stage: best.stage,
    stageFromRaw: r.clean(best.row['跟进阶段']),
    stageRule: best.byDeal ? '有配对订单' : best.byDefault ? '阶段为空且没有订单' : '按旧阶段',
    closeReason: best.closeReason ?? 0,
    closeNote: best.closeNote ?? '',
    owner: r.clean(latest['业务员']) || r.clean(latest['创建人']),
    createdBy: r.clean(rows[0]['创建人']),
    createTime: r.parseDateTime(rows[0]['创建时间']),
    sourceChannel: r.mapSourceChannel(rows[0]['来源']),
    aliLevel: r.mapAliLevel(rows[0]['阿里等级']),
    reachedValid: ['S3', 'S4', 'S5', 'S6', 'S7', 'WON'].includes(best.stage),
    demandSummary: parts.join('\n').slice(0, 2000),
  };
}

module.exports = { mergeParties, resolveOpportunity, emailKey, phoneKey, nameKey, contactKey };
