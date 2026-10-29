'use strict';
// 组装清洗结果（design.md 决策 2）：各清洗模块的输出 → out/bundle/*.json，供后端导入任务读取。
// 外键一律用旧编号（客户用分组 ID），新 ID 由导入时的迁移对照给出。
const r = require('../rules');
const { DISPOSITION, result, cents, yuan, mulRound } = require('./common');
const { mergeParties, resolveOpportunity } = require('./parties');
const { cleanSuppliers, resolveSupplier } = require('./suppliers');
const { cleanFxSettlements, cleanCostSheets, cleanOperatingReport } = require('./finance');
const { parseInternational, parseDomestic } = require('./shipments');
const { pairOrderLines, allocateOrderLines } = require('./pairing');
const ai = require('./ai');

const CONFIDENCE = { high: 1, medium: 3, low: 3 }; // inquiry_item.confidence：1-确认、3-待核实、4-未识别
const PLACEHOLDER_PREFIX = 'PLACEHOLDER:';

/** 一条型号行（AI 或规则的结果）→ 导入用的型号行；型号为空的丢弃 */
function lineOf(l, source) {
  const model = r.fixModelText(l.model).slice(0, 128);
  if (!model) return null;
  const q = Number(l.quantity);
  return {
    brand: r.clean(l.brand).slice(0, 64),
    model,
    modelKey: r.normalizeMpn(model),
    quantity: Number.isInteger(q) && q > 0 ? q : null,
    description: r.clean(l.description).slice(0, 300),
    condition: r.clean(l.condition),
    confidence: CONFIDENCE[l.confidence] || 3,
    source,
  };
}

/** 同一记录里同一型号合并（数量相加） */
function mergeLines(lines) {
  const out = new Map();
  for (const l of lines.filter(Boolean)) {
    const k = `${l.brand.toLowerCase()}|${l.modelKey}`;
    const prev = out.get(k);
    if (prev) prev.quantity = (prev.quantity || 0) + (l.quantity || 0) || null;
    else out.set(k, { ...l });
  }
  return [...out.values()].map((l, i) => ({ ...l, lineNo: i + 1 }));
}

/**
 * 记录的型号行：文本拆分结果 + 附件解析结果（型号见附件时）。
 * 都没有时整段原文作为一行，置信度「未识别」。
 */
function linesOfRecord(rec, attachmentTokens) {
  const textRes = ai.cachedResult('lines', rec);
  let lines = (textRes && textRes.lines ? textRes.lines : []).map((l) => lineOf(l, 'text'));
  const fromAtt = [];
  for (const token of attachmentTokens) {
    const a = ai.cachedAttachment(token, 'models');
    if (a && a.lines) fromAtt.push(...a.lines.map((l) => lineOf({ ...l, brand: l.brand || rec.brand }, `attachment:${token}`)));
  }
  lines = mergeLines([...lines, ...fromAtt]);
  const status = textRes ? (lines.length ? 'parsed' : 'empty') : 'pending';
  if (!lines.length) {
    const raw = r.clean(rec.text || rec.info);
    if (raw) lines = [{ lineNo: 1, brand: rec.brand, model: r.fixModelText(raw.split('\n')[0]).slice(0, 128), modelKey: r.normalizeMpn(raw.split('\n')[0]), quantity: Number(rec.quantity) > 0 ? Number(rec.quantity) : null, description: raw.slice(0, 300), condition: '', confidence: 4, source: 'raw' }];
  }
  return { lines, status };
}

/**
 * 组装。输入：
 *   tables：Excel 各表（load-source 的 tables）
 *   feishu：{ tables: { key: 解码后的飞书表 }, attachments: 附件索引 }
 *   decisions：人工复核结论
 *   helpers：{ lineRecords, quoteRecords }（来自 ai-split，保证与 AI 缓存键一致）
 */
