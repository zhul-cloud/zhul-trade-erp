'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const f = require('../clean/finance');
const { mulRound, allocate, cents, yuan } = require('../clean/common');

const src = (code) => ({ __src: { table: 't', file: 'f.xlsx', row: 2, code } });

test('金额运算：HALF_UP、尾差放在最大项、零值', () => {
  assert.equal(mulRound('417.10', '6.69'), '2790.40');
  assert.equal(mulRound('430.00', '6.69'), '2876.70');
  assert.equal(mulRound('0.01', '0.5'), '0.01'); // 0.005 进位
  assert.equal(mulRound('-12.00', '6.5'), '-78.00');
  assert.equal(mulRound('99999999999999.99', '7.123456'), '712345599999999.93');
  // 规格「按采购成本分摊单价」：USD 6,178.00 按 10,000 : 24,780 分摊
  assert.deepEqual(allocate(617800, [10000, 24780]).map(yuan), ['1776.31', '4401.69']);
  assert.equal(allocate(100, [0, 0, 0]).reduce((a, b) => a + b, 0), 100);
  assert.equal(cents('1.005'), 101);
});

test('结汇：规格场景「信保到账与结汇」', () => {
  const [res] = f.cleanFxSettlements([{
    ...src('JH1'), 销售单号: 'SO1', 结汇状态: '已结汇', 信保订单: '320100000001000011', '总金额(USD)': '$430.00',
    '交易服务费(USD)': '12.9', '实收金额(USD)': '$417.10', 结汇汇率: '6.69', 创建时间: '2026-10-23 14:20:11', 创建人: '邱丽芳',
  }]);
  assert.equal(res.disposition, 'migrate');
  assert.equal(res.data.amount, '430.00');
  assert.equal(res.data.platformFee, '12.90');
  assert.equal(res.data.netAmount, '417.10');
  assert.equal(res.data.platformOrderNo, '320100000001000011');
  assert.deepEqual(res.data.settlement, { date: '2026-10-23', amount: '417.10', exchangeRate: '6.69', amountCny: '2790.40' });
});

test('结汇：未结汇不生成结汇单；TT 不写平台订单号；三位小数手续费以实收为准；异常列为人工处理', () => {
  const rows = [
    { ...src('JH2'), 销售单号: 'SO2', 结汇状态: '未结汇', 信保订单: 'TT', '总金额(USD)': '$100.00', '交易服务费(USD)': '0', '实收金额(USD)': '$100.00', 结汇汇率: '7.1', 创建时间: '2026-01-01' },
    { ...src('JH3'), 销售单号: 'SO3', 结汇状态: '已结汇', 信保订单: '', '总金额(USD)': '$348.50', '交易服务费(USD)': '10.455', '实收金额(USD)': '$338.05', 结汇汇率: '6.7', 创建时间: '2026-01-01' },
    { ...src('JH4'), 销售单号: '', 结汇状态: '已结汇', '总金额(USD)': '$1.00', '实收金额(USD)': '$1.00', 结汇汇率: '7' },
    { ...src('JH5'), 销售单号: 'SO5', 结汇状态: '已结汇', '总金额(USD)': '$0.00', '实收金额(USD)': '$0.00', 结汇汇率: '7' },
    { ...src('JH6'), 销售单号: 'SO6', 结汇状态: '已结汇', '总金额(USD)': '$5.00', '实收金额(USD)': '$5.00', 结汇汇率: '' },
  ];
  const [a, b, c, d, e] = f.cleanFxSettlements(rows);
  assert.equal(a.data.settlement, null);
  assert.equal(a.data.platformOrderNo, '');
  assert.equal(b.data.platformFee, '10.45');
  assert.deepEqual(b.issues, []);
  assert.equal(c.disposition, 'manual');
  assert.equal(d.disposition, 'manual');
  assert.equal(e.disposition, 'manual');
});

test('成本核算快照：同一订单多张合并、退款核算单列为人工、毛利率重算', () => {
  const sheet = (code, so, rev, cost, gp, extra = {}) => ({
    ...src(code), 销售单号: so, 核算日期: '2026年10月', 销售日期: '2026年10月', 结算状态: '已结算', 客户类型: '老客户',
    '销售收入(RMB)': rev, '销售成本(RMB)': cost, 销售毛利: gp, '运费(RMB)': '', 附加费: '', '采购总金额(RMB)': '',
    '交易服务费(RMB)': '', '退款总金额(RMB)': '', '结汇总金额(RMB)': '', 退款单号: '', ...extra,
  });
  const res = f.cleanCostSheets([
    sheet('HS1', 'SO1', '¥2,876.70', '¥1,807.75', '¥1,068.95'),
    sheet('HS2', 'SO2', '¥100.00', '¥50.00', '¥50.00'),
    sheet('HS3', 'SO2', '¥0.00', '¥10.00', '-¥10.00', { 结算状态: '未结算' }),
    sheet('HS4', '', '¥0.00', '¥3,194.29', '-¥3,194.29', { 退款单号: 'TK1' }),
  ]);
  assert.equal(res[0].data.marginRate, '37.16');
  assert.equal(res[1].data.revenue, '100.00');
  assert.equal(res[1].data.cost, '60.00');
  assert.equal(res[1].data.settled, false);
  assert.deepEqual(res[1].data.sourceCodes, ['HS2', 'HS3']);
  assert.equal(res[2].disposition, 'merged');
  assert.equal(res[2].mergedInto, 'HS2');
  assert.equal(res[3].disposition, 'manual');
  assert.match(res[3].reason, /退款核算单/);
});

test('经营报表：规格场景「一个月的运营成本」，0 不生成，月末日期', () => {
  const [res] = f.cleanOperatingReport([{
    ...src('2025年7月'), 核算日期: '2025年7月', 房租: '1100', 水电: '260', 人员工资: '6000', 平台费: '10000', 营销费: '15015',
    CRM系统: '0', 其他支出费: '¥0.00', 其他收入金额: '¥0.00', 运营成本合计: '¥32,375.00',
  }]);
  assert.equal(res.data.expenses.length, 5);
  assert.equal(res.data.expenses.reduce((s, e) => s + cents(e.amount), 0), 3237500);
  assert.ok(res.data.expenses.every((e) => e.date === '2025-07-31'));
  assert.equal(res.data.otherIncome, null);
  assert.deepEqual(res.issues, []);
  assert.equal(f.monthEnd('2024-02'), '2024-02-29');
});
