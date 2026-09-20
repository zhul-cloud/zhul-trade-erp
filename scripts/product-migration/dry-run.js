#!/usr/bin/env node
'use strict';
// 干跑：读取独立站 data.js，只输出统计与异常，不写库、不写文件（除非 --json）。
// 用法：node scripts/product-migration/dry-run.js [--json] [--allowlist faq-allowlist.json]
const fs = require('node:fs');
const path = require('node:path');
const { loadSource } = require('./load-source');
const { buildPlan } = require('./plan');

function run(argv = process.argv.slice(2)) {
  const source = loadSource();
  const websiteRoot = path.resolve(path.dirname(source.file), '..');
  const alIdx = argv.indexOf('--allowlist');
  const alFile = alIdx >= 0 ? argv[alIdx + 1] : path.join(__dirname, 'faq-allowlist.json');
  const faqAllowlist = fs.existsSync(alFile) ? JSON.parse(fs.readFileSync(alFile, 'utf8')) : [];
  const plan = buildPlan(source, { websiteRoot, faqAllowlist });
  return { source, plan, faqAllowlist };
}

function print({ source, plan }) {
  const r = plan.report;
  const sum = (f) => plan.products.reduce((a, p) => a + f(p).length, 0);
  const line = (s = '') => console.log(s);
  line(`数据源：${source.file}`);
  line('\n== 将创建 ==');
  line(`商品 ${plan.products.length} / 品牌 ${plan.brands.length} / 品类 ${plan.categories.length} / 系列 ${plan.series.length}`);
  line(`规格 ${sum((p) => p.specs)} 条 / 技术资料 ${sum((p) => p.documents)} 条 / 应用场景 ${sum((p) => p.applications)} 条（${plan.products.filter((p) => p.applications.length).length} 个商品）`);
  line(`FAQ ${sum((p) => p.faqs)} 条（${plan.products.filter((p) => p.faqs.length).length} 个商品，全部 source=3 待审核）`);
  line(`参考价 ${r.priceImported} 条（全部 USD、本位币为空）/ 型号关系 ${r.relationships.imported.length} 条`);
  line('图片视频、物流、海关：0 行（没有可靠来源）');

  line('\n== 被排除或不迁移（不得静默丢弃）==');
  line(`采购报价排除 ${r.priceExcluded.length} 条（procurement_quote_min）`);
  line(`实拍图 ${r.notMigrated.photos} 张 / 营销图 ${r.notMigrated.linkedin} 张 / 成色备注 ${r.notMigrated.conditionNotes} 条：不迁移`);
  line(`规格里的自由文本（不自动解析，只报数）：重量 ${r.freeTextSpecs.weight} / 尺寸 ${r.freeTextSpecs.dimensions} / 原产国 ${r.freeTextSpecs.origin}`);
  line(`品牌 Logo：${r.logoNotMigrated} 个不迁移（独立站的站内相对路径，ERP 域名下打不开，界面回退为字母标）`);
  if (r.priceUnknownSource.length) line(`⚠ 有价格但来源不认识、未迁移：${JSON.stringify(r.priceUnknownSource)}`);

  line('\n== 需要人工关注 ==');
  const issues = [
    ['同品牌下归一化型号碰撞', r.mpnCollisions], ['品牌名大小写变体', r.brandCollisions],
    ['品牌名近似重复', r.brandNearDuplicates], ['系列名近似重复', r.seriesNearDuplicates],
    ['空型号 / 无品牌', r.emptyModels], ['缺品类', r.missingCategory], ['超出字段长度', r.overLength],
  ];
  for (const [name, list] of issues) line(`${name}：${list.length}${list.length ? `\n  ${JSON.stringify(list)}` : ''}`);
  if (r.brandsWithoutSiteData.length) line(`独立站品牌表里没有的品牌（国家、主题色留空）：${r.brandsWithoutSiteData.length} 个 — ${r.brandsWithoutSiteData.join('、')}`);

  line(`\n== 技术资料文件 ==`);
  for (const d of r.datasheets) line(`${d.model}: ${d.url} 文件${d.fileExists === null ? '未检查' : d.fileExists ? '存在' : '不存在'}${d.safeUrl ? '' : '（地址不合法，未迁移）'}`);

  line(`\n== 型号关系（compatibility）==`);
  line(`导入 ${r.relationships.imported.length} 条；跳过 ${r.relationships.skipped.length} 条；需人工 ${r.relationships.manual.length} 条；说明含卖家措辞已去掉 ${r.relationships.notesDropped.length} 条`);
  for (const m of r.relationships.manual) line(`  人工：${m.where} — ${m.reason}`);
  for (const m of r.relationships.skipped) line(`  跳过：${m.where} — ${m.reason}`);

  line(`\n== FAQ 排除清单（疑似卖家承诺，共 ${r.faqExcluded.length} 条，请人工复核误伤）==`);
  for (const f of r.faqExcluded) line(`  [${f.brand} ${f.model}] ${f.question}  ⇐ ${f.hits.join('、')}`);
  if (r.faqAllowed.length) line(`人工放行 ${r.faqAllowed.length} 条`);
  const total = plan.products.reduce((a, p) => a + (p.source.faq || []).length, 0);
  line(`\nFAQ：源数据 ${total} 条 − 排除 ${r.faqExcluded.length} − 同商品重复问题 ${total - r.faqExcluded.length - sum((p) => p.faqs)} = 导入 ${sum((p) => p.faqs)}`);
}

if (require.main === module) {
  const result = run();
  if (process.argv.includes('--json')) console.log(JSON.stringify(result.plan.report, null, 2));
  else print(result);
}

module.exports = { run };
