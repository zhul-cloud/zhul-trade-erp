#!/usr/bin/env node
'use strict';
// 生成导入 SQL 与回滚 SQL。导入是幂等的：重复执行不会产生重复行，也不会覆盖人工改过的内容。
//   商品主体、参考价等有唯一键的表：INSERT ... ON DUPLICATE KEY UPDATE id = id（命中则什么都不改）
//   规格、技术资料、应用场景、FAQ、型号关系没有唯一键：INSERT ... SELECT ... WHERE NOT EXISTS
// 所有迁移行 create_by = 'migration'。回滚是软删除（deleted_at），不做物理删除。
// 用法：node scripts/product-migration/generate-sql.js [--db zhul_erp] [--out 目录]
const fs = require('node:fs');
const path = require('node:path');
const { run } = require('./dry-run');

/** MySQL 字符串字面量转义：反斜杠、单引号、NUL、换行都处理掉 */
function q(value) {
  if (value === null || value === undefined) return 'NULL';
  const s = String(value)
    .replace(/\0/g, '')
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "''")
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n');
  return `'${s}'`;
}

/** 命中唯一键时什么都不改；带联表的 INSERT ... SELECT 里裸写 id 会有歧义，所以带上表名 */
const odku = (table) => `ON DUPLICATE KEY UPDATE ${table}.id = ${table}.id`;

const SRC = '独立站迁移';
const BY = `'migration', 'migration'`;

function buildSql(plan, { db = 'zhul_erp' } = {}) {
  const out = [];
  const push = (s = '') => out.push(s);
  const stats = { brands: 0, categories: 0, series: 0, products: 0, specs: 0, documents: 0, applications: 0, faqs: 0, prices: 0, relationships: 0 };

  push(`-- 商品主数据迁移（自动生成，请勿手工编辑）。来源：独立站 js/data.js`);
  push(`-- 幂等：可重复执行。回滚见 rollback.sql。目标库：${db}`);
  push(`SET NAMES utf8mb4;`);
  push(`USE \`${db}\`;`);
  push(`START TRANSACTION;`);
  push();
  push(`-- 品牌`);
  for (const b of plan.brands) {
    push(`INSERT INTO product_brand (tenant_id, brand_name, country, logo_url, brand_color, is_genuine, status, create_by, update_by)`);
    push(`VALUES (0, ${q(b.name)}, ${q(b.country)}, '', ${q(b.color)}, ${b.isGenuine}, 1, ${BY}) ${odku('product_brand')};`);
    stats.brands++;
  }
  push(`\n-- 品类`);
  for (const c of plan.categories) {
    push(`INSERT INTO product_category (tenant_id, category_code, category_name, sort_order, status, create_by, update_by)`);
    push(`VALUES (0, ${q(c.code)}, ${q(c.name)}, ${c.sort}, 1, ${BY}) ${odku('product_category')};`);
    stats.categories++;
  }
  push(`\n-- 系列`);
  for (const s of plan.series) {
    push(`INSERT INTO product_series (tenant_id, brand_id, series_name, description, status, create_by, update_by)`);
    push(`SELECT 0, b.id, ${q(s.name)}, '', 1, ${BY} FROM product_brand b WHERE b.tenant_id = 0 AND b.brand_name = ${q(s.brand)}`);
    push(`${odku('product_series')};`);
    stats.series++;
  }

  const pidOf = (brand, normalized) =>
    `(SELECT p.id FROM product p JOIN product_brand b ON b.id = p.brand_id WHERE p.tenant_id = 0 AND b.tenant_id = 0 AND b.brand_name = ${q(brand)} AND p.mpn_normalized = ${q(normalized)})`;

  push(`\n-- 商品及其子资料`);
  for (const p of plan.products) {
    push(`\n-- ${p.brand} ${p.mpnRaw}`);
    const seriesExpr = p.series
      ? `(SELECT s.id FROM product_series s WHERE s.tenant_id = 0 AND s.brand_id = b.id AND s.series_name = ${q(p.series)})`
      : 'NULL';
    push(`INSERT INTO product (tenant_id, mpn_raw, mpn_normalized, mpn_display, brand_id, category_id, series_id, product_name, short_description, spec_summary, lifecycle_status, lifecycle_source, status, create_by, update_by)`);
    push(`SELECT 0, ${q(p.mpnRaw)}, ${q(p.mpnNormalized)}, ${q(p.mpnRaw)}, b.id, c.id, ${seriesExpr}, '', '', ${q(p.specSummary)}, ${p.lifecycle}, ${q(p.lifecycleSource)}, 1, ${BY}`);
    push(`FROM product_brand b JOIN product_category c ON c.tenant_id = 0 AND c.category_code = ${q(p.category)}`);
    push(`WHERE b.tenant_id = 0 AND b.brand_name = ${q(p.brand)} ${odku('product')};`);
    stats.products++;
    push(`SET @pid = ${pidOf(p.brand, p.mpnNormalized)};`);

    for (const s of p.specs) {
      push(`INSERT INTO product_specification (tenant_id, product_id, spec_key, spec_label, spec_value, spec_unit, source, verified, sort_order, create_by, update_by)`);
      push(`SELECT 0, @pid, ${q(s.key)}, ${q(s.label)}, ${q(s.value)}, '', ${q(SRC)}, 0, ${s.sort}, ${BY} FROM DUAL`);
      push(`WHERE @pid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_specification x WHERE x.product_id = @pid AND x.spec_key = ${q(s.key)});`);
      stats.specs++;
    }
    for (const d of p.documents) {
      push(`INSERT INTO product_document (tenant_id, product_id, document_type, title, file_url, language, version, source, verified, sort_order, create_by, update_by)`);
      push(`SELECT 0, @pid, ${d.type}, ${q(d.title)}, ${q(d.url)}, ${q(d.language)}, '', ${q(SRC)}, 0, ${d.sort}, ${BY} FROM DUAL`);
      push(`WHERE @pid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_document x WHERE x.product_id = @pid AND x.file_url = ${q(d.url)});`);
      stats.documents++;
    }
    for (const a of p.applications) {
      push(`INSERT INTO product_application (tenant_id, product_id, title, description, icon, verified, sort_order, create_by, update_by)`);
      push(`SELECT 0, @pid, ${q(a.title)}, ${q(a.description)}, ${q(a.icon)}, 0, ${a.sort}, ${BY} FROM DUAL`);
      push(`WHERE @pid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_application x WHERE x.product_id = @pid AND x.title = ${q(a.title)});`);
      stats.applications++;
    }
    for (const f of p.faqs) {
      push(`INSERT INTO product_faq (tenant_id, product_id, question, answer, source, sort_order, create_by, update_by)`);
      push(`SELECT 0, @pid, ${q(f.question)}, ${q(f.answer)}, 3, ${f.sort}, ${BY} FROM DUAL`);
      push(`WHERE @pid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_faq x WHERE x.product_id = @pid AND x.question = ${q(f.question)});`);
      stats.faqs++;
    }
    if (p.price) {
      push(`INSERT INTO product_reference_price (tenant_id, product_id, price_original, currency_code, exchange_rate, price_cny, price_source, price_date, create_by, update_by)`);
      push(`SELECT 0, @pid, ${p.price.original}, ${q(p.price.currency)}, NULL, NULL, ${q(p.price.source)}, NULL, ${BY} FROM DUAL WHERE @pid IS NOT NULL`);
      push(`${odku('product_reference_price')};`);
      stats.prices++;
    }
  }

  push(`\n-- 型号关系（放在所有商品之后，关联商品才查得到）`);
  for (const p of plan.products) {
    for (const r of p.relationships) {
      const related = r.relatedBrand ? pidOf(r.relatedBrand, r.relatedNormalized) : 'NULL';
      push(`INSERT INTO product_relationship (tenant_id, product_id, related_mpn, related_product_id, relationship_type, confidence, note, verified_by, sort_order, create_by, update_by)`);
      push(`SELECT 0, o.id, ${q(r.relatedMpn)}, ${related}, ${r.type}, ${r.confidence}, ${q(r.note)}, '', 0, ${BY}`);
      push(`FROM product o JOIN product_brand ob ON ob.id = o.brand_id WHERE o.tenant_id = 0 AND ob.tenant_id = 0 AND ob.brand_name = ${q(p.brand)} AND o.mpn_normalized = ${q(p.mpnNormalized)}`);
      push(`AND NOT EXISTS (SELECT 1 FROM product_relationship x WHERE x.product_id = o.id AND x.related_mpn = ${q(r.relatedMpn)} AND x.relationship_type = ${r.type});`);
      stats.relationships++;
    }
  }
  push(`\nCOMMIT;`);
  return { sql: out.join('\n') + '\n', stats };
}

