package com.zhul.erp.modules.masterdata.customerprofile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CustomerProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.GradeAdjustRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.RfmDetailVO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerStatsMapper;
import com.zhul.erp.modules.masterdata.customerprofile.support.CustomerGradeSettings;
import com.zhul.erp.modules.masterdata.customerprofile.support.RfmCalculator;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 经营等级（spec master-data/customer-profile「客户经营等级」）：按未取消的销售订单算 RFM，存回客户表；
 * 客户详情与 RFM 明细实时计算，每日任务全量重算。非美元订单按人民币金额 ÷ 当前美元汇率折算，HALF_UP 两位。
 */
@Service
@RequiredArgsConstructor
public class CustomerGradeService {

    private final CustomerMapper customerMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final CustomerStatsMapper statsMapper;
    private final ExchangeRateService exchangeRateService;
    private final CustomerGradeSettings settings;
    private final UserBasicMapper userBasicMapper;
    private final LogService logService;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    /** RFM 明细：实时计算，与存的值不同时存回 */
    @Transactional(rollbackFor = Exception.class)
    public RfmDetailVO detail(CustomerDO c) {
        RfmCalculator.Params p = settings.params(c.getTenantId());
        BigDecimal usdRate = usdRate();
        List<SalesOrderDO> orders = salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getCustomerId, c.getId())
                .ne(SalesOrderDO::getStatus, SalesConstants.SO_CANCELLED)
                .isNull(SalesOrderDO::getDeletedAt)
                .orderByDesc(SalesOrderDO::getSalesDate)
                .orderByDesc(SalesOrderDO::getId));
        RfmDetailVO vo = new RfmDetailVO();
        vo.setUsdRate(usdRate);
        vo.setRecencyDays(Arrays.stream(p.recencyDays()).boxed().toList());
        vo.setFrequency(Arrays.stream(p.frequency()).boxed().toList());
        vo.setMonetaryUsd(List.of(p.monetaryUsd()));
        vo.setWeights(List.of(p.weights()));
        vo.setCutoffs(List.of(p.cutoffs()));
        LocalDate today = LocalDate.now();
        LocalDate since = yearStart(today);
        BigDecimal usdYear = BigDecimal.ZERO;
        BigDecimal usdTotal = BigDecimal.ZERO;
        int ordersInYear = 0;
        for (SalesOrderDO o : orders) {
            BigDecimal usd = toUsd(o.getCurrencyCode(), o.getTotalAmount(), o.getTotalAmountCny(), usdRate);
            usdTotal = usdTotal.add(usd);
            if (o.getSalesDate() != null && !o.getSalesDate().isBefore(since)) {
                ordersInYear++;
                usdYear = usdYear.add(usd);
                RfmDetailVO.OrderRow row = new RfmDetailVO.OrderRow();
                row.setId(o.getId());
                row.setSoNo(o.getSoNo());
                row.setSalesDate(o.getSalesDate());
                row.setCurrencyCode(o.getCurrencyCode());
                row.setAmount(o.getTotalAmount());
                row.setAmountCny(o.getTotalAmountCny());
                row.setUsd(usd);
                vo.getOrders().add(row);
            }
        }
        vo.setOrdersInYear(ordersInYear);
        vo.setUsdInYear(usdYear);
        vo.setUsdTotal(usdTotal);
        if (!orders.isEmpty()) {
            LocalDate last = orders.get(0).getSalesDate();
            vo.setLastOrderDate(last);
            vo.setFirstOrderDate(orders.stream().map(SalesOrderDO::getSalesDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null));
            vo.setDaysSinceLast(last == null ? null : ChronoUnit.DAYS.between(last, today));
        }
        RfmCalculator.Score s = orders.isEmpty() ? null
                : RfmCalculator.score(new RfmCalculator.Input(vo.getDaysSinceLast() == null ? 9999 : vo.getDaysSinceLast(), ordersInYear, usdYear), p);
        store(c, s, vo.getDaysSinceLast(), ordersInYear, usdYear);
        if (s != null) {
            vo.setR(s.r());
            vo.setF(s.f());
            vo.setM(s.m());
            vo.setScore(s.total());
        }
        vo.setCalc(c.getBizGradeCalc());
        vo.setAdjust(c.getBizGradeAdjust());
        vo.setFinalGrade(c.getBizGrade());
        vo.setAdjustReason(c.getBizGradeAdjustReason());
        vo.setAdjustByName(userName(c.getBizGradeAdjustBy()));
        vo.setAdjustAt(c.getBizGradeAdjustAt());
        vo.setCalcAt(c.getRfmCalcAt());
        vo.setFollowDays(RfmCalculator.followDays(c.getBizGrade(), p));
        return vo;
    }

    /** 客户详情里的经营等级卡片（取存的值，详情页先调用 detail 刷新） */
    public CustomerProfileVO.Grade gradeOf(CustomerDO c) {
        RfmCalculator.Params p = settings.params(c.getTenantId());
        CustomerProfileVO.Grade g = new CustomerProfileVO.Grade();
        g.setCalc(c.getBizGradeCalc());
        g.setFinalGrade(c.getBizGrade());
        g.setScore(c.getBizGradeScore());
        Map<String, Object> rfm = rfm(c.getRfmJson());
        g.setR(intOf(rfm.get("r")));
        g.setF(intOf(rfm.get("f")));
        g.setM(intOf(rfm.get("m")));
        g.setAdjust(c.getBizGradeAdjust());
        g.setAdjustReason(c.getBizGradeAdjustReason());
        g.setAdjustByName(userName(c.getBizGradeAdjustBy()));
        g.setAdjustAt(c.getBizGradeAdjustAt());
        g.setCalcAt(c.getRfmCalcAt());
        g.setFollowDays(RfmCalculator.followDays(c.getBizGrade(), p));
        return g;
    }

    /** 上调或下调一级（必须写原因），0 为取消调整；写操作日志 */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(CustomerDO c, GradeAdjustRequest req) {
        int calc = nz(c.getBizGradeCalc());
        if (calc == RfmCalculator.UNDEALT) {
            throw new BizException("这个客户还没有成交，没有经营等级，不能调整");
        }
        int adjust = req.getAdjust();
        String reason = req.getReason() == null ? "" : req.getReason().trim();
        if (adjust != 0 && reason.isEmpty()) {
            throw new BizException("请填写调整原因");
        }
        int before = nz(c.getBizGrade());
        int after = RfmCalculator.finalGrade(calc, adjust);
        Long me = currentUser.resolve();
        customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                .eq(CustomerDO::getId, c.getId())
                .set(CustomerDO::getBizGradeAdjust, adjust)
                .set(CustomerDO::getBizGradeAdjustReason, adjust == 0 ? "" : reason)
                .set(CustomerDO::getBizGradeAdjustBy, adjust == 0 ? 0L : (me == null ? 0L : me))
                .set(CustomerDO::getBizGradeAdjustAt, adjust == 0 ? null : LocalDateTime.now())
                .set(CustomerDO::getBizGrade, after));
        logService.recordOperateLog("客户管理", adjust == 0 ? "取消调整经营等级" : "调整经营等级",
                Map.of("customerCode", c.getCustomerCode(), "经营等级", RfmCalculator.GRADE_NAMES.get(before)),
                Map.of("customerCode", c.getCustomerCode(), "经营等级", RfmCalculator.GRADE_NAMES.get(after), "原因", reason));
    }

    /** 每日任务：按租户一次汇总订单，重算全部客户的经营等级。返回变化的客户数 */
    @Transactional(rollbackFor = Exception.class)
    public int recomputeTenant(int tenantId, List<CustomerDO> customers) {
        RfmCalculator.Params p = settings.params(tenantId);
        BigDecimal usdRate = usdRate();
        LocalDate today = LocalDate.now();
        Map<Long, LocalDate> lastDates = new HashMap<>();
        for (Map<String, Object> r : statsMapper.orderSpan(tenantId)) {
            lastDates.put(((Number) r.get("customer_id")).longValue(), toDate(r.get("last_date")));
        }
        Map<Long, int[]> counts = new HashMap<>();
        Map<Long, BigDecimal> usd = new HashMap<>();
        for (Map<String, Object> r : statsMapper.yearByCurrency(tenantId, yearStart(today))) {
            Long id = ((Number) r.get("customer_id")).longValue();
            counts.computeIfAbsent(id, k -> new int[1])[0] += ((Number) r.get("cnt")).intValue();
            usd.merge(id, toUsd((String) r.get("currency_code"), (BigDecimal) r.get("amt"), (BigDecimal) r.get("cny"), usdRate), BigDecimal::add);
        }
        int changed = 0;
        for (CustomerDO c : customers) {
            LocalDate last = lastDates.get(c.getId());
            int orders = counts.getOrDefault(c.getId(), new int[1])[0];
            BigDecimal amount = usd.getOrDefault(c.getId(), BigDecimal.ZERO);
            Long days = last == null ? null : ChronoUnit.DAYS.between(last, today);
            RfmCalculator.Score s = lastDates.containsKey(c.getId())
                    ? RfmCalculator.score(new RfmCalculator.Input(days == null ? 9999 : days, orders, amount), p) : null;
            if (store(c, s, days, orders, amount)) {
                changed++;
            }
        }
        return changed;
    }

    /** 存回计算值与最终等级（只改经营等级相关列，不动更新时间）；返回等级或分数是否有变化 */
    private boolean store(CustomerDO c, RfmCalculator.Score s, Long days, int orders, BigDecimal usd) {
        int calc = s == null ? RfmCalculator.UNDEALT : s.grade();
        int fin = RfmCalculator.finalGrade(calc, nz(c.getBizGradeAdjust()));
        BigDecimal score = s == null ? null : s.total();
        String json = null;
        if (s != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("r", s.r());
            m.put("f", s.f());
            m.put("m", s.m());
            m.put("days", days);
            m.put("orders", orders);
            m.put("usd", usd);
            json = write(m);
        }
        boolean changed = calc != nz(c.getBizGradeCalc()) || fin != nz(c.getBizGrade())
                || (score == null ? c.getBizGradeScore() != null : c.getBizGradeScore() == null || score.compareTo(c.getBizGradeScore()) != 0);
        LocalDateTime now = LocalDateTime.now();
        customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                .eq(CustomerDO::getId, c.getId())
                .set(CustomerDO::getBizGradeCalc, calc)
                .set(CustomerDO::getBizGrade, fin)
                .set(CustomerDO::getBizGradeScore, score)
                .set(CustomerDO::getRfmJson, json)
                .set(CustomerDO::getRfmCalcAt, now));
        c.setBizGradeCalc(calc);
        c.setBizGrade(fin);
        c.setBizGradeScore(score);
        c.setRfmJson(json);
        c.setRfmCalcAt(now);
        return changed;
    }

    /** 近 12 个月：订单日期不早于一年前的今天 */
    static LocalDate yearStart(LocalDate today) {
        return today.minusYears(1);
    }

    static BigDecimal toUsd(String currency, BigDecimal amount, BigDecimal cny, BigDecimal usdRate) {
        if ("USD".equalsIgnoreCase(currency)) {
            return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        }
        if (cny == null || usdRate == null || usdRate.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return cny.divide(usdRate, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal usdRate() {
        ExchangeRateService.Snapshot s = exchangeRateService.find("USD");
        return s == null ? null : s.rate();
    }

    private String userName(Long id) {
        if (id == null || id == 0) {
            return null;
        }
        UserBasicDO u = userBasicMapper.selectById(id);
        return u == null ? null : u.getName();
    }

    private Map<String, Object> rfm(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Integer intOf(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static LocalDate toDate(Object v) {
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return v instanceof LocalDate d ? d : null;
    }
}
