'use strict';
// 财务记录清洗（规格「采购、发货、收款与退款迁移」「运营成本与其他收入迁移」，order-cost「迁移订单按历史核算快照」）。
const r = require('../rules');
const { DISPOSITION, result, cents, yuan, mulRound } = require('./common');

/**
 * 外汇结算 → 到账（毛额、平台手续费、实收）+ 结汇（已结汇的，按实收结汇）。
 * 信保订单号是纯数字的写入平台订单号；「TT」等非数字表示线下转账，不写。
 */
function cleanFxSettlements(rows) {
  return rows.map((row) => {
    const soCode = r.clean(row['销售单号']);
    const total = r.parseAmount(row['总金额(USD)']);
    const fee = r.parseAmount(row['交易服务费(USD)']) ?? '0';
    const net = r.parseAmount(row['实收金额(USD)']);
    const rate = r.clean(row['结汇汇率']);
    const issues = [];
    if (!soCode) return result(row, DISPOSITION.MANUAL, null, { reason: '没有销售单号' });
    if (total === undefined || net === undefined || fee === undefined) return result(row, DISPOSITION.MANUAL, null, { reason: '金额不是数字' });
    if (!total || cents(total) === 0) return result(row, DISPOSITION.MANUAL, null, { reason: '总金额为 0 或为空（可能是人民币订单误建了结汇记录）' });
    if (!/^\d+(\.\d+)?$/.test(rate) || Number(rate) <= 0) return result(row, DISPOSITION.MANUAL, null, { reason: `结汇汇率无效：${rate}` });
    // 手续费有三位小数时（如 10.455）以实收为准：手续费 = 总金额 − 实收
    let feeCents = cents(fee);
    if (cents(total) - feeCents !== cents(net)) {
      const derived = cents(total) - cents(net);
      if (Math.abs(derived - Number(fee) * 100) < 1) feeCents = derived;
      else issues.push(`总金额 ${total} − 手续费 ${fee} ≠ 实收 ${net}`);
    }

    const settled = r.clean(row['结汇状态']) === '已结汇';
    const date = r.parseDate(row['创建时间']);
    const platformNo = r.clean(row['信保订单']);
    return result(row, DISPOSITION.MIGRATE, {
      soCode,
      currency: 'USD',
      receiptDate: date,
      amount: yuan(cents(total)),
      platformFee: yuan(feeCents),
      netAmount: yuan(cents(net)),
      exchangeRate: rate,
      amountCny: mulRound(total, rate),
      netAmountCny: mulRound(net, rate),
      platformOrderNo: /^\d{6,}$/.test(platformNo) ? platformNo : '',
      settled,
      settlement: settled ? { date, amount: yuan(cents(net)), exchangeRate: rate, amountCny: mulRound(net, rate) } : null,
      operator: r.clean(row['创建人']),
      createTime: r.parseDateTime(row['创建时间']),
      note: r.clean(row['备注']),
    }, { issues });
  });
}

const SNAPSHOT_FIELDS = {
  revenue: '销售收入(RMB)', cost: '销售成本(RMB)', grossProfit: '销售毛利', freight: '运费(RMB)', surcharge: '附加费',
  purchase: '采购总金额(RMB)', serviceFee: '交易服务费(RMB)', refund: '退款总金额(RMB)', fxTotal: '结汇总金额(RMB)',
};

/**
 * 成本核算 → 订单历史核算快照（按销售单号）。
 * 没有销售单号的核算单（退款核算、未关联订单的成本）列为人工处理，在核对报告里单独说明。
 * 同一订单有多张核算单的，各项金额相加合并为一份快照。
 */
