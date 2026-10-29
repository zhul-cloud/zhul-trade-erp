'use strict';
// 运行：node --test scripts/history-migration/test/
const test = require('node:test');
const assert = require('node:assert/strict');
const r = require('../rules');

test('型号归一化与后端 MpnNormalizer 一致', () => {
  assert.equal(r.normalizeMpn('6ES7 214-1BD23-0XB0'), '6es72141bd230xb0');
  assert.equal(r.normalizeMpn('ＭＲ‑Ｊ２Ｓ‑１００Ａ'), 'mrj2s100a');
  assert.equal(r.normalizeMpn(null), '');
});

test('型号里的西里尔形近字母换成拉丁字母', () => {
  assert.equal(r.fixModelText('027Н120'), '027H120');
  assert.equal(r.fixModelText('FX81.1XАТВЕНХAMAX'), 'FX81.1XATBEHXAMAX');
  assert.equal(r.fixModelText(' КS 9407 '), 'KS 9407');
});

test('金额：货币符号、千分位、负数、零值、空值、非金额', () => {
  assert.equal(r.parseAmount('¥71,644.08'), '71644.08');
  assert.equal(r.parseAmount('$6,178.00'), '6178.00');
  assert.equal(r.parseAmount('-¥12,712.88'), '-12712.88');
  assert.equal(r.parseAmount('0'), '0');
  assert.equal(r.parseAmount('¥0.00'), '0.00');
  assert.equal(r.parseAmount('99999999999999.99'), '99999999999999.99');
  assert.equal(r.parseAmount(''), null);
  assert.equal(r.parseAmount('D5F-2B34C-Y 1.8w'), undefined);
});

test('日期与月份', () => {
  assert.equal(r.parseDate('2026-10-23 14:20:11'), '2026-10-23');
  assert.equal(r.parseDate('2025/07/04'), '2025-07-04');
  assert.equal(r.parseDate(''), null);
  assert.equal(r.parseDateTime('2026-10-23'), '2026-10-23 00:00:00');
  assert.equal(r.parseMonth('2026年9月'), '2026-09');
  assert.equal(r.parsePercent('12.03%'), '12.03');
});

test('国家：清单中文名、别名、错别字、多个国家取第一个、对不上的为 null', () => {
  assert.equal(r.mapCountry('墨西哥').nameEn, 'Mexico');
  assert.equal(r.mapCountry('印度尼i西亚').nameZh, '印度尼西亚');
  assert.equal(r.mapCountry('孟加拉').nameZh, '孟加拉国');
  assert.equal(r.mapCountry('HK').nameZh, '中国香港');
  assert.equal(r.mapCountry('美国、巴基斯坦').nameZh, '美国');
  assert.equal(r.mapCountry('乌克兰 切尔卡瑟市（Черкаси / Cherkasy）').nameZh, '乌克兰');
  assert.equal(r.mapCountry('中国人').nameZh, '中国');
  assert.equal(r.mapCountry('未知'), null);
  assert.equal(r.mapCountry('John Mwale'), null);
  assert.equal(r.mapCountry(''), null);
});

test('客户来源与阿里等级', () => {
  assert.equal(r.mapSourceChannel('TM'), 1);
  assert.equal(r.mapSourceChannel('RFQ'), 1);
  assert.equal(r.mapSourceChannel('询盘'), 1);
  assert.equal(r.mapSourceChannel('whatsapp'), 5);
  assert.equal(r.mapSourceChannel('独立站'), 3);
  assert.equal(r.mapSourceChannel('公海'), 7);
  assert.equal(r.mapSourceChannel('邮箱'), 8);
  assert.equal(r.mapSourceChannel(''), 0);
  assert.equal(r.mapAliLevel('LO'), 'L0');
  assert.equal(r.mapAliLevel('lo'), 'L0');
  assert.equal(r.mapAliLevel('l1'), 'L1');
  assert.equal(r.mapAliLevel('L1+'), 'L1+');
  assert.equal(r.mapAliLevel('L9'), '');
});

