'use strict';
// 运行：node --test scripts/product-migration/
const test = require('node:test');
const assert = require('node:assert/strict');
const { normalizeMpn, sellerHits, specKeyOf, looksLikeModel } = require('./rules');
const { buildPlan } = require('./plan');
const { q, buildSql, buildRollback, MIGRATED_TABLES } = require('./generate-sql');

const base = () => ({
  BRANDS: [{ name: 'Siemens', country: 'Germany', logo: '/x.svg', color: '#009999' }],
  CATEGORIES: { controllers: 'PLC & Controllers', drives: 'Drives' },
  NON_GENUINE_BRANDS: ['PWERUN'],
  PRODUCTS: [],
});
const prod = (o) => ({ brand: 'Siemens', model: 'A-1', series: 'S1', cat: 'controllers', spec: 's', status: 'instock', ...o });

test('归一化与后端 MpnNormalizer 一致', () => {
  assert.equal(normalizeMpn('6ES7 214-1BD23-0XB0'), '6es72141bd230xb0');
  assert.equal(normalizeMpn('６ＥＳ７２１４'), '6es7214');
  assert.equal(normalizeMpn('---'), '');
  assert.equal(normalizeMpn(null), '');
});

test('卖家承诺识别：命中并报出规则名，产品事实不命中', () => {
  assert.ok(sellerHits('Every Fouwell-supplied part has a 12-month warranty').includes('质保 warranty'));
  assert.ok(sellerHits('Is this in stock and how fast can it ship?').length >= 2);
  assert.ok(sellerHits('email info@fouwell.com').includes('邮箱'));
  assert.ok(sellerHits('send us your nameplate').length === 1);
  assert.deepEqual(sellerHits('It is a CPU with 8 digital inputs, made in the US.'), []);
});

test('规格编码：小写蛇形，数字开头补前缀', () => {
  assert.equal(specKeyOf('Counters / Timers'), 'counters_timers');
  assert.equal(specKeyOf('3D view'), 'spec_3d_view');
  assert.equal(specKeyOf('***'), 'spec');
});

test('型号判断：单个型号 vs 一段描述', () => {
  assert.ok(looksLikeModel('6ES7212-1AE30-0XB0'));
  assert.ok(!looksLikeModel('Older non-EtherCAT Accurax G5'));
});

test('计划：品牌大小写变体、型号碰撞、缺品类、空型号都被报告而不是静默处理', () => {
  const src = base();
  src.PRODUCTS = [
    prod({ model: '6ES7 214-1BD23' }),
    prod({ model: '6es7214-1bd23' }), // 归一化后同一个型号
    prod({ brand: 'SIEMENS', model: 'B-2' }), // 品牌大小写变体
    prod({ model: 'C-3', cat: 'nonexistent' }),
    prod({ model: '---' }),
  ];
  const { report, products, brands } = buildPlan(src);
  assert.equal(report.mpnCollisions.length, 1);
  assert.equal(report.brandCollisions.length, 1);
  assert.equal(report.missingCategory.length, 1);
  assert.equal(report.emptyModels.length, 1);
  assert.equal(brands.length, 1);
  assert.equal(products.length, 2);
});

test('计划：生命周期映射与 no_known_replacement', () => {
  const src = base();
  src.PRODUCTS = [
    prod({ model: 'A1', status: 'instock' }), prod({ model: 'A2', status: 'legacy' }),
    prod({ model: 'A3', status: 'discont' }), prod({ model: 'A4', status: 'discont', no_known_replacement: true }),
  ];
  assert.deepEqual(buildPlan(src).products.map((p) => p.lifecycle), [1, 3, 4, 5]);
});

test('计划：非原厂品牌标记、品牌表缺失只报告', () => {
  const src = base();
  src.PRODUCTS = [prod({ brand: 'PWERUN', model: 'X1' })];
  const { brands, report } = buildPlan(src);
  assert.equal(brands[0].isGenuine, 0);
  assert.deepEqual(report.brandsWithoutSiteData, ['PWERUN']);
});

test('计划：参考价——采购报价排除，来源不明的报告，eBay / 网络参考迁入', () => {
  const src = base();
  src.PRODUCTS = [
    prod({ model: 'P1', sell_price: 10, sell_price_currency: 'USD', price_source: 'ebay_ref' }),
    prod({ model: 'P2', sell_price: 20, sell_price_currency: 'USD', price_source: 'web_ref:median_of_4' }),
    prod({ model: 'P3', sell_price: 30, sell_price_currency: 'USD', price_source: 'procurement_quote_min' }),
    prod({ model: 'P4', sell_price: 40, price_source: 'mystery' }),
  ];
  const { products, report } = buildPlan(src);
  assert.deepEqual(products.map((p) => p.price?.source ?? null), ['eBay 参考价（独立站迁移）', '网络参考价（独立站迁移）', null, null]);
  assert.equal(report.priceExcluded.length, 1);
  assert.equal(report.priceUnknownSource.length, 1);
});

