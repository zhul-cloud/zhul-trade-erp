'use strict';
// 供应商分类（规格「采购、发货、收款与退款迁移」，用户决定：公司与个人供货商建为供应商，名称带「店名」的按线上店铺处理）。
// 依据旧供应商库的「来源渠道」：淘宝 / 天猫 / 1688 / 闲鱼且没有微信 → 线上店铺；其余（微信、原厂、爱采购、工业控制网等）→ 供应商。
// 来源渠道为空时按名称判断：带「店名」为淘宝店铺，否则为供应商。
const r = require('../rules');
const { DISPOSITION, result } = require('./common');

/** 线上店铺渠道（purchase_order.channel / sourcing_quote.channel：1-淘宝、2-1688、3-闲鱼、5-其他） */
const SHOP_CHANNELS = [['淘宝', 1], ['天猫', 1], ['1688', 2], ['闲鱼', 3], ['咸鱼', 3]];

/** 「shuai6886  店名：华北UPS…」→ 店名部分；没有「店名」取整个名称 */
function shopNameOf(name) {
  const m = r.clean(name).match(/店名[:：]\s*(.+)$/);
  return (m ? m[1] : r.clean(name)).trim().slice(0, 100);
}

/** 一条供应商库记录 → { kind: 'shop' | 'supplier', channel, shopName?, supplier? } */
function classify(row) {
  const name = r.clean(row['供应商名称']);
  const channels = r.clean(row['来源渠道']).split(/,\s*/).filter(Boolean);
  const shop = SHOP_CHANNELS.find(([c]) => channels.includes(c));
  const hasWechat = channels.includes('微信');
  const isShop = shop ? !hasWechat : (!channels.length && /店名/.test(name));
  if (isShop) return { kind: 'shop', channel: shop ? shop[1] : 1, shopName: shopNameOf(name) };
  const company = r.clean(row['公司名称']);
  return {
    kind: 'supplier',
    channel: 4,
    supplier: {
      name: (company || name).slice(0, 128),
      shortName: company && name && company !== name ? name.slice(0, 50) : '',
      contactName: r.clean(row['联系人']).slice(0, 64),
      contactPhone: r.clean(row['联系人电话']).slice(0, 32),
      wechat: r.clean(row['微信']).slice(0, 64),
      address: r.clean(row['地址']).slice(0, 200),
      region: r.clean(row['区域']).slice(0, 100),
      remark: [
        channels.length ? `来源渠道：${channels.join('、')}` : '',
        r.clean(row['优势品牌品类']) ? `优势品牌品类：${r.clean(row['优势品牌品类'])}` : '',
        r.clean(row['合作等级']) ? `合作等级：${r.clean(row['合作等级'])}` : '',
        r.clean(row.QQ) ? `QQ：${r.clean(row.QQ)}` : '',
        r.clean(row['公司网站']) ? `网站：${r.clean(row['公司网站'])}` : '',
        r.clean(row['备注']),
      ].filter(Boolean).join('；').slice(0, 500),
    },
  };
}

/**
 * 清洗供应商库。
 *   library：飞书完整供应商库（解码后的行，带 __recordId）
 *   companies：导出 Excel 里的「99-供应商管理」（6 家公司，资料更全，单独迁为供应商）
 *   referenced：询价、采购单引用到的供应商编码集合
 * 规则：没被引用、名称也为空的跳过；线上店铺不建主数据，只给出渠道和店铺名；同名供应商合并为一家。
 * 返回 { results, byCode: Map(编码 → 分类结果), byRecordId: Map(飞书记录ID → 编码) }。
 */
function cleanSuppliers(library, companies, referenced) {
  const results = [];
  const byCode = new Map();
  const byRecordId = new Map();
  const supplierByName = new Map();
  for (const row of library) {
    const code = r.clean(row['供应商编码']);
    const withSrc = { ...row, __src: { table: 'supplierLibrary', file: '飞书', row: row.__recordId, code } };
    byRecordId.set(row.__recordId, code);
    const name = r.clean(row['供应商名称']) || r.clean(row['公司名称']);
    if (!name) {
      results.push(result(withSrc, referenced.has(code) ? DISPOSITION.MANUAL : DISPOSITION.SKIPPED, null, { reason: referenced.has(code) ? '名称为空但被询价或采购单引用' : '名称为空且没有被引用' }));
      continue;
    }
    const c = classify(row);
    if (c.kind === 'shop') {
      byCode.set(code, c);
      results.push(result(withSrc, DISPOSITION.MIGRATE, c, { reason: '线上店铺：不建供应商，询价与采购单记渠道和店铺名' }));
      continue;
    }
    const key = c.supplier.name.toLowerCase().replace(/\s+/g, '');
    const prev = supplierByName.get(key);
    if (prev) {
      byCode.set(code, { ...c, mergedInto: prev });
      results.push(result(withSrc, DISPOSITION.MERGED, null, { mergedInto: prev, reason: `同名供应商「${c.supplier.name}」` }));
      continue;
    }
    supplierByName.set(key, code);
    byCode.set(code, c);
    results.push(result(withSrc, DISPOSITION.MIGRATE, c));
  }
  for (const row of companies) {
    const code = row.__src.code;
    const c = {
      kind: 'supplier',
      channel: 4,
      supplier: {
        name: r.clean(row['供应商名称']).slice(0, 128), shortName: '', contactName: r.clean(row['联系人']).slice(0, 64),
        contactPhone: r.clean(row['联系人电话']).slice(0, 32), wechat: r.clean(row['微信']).slice(0, 64), address: r.clean(row['公司地址']).slice(0, 200),
        region: '', mainBrands: r.clean(row['主营品牌']).slice(0, 256),
        remark: [r.clean(row['主营品类']) ? `主营品类：${r.clean(row['主营品类'])}` : '', r.clean(row['备注'])].filter(Boolean).join('；').slice(0, 500),
      },
    };
    const key = c.supplier.name.toLowerCase().replace(/\s+/g, '');
    if (supplierByName.has(key)) {
      results.push(result(row, DISPOSITION.MERGED, null, { mergedInto: supplierByName.get(key), reason: `同名供应商「${c.supplier.name}」` }));
      continue;
    }
    supplierByName.set(key, code);
    byCode.set(code, c);
    results.push(result(row, DISPOSITION.MIGRATE, c));
  }
  return { results, byCode, byRecordId };
}

/** 引用多个供应商编码时（「S001,S002」）取第一个能对上的；返回分类结果或 null */
function resolveSupplier(byCode, codes) {
  for (const code of r.clean(codes).split(/[,，]\s*/).filter(Boolean)) {
    let c = byCode.get(code);
    if (c && c.mergedInto) c = { ...byCode.get(c.mergedInto), code: c.mergedInto };
    if (c) return { ...c, code: c.code || code };
  }
  return null;
}

module.exports = { cleanSuppliers, classify, shopNameOf, resolveSupplier };
