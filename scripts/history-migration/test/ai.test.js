'use strict';
// 不调用 AI 的部分：规则处理、JSON 提取、提示词结构
const test = require('node:test');
const assert = require('node:assert/strict');
const ai = require('../clean/ai');

test('品牌提示：去掉中文括号说明，「其他」为空', () => {
  assert.equal(ai.brandHint('Siemens (西门子)'), 'Siemens');
  assert.equal(ai.brandHint('Delta Electronics (台达电子，台湾)'), 'Delta Electronics');
  assert.equal(ai.brandHint('维控 wecon'), '维控 wecon');
  assert.equal(ai.brandHint('其他'), '');
  assert.equal(ai.brandHint('Siemens (西门子), 其他'), 'Siemens');
});

test('单一型号用规则，多行或带中文的交给 AI', () => {
  assert.equal(ai.isSimpleModel('VFD11AMS21ANSAA'), true);
  assert.equal(ai.isSimpleModel('3RT2017-1BB41'), true);
  assert.equal(ai.isSimpleModel('见附件'), false);
  assert.equal(ai.isSimpleModel('LX3V-4AD 10\nLX5S-1412MR-A'), false);
  assert.equal(ai.isSimpleModel('DOP-115MX 6'), false);
  assert.equal(ai.isSimpleModel('ABCDEF'), false); // 不含数字不当作型号
  const r = ai.ruleLines({ id: 'XP1', brand: 'Siemens', text: '3RT2017-1BB41', info: '', quantity: '2' });
  assert.deepEqual(r.lines, [{ brand: 'Siemens', model: '3RT2017-1BB41', quantity: 2, condition: '', description: '', confidence: 'high' }]);
  assert.equal(ai.ruleLines({ id: 'XP2', text: '', info: '', quantity: '' }).seeAttachment, true);
  assert.equal(ai.ruleLines({ id: 'XP3', text: '见附件', info: '', quantity: '' }), null);
});

test('询价：纯数字进价且型号单一用规则；空进价无报价', () => {
  assert.equal(ai.ruleQuotes({ id: 'XJ1', model: '3RT2017-1BB41', price: '170' }).quotes[0].unitPrice, '170');
  assert.deepEqual(ai.ruleQuotes({ id: 'XJ2', model: 'A1', price: '' }).quotes, []);
  assert.equal(ai.ruleQuotes({ id: 'XJ3', model: '3RT2017-1BB41', price: '型号：3RT2017-1BB41 品牌：西门子 170' }), null);
});

test('从模型输出里取 JSON：代码块、前后说明文字、数组', () => {
  assert.deepEqual(ai.extractJson('```json\n{"a":1}\n```'), { a: 1 });
  assert.deepEqual(ai.extractJson('结果如下：{"records":[]} 以上'), { records: [] });
  assert.deepEqual(ai.extractJson('[{"m":"x"}]'), [{ m: 'x' }]);
  assert.equal(ai.extractJson('没有结果'), undefined);
});

test('提示词带上全部记录与输出格式', () => {
  const p = ai.linesPrompt([{ id: 'XP1', text: 'A1\nB2' }]);
  assert.match(p, /"id":"XP1"/);
  assert.match(p, /seeAttachment/);
  assert.match(ai.quotesPrompt([{ id: 'XJ1', price: '1' }]), /unitPrice/);
});
