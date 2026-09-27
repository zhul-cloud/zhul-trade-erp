#!/usr/bin/env node
'use strict';
// 导入后核对（任务 8.4）：把数据库里的内容逐项和独立站 data.js 对一遍。
// 规格、生命周期、应用场景、参考价对全部商品核对；FAQ 与规格另外随机抽 5 个商品打印出来供人工目测。
// 用法：node verify-import.js [--db zhul_erp_test]   （密码用环境变量 MYSQL_PWD）
const { execFileSync } = require('node:child_process');
const { loadSource } = require('./load-source');
const { buildPlan } = require('./plan');
const { normalizeMpn } = require('./rules');
const { run } = require('./dry-run');

const argv = process.argv.slice(2);
const db = argv.includes('--db') ? argv[argv.indexOf('--db') + 1] : 'zhul_erp';
const query = (sql) =>
  execFileSync('mysql', ['-uroot', '-B', '-N', '--default-character-set=utf8mb4', db, '-e', sql], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
    .split('\n').filter(Boolean).map((l) => l.split('\t'));

const source = loadSource();
const { plan } = run([]);
const failures = [];
const check = (ok, msg) => { if (!ok) failures.push(msg); };

const idOf = new Map(query(`select b.brand_name, p.mpn_normalized, p.id from product p join product_brand b on b.id = p.brand_id where p.deleted_at is null`)
  .map(([b, n, id]) => [`${b.toLowerCase()}|${n}`, Number(id)]));
const lifecycleMap = { instock: 1, legacy: 3, discont: 4 };

// 1. 商品数与生命周期映射（全部）
check(idOf.size === source.PRODUCTS.length, `商品数不一致：库 ${idOf.size} / 源 ${source.PRODUCTS.length}`);
const lifecycle = new Map(query(`select id, lifecycle_status from product`).map(([id, s]) => [Number(id), Number(s)]));
for (const src of source.PRODUCTS) {
  const id = idOf.get(`${src.brand.toLowerCase()}|${normalizeMpn(src.model)}`);
  if (!id) { failures.push(`商品缺失：${src.brand} ${src.model}`); continue; }
  const expected = src.no_known_replacement ? 5 : lifecycleMap[src.status];
  check(lifecycle.get(id) === expected, `生命周期不一致：${src.model} 库 ${lifecycle.get(id)} / 期望 ${expected}`);
}

// 2. 规格（全部）：逐项 label / value / 顺序
const specs = new Map();
for (const [pid, label, value, sort] of query(`select product_id, spec_label, spec_value, sort_order from product_specification where deleted_at is null order by product_id, sort_order, id`)) {
  const k = Number(pid); if (!specs.has(k)) specs.set(k, []); specs.get(k).push([label, value, Number(sort)]);
}
for (const src of source.PRODUCTS) {
  const id = idOf.get(`${src.brand.toLowerCase()}|${normalizeMpn(src.model)}`);
  const got = (specs.get(id) || []).map(([l, v]) => `${l}=${v}`);
  const want = (src.specs || []).map(([l, v]) => `${String(l).trim()}=${String(v).trim()}`);
  check(JSON.stringify(got) === JSON.stringify(want), `规格不一致：${src.model}（库 ${got.length} 条 / 源 ${want.length} 条）`);
}

// 3. 参考价：全部与 sell_price 一致；采购报价的 14 条没有被迁入
const prices = new Map(query(`select product_id, price_original, currency_code, exchange_rate is null and price_cny is null from product_reference_price where deleted_at is null`)
  .map(([pid, p, c, empty]) => [Number(pid), { p: Number(p), c, empty: empty === '1' }]));
let procurement = 0;
for (const src of source.PRODUCTS) {
  const id = idOf.get(`${src.brand.toLowerCase()}|${normalizeMpn(src.model)}`);
  const got = prices.get(id);
  if (src.price_source === 'procurement_quote_min') {
    procurement++;
    check(!got, `采购报价被迁入了：${src.model}`);
  } else if (src.price_source === 'ebay_ref' || String(src.price_source).startsWith('web_ref')) {
    check(got && got.p === Number(src.sell_price) && got.c === 'USD' && got.empty, `参考价不一致：${src.model}`);
  } else {
    check(!got, `无可信来源的价格被迁入了：${src.model}`);
  }
}
check(procurement === 14, `采购报价商品应为 14 个，实际 ${procurement}`);

// 4. 应用场景（全部）：图标 emoji 原样、顺序一致
const apps = new Map();
for (const [pid, title, icon] of query(`select product_id, title, icon from product_application where deleted_at is null order by product_id, sort_order, id`)) {
  const k = Number(pid); if (!apps.has(k)) apps.set(k, []); apps.get(k).push(`${icon}|${title}`);
}
for (const src of source.PRODUCTS) {
  const id = idOf.get(`${src.brand.toLowerCase()}|${normalizeMpn(src.model)}`);
  const want = (src.applications || []).map((a) => `${String(a.icon).trim()}|${String(a.title).trim()}`);
  check(JSON.stringify(apps.get(id) || []) === JSON.stringify(want), `应用场景不一致：${src.model}`);
}

// 5. FAQ：全部为待审核；没有 Fouwell；每一条都能在源数据里找到原文
const faqRows = query(`select product_id, question, answer, source from product_faq where deleted_at is null`);
const faqSource = new Set();
for (const src of source.PRODUCTS) for (const f of src.faq || []) faqSource.add(`${String(f.q).trim()}\u0000${String(f.a).trim()}`);
for (const [, q, a, s] of faqRows) {
  check(s === '3', `FAQ 来源不是待审核：${q}`);
  check(!/fouwell/i.test(`${q} ${a}`), `FAQ 含 Fouwell：${q}`);
  check(faqSource.has(`${q}\u0000${a.replace(/\\n/g, '\n')}`) || faqSource.has(`${q}\u0000${a}`), `FAQ 与独立站原文不一致：${q}`);
}
check(faqRows.length === plan.report.faqExcluded.length * 0 + plan.products.reduce((n, p) => n + p.faqs.length, 0), `FAQ 条数与计划不一致：${faqRows.length}`);

// 6. 不该有的：图片视频、物流、海关
for (const t of ['product_media', 'product_logistics', 'product_customs']) {
  check(Number(query(`select count(*) from ${t}`)[0][0]) === 0, `${t} 应为 0 行`);
}

// 7. 品牌 / 品类 / 系列的数量
check(Number(query(`select count(*) from product_brand where deleted_at is null`)[0][0]) === plan.brands.length, '品牌数与计划不一致');
check(Number(query(`select count(*) from product_series where deleted_at is null`)[0][0]) === plan.series.length, '系列数与计划不一致');
check(Number(query(`select count(*) from product_category where deleted_at is null`)[0][0]) === plan.categories.length, '品类数与计划不一致');

// 随机抽 5 个商品，打印规格与 FAQ 供人工目测
const sample = [...source.PRODUCTS].sort(() => Math.random() - 0.5).slice(0, 5);
console.log('随机抽查（规格已逐项自动比对，这里打印便于目测）：');
for (const src of sample) {
  const id = idOf.get(`${src.brand.toLowerCase()}|${normalizeMpn(src.model)}`);
  console.log(`- ${src.brand} ${src.model}：规格 ${(specs.get(id) || []).length} 条，首条 ${JSON.stringify((specs.get(id) || [])[0])}`);
}

if (failures.length) {
  console.error(`\n✗ ${failures.length} 项不一致：`);
  for (const f of failures.slice(0, 40)) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`\n✓ 全部核对通过（库 ${db}）：商品 ${idOf.size}，规格 ${[...specs.values()].reduce((a, s) => a + s.length, 0)}，参考价 ${prices.size}，应用场景 ${[...apps.values()].reduce((a, s) => a + s.length, 0)}，FAQ ${faqRows.length}`);
