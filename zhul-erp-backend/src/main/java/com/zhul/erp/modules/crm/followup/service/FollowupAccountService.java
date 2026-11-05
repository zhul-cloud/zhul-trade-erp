package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.AccountCardVO;
import com.zhul.erp.modules.crm.followup.dto.AnnualPlanRequest;
import com.zhul.erp.modules.crm.followup.entity.CustomerAnnualPlanDO;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerAnnualPlanMapper;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupAccountMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.support.FollowupSettings;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.support.CustomerGradeSettings;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.masterdata.customerprofile.support.RfmCalculator;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 客户经营作战卡、战略客户与年度计划（第三期 design D6、D7）。可见范围跟随客户。
 * 事务边界：设为 / 取消战略客户、保存年度计划各自一个事务，含随后的重新推导。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupAccountService {

    /** 交叉销售：至少几个其他客户买过才推荐，最多推荐几个 */
    static final int CROSS_MIN_CUSTOMERS = 2;
    static final int CROSS_LIMIT = 2;
    private static final int LAST_ORDER_LINES = 3;
    private static final String[] GRADE_NAMES = {"", "S", "A", "B", "C"};

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final CustomerAnnualPlanMapper planMapper;
    private final FollowupAccountMapper accountMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final CustomerGradeSettings gradeSettings;
    private final FollowupSettings settings;
    private final FollowupSync sync;
    private final CurrentUserResolver currentUser;

    public AccountCardVO card(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        LocalDate today = LocalDate.now();
        AccountCardVO vo = new AccountCardVO();
        vo.setCustomerId(c.getId());
        int grade = c.getBizGrade() == null ? 0 : c.getBizGrade();
        vo.setBizGrade(grade >= 1 && grade <= 4 ? GRADE_NAMES[grade] : "");
        vo.setBizGradeScore(c.getBizGradeScore());
        RfmCalculator.Params rp = gradeSettings.params(TenantContext.getTenantId());
        vo.setFollowDays(RfmCalculator.followDays(grade, rp));
        vo.setProfileDone(Integer.bitCount(c.getProfileMask() == null ? 0 : c.getProfileMask()));
        vo.setProfileTotal(ProfileField.MODULES);
        FollowupItemDO item = itemMapper.selectOne(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_ACCOUNT)
                .eq(FollowupItemDO::getTargetType, FollowupConstants.TARGET_CUSTOMER)
                .eq(FollowupItemDO::getTargetId, customerId)
                .isNull(FollowupItemDO::getDeletedAt)
                .last("LIMIT 1"));
        if (item != null) {
            vo.setPatternText(item.getPatternText());
            vo.setPatternCycleDays(item.getPatternCycleDays());
            vo.setPatternNextDate(item.getPatternNextDate());
            vo.setPatternBrands(item.getPatternBrands());
            vo.setRecentAmount(item.getAmountCny());
            vo.setRecentOrderCount(item.getRecentOrderCount());
            vo.setLastOrderDate(item.getLastOrderDate());
            vo.setReason(item.getAccountReason());
            vo.setDormantSince(item.getDormantSince());
            vo.setDormantDays(item.getDormantSince() == null || item.getLastOrderDate() == null ? null
                    : (int) ChronoUnit.DAYS.between(item.getLastOrderDate(), today));
            vo.setCrossCategory(item.getCrossCategory());
        }
        vo.setLastOrderItems(lastOrderItems(customerId));
        crossSell(customerId).forEach(vo.getCrossSell()::add);
        boolean strategic = c.getStrategic() != null && c.getStrategic() == 1;
        vo.setStrategic(strategic);
        vo.setStrategicAt(c.getStrategicAt());
        vo.setSuggestStrategic(!strategic && grade == 1);
        vo.setLastReviewDate(c.getLastReviewDate());
        if (strategic) {
            LocalDate from = c.getLastReviewDate() != null ? c.getLastReviewDate()
                    : c.getStrategicAt() == null ? null : c.getStrategicAt().toLocalDate();
            vo.setNextReviewDate(from == null ? null
                    : from.plusDays(settings.accountParams(TenantContext.getTenantId()).reviewDays()));
            vo.setPlan(plan(customerId, today.getYear()));
        }
        return vo;
    }

    /** 交叉销售建议（design D6）：按需计算，不落库 */
    List<AccountCardVO.CrossSell> crossSell(Long customerId) {
        List<String> mine = accountMapper.categoriesOf(customerId);
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Long> peers = accountMapper.peers(customerId, mine);
        if (peers.isEmpty()) {
            return List.of();
        }
        return accountMapper.crossSell(peers, mine, CROSS_MIN_CUSTOMERS, CROSS_LIMIT).stream().map(m -> {
            AccountCardVO.CrossSell s = new AccountCardVO.CrossSell();
            s.setCategory(String.valueOf(m.get("category")));
            s.setCustomers(m.get("customers") instanceof Number n ? n.intValue() : 0);
            return s;
        }).toList();
    }

    /** 最近一单金额最大的几行：「Siemens 6ES7515-2AM02 ×10」 */
    private String lastOrderItems(Long customerId) {
        SalesOrderDO last = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>()
                .select(SalesOrderDO::getId)
                .eq(SalesOrderDO::getCustomerId, customerId)
                .ne(SalesOrderDO::getStatus, SalesConstants.SO_CANCELLED)
                .isNull(SalesOrderDO::getDeletedAt)
                .orderByDesc(SalesOrderDO::getSalesDate)
                .orderByDesc(SalesOrderDO::getId)
                .last("LIMIT 1"));
        if (last == null) {
            return "";
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .select(SalesOrderItemDO::getBrand, SalesOrderItemDO::getModel, SalesOrderItemDO::getQuantity,
                                SalesOrderItemDO::getAmountCny)
                        .eq(SalesOrderItemDO::getSoId, last.getId())
                        .isNull(SalesOrderItemDO::getDeletedAt)).stream()
                .sorted(Comparator.comparing((SalesOrderItemDO i) -> i.getAmountCny() == null ? BigDecimal.ZERO : i.getAmountCny())
                        .reversed())
                .limit(LAST_ORDER_LINES)
                .map(i -> (nvl(i.getBrand()) + " " + nvl(i.getModel())).trim() + " ×" + (i.getQuantity() == null ? 0 : i.getQuantity()))
                .collect(Collectors.joining("；"));
    }

    private AccountCardVO.Plan plan(Long customerId, int year) {
        CustomerAnnualPlanDO p = planMapper.selectOne(new LambdaQueryWrapper<CustomerAnnualPlanDO>()
                .eq(CustomerAnnualPlanDO::getCustomerId, customerId)
                .eq(CustomerAnnualPlanDO::getPlanYear, year)
                .isNull(CustomerAnnualPlanDO::getDeletedAt)
                .last("LIMIT 1"));
        AccountCardVO.Plan vo = new AccountCardVO.Plan();
        vo.setPlanYear(year);
        BigDecimal dealt = accountMapper.dealt(customerId, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1));
        vo.setDealtAmount((dealt == null ? BigDecimal.ZERO : dealt).setScale(2, RoundingMode.HALF_UP));
        if (p != null) {
            vo.setTargetAmount(p.getTargetAmount());
            vo.setFocus(p.getFocus());
            vo.setNote(p.getNote());
        }
        vo.setPercent(percent(vo.getDealtAmount(), vo.getTargetAmount()));
        return vo;
    }

    /** 完成比例：已成交 ÷ 目标 × 100，HALF_UP 保留整数，只用于显示；没有目标或目标为 0 时为 null */
    static Integer percent(BigDecimal dealt, BigDecimal target) {
        if (target == null || target.signum() <= 0) {
            return null;
        }
        return (dealt == null ? BigDecimal.ZERO : dealt).multiply(BigDecimal.valueOf(100))
                .divide(target, 0, RoundingMode.HALF_UP).intValue();
    }

    /** 设为 / 取消战略客户：只改 customer 的战略客户字段，写一条系统记录，再重新推导（进入或离开 G7） */
    @Transactional(rollbackFor = Exception.class)
    public void setStrategic(Long customerId, boolean strategic) {
        CustomerDO c = customerService.requireInScope(customerId);
        boolean now = c.getStrategic() != null && c.getStrategic() == 1;
        if (now == strategic) {
            return;
        }
        if (strategic && (c.getBizGrade() == null || c.getBizGrade() < 1 || c.getBizGrade() > 4)) {
            throw new BizException("只有成交客户才能设为战略客户");
        }
        customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>()
                .eq(CustomerDO::getId, customerId)
                .set(CustomerDO::getStrategic, strategic ? 1 : 0)
                .set(CustomerDO::getStrategicAt, strategic ? LocalDateTime.now() : null)
                .set(CustomerDO::getUpdateTime, LocalDateTime.now()));
        Long me = currentUser.resolve();
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_SYSTEM);
        r.setCustomerId(customerId);
        r.setFollowupItemId(0L);
        r.setTargetType(FollowupConstants.TARGET_CUSTOMER);
        r.setTargetId(customerId);
        r.setOpportunityId(0L);
        r.setTitle(strategic ? "设为战略客户" : "取消战略客户");
        r.setSituation(0);
        r.setBlockerCode("");
        r.setNextAction("");
        r.setChannel(0);
        r.setAiSummary("");
        r.setAiTaskId(0L);
        r.setStageBefore("");
        r.setStageAfter("");
        r.setOperatorId(me == null ? 0L : me);
        recordMapper.insert(r);
        sync.resync(customerId, LocalDate.now(), false);
        log.info("{}，customerId={}，operatorId={}", r.getTitle(), customerId, me);
    }

    /** 保存当年的年度计划（只有战略客户）；目标金额按 HALF_UP 保留 2 位，存本位币 */
    @Transactional(rollbackFor = Exception.class)
    public AccountCardVO.Plan savePlan(Long customerId, AnnualPlanRequest req) {
        CustomerDO c = customerService.requireInScope(customerId);
        if (c.getStrategic() == null || c.getStrategic() != 1) {
            throw new BizException("只有战略客户才能填写年度计划");
        }
        int year = LocalDate.now().getYear();
        CustomerAnnualPlanDO p = planMapper.selectOne(new LambdaQueryWrapper<CustomerAnnualPlanDO>()
                .eq(CustomerAnnualPlanDO::getCustomerId, customerId)
                .eq(CustomerAnnualPlanDO::getPlanYear, year)
                .isNull(CustomerAnnualPlanDO::getDeletedAt)
                .last("LIMIT 1"));
        boolean fresh = p == null;
        if (fresh) {
            p = new CustomerAnnualPlanDO();
            p.setTenantId(TenantContext.getTenantId());
            p.setCustomerId(customerId);
            p.setPlanYear(year);
            p.setCurrencyCode("CNY");
        }
        p.setTargetAmount(req.getTargetAmount().setScale(2, RoundingMode.HALF_UP));
        p.setFocus(req.getFocus() == null ? "" : req.getFocus().trim());
        p.setNote(req.getNote() == null ? "" : req.getNote().trim());
        if (fresh) {
            planMapper.insert(p);
        } else {
            planMapper.updateById(p);
        }
        log.info("保存年度计划，customerId={}，year={}", customerId, year);
        return plan(customerId, year);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