test('计划：FAQ 命中卖家承诺被排除，放行清单里的才导入，同商品重复问题只留一条', () => {
  const src = base();
  src.PRODUCTS = [prod({
    model: 'F1',
    faq: [
      { q: 'What is it?', a: 'A PLC.' },
      { q: 'What is it?', a: 'A PLC again.' },
      { q: 'Do you offer a warranty?', a: 'Yes, 12 months.' },
      { q: 'How does it compare?', a: 'Best price-per-I/O.' },
    ],
  })];
  let plan = buildPlan(src);
  assert.deepEqual(plan.products[0].faqs.map((f) => f.question), ['What is it?']);
  assert.equal(plan.report.faqExcluded.length, 2);
  plan = buildPlan(src, { faqAllowlist: [{ brand: 'Siemens', model: 'F1', question: 'How does it compare?' }] });
  assert.equal(plan.products[0].faqs.length, 2);
  assert.equal(plan.report.faqAllowed.length, 1);
});

test('计划：型号关系方向——direct/successor 挂在较早型号下，对称类型两端都建，自引用与描述性 from 报告', () => {
  const src = base();
  src.PRODUCTS = [
    prod({ model: 'OLD-100', status: 'discont' }),
    prod({ model: 'NEW-200', compatibility: [
      { from: 'OLD-100', type: 'successor', note: 'Replacement.' },
      { from: 'PEER-300', type: 'compatible', note: 'Compatible.' },
      { from: 'Older revisions of this unit', note: 'prose' },
      { from: 'NEW-200', type: 'direct', note: 'self' },
      { from: 'GONE-1', type: 'direct', note: 'not in catalog' },
    ] }),
    prod({ model: 'PEER-300' }),
  ];
  const { products, report } = buildPlan(src);
  const by = (m) => products.find((p) => p.mpnRaw === m);
  assert.deepEqual(by('OLD-100').relationships.map((r) => [r.relatedMpn, r.type]), [['NEW-200', 2]]);
  assert.deepEqual(by('NEW-200').relationships.map((r) => [r.relatedMpn, r.type, !!r.relatedBrand]), [['PEER-300', 4, true]]);
  assert.deepEqual(by('PEER-300').relationships.map((r) => [r.relatedMpn, r.type]), [['NEW-200', 4]]);
  assert.equal(report.relationships.manual.length, 2); // 描述性 from + 不在目录里的非对称关系
  assert.equal(report.relationships.skipped.length, 1); // 自引用
});

test('SQL 转义：单引号、反斜杠、换行、NUL', () => {
  assert.equal(q("O'Brien"), "'O''Brien'");
  assert.equal(q('a\\b'), "'a\\\\b'");
  assert.equal(q('line1\nline2'), "'line1\\nline2'");
  assert.equal(q('x\0y'), "'xy'");
  assert.equal(q(null), 'NULL');
  assert.equal(q('💧 Water'), "'💧 Water'");
});

test('生成的 SQL：幂等写法、create_by 标记、目标库，回滚覆盖所有迁移表', () => {
  const src = base();
  src.PRODUCTS = [prod({ model: 'S1', specs: [['Voltage', '24 V']], applications: [{ icon: '💧', title: 'Pumps', desc: 'd' }], faq: [{ q: 'Q?', a: 'A.' }], sell_price: 1, sell_price_currency: 'USD', price_source: 'ebay_ref' })];
  const { sql, stats } = buildSql(buildPlan(src), { db: 'zhul_erp_test' });
  assert.match(sql, /USE `zhul_erp_test`;/);
  assert.match(sql, /ON DUPLICATE KEY UPDATE product\.id = product\.id/);
  assert.match(sql, /WHERE @pid IS NOT NULL AND NOT EXISTS \(SELECT 1 FROM product_faq/);
  assert.ok(!/DELETE FROM|DROP |TRUNCATE/i.test(sql), '导入脚本里不能有物理删除');
  assert.equal((sql.match(/'migration', 'migration'/g) || []).length > 0, true);
  assert.deepEqual([stats.products, stats.specs, stats.applications, stats.faqs, stats.prices], [1, 1, 1, 1, 1]);
  const rb = buildRollback({ db: 'zhul_erp_test' });
  for (const t of MIGRATED_TABLES) assert.match(rb, new RegExp(`UPDATE ${t} SET deleted_at = NOW\\(\\)`));
  assert.ok(!/DELETE FROM|DROP |TRUNCATE/i.test(rb), '回滚是软删除，不能有物理删除');
});
