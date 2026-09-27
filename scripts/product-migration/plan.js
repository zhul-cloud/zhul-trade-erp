'use strict';
// 把独立站的 data.js 转成"导入计划"：要建哪些品牌、品类、系列、商品和子资料，
// 以及被排除、需要人工处理、超出字段长度的条目。纯函数，不读写数据库、不写文件，
// 干跑和生成 SQL 共用同一份计划，保证两者一致。

const fs = require('node:fs');
const path = require('node:path');
const { looksLikeModel, normalizeMpn, sellerHits, specKeyOf } = require('./rules');

const CREATE_BY = 'migration';
const LIFECYCLE = { instock: 1, legacy: 3, discont: 4 };
const OBSOLETE = 5;
const REL_TYPE = { direct: 1, successor: 2, functional: 3, compatible: 4 };
const REL_DEFAULT_TYPE = 5; // 没有 type 的条目，design.md 迁移表的默认值：交叉引用
const CONFIDENCE_MEDIUM = 3;
const SYMMETRIC = new Set([3, 4, 5, 6]);

// 数据库字段长度（schema_v1.2.sql），超出的要在干跑里报出来，而不是导入时才失败
const LIMITS = {
  brandName: 64, categoryName: 64, seriesName: 64, mpn: 128, specSummary: 300,
  specLabel: 64, specValue: 256, appTitle: 64, appDesc: 500, appIcon: 16,
  faqQuestion: 256, relNote: 500, docTitle: 128, docUrl: 512,
};

const key = (s) => String(s ?? '').trim().toLowerCase();

function isSafeUrl(url) {
  return /^(?:https?:\/\/[^\s\p{Cc}]+|\/(?![/\\])[^\s\p{Cc}]*)$/iu.test(url);
}

/**
 * @param {object} source loadSource() 的结果
 * @param {object} [opts]
 * @param {string} [opts.websiteRoot] 独立站根目录，用来检查技术资料文件是否存在
 * @param {Array<{brand:string,model:string,question:string}>} [opts.faqAllowlist] 人工放行的 FAQ
 */
