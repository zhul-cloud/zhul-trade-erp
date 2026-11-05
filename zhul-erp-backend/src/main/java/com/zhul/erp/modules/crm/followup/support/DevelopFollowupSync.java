package com.zhul.erp.modules.crm.followup.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerContactMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 主动开发（board 1）开发记录的同步（第四期 design D4、D5、D8）：一个客户最多一条开发记录，只能由业务员新建或加入，
 * 这里不补生成。由 {@link FollowupSync#resync} 在商机成交之后、订单履约和客户经营之前调用，事务边界跟随调用方。
 */
@Component
@RequiredArgsConstructor
public class DevelopFollowupSync {

    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final CustomerMapper customerMapper;
    private final CustomerContactMapper contactMapper;
    private final CustomerBackgroundReportMapper reportMapper;
    private final SalesOrderMapper orderMapper;
    private final FollowupSettings settings;

    /** @return 移出的对象数 */
    public int resync(Long customerId, LocalDate today, Map<String, Map<String, String>> actions) {
        FollowupItemDO row = find(customerId);
        if (row == null || row.getClosedAt() != null) {
            return 0;
        }
        CustomerDO c = customerMapper.selectById(customerId);
        if (c == null || c.getDeletedAt() != null) {
            close(row, FollowupConstants.CLOSE_DOC_GONE, null);
            return 1;
        }
        if (enteredSales(c)) {
            close(row, FollowupConstants.CLOSE_ENTERED_SALES, "已进入商机成交，开发记录结束");
            return 1;
        }
        refresh(row, c, today, actions);
        itemMapper.updateById(row);
        return 0;
    }

    /** 当前客户的开发记录（含已结束的） */
    public FollowupItemDO find(Long customerId) {
        return itemMapper.selectOne(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_DEVELOP)
                .eq(FollowupItemDO::getTargetType, FollowupConstants.TARGET_CUSTOMER)
                .eq(FollowupItemDO::getTargetId, customerId)
                .isNull(FollowupItemDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    /**
     * 客户已进入商机成交：成交过、有进行中的商机成交对象（询盘、报价、PI、商机），或有销售订单。
     * 加入开发的条件（design D2）与自动结束共用这一判断。
     */
    public boolean enteredSales(CustomerDO c) {
        if (c.getBizGrade() != null && c.getBizGrade() > 0) {
            return true;
        }
        Long open = itemMapper.selectCount(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getCustomerId, c.getId())
                .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_SALES)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt));
        if (open != null && open > 0) {
            return true;
        }
        Long orders = orderMapper.selectCount(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getCustomerId, c.getId())
                .isNull(SalesOrderDO::getDeletedAt));
        return orders != null && orders > 0;
    }

    /**
     * 新建或重新打开开发记录并推导阶段（调用方已校验加入条件）；重新打开时保留触达次数等。
     *
     * @return 写入后的开发记录
     */
    public FollowupItemDO open(CustomerDO c, String reason, Integer source, LocalDate today,
                               Map<String, Map<String, String>> actions) {
        FollowupItemDO row = find(c.getId());
        boolean fresh = row == null;
        if (fresh) {
            row = newItem(c, today);
        } else {
            row.setClosedAt(null);
            row.setCloseReason(FollowupConstants.CLOSE_NONE);
            row.setStopReason("");
            row.setStopNote("");
            row.setNurtureUntil(null);
            row.setNurtureReason("");
            row.setNurtureNote("");
            row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
            row.setCadenceFrom(today);
            row.setOwnerId(c.getOwnerId() == null ? 0L : c.getOwnerId());
        }
        row.setProspectReason(cut(reason, FollowupConstants.PROSPECT_TEXT_MAX));
        row.setProspectSource(source == null ? 0 : source);
        refresh(row, c, today, actions);
        if (fresh) {
            itemMapper.insert(row);
        } else {
            itemMapper.updateById(row);
        }
        return row;
    }

    /** 结束开发记录；title 不为空时写一条系统跟进记录 */
    public void close(FollowupItemDO row, int reason, String title) {
        row.setClosedAt(LocalDateTime.now());
        row.setCloseReason(reason);
        row.setNextActionDate(null);
        itemMapper.updateById(row);
        if (StringUtils.hasText(title)) {
            writeSystemRecord(row, title);
        }
    }

    public void writeSystemRecord(FollowupItemDO row, String title) {
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_SYSTEM);
        r.setCustomerId(row.getCustomerId());
        r.setFollowupItemId(row.getId());
        r.setTargetType(FollowupConstants.TARGET_CUSTOMER);
        r.setTargetId(row.getCustomerId());
        r.setOpportunityId(0L);
        r.setTitle(cut(title, 200));
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

    // ---------------------------------------------------------------- 推导与提醒

    private void refresh(FollowupItemDO row, CustomerDO c, LocalDate today, Map<String, Map<String, String>> actions) {
        List<CustomerBackgroundReportDO> reports = reportMapper.selectList(new LambdaQueryWrapper<CustomerBackgroundReportDO>()
                .select(CustomerBackgroundReportDO::getId, CustomerBackgroundReportDO::getStatus,
                        CustomerBackgroundReportDO::getPriority)
                .eq(CustomerBackgroundReportDO::getCustomerId, c.getId())
                .isNull(CustomerBackgroundReportDO::getDeletedAt)
                .orderByDesc(CustomerBackgroundReportDO::getId));
        boolean running = reports.stream().anyMatch(r -> Objects.equals(r.getStatus(), DevelopStageDeriver.REPORT_RUNNING));
        Integer priority = reports.stream().filter(r -> Objects.equals(r.getStatus(), DevelopStageDeriver.REPORT_DONE))
                .map(CustomerBackgroundReportDO::getPriority).filter(p -> p != null && p >= 1 && p <= 4).findFirst().orElse(null);
        DevelopStageDeriver.Result d = DevelopStageDeriver.derive(new DevelopStageDeriver.Input(
                nz(c.getPotentialRating()), running, priority, contactable(c), nz(row.getTouchCount()),
                row.getRepliedAt() != null, row.getSignalAt() != null, row.getStageCode()));
        if (!d.stage().equals(row.getStageCode()) || row.getStageSince() == null) {
            row.setStageSince(LocalDateTime.now());
            if (row.getStageCode() != null && Objects.equals(row.getNextActionSource(), FollowupConstants.NEXT_SYSTEM_ONCE)) {
                row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
            }
        }
        row.setStageCode(d.stage());
        row.setStageGroup(FollowupConstants.DEVELOP_GROUPS.get(d.stage()));
        row.setOwnerId(c.getOwnerId() == null ? 0L : c.getOwnerId());
        row.setDocNo(c.getCustomerCode() == null ? "" : c.getCustomerCode());
        cadence(row, d, today, actions == null ? Map.of() : actions.getOrDefault(d.stage(), Map.of()));
    }

    private void cadence(FollowupItemDO row, DevelopStageDeriver.Result d, LocalDate today, Map<String, String> actions) {
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
        LocalDate since = row.getStageSince() == null ? today : row.getStageSince().toLocalDate();
        // 当天提醒的阶段不早于加入开发的日期（重新开发时从重新开发当天起算）
        if (row.getCadenceFrom() != null && since.isBefore(row.getCadenceFrom())) {
            since = row.getCadenceFrom();
        }
        DevelopCadence.Reminder r = DevelopCadence.of(new DevelopCadence.Input(d.stage(), d.unqualified(), since,
                row.getFirstTouchDate(), settings.developSequence(TenantContext.getTenantId()), actions), today);
        if (Objects.equals(row.getNextActionSource(), FollowupConstants.NEXT_USER) && "D5".equals(d.stage())
                && r.date() != null && row.getNextActionDate() != null && r.date().isAfter(row.getNextActionDate())
                && !r.date().isAfter(today)) {
            // 业务员的日期已过、之后又到了新的序列节点：按序列提醒（换新价值点）
            row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
        }
        if (!Objects.equals(row.getNextActionSource(), FollowupConstants.NEXT_SYSTEM)) {
            return;
        }
        if (r.date() == null && "D6".equals(d.stage())) {
            // D6 按业务员定的日期；系统来源时保留原来的下一步（记跟进必填日期，这里一般不会出现）
            return;
        }
        row.setNextAction(cut(r.action(), FollowupConstants.NEXT_ACTION_MAX));
        row.setNextActionDate(r.date());
    }

    /** 有带邮箱、WhatsApp 或电话的联系人；存量客户的主联系人还在 customer 表上 */
    private boolean contactable(CustomerDO c) {
        if (StringUtils.hasText(c.getContactEmail()) || StringUtils.hasText(c.getContactPhone())
                || StringUtils.hasText(c.getWhatsapp())) {
            return true;
        }
        Long n = contactMapper.selectCount(new LambdaQueryWrapper<CustomerContactDO>()
                .eq(CustomerContactDO::getCustomerId, c.getId())
                .isNull(CustomerContactDO::getDeletedAt)
                .and(w -> w.ne(CustomerContactDO::getEmail, "").or().ne(CustomerContactDO::getPhone, "")
                        .or().ne(CustomerContactDO::getWhatsapp, "")));
        return n != null && n > 0;
    }

    private static FollowupItemDO newItem(CustomerDO c, LocalDate today) {
        FollowupItemDO row = new FollowupItemDO();
        row.setTenantId(TenantContext.getTenantId());
        row.setBoard(FollowupConstants.BOARD_DEVELOP);
        row.setTargetType(FollowupConstants.TARGET_CUSTOMER);
        row.setTargetId(c.getId());
        row.setCustomerId(c.getId());
        row.setOpportunityId(0L);
        row.setOwnerId(c.getOwnerId() == null ? 0L : c.getOwnerId());
        row.setSourceKey("");
        row.setCadenceFrom(today);
        row.setBlockerCode("");
        row.setNextAction("");
        row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
        row.setCurrencyCode("CNY");
        row.setAmount(BigDecimal.ZERO);
        row.setReceivedAmount(BigDecimal.ZERO);
        row.setAmountCny(BigDecimal.ZERO);
        row.setSnoozeCount(0);
        row.setLastFollowupId(0L);
        row.setNoReplyStreak(0);
        row.setFeedbackSeen(0);
        row.setNurtureReason("");
        row.setNurtureNote("");
        row.setTouchCount(0);
        row.setSignalText("");
        row.setStopReason("");
        row.setStopNote("");
        row.setCloseReason(FollowupConstants.CLOSE_NONE);
        return row;
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
