'use strict';
// 清洗：读取 Excel 导出与飞书读取结果，组装清洗结果与复核报告。
//   node scripts/history-migration/clean.js
// 输出：out/bundle/*.json（导入用）、out/review/*.csv（复核报告，UTF-8 带 BOM，可用 Excel 打开）
// 人工结论：review/decisions.json（含客户信息，不进 git），格式见 README。
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { loadSource } = require('./load-source');
const feishu = require('./feishu/decode');
const { buildBundle } = require('./clean/build');
const { lineRecords, quoteRecords } = require('./ai-split');

const OUT = path.resolve(process.env.HM_OUT_DIR || path.join(__dirname, 'out'));
const DECISIONS = path.join(__dirname, 'review', 'decisions.json');

function csv(rows, headers) {
  const esc = (v) => {
    const s = v === null || v === undefined ? '' : typeof v === 'object' ? JSON.stringify(v) : String(v);
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return `﻿${[headers.join(','), ...rows.map((r) => headers.map((h) => esc(r[h])).join(','))].join('\r\n')}\r\n`;
}

function main() {
  const src = loadSource();
  if (src.missing.length) throw new Error(`缺少导出文件：${src.missing.join('、')}`);
  const fsTables = {};
  for (const key of ['inquiries', 'salesOrders', 'sourcingQuotes', 'shipments', 'supplierLibrary', 'refunds']) fsTables[key] = feishu.loadTable(key) || { rows: [] };
  const decisions = fs.existsSync(DECISIONS) ? JSON.parse(fs.readFileSync(DECISIONS, 'utf8')) : {};
  const { bundle, report } = buildBundle({
    tables: src.tables,
    feishu: { tables: fsTables, attachments: feishu.loadAttachmentIndex() },
    decisions,
    helpers: { lineRecords, quoteRecords },
  });

  const bundleDir = path.join(OUT, 'bundle');
  fs.mkdirSync(bundleDir, { recursive: true });
  const hash = crypto.createHash('sha1');
  for (const [name, data] of Object.entries(bundle)) {
    const text = JSON.stringify(data);
    hash.update(name).update(text);
    fs.writeFileSync(path.join(bundleDir, `${name}.json`), text);
  }
  const version = hash.digest('hex').slice(0, 16);
  fs.writeFileSync(path.join(bundleDir, 'manifest.json'), JSON.stringify({ version, builtAt: new Date().toISOString(), sourceFiles: src.files, stats: report.stats }, null, 2));

  // ---------- 复核报告 ----------
  const dir = path.join(OUT, 'review');
  fs.mkdirSync(dir, { recursive: true });
  const summary = [];
  for (const [table, counts] of Object.entries(report.sections)) {
    summary.push({ 表: table, 迁移: counts.migrate || 0, 合并: counts.merged || 0, 跳过: counts.skipped || 0, 需人工处理: counts.manual || 0, 合计: Object.values(counts).reduce((a, b) => a + b, 0) });
  }
  fs.writeFileSync(path.join(dir, '1-汇总.csv'), csv(summary, ['表', '迁移', '合并', '跳过', '需人工处理', '合计']));
  const details = [];
  for (const [table, rows] of Object.entries(report.results)) {
    for (const x of rows) if (x.disposition !== 'migrate' || x.reason || (x.issues && x.issues.length)) {
      details.push({ 表: table, 旧编号: x.src.code, 文件: x.src.file, 行号: x.src.row, 结果: { migrate: '迁移', merged: '合并', skipped: '跳过', manual: '需人工处理' }[x.disposition], 合并到: x.mergedInto || '', 原因: [x.reason, ...(x.issues || [])].filter(Boolean).join('；') });
    }
  }
  fs.writeFileSync(path.join(dir, '2-逐条处理结果.csv'), csv(details, ['表', '旧编号', '文件', '行号', '结果', '合并到', '原因']));
  const byCode = new Map();
  for (const t of ['customers', 'opportunities']) for (const x of report.results[t]) byCode.set(x.src.code, x);
  const describe = (code) => {
    const row = src.tables.customers.find((c) => c.__src.code === code) || src.tables.opportunities.find((o) => o.__src.code === code) || {};
    return `${row['联系人'] || ''} / ${row['国家'] || row['国家/地区'] || ''} / ${row['邮箱'] || row['邮箱地址'] || ''} / ${row['手机号'] || row['联系方式'] || ''}`;
  };
  fs.writeFileSync(path.join(dir, '3-疑似重复客户.csv'), csv(report.suspects.map((s) => ({ 旧编号A: s.a, 资料A: describe(s.a), 旧编号B: s.b, 资料B: describe(s.b), 依据: s.reason, '结论（同一人 / 不是）': '' })),
    ['旧编号A', '资料A', '旧编号B', '资料B', '依据', '结论（同一人 / 不是）']));
  fs.writeFileSync(path.join(dir, '4-待处理事项.csv'), csv(report.issues.map((i) => ({ 类别: i.kind, 旧编号: i.code, 说明: i.text })), ['类别', '旧编号', '说明']));
  fs.writeFileSync(path.join(dir, '5-未配对订单行.csv'), csv(report.unmatchedOrderLines.map((id) => {
    const [so, no] = id.split('#');
    const line = (bundle.orders.find((o) => o.key === so) || { lines: [] }).lines.find((l) => String(l.lineNo) === no) || {};
    const sug = report.pairSuggestions.filter((s) => s.orderLine === id).map((s) => s.inquiryLine).join(' ');
    return { 订单: so, 行号: no, 型号: line.model, 建议的询盘行: sug };
  }), ['订单', '行号', '型号', '建议的询盘行']));
  fs.writeFileSync(path.join(dir, '6-人员.csv'), csv(bundle.users.map((u) => ({ 姓名: u.name, 推断岗位: u.role === 'purchaser' ? '采购员' : '业务员', 作为业务员的单据数: u.sales, 作为采购员的单据数: u.purchase })),
    ['姓名', '推断岗位', '作为业务员的单据数', '作为采购员的单据数']));

  console.log(`清洗结果版本 ${version}`);
  console.log(JSON.stringify(report.stats, null, 2));
  console.table(summary);
  console.log(`待处理事项 ${report.issues.length} 条，疑似重复 ${report.suspects.length} 组；报告在 ${dir}`);
}

main();