function buildPlan(source, opts = {}) {
  const { PRODUCTS, BRANDS, CATEGORIES } = source;
  const nonGenuine = new Set((source.NON_GENUINE_BRANDS || []).map(key));
  const allow = new Set((opts.faqAllowlist || []).map((a) => `${key(a.brand)}|${normalizeMpn(a.model)}|${key(a.question)}`));

  const report = {
    brandCollisions: [], brandNearDuplicates: [], emptyModels: [], missingCategory: [],
    mpnCollisions: [], seriesNearDuplicates: [], overLength: [],
    faqExcluded: [], faqAllowed: [], priceImported: 0, priceExcluded: [], priceUnknownSource: [],
    freeTextSpecs: { weight: 0, dimensions: 0, origin: 0 },
    datasheets: [], relationships: { imported: [], skipped: [], manual: [], notesDropped: [] },
    notMigrated: { photos: 0, linkedin: 0, conditionNotes: 0 },
    brandsWithoutSiteData: [], logoNotMigrated: 0,
  };
  const over = (what, where, value, limit) => {
    if (String(value ?? '').length > limit) report.overLength.push({ what, where, length: String(value).length, limit });
  };

  // ---------- 品牌 ----------
  const siteBrand = new Map();
  for (const b of BRANDS) siteBrand.set(key(b.name), b);
  const brandByKey = new Map(); // 小写名称 → 规范品牌
  const brandRaw = new Map();
  for (const p of PRODUCTS) {
    const name = String(p.brand ?? '').trim();
    const k = key(name);
    if (!k) continue;
    if (!brandRaw.has(k)) brandRaw.set(k, new Set());
    brandRaw.get(k).add(name);
  }
  for (const [k, names] of brandRaw) {
    const list = [...names];
    if (list.length > 1) report.brandCollisions.push({ key: k, variants: list });
    const canonical = list[0];
    const site = siteBrand.get(k);
    if (!site) report.brandsWithoutSiteData.push(canonical);
    if (site?.logo) report.logoNotMigrated += 1;
    over('品牌名称', canonical, canonical, LIMITS.brandName);
    brandByKey.set(k, {
      name: canonical,
      country: site?.country ?? '',
      color: site?.color ?? '',
      isGenuine: nonGenuine.has(k) ? 0 : 1,
    });
  }
  // 品牌名近似重复：去掉所有非字母数字后相同，但写法不同（如 "Pro-face" 与 "ProFace"）
  const looseBrand = new Map();
  for (const b of brandByKey.values()) {
    const loose = normalizeMpn(b.name);
    if (!looseBrand.has(loose)) looseBrand.set(loose, []);
    looseBrand.get(loose).push(b.name);
  }
  for (const [, names] of looseBrand) if (names.length > 1) report.brandNearDuplicates.push(names);

  // ---------- 品类 ----------
  const categories = Object.entries(CATEGORIES).map(([code, name], i) => {
    over('品类名称', code, name, LIMITS.categoryName);
    return { code, name, sort: i + 1 };
  });
  const categoryCodes = new Set(categories.map((c) => c.code));

  // ---------- 系列 ----------
  const seriesMap = new Map(); // "品牌|系列" → {brand, name}
  const seriesLoose = new Map();

  // ---------- 商品 ----------
  const products = [];
  const productKey = new Map(); // "品牌|归一化型号" → product
  const byNormalized = new Map(); // 归一化型号 → product[]（解析型号关系用）

  for (const src of PRODUCTS) {
    const mpnRaw = String(src.model ?? '').trim();
    const normalized = normalizeMpn(mpnRaw);
    const brand = brandByKey.get(key(src.brand));
    if (!mpnRaw || !normalized) {
      report.emptyModels.push({ brand: src.brand, model: src.model });
      continue;
    }
    if (!brand) {
      report.emptyModels.push({ brand: src.brand, model: src.model, reason: '品牌为空' });
      continue;
    }
    if (!categoryCodes.has(src.cat)) {
      report.missingCategory.push({ brand: brand.name, model: mpnRaw, cat: src.cat });
      continue;
    }
    const pk = `${key(brand.name)}|${normalized}`;
    if (productKey.has(pk)) {
      report.mpnCollisions.push({ brand: brand.name, model: mpnRaw, collidesWith: productKey.get(pk).mpnRaw });
      continue;
    }
    over('型号', mpnRaw, mpnRaw, LIMITS.mpn);
    over('规格摘要', mpnRaw, src.spec, LIMITS.specSummary);

    let seriesName = String(src.series ?? '').trim();
    if (seriesName) {
      over('系列名称', mpnRaw, seriesName, LIMITS.seriesName);
      const sk = `${key(brand.name)}|${key(seriesName)}`;
      if (!seriesMap.has(sk)) seriesMap.set(sk, { brand: brand.name, name: seriesName });
      else seriesName = seriesMap.get(sk).name;
      const loose = `${key(brand.name)}|${normalizeMpn(seriesName)}`;
      if (!seriesLoose.has(loose)) seriesLoose.set(loose, new Set());
      seriesLoose.get(loose).add(seriesName);
    }

    let lifecycle = LIFECYCLE[src.status] ?? 6;
    if (src.no_known_replacement) lifecycle = OBSOLETE;
    const product = {
      key: pk, brand: brand.name, category: src.cat, series: seriesName || null,
      mpnRaw, mpnNormalized: normalized, specSummary: String(src.spec ?? '').trim(),
      lifecycle, lifecycleSource: `迁移自独立站 status=${src.status}${src.no_known_replacement ? '（no_known_replacement）' : ''}，未逐条核实`,
      specs: [], documents: [], applications: [], faqs: [], price: null, relationships: [], source: src,
    };
    productKey.set(pk, product);
    if (!byNormalized.has(normalized)) byNormalized.set(normalized, []);
    byNormalized.get(normalized).push(product);
    products.push(product);

    if (src.photo) report.notMigrated.photos += 1;
    if (src.linkedin) report.notMigrated.linkedin += 1;
    if (src.condition_note || src.condition_note_ru) report.notMigrated.conditionNotes += 1;

    // ----- 规格 -----
    const usedKeys = new Map();
    (src.specs || []).forEach(([label, value], i) => {
      const cleanLabel = String(label ?? '').trim();
      const cleanValue = String(value ?? '').trim();
      if (!cleanLabel || !cleanValue) return;
      over('规格名称', mpnRaw, cleanLabel, LIMITS.specLabel);
      over('规格值', `${mpnRaw} / ${cleanLabel}`, cleanValue, LIMITS.specValue);
      let k = specKeyOf(cleanLabel);
      const n = (usedKeys.get(k) ?? 0) + 1;
      usedKeys.set(k, n);
      if (n > 1) k = `${k}_${n}`;
      product.specs.push({ key: k, label: cleanLabel, value: cleanValue, sort: i });
      if (/^net weight$/i.test(cleanLabel)) report.freeTextSpecs.weight += 1;
      else if (/^dimensions/i.test(cleanLabel)) report.freeTextSpecs.dimensions += 1;
      else if (/^(country of )?origin$/i.test(cleanLabel)) report.freeTextSpecs.origin += 1;
    });

    // ----- 技术资料 -----
    if (src.datasheet) {
      const url = String(src.datasheet).trim();
      let exists = null;
      if (opts.websiteRoot && url.startsWith('/')) exists = fs.existsSync(path.join(opts.websiteRoot, url));
      report.datasheets.push({ model: mpnRaw, url, fileExists: exists, safeUrl: isSafeUrl(url) });
      if (isSafeUrl(url)) product.documents.push({ type: 1, title: 'Datasheet', url, language: 'en', sort: 0 });
    }

    // ----- 应用场景 -----
    (src.applications || []).forEach((a, i) => {
      const title = String(a.title ?? '').trim();
      if (!title) return;
      over('应用场景标题', mpnRaw, title, LIMITS.appTitle);
      over('应用场景说明', `${mpnRaw} / ${title}`, a.desc, LIMITS.appDesc);
      over('应用场景图标', `${mpnRaw} / ${title}`, a.icon, LIMITS.appIcon);
      product.applications.push({ title, description: String(a.desc ?? '').trim(), icon: String(a.icon ?? '').trim(), sort: i });
    });

    // ----- FAQ（命中卖家承诺关键词的不导入）-----
    const seenQ = new Set();
    (src.faq || []).forEach((f, i) => {
      const q = String(f.q ?? '').trim();
      const a = String(f.a ?? '').trim();
      if (!q || !a) return;
      const hits = sellerHits(q, a);
      const ak = `${key(brand.name)}|${normalized}|${key(q)}`;
      if (hits.length && !allow.has(ak)) {
        report.faqExcluded.push({ brand: brand.name, model: mpnRaw, question: q, hits });
        return;
      }
      if (hits.length) report.faqAllowed.push({ brand: brand.name, model: mpnRaw, question: q, hits });
      over('FAQ 问题', mpnRaw, q, LIMITS.faqQuestion);
      if (seenQ.has(key(q))) return; // 同商品内重复问题：只留第一条
      seenQ.add(key(q));
      product.faqs.push({ question: q, answer: a, sort: i });
    });

    // ----- 参考价 -----
    if (src.sell_price !== undefined && src.sell_price !== null) {
      const ps = String(src.price_source ?? '');
      if (ps === 'procurement_quote_min') {
        report.priceExcluded.push({ model: mpnRaw, price: src.sell_price, reason: '采购报价（福唯自己的成本，属租户级数据）' });
      } else if (ps === 'ebay_ref' || ps.startsWith('web_ref')) {
        product.price = {
          original: Number(src.sell_price), currency: String(src.sell_price_currency || 'USD'),
          source: ps === 'ebay_ref' ? 'eBay 参考价（独立站迁移）' : '网络参考价（独立站迁移）',
        };
        report.priceImported += 1;
      } else {
        report.priceUnknownSource.push({ model: mpnRaw, price: src.sell_price, source: ps || '（空）' });
      }
    }
  }

  for (const [, names] of seriesLoose) if (names.size > 1) report.seriesNearDuplicates.push([...names]);

  // ---------- 型号关系（compatibility）----------
  // 方向约定：关系挂在商品 A 下，"关联型号 X 是 A 的{官方替代 / 后续型号}"。
  // 独立站的 from 是"较早的型号"，所以 direct / successor 要挂在 from 对应的商品下、关联到当前商品。
  const relKeys = new Set();
  const findProduct = (raw, preferBrand) => {
    const list = byNormalized.get(normalizeMpn(raw)) || [];
    if (list.length === 1) return list[0];
    return list.find((p) => key(p.brand) === key(preferBrand)) || null;
  };
  const addRel = (owner, relatedMpn, related, type, note) => {
    const rk = `${owner.key}|${normalizeMpn(relatedMpn)}|${type}`;
    if (relKeys.has(rk)) return;
    relKeys.add(rk);
    let cleanNote = String(note ?? '').trim();
    if (sellerHits(cleanNote).length) {
      report.relationships.notesDropped.push({ owner: owner.mpnRaw, related: relatedMpn, hits: sellerHits(cleanNote) });
      cleanNote = '';
    }
    over('关系说明', owner.mpnRaw, cleanNote, LIMITS.relNote);
    const row = {
      relatedMpn, relatedBrand: related ? related.brand : null, relatedNormalized: related ? related.mpnNormalized : null,
      type, confidence: CONFIDENCE_MEDIUM, note: cleanNote,
    };
    owner.relationships.push(row);
    report.relationships.imported.push({ owner: owner.mpnRaw, related: relatedMpn, type, inCatalog: !!related });
  };
  for (const p of products) {
    for (const c of p.source.compatibility || []) {
      const from = String(c.from ?? '').trim();
      const type = c.type ? REL_TYPE[c.type] : REL_DEFAULT_TYPE;
      const desc = `${p.mpnRaw} ← ${from}`;
      if (c.type && !type) {
        report.relationships.manual.push({ where: desc, reason: `未知的 type=${c.type}` });
        continue;
      }
      if (!looksLikeModel(from)) {
        report.relationships.manual.push({ where: desc, reason: 'from 是一段描述而不是型号，无法自动关联' });
        continue;
      }
      if (normalizeMpn(from) === p.mpnNormalized) {
        report.relationships.skipped.push({ where: desc, reason: '关联型号就是自己，由对端商品上的条目覆盖' });
        continue;
      }
      const other = findProduct(from, p.brand);
      if (SYMMETRIC.has(type)) {
        addRel(p, from, other, type, c.note);
        if (other) addRel(other, p.mpnRaw, p, type, c.note); // 两端都在目录内：补反向关系
      } else if (other) {
        addRel(other, p.mpnRaw, p, type, c.note);
      } else {
        report.relationships.manual.push({
          where: desc, reason: `非对称关系（类型 ${type}）要挂在 ${from} 下，但它不在目录里，无法建立`,
        });
      }
    }
  }

  const brands = [...brandByKey.values()];
  const series = [...seriesMap.values()];
  return { brands, categories, series, products, report, createBy: CREATE_BY };
}

module.exports = { buildPlan, LIMITS, CREATE_BY, REL_TYPE };
