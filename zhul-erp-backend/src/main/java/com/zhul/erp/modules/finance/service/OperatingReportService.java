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
import com.zhul.erp.modules.finance.support.BuShareCalc;
import com.zhul.erp.modules.finance.support.OrderCostCalc;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 经营统计（spec finance/operating-report）：按年、每月汇总——
 * 销售收入、成本、毛利按销售日期归月且不扣退款（退款按退款日期单列，避免重复扣减）；
 * 其他收入按日期归月；运营成本取不关联订单的有效费用单，按类别与分摊后的月份计（每月 = 人民币 ÷ 月数，HALF_UP 两位，尾差放最后一个月）。
 * 预计盈亏 = 毛利 − 退款 + 其他收入 − 运营成本；净利率 = 预计盈亏 ÷ 销售收入。只读。
 * <p>
 * 按事业部查看（spec「按事业部查看经营统计」）：订单与退款只算本事业部的；运营成本 = 直接费用（归属本事业部的费用单）
 * + 分摊的公共成本（「公共」费用按当月销售收入比例分到本事业部，见 {@link BuShareCalc}）；「公共」的其他收入同比例分摊。
 * 各事业部之和等于全公司。
 */
@Service
@RequiredArgsConstructor
public class OperatingReportService {

    private static final int MAX_AMORTIZE = 36;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String INCOME = "__income";

    private final SalesOrderMapper orderMapper;
    private final OrderCostService orderCostService;
    private final CustomerRefundMapper refundMapper;
    private final OtherIncomeMapper incomeMapper;
    private final ExpenseMapper expenseMapper;
    private final ExpenseService expenseService;
    private final BusinessUnitService businessUnitService;

