'use strict';
// 与旧报表核对（规格「与旧报表核对」）：核对逻辑在后端（MigrationVerifier，迁移页面上的结果也是它算的），这里只负责调用并输出差异表。
//   node scripts/history-migration/verify.js <租户ID>
// 先生成旧报表数字 out/bundle/reports.json，再用平台账号令牌（~/.zhul-platform-token）调用本机后端 HM_API（默认 http://localhost:8080/api/v1）。
// 输出 out/review/7-报表核对.csv；金额类按「订单数 × ¥0.01」容差判断，数量类必须相等，已知差异单独标注。
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { writeReports } = require('./reports');

const OUT = path.resolve(process.env.HM_OUT_DIR || path.join(__dirname, 'out'));
const API = process.env.HM_API || 'http://localhost:8080/api/v1';

async function main() {
  const tenantId = Number(process.argv[2]);
  if (!Number.isInteger(tenantId) || tenantId <= 0) throw new Error('请指定租户ID');
  const bundleDir = path.join(OUT, 'bundle');
  writeReports(bundleDir);
  const token = fs.readFileSync(path.join(os.homedir(), '.zhul-platform-token'), 'utf8').trim();
  const res = await fetch(`${API}/system/history-migrations/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ tenantId, bundleDir }),
  });
  const body = await res.json();
  if (body.code !== 0) throw new Error(`核对失败：${body.message || res.status}`);
  const { summary, byMetric, rows } = body.data;

  const dir = path.join(OUT, 'review');
  fs.mkdirSync(dir, { recursive: true });
  const headers = ['报表', '范围', '指标', '旧报表', '新系统', '差额', '容差', '结论', '说明'];
  const keys = ['report', 'scope', 'metric', 'old', 'new', 'diff', 'tolerance', 'verdict', 'note'];
  const esc = (v) => { const s = String(v ?? ''); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s; };
  fs.writeFileSync(path.join(dir, '7-报表核对.csv'), `﻿${[headers.join(','), ...rows.map((x) => keys.map((k) => esc(x[k])).join(','))].join('\r\n')}\r\n`);
  console.table(byMetric.map((m) => ({ 报表指标: `${m.report}|${m.metric}`, 一致: m.consistent, 已知差异: m.known, 不一致: m.inconsistent })));
  console.log(`共 ${summary.total} 项，不一致 ${summary.inconsistent} 项；明细见 ${path.join(dir, '7-报表核对.csv')}`);
}

if (require.main === module) {
  main().catch((e) => {
    console.error(e.message);
    process.exit(1);
  });
}
