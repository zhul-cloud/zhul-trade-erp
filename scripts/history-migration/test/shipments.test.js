'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { parseInternational, parseDomestic } = require('../clean/shipments');

test('国际段：规格场景「6800001110 卡塔尔 2KG，运费：392元，DHL」', () => {
  const r = parseInternational('6800001110  卡塔尔  2KG，运费：392元，DHL');
  assert.equal(r.carrier, 'DHL');
  assert.equal(r.waybillNo, '6800001110');
  assert.equal(r.country.nameZh, '卡塔尔');
  assert.equal(r.weightKg, '2');
  assert.equal(r.freight, '392.00');
});

test('国际段：「费用 X KG 金额 超值 25」写法，运费含附加费', () => {
  const r = parseInternational('7300009160  墨西哥单号请查收，DHL 费用 12 KG 975 超值 25');
  assert.equal(r.country.nameZh, '墨西哥');
  assert.equal(r.weightKg, '12');
  assert.equal(r.baseFreight, '975.00');
  assert.deepEqual(r.surcharges, [{ name: '超值', amount: '25.00' }]);
  assert.equal(r.freight, '1000.00');
});

test('国际段：多项附加费、UPS 单号、没写承运商时推断', () => {
  const a = parseInternational('3700008165 罗马尼亚 19.5KG,运费：1370元，超值+25，偏远+325');
  assert.equal(a.freight, '1720.00');
  assert.equal(a.carrier, 'DHL');
  assert.equal(a.carrierInferred, true);
  const b = parseInternational('1ZHV25000001601018  墨西哥单号请查收，UPS 费用 6 KG 595 超值 25');
  assert.equal(b.carrier, 'UPS');
  assert.equal(b.waybillNo, '1ZHV25000001601018');
  assert.equal(b.freight, '620.00');
  const c = parseInternational('888500000288美国 9KG,运费：953元，超值+25，偏远+36');
  assert.equal(c.country.nameZh, '美国');
  assert.equal(c.carrier, 'FEDEX');
});

test('国际段：国家写简称、屏幕尺寸不当成运费、只有运单号', () => {
  const a = parseInternational('1447382252  印尼单号请查收，DHL 2.5 KG  220 超值 25 (5.7英寸屏）');
  assert.equal(a.country.nameZh, '印度尼西亚');
  assert.equal(a.freight, '245.00');
  const b = parseInternational('DHL 3800006164 、991');
  assert.equal(b.waybillNo, '3800006164');
  assert.equal(b.freight, null);
  assert.equal(parseInternational(''), null);
});

test('国内段：多个单号、承运商、金额', () => {
  const r = parseDomestic('SF0210000194519 顺丰 张总    51\nSF0210000290371  顺丰 张总   36');
  assert.deepEqual(r.map((x) => [x.carrier, x.trackingNo, x.amount]), [['顺丰', 'SF0210000194519', '51.00'], ['顺丰', 'SF0210000290371', '36.00']]);
  assert.equal(parseDomestic('KY4001100055315  跨越  张总   102.25元')[0].amount, '102.25');
  assert.equal(parseDomestic('运单号中通74100500002871')[0].carrier, '中通');
  assert.equal(parseDomestic('JDVB60000202112 京东')[0].trackingNo, 'JDVB60000202112');
  assert.deepEqual(parseDomestic('无'), []);
});