function buildBundle({ tables, feishu, decisions = {}, helpers }) {
  const report = { sections: {}, issues: [] };
  const issue = (kind, code, text) => report.issues.push({ kind, code, text });
  const fsRow = (key, codeField) => new Map(((feishu.tables[key] || {}).rows || []).map((x) => [x[codeField], x]));
  const fsInq = fsRow('inquiries', '询盘单号');
  const fsSo = fsRow('salesOrders', '销售单号');
  const fsQuote = fsRow('sourcingQuotes', '询价单号');
  const fsShip = fsRow('shipments', '发货单号');
  const okAtt = (token) => feishu.attachments[token] && feishu.attachments[token].status === 'ok';

  // ---------- 客户与商机 ----------
  const parties = mergeParties(tables, decisions);
  const groupOf = new Map(); // 旧客户编码 / 商机编码 → 分组 ID
  for (const g of parties.groups) {
    for (const c of g.customerCodes) groupOf.set(c, g.id);
    for (const o of g.opportunityCodes) groupOf.set(o, g.id);
  }
  for (const x of parties.customerResults) if (x.mergedInto) groupOf.set(x.src.code, groupOf.get(x.mergedInto) || x.mergedInto);
  const unreliable = new Set(parties.unreliableOpps.map((x) => x.code));

  // ---------- 供应商 ----------
  const referenced = new Set();
  for (const x of [...tables.sourcingQuotes, ...tables.purchaseOrders]) for (const c of r.clean(x['供应商']).split(/[,，]\s*/).filter(Boolean)) referenced.add(c);
  const suppliers = cleanSuppliers((feishu.tables.supplierLibrary || { rows: [] }).rows, tables.suppliers, referenced);

  // ---------- 询盘 ----------
  const { inq: inqRecs, so: soRecs } = helpers.lineRecords(tables);
  const inqRecByCode = new Map(inqRecs.map((x) => [x.id, x]));
  const users = new Map(); // 姓名 → { sales, purchase }
  const seeUser = (name, role) => {
    const n = r.clean(name);
    if (!n) return;
    const u = users.get(n) || { name: n, sales: 0, purchase: 0 };
    u[role]++;
    users.set(n, u);
  };
  const placeholders = new Map();
  const inquiries = [];
  const inquiryResults = [];
  const parseStatus = { parsed: 0, empty: 0, pending: 0 };
  for (const row of tables.inquiries) {
    const code = row.__src.code;
    const custCode = r.clean(row['客户']);
    const oppCode = r.clean(row['商机信息']);
    let customerKey = custCode ? groupOf.get(custCode) : null;
    if (!customerKey && oppCode && !unreliable.has(oppCode)) customerKey = groupOf.get(oppCode) || null;
    const owner = r.clean(row['业务员']) || r.clean(row['创建人']);
    if (!customerKey) {
      customerKey = `${PLACEHOLDER_PREFIX}${owner || '未知'}`;
      placeholders.set(customerKey, owner || '未知');
    }
    seeUser(owner, 'sales');
    const fs = fsInq.get(code);
    const tokens = (fs ? fs.__attachments : []).map((a) => a.token).filter(okAtt);
    const { lines, status } = linesOfRecord(inqRecByCode.get(code), tokens);
    parseStatus[status]++;
    const opp = oppCode && !unreliable.has(oppCode) && groupOf.get(oppCode) === customerKey ? oppCode : null;
    inquiries.push({
      key: code,
      customerKey,
      opportunityKey: opp,
      inquiryDate: r.parseDate(row['询盘日期']) || r.parseDate(row['创建时间']),
      urgent: r.mapUrgent(row['紧急程度']),
      level: r.mapInquiryLevel(row['询盘分级']),
      rawStatus: r.clean(row['状态']),
      owner,
      createdBy: r.clean(row['创建人']),
      createTime: r.parseDateTime(row['创建时间']),
      rawContent: [r.clean(row['型号']), r.clean(row['询盘信息'])].filter(Boolean).join('\n').slice(0, 300),
      remark: [r.clean(row['备注']), r.clean(row['产品偏好']) ? `产品偏好：${r.clean(row['产品偏好'])}` : '', r.clean(row['客户反馈']) ? `客户反馈：${r.clean(row['客户反馈'])}` : '']
        .filter(Boolean).join('；').slice(0, 500),
      category: r.clean(row['品类']).split(',')[0].slice(0, 32),
      attachments: tokens,
      lines,
    });
    inquiryResults.push(result(row, DISPOSITION.MIGRATE, null, status === 'pending' ? { reason: '型号还没有 AI 拆分结果' } : {}));
  }
  const inquiryByKey = new Map(inquiries.map((q) => [q.key, q]));

  // ---------- 询价 ----------
  const quoteRecs = new Map(helpers.quoteRecords(tables).map((x) => [x.id, x]));
  const tasks = new Map(); // 询盘 + 采购员 → 任务
  const quoteResults = [];
  for (const row of tables.sourcingQuotes) {
    const code = row.__src.code;
    const inqCodes = r.clean(row['询盘单号']).split(/[,，]\s*/).filter(Boolean);
    const inquiry = inqCodes.map((c) => inquiryByKey.get(c)).find(Boolean);
    if (!inquiry) { quoteResults.push(result(row, DISPOSITION.MANUAL, null, { reason: `找不到询盘单 ${inqCodes.join(',') || '（空）'}` })); continue; }
    const purchaser = r.clean(row['采购员']) || r.clean(row['创建人']);
    seeUser(purchaser, 'purchase');
    const res = ai.cachedResult('quotes', quoteRecs.get(code));
    const sup = resolveSupplier(suppliers.byCode, row['供应商']);
    const channel = sup ? sup.channel : 5;
    const cond = r.mapCondition(row['状态']);
    const lead = r.mapLeadTime(row['交货期']);
    const quotes = (res ? res.quotes : []).map((q) => {
      const model = r.fixModelText(q.model).slice(0, 128);
      const qc = q.condition ? r.mapCondition(q.condition) : cond;
      const ql = q.leadTime ? r.mapLeadTime(q.leadTime) : lead;
      const price = q.noStock ? null : (r.parseAmount(q.unitPrice) || null);
      return {
        model, modelKey: r.normalizeMpn(model), noStock: !!q.noStock || price === null,
        unitPrice: price, taxIncluded: q.taxIncluded === true ? 1 : 0,
        itemCondition: qc.code, leadTime: ql.code,
        note: [q.note, qc.exact ? '' : `货况原文：${q.condition || row['状态']}`, ql.exact ? '' : `货期原文：${q.leadTime || row['交货期']}`, r.clean(row['备注'])]
          .map(r.clean).filter(Boolean).join('；').slice(0, 300),
      };
    }).filter((q) => q.model || q.unitPrice);
    if (!res) issue('询价未拆分', code, '还没有 AI 进价抽取结果');
    const taskKey = `${inquiry.key}#${purchaser}`;
    const task = tasks.get(taskKey) || { key: taskKey, inquiryKey: inquiry.key, purchaser, createTime: r.parseDateTime(row['报价时间']), quotes: [] };
    tasks.set(taskKey, task);
    quotes.forEach((q, i) => {
      // 配到询盘的型号行：归一化型号相同；配不上新增一行
      let line = inquiry.lines.find((l) => l.modelKey && l.modelKey === q.modelKey);
      if (!line && q.modelKey) {
        line = { lineNo: inquiry.lines.length + 1, brand: '', model: q.model, modelKey: q.modelKey, quantity: null, description: '', condition: '', confidence: 3, source: `quote:${code}` };
        inquiry.lines.push(line);
      }
      task.quotes.push({
        key: `${code}#${i + 1}`, lineNo: line ? line.lineNo : null, ...q,
        channel, shopName: sup && sup.kind === 'shop' ? sup.shopName : (sup ? '' : r.clean(row['供应商名称']).slice(0, 128)),
        supplierKey: sup && sup.kind === 'supplier' ? sup.code : null,
        quotedBy: purchaser, quotedAt: r.parseDateTime(row['报价时间']),
        attachments: ((fsQuote.get(code) || {}).__attachments || []).map((a) => a.token).filter(okAtt),
      });
    });
    quoteResults.push(result(row, quotes.length ? DISPOSITION.MIGRATE : DISPOSITION.SKIPPED, null, quotes.length ? {} : { reason: res ? '进价里没有可用的报价' : '还没有 AI 进价抽取结果' }));
  }

  // ---------- 订单 ----------
  const fx = cleanFxSettlements(tables.fxSettlements);
  const receiptsBySo = new Map();
  for (const x of fx) if (x.data) (receiptsBySo.get(x.data.soCode) || receiptsBySo.set(x.data.soCode, []).get(x.data.soCode)).push({ key: x.src.code, ...x.data });
  const costs = cleanCostSheets(tables.costSheets);
  const snapshotBySo = new Map(costs.filter((x) => x.data).map((x) => [x.data.soCode, x.data]));
  const soRecByCode = new Map(soRecs.map((x) => [x.id, x]));
  const orders = [];
  const orderResults = [];
  for (const row of tables.salesOrders) {
    const code = row.__src.code;
    const custCode = r.clean(row['客户编码']);
    const owner = r.clean(row['业务员']) || r.clean(row['创建人']);
    let customerKey = custCode ? groupOf.get(custCode) : null;
    if (custCode && !customerKey) { orderResults.push(result(row, DISPOSITION.MANUAL, null, { reason: `客户 ${custCode} 找不到` })); continue; }
    if (!customerKey) {
      // 旧订单没有填客户：挂在该业务员名下的占位客户，订单收入照常计入
      customerKey = `${PLACEHOLDER_PREFIX}${owner || '未知'}`;
      placeholders.set(customerKey, owner || '未知');
      issue('订单没有客户', code, `挂在「未登记客户（历史迁移）-${owner || '未知'}」名下`);
    }
    seeUser(owner, 'sales');
    seeUser(row['采购员'], 'purchase');
    const fs = fsSo.get(code);
    const tokens = (fs ? fs.__attachments : []).map((a) => a.token).filter(okAtt);
    const { lines, status } = linesOfRecord(soRecByCode.get(code), tokens.filter((t) => /image|pdf/.test(feishu.attachments[t].mime)));
    const usd = r.parseAmount(row['销售总金额(USD)']);
    const cny = r.parseAmount(row['销售总金额(RMB)']);
    const isCny = (!usd || cents(usd) === 0) && cny && cents(cny) > 0;
    const receipts = receiptsBySo.get(code) || [];
    const rate = isCny ? '1' : (receipts[0] ? receipts[0].exchangeRate : null);
    if (!isCny && !rate) issue('订单没有结汇汇率', code, '没有结汇记录，导入时取销售日期的系统汇率');
    const total = isCny ? cny : (usd || '0');
    // PI/INV 附件里的逐行单价（按型号对上）
    const piLines = [];
    for (const t of tokens) {
      const p = ai.cachedAttachment(t, 'pi');
      if (p && p.isInvoice && Array.isArray(p.lines)) piLines.push(...p.lines.map((l) => ({ key: r.normalizeMpn(r.fixModelText(l.model)), unitPrice: r.parseAmount(l.unitPrice) || null })));
    }
    const alloc = allocateOrderLines(total, lines.map((l) => ({ quantity: l.quantity || 1, unitPrice: (piLines.find((p) => p.key && p.key === l.modelKey) || {}).unitPrice ?? null, costCny: null })));
    orders.push({
      key: code,
      customerKey,
      // 销售日期以旧系统的「销售日期」月份为准（统计按它归月）；创建日期在同一个月时用创建日期，否则取该月 1 日
      salesDate: (() => {
        const ym = r.parseMonth(row['销售日期']);
        const created = r.parseDate(row['创建时间']);
        if (!ym) return created;
        return created && created.startsWith(ym) ? created : `${ym}-01`;
      })(),
      customerType: r.mapCustomerType(row['客户类型']),
      stockType: r.mapStockType(row['交货状态']),
      owner,
      purchaser: r.clean(row['采购员']),
      currency: isCny ? 'CNY' : 'USD',
      exchangeRate: rate,
      totalAmount: yuan(cents(total)),
      totalAmountCny: rate ? mulRound(total, rate) : null,
      paymentMethod: r.mapPaymentMethod(row['付款方式']),
      progress: r.mapOrderProgress(row['状态']),
      rawStatus: r.clean(row['状态']),
      remark: r.clean(row['备注']).slice(0, 500),
      createdBy: r.clean(row['创建人']),
      createTime: r.parseDateTime(row['创建时间']),
      parseStatus: status,
      allocationBasis: alloc.basis,
      lines: lines.map((l, i) => ({ ...l, unitPrice: alloc.unitPrices[i], amount: alloc.amounts[i], amountCny: rate ? mulRound(alloc.amounts[i], rate) : null, inquiryLine: null })),
      receipts,
      snapshot: snapshotBySo.get(code) || null,
      attachments: tokens,
    });
    if (!snapshotBySo.get(code)) issue('订单没有成本核算', code, '没有核算单，订单成本快照为空');
    orderResults.push(result(row, DISPOSITION.MIGRATE));
  }

  // ---------- 配对：订单行 → 询盘行 ----------
  const inquiryLines = inquiries.flatMap((q) => q.lines.map((l) => ({ id: `${q.key}#${l.lineNo}`, customer: q.customerKey, owner: q.owner, modelKey: l.modelKey, date: q.inquiryDate })));
  const orderLines = orders.flatMap((o) => o.lines.map((l) => ({ id: `${o.key}#${l.lineNo}`, customer: o.customerKey, owner: o.owner, modelKey: l.modelKey, date: o.salesDate })));
  const pairing = pairOrderLines(inquiryLines, orderLines);
  const dealInquiries = new Set();
  for (const o of orders) {
    for (const l of o.lines) {
      const p = pairing.pairs.get(`${o.key}#${l.lineNo}`);
      if (!p) continue;
      const [inquiryKey, lineNo] = p.inquiryLine.split('#');
      l.inquiryLine = { inquiryKey, lineNo: Number(lineNo), round: p.round };
      dealInquiries.add(inquiryKey);
    }
  }
  for (const q of inquiries) q.status = r.mapInquiryStatus(q.rawStatus, dealInquiries.has(q.key));
  // 新老客户：询盘日期之前该客户已有订单为老客户（旧询盘没有这个字段）
  const firstOrderDate = new Map();
  for (const o of orders) if (!firstOrderDate.has(o.customerKey) || o.salesDate < firstOrderDate.get(o.customerKey)) firstOrderDate.set(o.customerKey, o.salesDate);
  for (const q of inquiries) q.customerType = firstOrderDate.has(q.customerKey) && firstOrderDate.get(q.customerKey) < q.inquiryDate ? 2 : 1;

  // ---------- 客户、商机定稿 ----------
  const groupHasOrder = new Set(orders.map((o) => o.customerKey));
  const oppRows = new Map(tables.opportunities.map((x) => [x.__src.code, x]));
  const customers = parties.groups.map((g) => ({ key: g.id, oldCodes: [...g.customerCodes, ...g.opportunityCodes], ...g.customer }));
  for (const [key, owner] of placeholders) {
    customers.push({ key, oldCodes: [], placeholder: true, name: `未登记客户（历史迁移）-${owner}`, contactName: '', country: '', sourceChannel: 0, aliLevel: '', customerRole: 0, email: '', phone: '', whatsapp: '', address: '', owner, createdBy: owner, createTime: null, remark: '旧系统询盘没有登记客户，迁移时统一挂在这里' });
  }
  const opportunities = parties.groups.filter((g) => g.opportunityCodes.length).map((g) => ({
    key: g.opportunityCodes[0], customerKey: g.id, ...resolveOpportunity(g.opportunityCodes.map((c) => oppRows.get(c)), groupHasOrder.has(g.id)),
  }));

  // ---------- 采购单 ----------
  const orderByKey = new Map(orders.map((o) => [o.key, o]));
  const purchases = [];
  const purchaseResults = [];
  const posBySo = new Map();
  for (const row of tables.purchaseOrders) (posBySo.get(r.clean(row['销售单号'])) || posBySo.set(r.clean(row['销售单号']), []).get(r.clean(row['销售单号']))).push(row);
  for (const row of tables.purchaseOrders) {
    const code = row.__src.code;
    const so = orderByKey.get(r.clean(row['销售单号']));
    if (!so) { purchaseResults.push(result(row, DISPOSITION.MANUAL, null, { reason: `订单 ${r.clean(row['销售单号'])} 没有迁移` })); continue; }
    const purchaser = r.clean(row['采购员']) || r.clean(row['创建人']);
    seeUser(purchaser, 'purchase');
    const sup = resolveSupplier(suppliers.byCode, row['供应商']);
    // 一张订单多张采购单时，按采购单型号文字里出现的型号分；都没出现就全部
    const textKey = r.normalizeMpn(row['型号']);
    let soLines = so.lines;
    if ((posBySo.get(so.key) || []).length > 1) {
      const hit = so.lines.filter((l) => l.modelKey && textKey.includes(l.modelKey));
      if (hit.length) soLines = hit;
    }
    const total = r.parseAmount(row['采购总金额(RMB)']) || '0';
    const alloc = allocateOrderLines(total, soLines.map((l) => ({ quantity: l.quantity || 1, costCny: null })));
    purchases.push({
      key: code,
      soKey: so.key,
      purchaser,
      channel: sup ? sup.channel : 5,
      shopName: sup ? (sup.kind === 'shop' ? sup.shopName : '') : '未登记',
      supplierKey: sup && sup.kind === 'supplier' ? sup.code : null,
      orderDate: r.parseDate(row['采购时间']) || r.parseDate(row['创建时间']),
      expectedShipDate: r.parseDate(row['预计发货日期']),
      currency: 'CNY',
      totalAmount: yuan(cents(total)),
      paidAmount: yuan(cents(r.parseAmount(row['已付款金额(RMB)']) || '0')),
      rawStatus: r.clean(row['状态']),
      remark: [r.clean(row['备注']), r.clean(row['状态']) === '已退货' ? '旧系统状态：已退货' : '', r.clean(row['转账账号']) ? '付款账户见旧系统' : ''].filter(Boolean).join('；').slice(0, 500),
      createdBy: r.clean(row['创建人']),
      createTime: r.parseDateTime(row['创建时间']),
      lines: soLines.map((l, i) => ({ soLineNo: l.lineNo, model: l.model, brand: l.brand, quantity: l.quantity || 1, unitPrice: alloc.unitPrices[i], amount: alloc.amounts[i] })),
    });
    purchaseResults.push(result(row, DISPOSITION.MIGRATE));
  }

  // ---------- 发货 ----------
  const purchaseByKey = new Map(purchases.map((p) => [p.key, p]));
  const shipments = [];
  const shipmentResults = [];
  const parsedStats = { international: 0, internationalWaybill: 0, domestic: 0, domesticTracking: 0 };
  for (const row of tables.shipments) {
    const code = row.__src.code;
    const po = purchaseByKey.get(r.clean(row['采购单号']));
    if (!po) { shipmentResults.push(result(row, DISPOSITION.MANUAL, null, { reason: `采购单 ${r.clean(row['采购单号'])} 没有迁移` })); continue; }
    const intl = parseInternational(row['物流单号(国际)']);
    const supplierTracks = parseDomestic(row['物流单号(供应商)']);
    const domestic = parseDomestic(row['物流单号(国内)']);
    if (intl) { parsedStats.international++; if (intl.waybillNo) parsedStats.internationalWaybill++; }
    for (const t of [row['物流单号(供应商)'], row['物流单号(国内)']]) {
      if (!r.clean(t)) continue;
      parsedStats.domestic++;
      if (parseDomestic(t).length) parsedStats.domesticTracking++;
    }
    const fs = fsShip.get(code) || { __attachments: [] };
    shipments.push({
      key: code, poKey: po.key, soKey: po.soKey,
      shipDate: r.parseDate(row['发货日期']),
      supplierTracks, domestic, domesticFreight: r.parseAmount(row['物流运费(国内)']) || null, international: intl,
      images: {
        ship: fs.__attachments.filter((a) => a.field === '发货图片' && okAtt(a.token) && /image/.test(a.mime)).map((a) => a.token),
        arrival: fs.__attachments.filter((a) => a.field === '到货图片' && okAtt(a.token) && /image/.test(a.mime)).map((a) => a.token),
      },
      note: [r.clean(row['物流单号(供应商)']), r.clean(row['物流单号(国内)']), r.clean(row['物流单号(国际)'])].filter(Boolean).join(' | ').slice(0, 300),
    });
    shipmentResults.push(result(row, DISPOSITION.MIGRATE));
  }

  // ---------- 费用与其他收入 ----------
  const op = cleanOperatingReport(tables.operatingReport);
  const expenses = op.filter((x) => x.data).flatMap((x) => x.data.expenses.map((e) => ({ key: `${x.data.month}#${e.category}`, ...e })));
  const otherIncomes = op.filter((x) => x.data && x.data.otherIncome).map((x) => ({ key: x.data.month, ...x.data.otherIncome }));

  // ---------- 退款：退款单 → 原订单（人工对照） ----------
  const refundMap = decisions.refundOrders || {};
  const refunds = [];
  for (const x of costs.filter((c) => c.disposition === DISPOSITION.MANUAL && /退款核算单/.test(c.reason || ''))) {
    const row = tables.costSheets.find((s) => s.__src.code === x.src.code);
    const no = r.clean(row['退款单号']);
    const soKey = refundMap[no];
    if (!soKey || !orderByKey.has(soKey)) { issue('退款单待对应订单', no, `退款 ${row['退款总金额(RMB)']}（${row['核算日期']}），请在 decisions.refundOrders 里填写原订单号`); continue; }
    refunds.push({ key: no, soKey, refundDate: `${r.parseMonth(row['核算日期'])}-15`, amountCny: yuan(cents(r.parseAmount(row['退款总金额(RMB)']) || '0')), costCny: yuan(cents(r.parseAmount(row['销售成本(RMB)']) || '0')) });
  }

  // ---------- 素材：图片按型号归档 ----------
  const media = [];
  const addMedia = (tokens, lines, category, sourceKey) => {
    const models = lines.filter((l) => l.modelKey && l.confidence !== 4);
    if (!models.length) return;
    for (const t of tokens) {
      const a = feishu.attachments[t];
      if (!a || a.status !== 'ok' || !/image/.test(a.mime)) continue;
      for (const l of models) media.push({ token: t, file: a.file, brand: l.brand, model: l.model, category, sourceKey });
    }
  };
  for (const q of inquiries) addMedia(q.attachments, q.lines, 3, q.key); // 客户来图
  for (const t of tasks.values()) {
    const q = inquiryByKey.get(t.inquiryKey);
    for (const quote of t.quotes) addMedia(quote.attachments, q.lines.filter((l) => l.lineNo === quote.lineNo), 4, quote.key); // 供方实拍
  }
  for (const s of shipments) {
    const lines = (orderByKey.get(s.soKey) || { lines: [] }).lines;
    addMedia(s.images.ship, lines, 6, s.key); // 供方发货
    addMedia(s.images.arrival, lines, 7, s.key); // 验货记录
  }

  // ---------- 人员 ----------
  const people = [...users.values()].map((u) => ({ name: u.name, role: u.purchase > u.sales ? 'purchaser' : 'sales', sales: u.sales, purchase: u.purchase }));

  // ---------- 报告 ----------
  const count = (rs) => rs.reduce((m, x) => (m[x.disposition] = (m[x.disposition] || 0) + 1, m), {});
  report.sections = {
    客户: count(parties.customerResults), 商机: count(parties.opportunityResults), 询盘: count(inquiryResults), 询价: count(quoteResults),
    订单: count(orderResults), 采购单: count(purchaseResults), 发货: count(shipmentResults), 结汇: count(fx), 成本核算: count(costs),
    经营报表: count(op), 供应商库: count(suppliers.results),
  };
  report.stats = {
    新客户: customers.length, 占位客户: placeholders.size, 商机: opportunities.length, 疑似重复: parties.suspects.length,
    型号拆分: parseStatus, 订单行: orderLines.length, 已配对订单行: pairing.pairs.size, 未配对订单行: pairing.unmatched.length,
    配对建议: pairing.suggestions.length, 发货解析: parsedStats, 素材: media.length, 人员: people.length,
  };
  report.suspects = parties.suspects;
  report.unreliableOpps = parties.unreliableOpps;
  report.unmatchedOrderLines = pairing.unmatched;
  report.pairSuggestions = pairing.suggestions;
  report.results = { customers: parties.customerResults, opportunities: parties.opportunityResults, inquiries: inquiryResults, quotes: quoteResults, orders: orderResults, purchases: purchaseResults, shipments: shipmentResults, fx, costs, operating: op, suppliers: suppliers.results };

  const bundle = {
    users: people,
    suppliers: suppliers.results.filter((x) => x.disposition === DISPOSITION.MIGRATE && x.data.kind === 'supplier').map((x) => ({ key: x.src.code, ...x.data.supplier })),
    customers,
    opportunities,
    inquiries: inquiries.map(({ rawStatus, ...q }) => q),
    sourcing: [...tasks.values()],
    orders,
    purchases,
    shipments,
    finance: { expenses, otherIncomes, refunds },
    media,
  };
  return { bundle, report };
}

module.exports = { buildBundle, lineOf, mergeLines, PLACEHOLDER_PREFIX };
