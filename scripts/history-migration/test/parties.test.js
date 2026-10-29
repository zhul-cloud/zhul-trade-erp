'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { mergeParties, resolveOpportunity, phoneKey, nameKey } = require('../clean/parties');

let n = 0;
const cust = (code, o) => ({ __src: { table: 'customers', file: 'c.xlsx', row: ++n, code }, 客户编码: code, 联系人: '', 客户名称: '', 客户来源: 'TM', 国家: '', 客户等级: '', 地址: '', 手机号: '', 邮箱: '', Whatsapp: '', 创建时间: '2025-05-01 10:00:00', 创建人: '邱丽芳', 公司地址: '', ...o });
const opp = (code, o) => ({ __src: { table: 'opportunities', file: 'o.xlsx', row: ++n, code }, 商机编码: code, 商机日期: '2025-09-10 00:00:00', 业务员: '郭如', 联系人: '', 客户名称: '', 联系方式: '', '国家/地区': '', 阿里等级: 'L2', 来源: 'TM', 跟进阶段: 'P1-询盘首回', 客户类型: '', 邮箱地址: '', 创建人: '郭如', 创建时间: '2025-09-10 09:00:00', ...o });
const inq = (code, o) => ({ __src: { table: 'inquiries', code }, 询盘单号: code, 客户: '', 商机信息: '', ...o });
const disp = (rs, code) => rs.find((x) => x.src.code === code);

test('比较键与后端一致', () => {
  assert.equal(phoneKey('+49 30 1234'), '49301234');
  assert.equal(phoneKey('0049301234'), '49301234');
  assert.equal(phoneKey('123'), '');
  assert.equal(nameKey('ABC-Automation Co., Ltd.'), 'abcautomation');
  assert.equal(nameKey('Trading Co Ltd'), 'trading');
  assert.equal(nameKey('Co Ltd'), 'coltd');
});

test('规格「商机联系人与旧客户是同一人」：联系人与国家相同 → 一个客户', () => {
  const m = mergeParties({
    customers: [cust('C00729', { 联系人: 'Diego Navarro', 国家: '墨西哥' })],
    opportunities: [opp('SJ1', { 联系人: 'diego  navarro', '国家/地区': '墨西哥' })],
    inquiries: [],
  });
  assert.equal(m.groups.length, 1);
  assert.deepEqual(m.groups[0].customerCodes, ['C00729']);
  assert.deepEqual(m.groups[0].opportunityCodes, ['SJ1']);
  assert.equal(m.groups[0].customer.country, 'Mexico');
  assert.equal(m.groups[0].customer.name, 'Diego Navarro');
});

test('规格「不确定是否同一人」：同名不同国且无相同联系方式 → 不合并，列为疑似；人工结论沿用', () => {
  const data = {
    customers: [cust('C1', { 联系人: 'Karim Saleh', 国家: '巴基斯坦' })],
    opportunities: [opp('SJ2', { 联系人: 'Karim Saleh', '国家/地区': '阿联酋' })],
    inquiries: [],
  };
  const m = mergeParties(data);
  assert.equal(m.groups.length, 2);
  assert.deepEqual(m.suspects.map((s) => [s.a, s.b]), [['C1', 'SJ2']]);
  const notSame = mergeParties(data, { notSamePerson: [['SJ2', 'C1']] });
  assert.equal(notSame.groups.length, 2);
  assert.equal(notSame.suspects.length, 0);
  const same = mergeParties(data, { samePerson: [['C1', 'SJ2']] });
  assert.equal(same.groups.length, 1);
});

