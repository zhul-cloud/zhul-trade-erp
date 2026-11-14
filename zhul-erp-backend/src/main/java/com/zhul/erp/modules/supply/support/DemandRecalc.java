package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScDemandSignalMapper;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.support.DemandRules.Counts;
import com.zhul.erp.modules.supply.support.DemandRules.Presence;
import com.zhul.erp.modules.supply.support.EvidenceIndex.Evidence;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 每天重算（design 决策 4–7、12）：次数、需求等级、建议优先级 → 高中优先级进入覆盖矩阵 → 建议覆盖状态与证据过期降级 → 系统提醒。
 * 事务边界：调用方把同一租户的重算与提醒放在一个事务里；调用方负责租户上下文。
 */
@Component
@RequiredArgsConstructor
public class DemandRecalc {

    private final ScDemandMapper demandMapper;
    private final ScDemandSignalMapper signalMapper;
    private final ScFollowupLogMapper logMapper;
    private final EvidenceIndex evidenceIndex;
    private final SupplyLookups lookups;
    private final DocLogs docLogs;

    /** 重算结果，供日志与测试 */
    public record Summary(int demands, int entered, int downgraded, int reminders) {
    }

    /** @param stockouts 本次新出现询价无货的需求对象 → 无货说明 */
    public Summary run(LocalDate today, Map<Long, String> stockouts) {
        return run(today, stockouts, null);
    }

    /** 只重算这些需求对象（归类、合并、记处理之后）；ids 为 null 时重算全部 */
    public Summary run(LocalDate today, Map<Long, String> stockouts, java.util.Collection<Long> ids) {
        return run(today, stockouts, ids, java.util.Set.of());
    }

    /** 低优先级的需求手动加入覆盖矩阵 */
    public Summary enterMatrix(Long id, LocalDate today) {
        return run(today, Map.of(), List.of(id), java.util.Set.of(id));
    }

