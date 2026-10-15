package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.OperatingReportVO;
import com.zhul.erp.modules.finance.dto.OrderCostVO;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.OtherIncomeDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.OtherIncomeMapper;
import com.zhul.erp.modules.finance.support.OrderCostCalc;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营统计（spec finance/operating-report）：按年、每月汇总——
 * 销售收入、成本、毛利按销售日期归月且不扣退款（退款按退款日期单列，避免重复扣减）；
 * 其他收入按日期归月；运营成本取不关联订单的有效费用单，按类别与分摊后的月份计（每月 = 人民币 ÷ 月数，HALF_UP 两位，尾差放最后一个月）。
 * 预计盈亏 = 毛利 − 退款 + 其他收入 − 运营成本；净利率 = 预计盈亏 ÷ 销售收入。只读。
 */
@Service
@RequiredArgsConstructor
public class OperatingReportService {

    private static final int MAX_AMORTIZE = 36;

    private final SalesOrderMapper orderMapper;
    private final OrderCostService orderCostService;
    private final CustomerRefundMapper refundMapper;
    private final OtherIncomeMapper incomeMapper;
    private final ExpenseMapper expenseMapper;
    private final ExpenseService expenseService;

    public OperatingReportVO year(Integer year) {
        LocalDate today = LocalDate.now();
        int y = year == null ? today.getYear() : year;
        int lastMonth = y == today.getYear() ? today.getMonthValue() : 12;
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);

        Map<String, String> names = expenseService.categoryNames();
        List<OperatingReportVO.Category> categories = new ArrayList<>();
        names.forEach((code, name) -> {
            if (!ExpenseService.CATEGORY_ORDER.equals(code)) {
                OperatingReportVO.Category c = new OperatingReportVO.Category();
                c.setCode(code);
                c.setName(name);
                categories.add(c);
            }
        });
        Acc[] months = new Acc[13];
        for (int m = 1; m <= 12; m++) {
            months[m] = new Acc();
        }

