'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const s = require('../clean/suppliers');

const lib = (code, o) => ({ __recordId: `rec${code}`, 供应商编码: code, 供应商名称: '', 公司名称: '', 来源渠道: '', 联系人: '', 联系人电话: '', 微信: '', 地址: '', 区域: '', 优势品牌品类: '', 合作等级: '', QQ: '', 公司网站: '', 备注: '', ...o });

test('按来源渠道分类：淘宝/1688/闲鱼为线上店铺，带微信或原厂等为供应商', () => {
  assert.deepEqual(s.classify(lib('S1', { 供应商名称: 'shuai6886  店名：华北UPS总代理', 来源渠道: '淘宝' })), { kind: 'shop', channel: 1, shopName: '华北UPS总代理' });
  assert.equal(s.classify(lib('S2', { 供应商名称: 'X', 来源渠道: '1688' })).channel, 2);
  assert.equal(s.classify(lib('S3', { 供应商名称: 'X', 来源渠道: '闲鱼' })).channel, 3);
  assert.equal(s.classify(lib('S4', { 供应商名称: 'A欧姆龙----刘旭忠', 来源渠道: '微信' })).kind, 'supplier');
  assert.equal(s.classify(lib('S5', { 供应商名称: 'X', 来源渠道: '1688, 微信' })).kind, 'supplier');
  assert.equal(s.classify(lib('S6', { 供应商名称: '李晓 东元机电', 公司名称: '淄博左元机电有限公司', 来源渠道: '原厂代理' })).supplier.name, '淄博左元机电有限公司');
  assert.equal(s.classify(lib('S7', { 供应商名称: 'tb123 店名：鼎盛工控' })).kind, 'shop'); // 渠道为空按名称
  assert.equal(s.classify(lib('S8', { 供应商名称: '汕头市伺科科技' })).kind, 'supplier');
});

test('清洗：空名称按是否被引用分为跳过或人工；同名供应商合并；多编码取第一个能对上的', () => {
  const res = s.cleanSuppliers([
    lib('S1', { 供应商名称: '' }),
    lib('S2', { 供应商名称: '' }),
    lib('S3', { 供应商名称: '王峰', 来源渠道: '微信' }),
    lib('S4', { 供应商名称: '王峰 ', 来源渠道: '微信' }),
    lib('S5', { 供应商名称: 'tb 店名：某店', 来源渠道: '淘宝' }),
  ], [], new Set(['S2']));
  const by = Object.fromEntries(res.results.map((x) => [x.src.code, x]));
  assert.equal(by.S1.disposition, 'skipped');
  assert.equal(by.S2.disposition, 'manual');
  assert.equal(by.S4.disposition, 'merged');
  assert.equal(s.resolveSupplier(res.byCode, 'S4').code, 'S3');
  assert.equal(s.resolveSupplier(res.byCode, 'S9, S5').shopName, '某店');
  assert.equal(s.resolveSupplier(res.byCode, ''), null);
});
