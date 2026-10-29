'use strict';
// 旧报表数字（规格「与旧报表核对」）：从飞书导出的 4 张月度报表取数，写成 out/bundle/reports.json，随迁移包上传，由后端核对。
//   node scripts/history-migration/reports.js
// 金额保留原文精度（字符串），数量为整数；客户行按客户编码对到清洗后的客户，没填客户编码的对到该业务员的占位客户。
const fs = require('node:fs');
const path = require('node:path');
const { loadSource } = require('./load-source');
const r = require('./rules');

const OUT = path.resolve(process.env.HM_OUT_DIR || path.join(__dirname, 'out'));

const amount = (v) => r.parseAmount(v) ?? null;
const int = (v) => Number(v || 0);

function buildReports(tables, bundleCustomers) {
  const groupOfOld = {};
  for (const c of bundleCustomers) for (const code of c.oldCodes || []) groupOfOld[code] = c.key;
  return {
    operating: tables.operatingReport.map((x) => ({
      month: r.parseMonth(x['核算日期']),
      revenue: amount(x['销售收入(RMB)']),
      cost: amount(x['销售成本(RMB)']),
      refund: amount(x['退款金额(RMB)']),
      otherIncome: amount(x['其他收入金额']),
      opex: amount(x['运营成本合计']),
    })),
    companySales: tables.companySalesReport.map((x) => ({
      month: r.parseMonth(x['销售日期']),
      opportunities: int(x['商机数']),
      inquiries: int(x['询盘数']),
      inquiriesNew: int(x['询盘数(新客)']),
      inquiriesOld: int(x['询盘数(老客)']),
      orders: int(x['销售订单数']),
      ordersNew: int(x['销售订单数(新客户)']),
      ordersOld: int(x['销售订单数(老客户)']),
      dealCustomers: int(x['成交客户数']),
      revenue: amount(x['销售收入(RMB)']),
      profit: amount(x['销售毛利']),
    })),
    staffSales: tables.staffSalesReport.map((x) => ({
      month: r.parseMonth(x['销售日期']),
      staff: r.clean(x['业务员']),
      inquiries: int(x['询盘数']),
      orders: int(x['销售订单数']),
      revenue: amount(x['销售收入(RMB)']),
      profit: amount(x['销售毛利']),
    })),
    customerSales: tables.customerSalesReport.map((x) => {
      const code = r.clean(x['客户编码']);
      return {
        month: r.parseMonth(x['销售日期']),
        code,
        contact: r.clean(x['客户联系人']),
        customerKey: code ? groupOfOld[code] ?? null : `PLACEHOLDER:${r.clean(x['业务员'])}`,
        orders: int(x['订单数']),
        revenue: amount(x['销售收入(RMB)']),
        profit: amount(x['销售毛利']),
      };
    }),
  };
}

/** 生成 out/bundle/reports.json，返回各报表行数 */
function writeReports(bundleDir = path.join(OUT, 'bundle')) {
  const { tables, missing } = loadSource();
  const need = ['operatingReport', 'companySalesReport', 'staffSalesReport', 'customerSalesReport'];
  const lack = need.filter((k) => missing.includes(k));
  if (lack.length) throw new Error(`缺少旧报表：${lack.join('、')}`);
  const customers = JSON.parse(fs.readFileSync(path.join(bundleDir, 'customers.json'), 'utf8'));
  const reports = buildReports(tables, customers);
  fs.writeFileSync(path.join(bundleDir, 'reports.json'), JSON.stringify(reports));
  return Object.fromEntries(Object.entries(reports).map(([k, v]) => [k, v.length]));
}

module.exports = { buildReports, writeReports };

if (require.main === module) {
  console.log('旧报表数字已写入 reports.json：', writeReports());
}
