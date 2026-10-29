'use strict';
// 读取飞书旧 ERP 导出的 Excel。按工作表名称识别是哪张表（文件名会带「(1)」之类的后缀，不可靠）。
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { readXlsx } = require('./lib/xlsx');

const DEFAULT_SOURCE_DIR = path.join(os.homedir(), 'Downloads/历史数据');

/** 工作表名称 → 逻辑表名，以及该表的旧编号列（用于追溯与去重） */
const SHEETS = {
  '99-客户信息': { key: 'customers', code: '客户编码' },
  '00-商机跟进': { key: 'opportunities', code: '商机编码' },
  '00-内部询价': { key: 'inquiries', code: '询盘单号' },
  '00-采购询价': { key: 'sourcingQuotes', code: '询价单号' },
  '02-销售订单': { key: 'salesOrders', code: '销售单号' },
  '02-采购订单': { key: 'purchaseOrders', code: '采购单号' },
  '04-发货订单': { key: 'shipments', code: '发货单号' },
  '99-供应商管理': { key: 'suppliers', code: '供应商编码' },
  '外汇结算': { key: 'fxSettlements', code: '结汇单号' },
  '成本核算': { key: 'costSheets', code: '核算单号' },
  '公司经营报表(月度)': { key: 'operatingReport', code: '核算日期' },
  '公司销售报表(月度)': { key: 'companySalesReport', code: '销售日期' },
  '业务销售报表(月度)': { key: 'staffSalesReport', code: null },
  '客户销售报表(月度)': { key: 'customerSalesReport', code: '序列号' },
};

/**
 * 读取目录下全部导出文件。
 * 返回 { dir, tables: { 逻辑表名: rows }, files: { 逻辑表名: 文件名 } }；
 * 每行带 __src = { table, file, row, code }，用于追溯到源文件的具体行。
 * 缺少的表在 missing 里列出，由调用方决定是否中止。
 */
function loadSource(dir = process.env.HM_SOURCE_DIR || DEFAULT_SOURCE_DIR) {
  const tables = {};
  const files = {};
  const unknown = [];
  for (const name of fs.readdirSync(dir).filter((f) => f.endsWith('.xlsx') && !f.startsWith('~$')).sort()) {
    const { sheetName, rows } = readXlsx(path.join(dir, name));
    const def = SHEETS[sheetName];
    if (!def) { unknown.push(`${name}（工作表 ${sheetName}）`); continue; }
    if (tables[def.key]) throw new Error(`工作表 ${sheetName} 出现在两个文件里：${files[def.key]}、${name}`);
    tables[def.key] = rows.map((r) => {
      const code = def.code ? String(r[def.code] ?? '').trim() : '';
      return { ...r, __src: { table: def.key, file: name, row: r.__row, code } };
    });
    files[def.key] = name;
  }
  const missing = Object.values(SHEETS).map((d) => d.key).filter((k) => !tables[k]);
  return { dir, tables, files, missing, unknown };
}

module.exports = { loadSource, SHEETS, DEFAULT_SOURCE_DIR };

if (require.main === module) {
  const { dir, tables, missing, unknown } = loadSource(process.argv[2]);
  console.log(`数据目录：${dir}`);
  for (const [k, rows] of Object.entries(tables)) console.log(`  ${k.padEnd(20)} ${rows.length}`);
  if (missing.length) console.log(`缺少：${missing.join('、')}`);
  if (unknown.length) console.log(`无法识别的文件：${unknown.join('、')}`);
}