const MIGRATED_TABLES = [
  'product_relationship', 'product_reference_price', 'product_faq', 'product_application',
  'product_document', 'product_specification', 'product', 'product_series', 'product_category', 'product_brand',
];

function buildRollback({ db = 'zhul_erp' } = {}) {
  const lines = [
    `-- 回滚商品主数据迁移：软删除所有 create_by = 'migration' 的行（不做物理删除）。`,
    `-- 注意：软删除的行仍占着唯一键，回滚后再次执行 migrate.sql 不会让它们"复活"；需要重新导入时先人工恢复或清理这些行。`,
    `SET NAMES utf8mb4;`, `USE \`${db}\`;`, `START TRANSACTION;`,
  ];
  for (const t of MIGRATED_TABLES) {
    lines.push(`UPDATE ${t} SET deleted_at = NOW(), update_by = 'migration-rollback' WHERE create_by = 'migration' AND deleted_at IS NULL;`);
  }
  lines.push('COMMIT;');
  return lines.join('\n') + '\n';
}

if (require.main === module) {
  const argv = process.argv.slice(2);
  const opt = (name, dflt) => (argv.includes(name) ? argv[argv.indexOf(name) + 1] : dflt);
  const db = opt('--db', 'zhul_erp');
  const outDir = path.resolve(opt('--out', path.join(__dirname, 'out')));
  const { plan } = run([]);
  const { sql, stats } = buildSql(plan, { db });
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'migrate.sql'), sql);
  fs.writeFileSync(path.join(outDir, 'rollback.sql'), buildRollback({ db }));
  console.log(`已生成 ${outDir}/migrate.sql 与 rollback.sql（目标库 ${db}）`);
  console.log(JSON.stringify(stats));
}

module.exports = { q, buildSql, buildRollback, MIGRATED_TABLES };
