package com.zhul.erp.modules.crm.followup.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.repository.OpportunityMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.masterdata.customerprofile.support.CustomerGradeSettings;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户经营（board 5）跟进对象的同步（第三期 design D1–D5）：一个成交客户一行，按订单、其他板块和客户档案推导阶段、
 * 采购规律、沉睡和系统提醒。由 {@link FollowupSync#resync} 在商机成交、订单履约、售后评价之后调用（要读它们的结果），
 * 事务边界跟随调用方，本类不吞异常。
 */
@Component
@RequiredArgsConstructor
public class AccountFollowupSync {

    private static final int CUSTOMER_ENABLED = 1;
    private static final Map<Integer, String> BOARD_SHORT = Map.of(FollowupConstants.BOARD_SALES, "商机成交",
            FollowupConstants.BOARD_FULFIL, "订单履约", FollowupConstants.BOARD_AFTER_SALES, "售后评价");

    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final QuotationMapper quotationMapper;
    private final OpportunityMapper opportunityMapper;
    private final FollowupSettings settings;
    private final CustomerGradeSettings gradeSettings;

    /**
     * @param bootstrap 核对任务补生成时为 true：沉睡日期按「最近一单 + 沉睡天数」回推
     * @return 新建、重新打开或移出的对象数
     */
    public int resync(Long customerId, LocalDate today, boolean bootstrap) {
        CustomerDO c = customerMapper.selectById(customerId);
        FollowupItemDO row = itemMapper.selectOne(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_ACCOUNT)
                .eq(FollowupItemDO::getTargetType, FollowupConstants.TARGET_CUSTOMER)
                .eq(FollowupItemDO::getTargetId, customerId)
                .isNull(FollowupItemDO::getDeletedAt)
                .last("LIMIT 1"));
        boolean eligible = c != null && c.getDeletedAt() == null && Objects.equals(c.getStatus(), CUSTOMER_ENABLED)
                && c.getBizGrade() != null && c.getBizGrade() >= 1 && c.getBizGrade() <= 4;
        if (!eligible) {
            if (row != null && row.getClosedAt() == null) {
                row.setClosedAt(LocalDateTime.now());
                row.setCloseReason(FollowupConstants.CLOSE_NOT_CUSTOMER);
                row.setNextActionDate(null);
                itemMapper.updateById(row);
                return 1;
            }
            return 0;
        }
        int changes = 0;
        boolean fresh = row == null;
        if (fresh) {
            row = newItem(c, today);
            changes++;
        } else if (row.getClosedAt() != null) {
            // 重新有等级：重新打开，保留原来的下一步和记录
            row.setClosedAt(null);
            row.setCloseReason(FollowupConstants.CLOSE_NONE);
            changes++;
        }
        Integer tenantId = TenantContext.getTenantId();
        FollowupSettings.AccountParams ap = settings.accountParams(tenantId);

        // 订单：近 3 年未取消、不含售后补发（迁移订单计入）→ 采购规律、近 12 个月成交
        List<SalesOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .select(SalesOrderDO::getId, SalesOrderDO::getSalesDate, SalesOrderDO::getTotalAmountCny)
                .eq(SalesOrderDO::getCustomerId, customerId)
                .ne(SalesOrderDO::getStatus, SalesConstants.SO_CANCELLED)
                .and(w -> w.isNull(SalesOrderDO::getAfterSalesId).or().eq(SalesOrderDO::getAfterSalesId, 0))
                .and(w -> w.isNull(SalesOrderDO::getOriginalSoId).or().eq(SalesOrderDO::getOriginalSoId, 0))
                .ge(SalesOrderDO::getSalesDate, today.minusYears(PurchasePattern.YEARS))
                .isNull(SalesOrderDO::getDeletedAt));
        PurchasePattern.Result pattern = PurchasePattern.of(patternOrders(orders), today);
        row.setPatternCycleDays(pattern.cycleDays());
        row.setPatternNextDate(pattern.nextDate());
        row.setPatternText(cut(pattern.text(), 100));
        row.setPatternBrands(cut(pattern.brands(), 200));
        LocalDate yearAgo = today.minusMonths(12);
        List<SalesOrderDO> recent = orders.stream().filter(o -> o.getSalesDate() != null && !o.getSalesDate().isBefore(yearAgo))
                .toList();
        row.setRecentOrderCount(recent.size());
        row.setAmountCny(recent.stream().map(o -> o.getTotalAmountCny() == null ? BigDecimal.ZERO : o.getTotalAmountCny())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // 激活记录的天数按激活前的最近一单算（新订单激活时 lastOrderDate 已换成新订单）
        LocalDate prevLastOrder = row.getLastOrderDate();
        row.setLastOrderDate(lastOrderDate(customerId));
        row.setDocNo(c.getCustomerCode() == null ? "" : c.getCustomerCode());
        row.setCurrencyCode("CNY");
        row.setAmount(BigDecimal.ZERO);
        row.setReceivedAmount(BigDecimal.ZERO);

        // 其他板块进行中的对象（不含培育中）：商机成交 → G5；任一板块 → 暂停提醒
        List<FollowupItemDO> others = itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .select(FollowupItemDO::getId, FollowupItemDO::getBoard)
                .eq(FollowupItemDO::getCustomerId, customerId)
                .in(FollowupItemDO::getBoard, FollowupConstants.BOARD_SALES, FollowupConstants.BOARD_FULFIL,
                        FollowupConstants.BOARD_AFTER_SALES)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getNurtureUntil)
                .isNull(FollowupItemDO::getDeletedAt));
        boolean salesOpen = others.stream().anyMatch(o -> o.getBoard() == FollowupConstants.BOARD_SALES);
        // 「有新需求」「聊了新品类」之后建了询盘：两个标记清零（询盘本身让客户进入 G5）
        if ((nz(row.getAccountNeed()) == 1 || nz(row.getAccountCross()) == 1) && row.getLastFollowupAt() != null
                && inquiryMapper.selectCount(new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getCustomerId, customerId)
                .ge(CustomerInquiryDO::getCreateTime, row.getLastFollowupAt())
                .isNull(CustomerInquiryDO::getDeletedAt)) > 0) {
            row.setAccountNeed(0);
            row.setAccountCross(0);
        }
        String stage = salesOpen || nz(row.getAccountNeed()) == 1 ? FollowupConstants.G5
                : nz(c.getStrategic()) == 1 ? FollowupConstants.G7
                : nz(row.getAccountCross()) == 1 ? FollowupConstants.G6 : FollowupConstants.G4;
        if (!stage.equals(row.getStageCode()) || row.getStageSince() == null) {
            row.setStageSince(LocalDateTime.now());
        }
        row.setStageCode(stage);
        row.setStageGroup(FollowupConstants.ACCOUNT_GROUPS.get(stage));

        boolean activated = dormant(row, customerId, ap, today, fresh && bootstrap);
        cadence(row, c, others, ap, today, tenantId);
        if (fresh) {
            itemMapper.insert(row);
        } else {
            itemMapper.updateById(row);
        }
        if (activated) {
            writeActivated(row, prevLastOrder, today);
        }
        return changes;
    }

    // ---------------------------------------------------------------- 沉睡（design D5）

    /** @return 是否刚刚取消沉睡（激活） */
    private boolean dormant(FollowupItemDO row, Long customerId, FollowupSettings.AccountParams ap, LocalDate today,
                            boolean backfill) {
        LocalDate last = row.getLastOrderDate();
        boolean longAgo = last != null && ChronoUnit.DAYS.between(last, today) > ap.dormantDays();
        boolean asleep = longAgo && quiet(customerId, today.minusDays(ap.dormantQuietDays()).atStartOfDay());
        if (asleep) {
            if (row.getDormantSince() == null) {
                // 上线补生成时按「最近一单 + 沉睡天数」回推首次满足的日期，激活节点才有意义
                LocalDate since = backfill ? last.plusDays(ap.dormantDays() + 1L) : today;
                row.setDormantSince(since.isAfter(today) ? today : since);
            }
            return false;
        }
        if (row.getDormantSince() != null) {
            row.setDormantSince(null);
            return true;
        }
        return false;
    }

    /** 近 n 天没有新询盘、报价、商机 */
    private boolean quiet(Long customerId, LocalDateTime from) {
        return inquiryMapper.selectCount(new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getCustomerId, customerId)
                .ge(CustomerInquiryDO::getCreateTime, from)
                .isNull(CustomerInquiryDO::getDeletedAt)) == 0
                && quotationMapper.selectCount(new LambdaQueryWrapper<QuotationDO>()
                .eq(QuotationDO::getCustomerId, customerId)
                .ge(QuotationDO::getCreateTime, from)
                .isNull(QuotationDO::getDeletedAt)) == 0
                && opportunityMapper.selectCount(new LambdaQueryWrapper<OpportunityDO>()
                .eq(OpportunityDO::getCustomerId, customerId)
                .ge(OpportunityDO::getCreateTime, from)
                .isNull(OpportunityDO::getDeletedAt)) == 0;
    }

    /** 激活的系统记录：团队视图按标题前缀计「激活」 */
    private void writeActivated(FollowupItemDO row, LocalDate prevLastOrder, LocalDate today) {
        long days = prevLastOrder == null ? 0 : ChronoUnit.DAYS.between(prevLastOrder, today);
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_SYSTEM);
        r.setCustomerId(row.getCustomerId());
        r.setFollowupItemId(row.getId());
        r.setTargetType(FollowupConstants.TARGET_CUSTOMER);
        r.setTargetId(row.getCustomerId());
        r.setOpportunityId(0L);
        r.setTitle(FollowupConstants.ACTIVATED_PREFIX + "：距上一单 " + days + " 天后有了新动向");
        r.setSituation(0);
        r.setBlockerCode("");
        r.setNextAction("");
        r.setChannel(0);
        r.setAiSummary("");
        r.setAiTaskId(0L);
        r.setStageBefore(row.getStageCode());
        r.setStageAfter(row.getStageCode());
        r.setOperatorId(0L);
        recordMapper.insert(r);
    }

    // ---------------------------------------------------------------- 提醒（design D4）

    private void cadence(FollowupItemDO row, CustomerDO c, List<FollowupItemDO> others, FollowupSettings.AccountParams ap,
                         LocalDate today, Integer tenantId) {
        // 提醒从生成当天起算（上线补生成的存量客户不会一下子全部逾期）
        LocalDate lastContact = later(lastContact(row.getCustomerId(), row.getLastOrderDate()), row.getCadenceFrom());
        LocalDate reviewFrom = c.getLastReviewDate() != null ? c.getLastReviewDate()
                : c.getStrategicAt() == null ? null : c.getStrategicAt().toLocalDate();
        AccountCadence.Reminder r = AccountCadence.of(new AccountCadence.Input(c.getBizGrade(),
                gradeSettings.params(tenantId).followDays(), lastContact, row.getPatternNextDate(), ap.aheadDays(),
                row.getLastOrderDate(), row.getDormantSince(), ap.dormantIntervalDays(), nz(c.getStrategic()) == 1, reviewFrom,
                ap.reviewDays()), today);
        boolean need = nz(row.getAccountNeed()) == 1;
        row.setAccountReason(cut(need ? "客户有新需求，建询盘" : r == null ? "" : r.reason(), 100));
        if (!others.isEmpty()) {
            // 暂停：其他板块有进行中的对象；不改来源，暂停结束后恢复
            int board = others.stream().mapToInt(FollowupItemDO::getBoard).min().orElse(FollowupConstants.BOARD_SALES);
            String where = "在" + BOARD_SHORT.get(board) + "跟进中";
            row.setNextAction(FollowupConstants.G5.equals(row.getStageCode()) && board == FollowupConstants.BOARD_SALES
                    ? "复购机会 · " + where : where);
            row.setNextActionDate(null);
            return;
        }
        if (row.getNurtureUntil() != null) {
            if (row.getNurtureUntil().isAfter(today)) {
                row.setNextActionDate(null);
                return;
            }
            row.setNurtureUntil(null);
            row.setNurtureReason("");
            row.setNurtureNote("");
            row.setNextActionSource(FollowupConstants.NEXT_SYSTEM_ONCE);
            row.setNextAction("培育到期，重新联系客户");
            row.setNextActionDate(today);
            return;
        }
        if (!Objects.equals(row.getNextActionSource(), FollowupConstants.NEXT_SYSTEM) && row.getNextActionDate() == null) {
            // 暂停结束：暂停时清掉的日期按系统节奏重新给
            row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
        }
        if (!Objects.equals(row.getNextActionSource(), FollowupConstants.NEXT_SYSTEM)) {
            return;
        }
        if (need) {
            row.setNextAction("建询盘");
            row.setNextActionDate(today);
            return;
        }
        row.setNextAction(r == null ? "" : cut(r.action(), FollowupConstants.NEXT_ACTION_MAX));
        row.setNextActionDate(r == null ? null : r.date());
    }

    /** 上次联系日：该客户任意板块最近一条跟进记录（含系统记录）；没有时取最近一次下单日 */
    private LocalDate lastContact(Long customerId, LocalDate lastOrderDate) {
        CustomerFollowupDO last = recordMapper.selectOne(new LambdaQueryWrapper<CustomerFollowupDO>()
                .select(CustomerFollowupDO::getCreateTime)
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .orderByDesc(CustomerFollowupDO::getId)
                .last("LIMIT 1"));
        return last != null && last.getCreateTime() != null ? last.getCreateTime().toLocalDate() : lastOrderDate;
    }

    // ---------------------------------------------------------------- 工具

    private LocalDate lastOrderDate(Long customerId) {
        SalesOrderDO o = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>()
                .select(SalesOrderDO::getSalesDate)
                .eq(SalesOrderDO::getCustomerId, customerId)
                .ne(SalesOrderDO::getStatus, SalesConstants.SO_CANCELLED)
                .and(w -> w.isNull(SalesOrderDO::getAfterSalesId).or().eq(SalesOrderDO::getAfterSalesId, 0))
                .and(w -> w.isNull(SalesOrderDO::getOriginalSoId).or().eq(SalesOrderDO::getOriginalSoId, 0))
                .isNotNull(SalesOrderDO::getSalesDate)
                .isNull(SalesOrderDO::getDeletedAt)
                .orderByDesc(SalesOrderDO::getSalesDate)
                .last("LIMIT 1"));
        return o == null ? null : o.getSalesDate();
    }

    private List<PurchasePattern.Order> patternOrders(List<SalesOrderDO> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PurchasePattern.Line>> lines = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .select(SalesOrderItemDO::getSoId, SalesOrderItemDO::getBrand, SalesOrderItemDO::getCategory,
                                SalesOrderItemDO::getAmountCny)
                        .in(SalesOrderItemDO::getSoId, orders.stream().map(SalesOrderDO::getId).toList())
                        .isNull(SalesOrderItemDO::getDeletedAt)).stream()
                .collect(Collectors.groupingBy(SalesOrderItemDO::getSoId, Collectors.mapping(
                        i -> new PurchasePattern.Line(i.getBrand(), i.getCategory(), i.getAmountCny()), Collectors.toList())));
        List<PurchasePattern.Order> out = new ArrayList<>(orders.size());
        for (SalesOrderDO o : orders) {
            out.add(new PurchasePattern.Order(o.getSalesDate(), lines.getOrDefault(o.getId(), List.of())));
        }
        return out;
    }

    private static FollowupItemDO newItem(CustomerDO c, LocalDate today) {
        FollowupItemDO row = new FollowupItemDO();
        row.setTenantId(TenantContext.getTenantId());
        row.setBoard(FollowupConstants.BOARD_ACCOUNT);
        row.setTargetType(FollowupConstants.TARGET_CUSTOMER);
        row.setTargetId(c.getId());
        row.setCustomerId(c.getId());
        row.setOpportunityId(0L);
        row.setOwnerId(c.getOwnerId() == null ? 0L : c.getOwnerId());
        row.setSourceKey("");
        row.setCadenceFrom(today);
        row.setStageCode(FollowupConstants.G4);
        row.setBlockerCode("");
        row.setNextAction("");
        row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
        row.setSnoozeCount(0);
        row.setLastFollowupId(0L);
        row.setNoReplyStreak(0);
        row.setFeedbackSeen(0);
        row.setNurtureReason("");
        row.setNurtureNote("");
        row.setAccountNeed(0);
        row.setAccountCross(0);
        row.setCrossCategory("");
        row.setCloseReason(FollowupConstants.CLOSE_NONE);
        return row;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