        // 订单：销售日期归月，收入与毛利不扣退款
        List<SalesOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .isNull(SalesOrderDO::getDeletedAt)
                .ge(SalesOrderDO::getSalesDate, from)
                .le(SalesOrderDO::getSalesDate, to));
        Map<Long, OrderCostVO> costs = orderCostService.of(orders);
        for (SalesOrderDO o : orders) {
            OrderCostVO c = costs.get(o.getId());
            if (c == null) {
                continue;
            }
            Acc a = months[o.getSalesDate().getMonthValue()];
            a.orders++;
            a.unsettled += Boolean.TRUE.equals(c.getSettled()) ? 0 : 1;
            a.revenue = a.revenue.add(c.getRevenue());
            a.cost = a.cost.add(c.getCost());
        }
        // 退款与其他收入
        refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                        .eq(CustomerRefundDO::getTenantId, PiStore.tenantId())
                        .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                        .isNull(CustomerRefundDO::getDeletedAt)
                        .ge(CustomerRefundDO::getRefundDate, from)
                        .le(CustomerRefundDO::getRefundDate, to))
                .forEach(f -> months[f.getRefundDate().getMonthValue()].refund = months[f.getRefundDate().getMonthValue()].refund.add(f.getAmountCny()));
        incomeMapper.selectList(new LambdaQueryWrapper<OtherIncomeDO>()
                        .eq(OtherIncomeDO::getTenantId, PiStore.tenantId())
                        .eq(OtherIncomeDO::getStatus, FinanceConstants.RECORD_VALID)
                        .isNull(OtherIncomeDO::getDeletedAt)
                        .ge(OtherIncomeDO::getIncomeDate, from)
                        .le(OtherIncomeDO::getIncomeDate, to))
                .forEach(x -> months[x.getIncomeDate().getMonthValue()].other = months[x.getIncomeDate().getMonthValue()].other.add(x.getAmount()));
        // 运营成本：不关联订单的有效费用单，按分摊后的月份；往前查 35 个月覆盖跨年分摊
        List<ExpenseDO> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ExpenseDO>()
                .eq(ExpenseDO::getTenantId, PiStore.tenantId())
                .eq(ExpenseDO::getStatus, FinanceConstants.FE_VALID)
                .isNull(ExpenseDO::getDeletedAt)
                .ge(ExpenseDO::getOccurDate, from.minusMonths(MAX_AMORTIZE - 1L))
                .le(ExpenseDO::getOccurDate, to)
                .apply("NOT EXISTS (SELECT 1 FROM fin_expense_allocation a WHERE a.expense_id = fin_expense.id AND a.deleted_at IS NULL)"));
        for (ExpenseDO e : expenses) {
            String category = ExpenseService.CATEGORY_ORDER.equals(e.getCategory()) || !names.containsKey(e.getCategory())
                    ? ExpenseService.CATEGORY_OTHER : e.getCategory();
            int n = e.getAmortizeMonths() == null || e.getAmortizeMonths() < 1 ? 1 : e.getAmortizeMonths();
            BigDecimal share = e.getAmountCny().divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            YearMonth start = YearMonth.from(e.getOccurDate());
            for (int i = 0; i < n; i++) {
                YearMonth ym = start.plusMonths(i);
                if (ym.getYear() != y) {
                    continue;
                }
                BigDecimal v = i == n - 1 ? e.getAmountCny().subtract(share.multiply(BigDecimal.valueOf(n - 1L))) : share;
                months[ym.getMonthValue()].opex.merge(category, v, BigDecimal::add);
            }
        }

        OperatingReportVO vo = new OperatingReportVO();
        vo.setYear(y);
        vo.setCategories(categories);
        List<OperatingReportVO.Row> rows = new ArrayList<>();
        Acc total = new Acc();
        for (int m = 1; m <= lastMonth; m++) {
            rows.add(row(m, m + " 月", months[m], categories));
            total.add(months[m]);
        }
        vo.setRows(rows);
        vo.setTotal(row(0, "全年", total, categories));
        return vo;
    }

    private static OperatingReportVO.Row row(int month, String label, Acc a, List<OperatingReportVO.Category> categories) {
        OperatingReportVO.Row r = new OperatingReportVO.Row();
        r.setMonth(month);
        r.setLabel(label);
        r.setOrderCount(a.orders);
        r.setUnsettledCount(a.unsettled);
        BigDecimal revenue = OrderCostCalc.money(a.revenue);
        BigDecimal profit = OrderCostCalc.money(a.revenue.subtract(a.cost));
        r.setRevenue(revenue);
        r.setCost(OrderCostCalc.money(a.cost));
        r.setProfit(profit);
        r.setProfitRate(OrderCostCalc.rate(profit, revenue));
        r.setRefund(OrderCostCalc.money(a.refund));
        r.setOtherIncome(OrderCostCalc.money(a.other));
        Map<String, BigDecimal> opex = new LinkedHashMap<>();
        BigDecimal opexTotal = BigDecimal.ZERO;
        for (OperatingReportVO.Category c : categories) {
            BigDecimal v = OrderCostCalc.money(a.opex.getOrDefault(c.getCode(), BigDecimal.ZERO));
            opex.put(c.getCode(), v);
            opexTotal = opexTotal.add(v);
        }
        r.setOpex(opex);
        r.setOpexTotal(opexTotal);
        BigDecimal net = profit.subtract(r.getRefund()).add(r.getOtherIncome()).subtract(opexTotal);
        r.setNet(net);
        r.setNetRate(OrderCostCalc.rate(net, revenue));
        return r;
    }

    /** 一个月的累计 */
    private static final class Acc {
        private int orders;
        private int unsettled;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private BigDecimal refund = BigDecimal.ZERO;
        private BigDecimal other = BigDecimal.ZERO;
        private final Map<String, BigDecimal> opex = new LinkedHashMap<>();

        private void add(Acc o) {
            orders += o.orders;
            unsettled += o.unsettled;
            revenue = revenue.add(o.revenue);
            cost = cost.add(o.cost);
            refund = refund.add(o.refund);
            other = other.add(o.other);
            o.opex.forEach((k, v) -> opex.merge(k, v, BigDecimal::add));
        }
    }
}