    private Summary run(LocalDate today, Map<Long, String> stockouts, java.util.Collection<Long> ids,
                        java.util.Set<Long> forceEnter) {
        if (ids != null && ids.isEmpty()) {
            return new Summary(0, 0, 0, 0);
        }
        LocalDateTime from = today.minusDays(SupplyConstants.PRIORITY_WINDOW_DAYS - 1L).atStartOfDay();
        Map<Long, Map<String, Object>> stats = new HashMap<>(256);
        for (Map<String, Object> m : signalMapper.stats(from, lookups.supplyLostCodes(), ids)) {
            stats.put(((Number) m.get("demandId")).longValue(), m);
        }
        List<ScDemandDO> demands = demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .in(ids != null, ScDemandDO::getId, ids == null ? List.of(0L) : ids)
                .isNull(ScDemandDO::getDeletedAt));
        int days = lookups.evidenceDays();
        EvidenceIndex.Snapshot ev = evidenceIndex.load(demands.stream()
                .filter(d -> d.getStatus() != SupplyConstants.DEMAND_PAUSED).toList());
        int entered = 0;
        int downgraded = 0;
        int reminders = 0;
        for (ScDemandDO d : demands) {
            ScDemandDO before = copy(d);
            applyStats(d, stats.get(d.getId()));
            if (d.getStatus() == SupplyConstants.DEMAND_WATCHING
                    && (d.getPriority() >= SupplyConstants.PRIORITY_MID || forceEnter.contains(d.getId()))) {
                d.setStatus(SupplyConstants.DEMAND_IN_MATRIX);
                d.setEnteredMatrixAt(LocalDateTime.now());
                entered++;
            }
            Integer remind = null;
            String remindText = null;
            if (d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX) {
                boolean isNew = before.getStatus() != SupplyConstants.DEMAND_IN_MATRIX;
                applyCoverage(d, ev.latest(d, null), ev.leadSuppliers(d), today, days);
                if (isNew) {
                    d.setCoverageStatus(d.getSuggestedCoverage());
                    d.setCoveragePending(1);
                    remind = SupplyConstants.REMIND_NEW_IN_MATRIX;
                    remindText = "新进入矩阵，确认覆盖状态";
                    docLogs.add(DocumentType.SC, d.getId(), "进入覆盖矩阵", "优先级" + DemandRules.priorityName(d.getPriority())
                            + "，建议状态「" + DemandRules.coverageName(d.getSuggestedCoverage()) + "」");
                } else if (d.getCoverageStatus() == SupplyConstants.COVER_READY
                        && !DemandRules.evidenceValid(d.getEvidenceDate(), today, days)) {
                    d.setCoverageStatus(SupplyConstants.COVER_CONFIRM);
                    downgraded++;
                    docLogs.add(DocumentType.SC, d.getId(), "覆盖状态", "系统：证据过期，可直接报价 → 需确认");
                    if (ev.sourcingTask(d.getModelKey()) == null) {
                        long age = d.getEvidenceDate() == null ? 0 : ChronoUnit.DAYS.between(d.getEvidenceDate(), today);
                        remind = SupplyConstants.REMIND_EXPIRED;
                        remindText = "价格证据已过 " + age + " 天，重新询价";
                    }
                }
            }
            if (remind == null && stockouts.containsKey(d.getId()) && d.getStatus() != SupplyConstants.DEMAND_PAUSED) {
                remind = SupplyConstants.REMIND_STOCKOUT;
                String why = stockouts.get(d.getId());
                remindText = "询价无货" + (StringUtils.hasText(why) ? " · " + why : "") + "，找替代渠道或确认是否停产";
            }
            if (remind == null && d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX
                    && d.getPriority() >= SupplyConstants.PRIORITY_MID
                    && d.getCoverageStatus() != SupplyConstants.COVER_READY && !StringUtils.hasText(d.getNextAction())) {
                remind = SupplyConstants.REMIND_NO_NEXT;
                remindText = "还没有安排下一步";
            }
            if (remind != null && remind(d, remind, remindText, today)) {
                reminders++;
            }
            save(before, d);
        }
        return new Summary(demands.size(), entered, downgraded, reminders);
    }

    /** 写提醒：人定的下一步还没到期时不覆盖，只在时间线留一条；返回是否写到了下一步上 */
    private boolean remind(ScDemandDO d, int type, String text, LocalDate today) {
        boolean manualPending = d.getNextActionSource() == SupplyConstants.NEXT_MANUAL && d.getNextActionDate() != null
                && d.getNextActionDate().isAfter(today) && StringUtils.hasText(d.getNextAction());
        if (manualPending) {
            ScFollowupLogDO l = new ScFollowupLogDO();
            l.setTenantId(PiStore.tenantId());
            l.setRecordType(SupplyConstants.LOG_SYSTEM);
            l.setSourceType(SupplyConstants.LOG_SRC_REMINDER);
            l.setContent("系统提醒：" + text);
            l.setDemandIds("[" + d.getId() + "]");
            l.setGapProjectId(0L);
            l.setOperatorId(0L);
            logMapper.insert(l);
            return false;
        }
        d.setNextAction(text);
        d.setNextActionDate(today);
        d.setNextActionSource(SupplyConstants.NEXT_SYSTEM);
        d.setReminderType(type);
        d.setDeferCount(0);
        return true;
    }

    private static void applyStats(ScDemandDO d, Map<String, Object> m) {
        Counts c = new Counts(i(m, "inquiry"), i(m, "orders"), i(m, "stockout"), i(m, "lost"), i(m, "lostSupply"),
                i(m, "search"), i(m, "research"), i(m, "observeSources"));
        d.setCntInquiry(c.inquiry());
        d.setCntOrder(c.order());
        d.setCntStockout(c.stockout());
        d.setCntLost(c.lost());
        d.setCntLostSupply(c.lostSupply());
        d.setCntSearch(c.search());
        d.setCntResearch(c.research());
        Object last = m == null ? null : m.get("lastAt");
        d.setLastSignalAt(last instanceof Timestamp t ? t.toLocalDateTime() : last instanceof LocalDateTime l ? l : null);
        d.setDemandLevel(DemandRules.level(new Presence(i(m, "inquiryAll") > 0, i(m, "commercialAll") > 0,
                i(m, "observeAll"), i(m, "observeSources"))));
        DemandRules.Priority p = DemandRules.priority(c);
        d.setSuggestedPriority(p.value());
        d.setPriorityRule(p.rule());
        if (d.getPriorityManual() == null || d.getPriorityManual() == 0) {
            d.setPriority(p.value());
        }
    }

    /** 建议状态与依据；只算建议，不改已确认的状态 */
    public static void applyCoverage(ScDemandDO d, Evidence e, int leads, LocalDate today, int days) {
        d.setSuggestedCoverage(DemandRules.suggestCoverage(e == null ? null : e.date(), today, days, leads));
        d.setEvidenceType(e == null ? SupplyConstants.EVIDENCE_NONE : e.type());
        d.setEvidenceId(e == null ? 0L : e.id());
        d.setEvidenceDate(e == null ? null : e.date());
        String basis;
        if (e != null) {
            long age = ChronoUnit.DAYS.between(e.date(), today);
            String what = e.type() == SupplyConstants.EVIDENCE_PO ? "采购订单" : "询价记录";
            basis = DemandRules.evidenceValid(e.date(), today, days)
                    ? what + " " + e.date() + "（" + age + " 天前）"
                    : "最近证据 " + age + " 天前，已过期";
        } else if (leads > 0) {
            basis = leads + " 家供应商主营该品牌";
        } else {
            basis = "没有价格记录，也没有供应商主营该品牌";
        }
        d.setCoverageBasis(basis);
    }

    private static int i(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static ScDemandDO copy(ScDemandDO d) {
        ScDemandDO c = new ScDemandDO();
        org.springframework.beans.BeanUtils.copyProperties(d, c);
        return c;
    }

    /** 只在有变化时更新；可空字段用 set 显式写 */
    private void save(ScDemandDO before, ScDemandDO d) {
        if (Objects.equals(before, d)) {
            return;
        }
        demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>()
                .eq(ScDemandDO::getId, d.getId())
                .set(ScDemandDO::getStatus, d.getStatus())
                .set(ScDemandDO::getDemandLevel, d.getDemandLevel())
                .set(ScDemandDO::getSuggestedPriority, d.getSuggestedPriority())
                .set(ScDemandDO::getPriorityRule, d.getPriorityRule())
                .set(ScDemandDO::getPriority, d.getPriority())
                .set(ScDemandDO::getCntInquiry, d.getCntInquiry())
                .set(ScDemandDO::getCntOrder, d.getCntOrder())
                .set(ScDemandDO::getCntStockout, d.getCntStockout())
                .set(ScDemandDO::getCntLost, d.getCntLost())
                .set(ScDemandDO::getCntLostSupply, d.getCntLostSupply())
                .set(ScDemandDO::getCntSearch, d.getCntSearch())
                .set(ScDemandDO::getCntResearch, d.getCntResearch())
                .set(ScDemandDO::getLastSignalAt, d.getLastSignalAt())
                .set(ScDemandDO::getCoverageStatus, d.getCoverageStatus())
                .set(ScDemandDO::getSuggestedCoverage, d.getSuggestedCoverage())
                .set(ScDemandDO::getCoveragePending, d.getCoveragePending())
                .set(ScDemandDO::getCoverageBasis, d.getCoverageBasis())
                .set(ScDemandDO::getEvidenceType, d.getEvidenceType())
                .set(ScDemandDO::getEvidenceId, d.getEvidenceId())
                .set(ScDemandDO::getEvidenceDate, d.getEvidenceDate())
                .set(ScDemandDO::getEnteredMatrixAt, d.getEnteredMatrixAt())
                .set(ScDemandDO::getNextAction, d.getNextAction())
                .set(ScDemandDO::getNextActionDate, d.getNextActionDate())
                .set(ScDemandDO::getNextActionSource, d.getNextActionSource())
                .set(ScDemandDO::getReminderType, d.getReminderType())
                .set(ScDemandDO::getDeferCount, d.getDeferCount())
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
    }
}
