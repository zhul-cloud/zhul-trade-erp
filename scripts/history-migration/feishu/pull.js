'use strict';
// 从飞书旧 ERP 读取记录与附件（只读），结果写到 out/feishu/。需要 ego 浏览器里已登录飞书。
//   node scripts/history-migration/feishu/pull.js              只读记录
//   node scripts/history-migration/feishu/pull.js attachments  读记录（已有就复用）并下载附件，可断点续传
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { SHEETS } = require('../load-source');

const WIKI_URL = process.env.HM_FEISHU_URL
  || 'https://tq32b0beypi.feishu.cn/wiki/PAJzwD6YAiEwsxkxwc3c2ZxdnUg?table=tblIvCdp4JXZdvBN';
const OUT_DIR = path.resolve(process.env.HM_OUT_DIR || path.join(__dirname, '../out'), 'feishu');

/**
 * 要读的表：逻辑表名 → 飞书表名或表 ID。
 * 飞书表名与导出 Excel 的工作表名相同；报表是汇总数据，不读。
 * 另外两张没导出过 Excel 的表按表 ID 读：询价和采购单「供应商」字段关联的完整供应商库，成本核算「退款单号」关联的退款表。
 */
const TABLES = {
  ...Object.fromEntries(Object.entries(SHEETS)
    .filter(([, d]) => !/Report$/.test(d.key))
    .map(([sheet, d]) => [d.key, sheet])),
  supplierLibrary: 'tblfbh3CaIj5fguI',
  refunds: 'tblr1GyD3KsLPLSW',
};

function main() {
  const mode = process.argv[2] === 'attachments' ? 'attachments' : 'records';
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const config = {
    wikiUrl: WIKI_URL,
    outDir: OUT_DIR,
    tables: TABLES,
    mode,
    skipMime: ['video/', 'application/zip', 'application/x-zip'], // 视频、压缩包不下载
    concurrency: 4,
  };
  const script = `const CONFIG = ${JSON.stringify(config)};\n${fs.readFileSync(path.join(__dirname, 'browser-pull.mjs'), 'utf8')}`;
  const res = spawnSync('ego-browser', ['nodejs'], { input: script, stdio: ['pipe', 'inherit', 'inherit'], maxBuffer: 1 << 26 });
  process.exit(res.status ?? 1);
}

main();