test('强证据合并：邮箱、电话相同；同名同国但邮箱矛盾的不自动合并', () => {
  const m = mergeParties({
    customers: [cust('C1', { 联系人: 'A', 邮箱: 'Buyer@X.com ' }), cust('C2', { 联系人: 'Bob', 国家: '印度', 邮箱: 'b1@x.com' })],
    opportunities: [
      opp('SJ1', { 联系人: 'Someone', 邮箱地址: 'buyer@x.com' }),
      opp('SJ2', { 联系人: 'Bob', '国家/地区': '印度', 邮箱地址: 'b2@y.com' }),
      opp('SJ3', { 联系人: 'Zed', 联系方式: '+91 98765 43210' }),
      opp('SJ4', { 联系人: 'Zed2', 联系方式: '0091 9876543210' }),
    ],
    inquiries: [],
  });
  const groupOf = (code) => m.groups.find((g) => g.customerCodes.includes(code) || g.opportunityCodes.includes(code)).id;
  assert.equal(groupOf('SJ1'), groupOf('C1'));
  assert.notEqual(groupOf('SJ2'), groupOf('C2'));
  assert.equal(groupOf('SJ3'), groupOf('SJ4'));
  assert.ok(m.suspects.some((s) => s.reason.includes('联系方式不同')));
});

test('商机入选、旧客户重复合并、被多个客户引用的商机不作为依据', () => {
  const m = mergeParties({
    customers: [cust('C1', { 联系人: 'Mark Ellison', 国家: '美国', 创建时间: '2025-06-01' }), cust('C2', { 联系人: 'Mark Ellison', 国家: '美国', 创建时间: '2026-01-01' }), cust('C3', { 联系人: 'X' })],
    opportunities: [
      opp('SJ1', { 跟进阶段: '' }), // 无阶段无询盘 → 跳过
      opp('SJ2', { 跟进阶段: '', 联系人: 'Northwind' }), // 被 C1、C3 的询盘同时引用 → 引用不可信
    ],
    inquiries: [inq('XP1', { 客户: 'C1', 商机信息: 'SJ2' }), inq('XP2', { 客户: 'C3', 商机信息: 'SJ2' })],
  });
  assert.equal(disp(m.opportunityResults, 'SJ1').disposition, 'skipped');
  assert.equal(disp(m.opportunityResults, 'SJ2').disposition, 'skipped');
  assert.equal(disp(m.customerResults, 'C2').disposition, 'merged');
  assert.equal(disp(m.customerResults, 'C2').mergedInto, 'C1');
  assert.deepEqual(m.unreliableOpps, [{ code: 'SJ2', customers: 2 }]);
  assert.ok(m.groups.every((g) => !(g.customerCodes.includes('C1') && g.customerCodes.includes('C3'))));
});

test('规格「同一联系人多条商机」：首次接触取最早，阶段取最靠后', () => {
  const o = resolveOpportunity([
    opp('SJ2', { 商机日期: '2026-02-03 00:00:00', 跟进阶段: 'P3-已报价', 业务员: '朱娜芬' }),
    opp('SJ1', { 商机日期: '2025-09-10 00:00:00', 跟进阶段: 'P1-询盘首回' }),
  ], false);
  assert.equal(o.firstContactDate, '2025-09-10');
  assert.equal(o.stage, 'S4');
  assert.equal(o.owner, '朱娜芬');
  assert.deepEqual(o.sourceCodes, ['SJ1', 'SJ2']);
  assert.equal(o.reachedValid, true);
});

test('阶段为空：有配对订单为赢单，否则为已报价；P9 输单带原因', () => {
  assert.equal(resolveOpportunity([opp('SJ1', { 跟进阶段: '' })], true).stage, 'WON');
  assert.equal(resolveOpportunity([opp('SJ1', { 跟进阶段: '' })], false).stage, 'S4');
  assert.equal(resolveOpportunity([opp('SJ1', { 跟进阶段: 'P1-询盘首回' })], true).stage, 'WON');
  const lost = resolveOpportunity([opp('SJ1', { 跟进阶段: 'P9-输单', 输单原因: '价格不匹配' })], false);
  assert.equal(lost.stage, 'LOST');
  assert.equal(lost.closeReason, 11);
  assert.equal(resolveOpportunity([], false), null);
});
