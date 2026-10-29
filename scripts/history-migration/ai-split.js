'use strict';
// AI 拆分：型号文本（询盘、订单）、询价进价、附件。结果写入 out/ai-cache/，可反复运行，只处理没缓存的。
//   node scripts/history-migration/ai-split.js lines        询盘与订单的型号拆分
//   node scripts/history-migration/ai-split.js quotes       询价进价抽取
//   node scripts/history-migration/ai-split.js attachments  附件解析（型号见附件的询盘/订单、多型号订单的 PI）
//   node scripts/history-migration/ai-split.js all
// 选项：--sample N 只处理按固定规则抽出的 N 条（用于抽样把关）；--concurrency N（默认 3）
const crypto = require('node:crypto');
const { loadSource } = require('./load-source');
const { readXlsx } = require('./lib/xlsx');
const feishu = require('./feishu/decode');
const ai = require('./clean/ai');
const r = require('./rules');

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

function arg(name, def) {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : def;
}

/** 按旧编号的哈希排序取前 N 条：抽样固定、可重复 */
function sample(records, n) {
  if (!n) return records;
  const h = (s) => crypto.createHash('md5').update(String(s)).digest('hex');
  return [...records].sort((a, b) => h(a.id).localeCompare(h(b.id))).slice(0, n);
}

/** 询盘、订单的型号拆分输入 */
function lineRecords(tables) {
  const inq = tables.inquiries.map((x) => ({
    id: x.__src.code, brand: ai.brandHint(x['品牌']), category: r.clean(x['品类']).split(',')[0],
    text: r.clean(x['型号']), info: r.clean(x['询盘信息']).slice(0, 1500), quantity: r.clean(x['数量']),
  }));
  const so = tables.salesOrders.map((x) => ({
    id: x.__src.code, brand: ai.brandHint(x['品牌']), category: r.clean(x['品类']).split(',')[0],
    text: r.clean(x['型号']), info: '', quantity: r.clean(x['数量']),
  }));
  return { inq, so };
}

/** 询价进价抽取输入 */
function quoteRecords(tables) {
  return tables.sourcingQuotes.map((x) => ({
    id: x.__src.code, model: r.clean(x['型号']).slice(0, 800), price: r.clean(x['进价']).slice(0, 800),
    note: r.clean(x['备注']).slice(0, 200), condition: r.clean(x['状态']), leadTime: r.clean(x['交货期']),
  }));
}

/** xlsx → 制表符分隔文本（附件表格交给 AI 前用） */
function sheetText(file) {
  const { headers, rows } = readXlsx(file);
  return [headers.join('\t'), ...rows.map((row) => headers.map((h) => String(row[h] ?? '').replace(/\s+/g, ' ')).join('\t'))].join('\n');
}

/** 需要解析的附件：型号缺失或 AI 判断要看附件的询盘/订单（models），多型号订单的 PI/INV（pi） */
function attachmentJobs(tables) {
  const index = feishu.loadAttachmentIndex();
  const jobs = [];
  const { inq, so } = lineRecords(tables);
  for (const [key, recs] of [['inquiries', inq], ['salesOrders', so]]) {
    const fsTable = feishu.loadTable(key);
    const byCode = new Map(fsTable.rows.map((row) => [row[key === 'inquiries' ? '询盘单号' : '销售单号'], row]));
    for (const rec of recs) {
      const res = ai.cachedResult('lines', rec);
      const atts = (byCode.get(rec.id) || { __attachments: [] }).__attachments
        .filter((a) => /image|pdf|sheet|excel/.test(a.mime || '') && feishu.attachmentPath(index, a.token));
      if (!atts.length) continue;
      const needModels = !rec.text || /附件|见图|图片/.test(rec.text) || (res && (res.seeAttachment || !(res.lines || []).length));
      const needPi = key === 'salesOrders' && res && (res.lines || []).length > 1;
      for (const a of atts) {
        const absPath = feishu.attachmentPath(index, a.token);
        const context = { code: rec.id, brand: rec.brand, text: rec.text.slice(0, 200) };
        if (needModels) jobs.push({ token: a.token, file: index[a.token].file, absPath, mime: a.mime, mode: 'models', context, code: rec.id });
        if (needPi && /pdf|sheet|excel/.test(a.mime)) jobs.push({ token: a.token, file: index[a.token].file, absPath, mime: a.mime, mode: 'pi', context, code: rec.id });
      }
    }
  }
  return jobs;
}

async function runAttachments(tables, { concurrency, n }) {
  const jobs = sample(attachmentJobs(tables).map((j) => ({ ...j, id: `${j.code}:${j.token}:${j.mode}` })), n);
  log(`附件：待解析 ${jobs.length} 个（型号 ${jobs.filter((j) => j.mode === 'models').length}，PI ${jobs.filter((j) => j.mode === 'pi').length}）`);
  const stats = { done: 0, cached: 0, failed: 0, costUsd: 0, stoppedByLimit: false };
  let next = 0;
  async function worker() {
    while (next < jobs.length && !stats.stoppedByLimit) {
      const job = jobs[next++];
      try {
        const res = await ai.parseAttachment(job, { readSheet: sheetText });
        if (res.cached) stats.cached++; else { stats.done++; stats.costUsd += res.costUsd || 0; }
      } catch (e) {
        if (e instanceof ai.UsageLimitError) { stats.stoppedByLimit = true; log(`额度用完，停止：${e.message}`); return; }
        stats.failed++;
        log(`附件 ${job.code} ${job.file} 失败：${e.message.slice(0, 150)}`);
      }
      if ((stats.done + stats.failed) % 10 === 0 && stats.done + stats.failed > 0) log(`  进度：新解析 ${stats.done}，已缓存 ${stats.cached}，失败 ${stats.failed}，累计折算费用 $${stats.costUsd.toFixed(2)}`);
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker));
  return stats;
}

async function main() {
  const kind = process.argv[2] || 'all';
  const n = Number(arg('sample', 0)) || 0;
  const concurrency = Number(arg('concurrency', 3));
  const { tables } = loadSource();
  const report = {};
  if (kind === 'lines' || kind === 'all') {
    const { inq, so } = lineRecords(tables);
    report.lines = await ai.runBatches('lines', sample([...inq, ...so], n), { concurrency, log });
  }
  if ((kind === 'quotes' || kind === 'all') && !report.lines?.stoppedByLimit) {
    report.quotes = await ai.runBatches('quotes', sample(quoteRecords(tables), n), { concurrency, log });
  }
  if ((kind === 'attachments' || kind === 'all') && !report.lines?.stoppedByLimit && !report.quotes?.stoppedByLimit) {
    report.attachments = await runAttachments(tables, { concurrency, n });
  }
  log('结果', JSON.stringify(report));
  if (Object.values(report).some((s) => s.stoppedByLimit)) {
    log('订阅额度用完了。额度恢复后重新运行同一条命令，会从断点继续。');
    process.exitCode = 2;
  }
}

if (require.main === module) main().catch((e) => { console.error(e); process.exit(1); });

module.exports = { lineRecords, quoteRecords, attachmentJobs, sheetText, sample };