test('商机阶段对应（规格中的阶段表）', () => {
  assert.deepEqual(r.mapOpportunityStage('P0-未回复'), { stage: 'S1' });
  assert.deepEqual(r.mapOpportunityStage('P1-询盘首回'), { stage: 'S2' });
  assert.deepEqual(r.mapOpportunityStage('P2-建立链接'), { stage: 'S2' });
  assert.deepEqual(r.mapOpportunityStage('P3-已报价'), { stage: 'S4' });
  assert.deepEqual(r.mapOpportunityStage('P4-高意向'), { stage: 'S6' });
  assert.deepEqual(r.mapOpportunityStage('P8-重点关注'), { stage: 'S6' });
  assert.deepEqual(r.mapOpportunityStage('P5-PI'), { stage: 'S7' });
  assert.deepEqual(r.mapOpportunityStage('P6-正式成交'), { stage: 'WON' });
  assert.deepEqual(r.mapOpportunityStage('P9-输单', '价格不匹配'), { stage: 'LOST', closeReason: 11, closeNote: '价格不匹配' });
  assert.deepEqual(r.mapOpportunityStage('P7-沉睡/流失'), { stage: 'LOST', closeReason: 19, closeNote: '沉睡/流失' });
  assert.equal(r.mapOpportunityStage(''), null);
  assert.equal(r.mapOpportunityStage('TM'), null);
});

test('询盘等级、紧急、状态', () => {
  assert.equal(r.mapInquiryLevel('S级'), 1);
  assert.equal(r.mapInquiryLevel('C级'), 4);
  assert.equal(r.mapInquiryLevel(''), 3);
  assert.equal(r.mapUrgent('紧急'), 1);
  assert.equal(r.mapUrgent('中'), 0);
  assert.equal(r.mapInquiryStatus('询价完成', true), 8);
  assert.equal(r.mapInquiryStatus('询价完成', false), 7);
  assert.equal(r.mapInquiryStatus('无法报价', false), 9);
  assert.equal(r.mapInquiryStatus('未询价', false), 9);
});

test('货况：精确对应与近似归类（近似的要求写备注）', () => {
  assert.deepEqual(r.mapCondition('全新原装'), { code: 1, exact: true });
  assert.deepEqual(r.mapCondition('翻新99新可充新'), { code: 3, exact: false });
  assert.deepEqual(r.mapCondition('99%新'), { code: 2, exact: false });
  assert.deepEqual(r.mapCondition('9成新'), { code: 4, exact: false });
  assert.deepEqual(r.mapCondition('国产高仿'), { code: 6, exact: false });
  assert.deepEqual(r.mapCondition('全新后标'), { code: 1, exact: false });
  assert.deepEqual(r.mapCondition(''), { code: 0, exact: true });
});

test('货期：按上限归档', () => {
  assert.deepEqual(r.mapLeadTime('现货'), { code: 1, exact: true });
  assert.deepEqual(r.mapLeadTime('3-5天'), { code: 4, exact: true });
  assert.equal(r.mapLeadTime('3天').code, 3);
  assert.equal(r.mapLeadTime('1周').code, 5);
  assert.equal(r.mapLeadTime('2周').code, 6);
  assert.equal(r.mapLeadTime('4-6周').code, 8);
  assert.equal(r.mapLeadTime('6-8工作周').code, 8);
  assert.equal(r.mapLeadTime('8-10周').code, 9);
  assert.equal(r.mapLeadTime('3个月').code, 9);
  assert.equal(r.mapLeadTime('5个工作日').code, 4);
  assert.equal(r.mapLeadTime('8-10').code, 9);
  assert.deepEqual(r.mapLeadTime(''), { code: 0, exact: true });
});

test('付款方式、订单进度、现货期货、新老客户', () => {
  assert.equal(r.mapPaymentMethod('信保订单'), 'ALIBABA_TA');
  assert.equal(r.mapPaymentMethod('Paypal'), 'PAYPAL');
  assert.equal(r.mapPaymentMethod('银行转账'), 'TT');
  assert.equal(r.mapPaymentMethod('现金'), null);
  assert.equal(r.mapOrderProgress('货代已签收'), 'SHIPPED');
  assert.equal(r.mapOrderProgress('已退款'), 'COMPLETED');
  assert.equal(r.mapOrderProgress('已发货'), 'RECEIVED');
  assert.equal(r.mapStockType('期货'), 2);
  assert.equal(r.mapCustomerType('老客户'), 2);
  assert.equal(r.mapCustomerType('新客户'), 1);
});

test('客户角色', () => {
  assert.equal(r.mapCustomerRole('贸易商'), 4);
  assert.equal(r.mapCustomerRole('终端工厂'), 1);
  assert.equal(r.mapCustomerRole('维修与备件采购'), 6);
  assert.equal(r.mapCustomerRole(''), 0);
});
