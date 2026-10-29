'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { pairOrderLines, allocateOrderLines } = require('../clean/pairing');

const q = (id, customer, owner, modelKey, date) => ({ id, customer, owner, modelKey, date });

test('规格「成交次数」：同一客户、同型号、询盘在先 → 配对', () => {
  const { pairs, unmatched } = pairOrderLines(
    [q('XP1#1', 'C00721', '邱丽芳', 'mrj2s100a', '2026-10-10'), q('XP0#1', 'C00721', '邱丽芳', 'mrj2s100a', '2026-03-01')],
    [q('SO1#1', 'C00721', '邱丽芳', 'mrj2s100a', '2026-10-22')],
  );
  assert.deepEqual(pairs.get('SO1#1'), { inquiryLine: 'XP1#1', round: 1 });
  assert.deepEqual(unmatched, []);
});

test('询盘晚于订单不配；第 2 轮按业务员且限 180 天内；配不上的列出', () => {
  const { pairs, unmatched } = pairOrderLines(
    [
      q('XP1#1', 'C1', '郭如', 'abc123', '2026-10-25'), // 晚于订单
      q('XP2#1', 'C9', '郭如', 'abc123', '2026-06-01'), // 其他客户、同业务员、141 天前
      q('XP3#1', 'C9', '郭如', 'zzz999', '2025-01-01'), // 超过 180 天
    ],
    [q('SO1#1', 'C1', '郭如', 'abc123', '2026-10-20'), q('SO2#1', 'C1', '郭如', 'zzz999', '2026-10-20')],
  );
  assert.deepEqual(pairs.get('SO1#1'), { inquiryLine: 'XP2#1', round: 2 });
  assert.deepEqual(unmatched, ['SO2#1']);
});

test('复购：一条询盘行被多张订单配对；前缀相近只给建议', () => {
  const { pairs, suggestions } = pairOrderLines(
    [q('XP1#1', 'C1', 'A', '6es72141ag400xb0', '2026-01-01')],
    [q('SO1#1', 'C1', 'A', '6es72141ag400xb0', '2026-02-01'), q('SO2#1', 'C1', 'A', '6es72141ag400xb0', '2026-05-01'), q('SO3#1', 'C1', 'A', '6es72141ag40', '2026-05-01')],
  );
  assert.equal(pairs.get('SO1#1').inquiryLine, 'XP1#1');
  assert.equal(pairs.get('SO2#1').inquiryLine, 'XP1#1');
  assert.equal(pairs.has('SO3#1'), false);
  assert.deepEqual(suggestions, [{ orderLine: 'SO3#1', inquiryLine: 'XP1#1' }]);
});

test('规格「按采购成本分摊单价」：USD 6,178.00 按 10,000 : 24,780', () => {
  const r = allocateOrderLines('6178', [{ quantity: 10, costCny: '10000' }, { quantity: 86, costCny: '24780' }]);
  assert.equal(r.basis, 'cost');
  assert.deepEqual(r.amounts, ['1776.31', '4401.69']);
  assert.deepEqual(r.unitPrices, ['177.63', '51.18']);
});

test('PI 单价合计等于订单总额时直接用；对不上时退回采购成本；都没有成本按数量', () => {
  assert.deepEqual(allocateOrderLines('300', [{ quantity: 2, unitPrice: '100' }, { quantity: 1, unitPrice: '100' }]), { amounts: ['200.00', '100.00'], unitPrices: ['100.00', '100.00'], basis: 'pi' });
  assert.equal(allocateOrderLines('301', [{ quantity: 2, unitPrice: '100', costCny: '1' }, { quantity: 1, unitPrice: '100', costCny: '1' }]).basis, 'cost');
  const byQty = allocateOrderLines('100', [{ quantity: 1 }, { quantity: 2 }]);
  assert.equal(byQty.basis, 'quantity');
  assert.deepEqual(byQty.amounts, ['33.33', '66.67']);
  assert.deepEqual(allocateOrderLines('0', [{ quantity: 1 }]).amounts, ['0.00']);
});