    public OperatingReportVO year(Integer year, Integer businessUnitId) {
        Integer unit = businessUnitService.statsUnit(businessUnitId);
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
        // 各事业部每月销售收入（分摊比例用，全部事业部都要算）
        Map<Integer, BigDecimal[]> unitRevenue = new HashMap<>();

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
            int m = o.getSalesDate().getMonthValue();
            if (o.getBusinessUnitId() != null) {
                unitRevenue.computeIfAbsent(o.getBusinessUnitId(), k -> zeros())[m] =
                        unitRevenue.get(o.getBusinessUnitId())[m].add(c.getRevenue());
            }
            if (!mine(unit, o.getBusinessUnitId())) {
                continue;
            }
            Acc a = months[m];
            a.orders++;
            a.unsettled += Boolean.TRUE.equals(c.getSettled()) ? 0 : 1;
            a.revenue = a.revenue.add(c.getRevenue());
            a.cost = a.cost.add(c.getCost());
        }
        // 退款：跟随订单的事业部
        List<CustomerRefundDO> refunds = refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                .eq(CustomerRefundDO::getTenantId, PiStore.tenantId())
                .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                .isNull(CustomerRefundDO::getDeletedAt)
                .ge(CustomerRefundDO::getRefundDate, from)
                .le(CustomerRefundDO::getRefundDate, to));
        Map<Long, Integer> refundUnits = unit == null ? Map.of() : orderUnits(refunds.stream().map(CustomerRefundDO::getSoId).toList());
        for (CustomerRefundDO f : refunds) {
            if (unit == null || Objects.equals(refundUnits.get(f.getSoId()), unit)) {
                Acc a = months[f.getRefundDate().getMonthValue()];
                a.refund = a.refund.add(f.getAmountCny());
            }
        }
        // 公共的其他收入与运营成本按月、按类别先汇总，再按比例分到事业部
        Map<String, BigDecimal>[] shared = sharedHolder();
        for (OtherIncomeDO x : incomeMapper.selectList(new LambdaQueryWrapper<OtherIncomeDO>()
                .eq(OtherIncomeDO::getTenantId, PiStore.tenantId())
                .eq(OtherIncomeDO::getStatus, FinanceConstants.RECORD_VALID)
                .isNull(OtherIncomeDO::getDeletedAt)
                .ge(OtherIncomeDO::getIncomeDate, from)
                .le(OtherIncomeDO::getIncomeDate, to))) {
            int m = x.getIncomeDate().getMonthValue();
            if (unit != null && x.getBusinessUnitId() == null) {
                shared[m].merge(INCOME, x.getAmount(), BigDecimal::add);
            } else if (mine(unit, x.getBusinessUnitId())) {
                months[m].other = months[m].other.add(x.getAmount());
            }
        }
        // 运营成本：不关联订单的有效费用单，按分摊后的月份；往前查 35 个月覆盖跨年分摊
        List<ExpenseDO> expenses = expenseMapper.selectList(new LambdaQueryWrapper<ExpenseDO>()
                .eq(ExpenseDO::getTenantId, PiStore.tenantId())
                .eq(ExpenseDO::getStatus, FinanceConstants.FE_VALID)
                .isNull(ExpenseDO::getDeletedAt)
                .ge(ExpenseDO::getOccurDate, from.minusMonths(MAX_AMORTIZE - 1L))
                .le(ExpenseDO::getOccurDate, to)
                .apply("NOT EXISTS (SELECT 1 FROM fin_expense_allocation a WHERE a.expense_id = fin_expense.id AND a.deleted_at IS NULL)"));
        for (ExpenseDO e : expenses) {
            boolean isPublic = e.getBusinessUnitId() == null;
            if (!isPublic && !mine(unit, e.getBusinessUnitId())) {
                continue;
            }
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
                int m = ym.getMonthValue();
                if (isPublic && unit != null) {
                    shared[m].merge(category, v, BigDecimal::add);
                } else if (isPublic) {
                    months[m].opexShared.merge(category, v, BigDecimal::add);
                } else {
                    months[m].opexDirect.merge(category, v, BigDecimal::add);
                }
            }
        }
        // 分到当前事业部
        if (unit != null) {
            List<BusinessUnitService.Unit> units = businessUnitService.units();
            BigDecimal min = BigDecimal.valueOf(businessUnitService.minSharePercent()).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            for (int m = 1; m <= 12; m++) {
                Map<Integer, BigDecimal> shares = sharesOf(units, unitRevenue, y, m, min);
                months[m].sharePercent = shares.containsKey(unit) ? BuShareCalc.display(shares.get(unit)) : BigDecimal.ZERO.setScale(4);
                if (!shares.containsKey(unit)) {
                    continue;
                }
                for (Map.Entry<String, BigDecimal> e : shared[m].entrySet()) {
                    BigDecimal mineAmount = BuShareCalc.split(e.getValue(), shares).get(unit);
                    if (INCOME.equals(e.getKey())) {
                        months[m].other = months[m].other.add(mineAmount);
                    } else {
                        months[m].opexShared.merge(e.getKey(), mineAmount, BigDecimal::add);
                    }
                }
            }
        }

        OperatingReportVO vo = new OperatingReportVO();
        vo.setYear(y);
        vo.setBusinessUnitId(unit);
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

    /**
     * 当月参与分摊的事业部及比例：开始核算月份不晚于该月的事业部；一个都没有时由开始核算最早的事业部承担。
     */
    private static Map<Integer, BigDecimal> sharesOf(List<BusinessUnitService.Unit> units, Map<Integer, BigDecimal[]> revenue,
                                                     int year, int month, BigDecimal min) {
        YearMonth ym = YearMonth.of(year, month);
        List<BusinessUnitService.Unit> active = units.stream()
                .filter(u -> u.startMonth() == null || !u.startMonth().isAfter(ym)).toList();
        if (active.isEmpty() && !units.isEmpty()) {
            YearMonth first = units.stream().map(BusinessUnitService.Unit::startMonth).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null);
            active = units.stream().filter(u -> Objects.equals(u.startMonth(), first)).toList();
        }
        Map<Integer, BigDecimal> rev = new LinkedHashMap<>();
        for (BusinessUnitService.Unit u : active) {
            BigDecimal[] r = revenue.get(u.id());
            rev.put(u.id(), r == null ? BigDecimal.ZERO : r[month]);
        }
        return BuShareCalc.shares(rev, min);
    }

    /** 订单 → 事业部 */
    private Map<Long, Integer> orderUnits(List<Long> soIds) {
        Set<Long> ids = soIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> out = new HashMap<>();
        orderMapper.selectBatchIds(ids).forEach(o -> out.put(o.getId(), o.getBusinessUnitId()));
        return out;
    }

    /** 全公司（unit 为空）都算；否则只算本事业部的 */
    private static boolean mine(Integer unit, Integer docUnit) {
        return unit == null || unit.equals(docUnit);
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] a = new BigDecimal[13];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BigDecimal>[] sharedHolder() {
        Map<String, BigDecimal>[] out = new Map[13];
        for (int m = 0; m <= 12; m++) {
            out[m] = new LinkedHashMap<>();
        }
        return out;
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
        Map<String, BigDecimal> direct = new LinkedHashMap<>();
        Map<String, BigDecimal> shared = new LinkedHashMap<>();
        BigDecimal opexTotal = BigDecimal.ZERO;
        BigDecimal directTotal = BigDecimal.ZERO;
        BigDecimal sharedTotal = BigDecimal.ZERO;
        for (OperatingReportVO.Category c : categories) {
            BigDecimal d = OrderCostCalc.money(a.opexDirect.getOrDefault(c.getCode(), BigDecimal.ZERO));
            BigDecimal s = OrderCostCalc.money(a.opexShared.getOrDefault(c.getCode(), BigDecimal.ZERO));
            direct.put(c.getCode(), d);
            shared.put(c.getCode(), s);
            opex.put(c.getCode(), d.add(s));
            directTotal = directTotal.add(d);
            sharedTotal = sharedTotal.add(s);
            opexTotal = opexTotal.add(d).add(s);
        }
        r.setOpex(opex);
        r.setOpexTotal(opexTotal);
        r.setOpexDirect(direct);
        r.setOpexDirectTotal(directTotal);
        r.setOpexShared(shared);
        r.setOpexSharedTotal(sharedTotal);
        r.setSharePercent(month == 0 ? null : a.sharePercent);
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
        private BigDecimal sharePercent;
        private final Map<String, BigDecimal> opexDirect = new LinkedHashMap<>();
        private final Map<String, BigDecimal> opexShared = new LinkedHashMap<>();

        private void add(Acc o) {
            orders += o.orders;
            unsettled += o.unsettled;
            revenue = revenue.add(o.revenue);
            cost = cost.add(o.cost);
            refund = refund.add(o.refund);
            other = other.add(o.other);
            o.opexDirect.forEach((k, v) -> opexDirect.merge(k, v, BigDecimal::add));
            o.opexShared.forEach((k, v) -> opexShared.merge(k, v, BigDecimal::add));
        }
    }
}