function cleanCostSheets(rows) {
  const results = [];
  const bySo = new Map();
  for (const row of rows) {
    const soCode = r.clean(row['销售单号']);
    if (!soCode) {
      results.push(result(row, DISPOSITION.MANUAL, null, {
        reason: r.clean(row['退款单号']) ? `退款核算单（没有关联订单，退款单 ${r.clean(row['退款单号'])}）` : '没有关联销售订单',
      }));
      continue;
    }
    const amounts = {};
    const bad = [];
    const issues = [];
    for (const [k, col] of Object.entries(SNAPSHOT_FIELDS)) {
      let v = r.parseAmount(row[col]);
      // 引用了多条采购单时显示为「¥7,360.00,¥7,360.00」：值都相同视为重复显示取一个，不同则相加
      if (v === undefined && /,¥/.test(r.clean(row[col]))) {
        const parts = r.clean(row[col]).split(/,(?=¥|-¥)/).map(r.parseAmount);
        if (parts.every((x) => x !== undefined && x !== null)) {
          v = new Set(parts).size === 1 ? parts[0] : yuan(parts.reduce((a, x) => a + cents(x), 0));
          issues.push(`${col} 为多个值 ${r.clean(row[col])}，取 ${v}`);
        }
      }
      if (v === undefined) bad.push(col);
      amounts[k] = v ?? '0';
    }
    if (bad.length) { results.push(result(row, DISPOSITION.MANUAL, null, { reason: `金额不是数字：${bad.join('、')}` })); continue; }
    const snap = {
      soCode,
      accountingMonth: r.parseMonth(row['核算日期']),
      salesMonth: r.parseMonth(row['销售日期']),
      settled: r.clean(row['结算状态']) === '已结算',
      refundNo: r.clean(row['退款单号']),
      customerType: r.mapCustomerType(row['客户类型']),
      ...amounts,
    };
    const prev = bySo.get(soCode);
    if (prev) {
      for (const k of Object.keys(SNAPSHOT_FIELDS)) prev.data[k] = yuan(cents(prev.data[k]) + cents(snap[k]));
      prev.data.settled = prev.data.settled && snap.settled;
      prev.data.sourceCodes.push(row.__src.code);
      results.push(result(row, DISPOSITION.MERGED, null, { mergedInto: prev.src.code, reason: `与 ${prev.src.code} 同属订单 ${soCode}，金额相加` }));
      continue;
    }
    const res = result(row, DISPOSITION.MIGRATE, { ...snap, sourceCodes: [row.__src.code] }, { issues });
    bySo.set(soCode, res);
    results.push(res);
  }
  // 毛利率由合并后的收入、退款与毛利重新计算：毛利 ÷（收入 − 退款），保留两位百分比
  for (const res of bySo.values()) {
    const d = res.data;
    const base = cents(d.revenue) - cents(d.refund);
    d.marginRate = base > 0 ? (Math.round((cents(d.grossProfit) * 10000) / base) / 100).toFixed(2) : null;
  }
  return results;
}

/** 经营报表列 → 费用类别（字典 expense_category） */
const EXPENSE_COLUMNS = [
  ['房租', 'RENT'], ['水电', 'UTILITY'], ['人员工资', 'SALARY'], ['平台费', 'PLATFORM'],
  ['营销费', 'MARKETING'], ['CRM系统', 'CRM'], ['其他支出费', 'OTHER'],
];

/** 某月最后一天 'YYYY-MM-DD' */
function monthEnd(ym) {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(Date.UTC(y, m, 0));
  return `${ym}-${String(d.getUTCDate()).padStart(2, '0')}`;
}

/**
 * 公司经营报表 → 每月每个类别一张费用单（不关联订单、分摊 1 个月、发生日期为月末），金额为 0 的不生成；
 * 其他收入不为 0 的月份生成一张其他收入。每行报表产出 { expenses, otherIncome }，供核对使用的报表数字原样带上。
 */
function cleanOperatingReport(rows) {
  return rows.map((row) => {
    const ym = r.parseMonth(row['核算日期']);
    if (!ym) return result(row, DISPOSITION.MANUAL, null, { reason: `核算日期无法识别：${row['核算日期']}` });
    const date = monthEnd(ym);
    const expenses = [];
    const bad = [];
    for (const [col, category] of EXPENSE_COLUMNS) {
      const v = r.parseAmount(row[col]);
      if (v === undefined) { bad.push(col); continue; }
      if (v && cents(v) !== 0) expenses.push({ category, name: `${ym} ${col}`, date, amount: yuan(cents(v)) });
    }
    if (bad.length) return result(row, DISPOSITION.MANUAL, null, { reason: `金额不是数字：${bad.join('、')}` });
    const oi = r.parseAmount(row['其他收入金额']);
    const total = expenses.reduce((s, e) => s + cents(e.amount), 0);
    const reportTotal = cents(r.parseAmount(row['运营成本合计']) ?? '0');
    const issues = total === reportTotal ? [] : [`各类费用合计 ${yuan(total)} ≠ 报表运营成本合计 ${yuan(reportTotal)}`];
    return result(row, DISPOSITION.MIGRATE, {
      month: ym,
      expenses,
      otherIncome: oi && cents(oi) !== 0 ? { date, amount: yuan(cents(oi)), category: 'OTHER' } : null,
    }, { issues });
  });
}

module.exports = { cleanFxSettlements, cleanCostSheets, cleanOperatingReport, monthEnd, EXPENSE_COLUMNS };
