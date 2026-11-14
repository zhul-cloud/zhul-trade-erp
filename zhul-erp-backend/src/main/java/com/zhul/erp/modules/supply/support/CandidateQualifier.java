package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDevCandidateDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.entity.ScGapProjectDO;
import com.zhul.erp.modules.supply.repository.ScDevCandidateMapper;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.repository.ScGapProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 候选自动合格（spec supply-chain/supplier-development「候选供应商」；design 决策 5）：
 * 已关联供应商库、未放弃的候选，在所属开发任务范围内有近 90 天证据就标为合格并记下依据；合格的证据过期后退回「已报价」。
 * 阶段变化写一条系统处理记录（对象类型为候选）。依据的写入与阶段变更在同一事务里。调用方负责租户上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateQualifier {

    private final ScDevCandidateMapper candidateMapper;
    private final ScGapProjectMapper projectMapper;
    private final ScFollowupLogMapper logMapper;
    private final DevPool pool;
    private final ObjectMapper objectMapper;

    /** 一次判定的结果 */
    public record Change(long candidateId, int from, int to) {
    }

    /** 全部进行中开发任务的候选；返回阶段有变化的数量 */
    @Transactional
    public int refresh(LocalDate today) {
        return refresh(today, null);
    }

    /** 只处理指定开发任务（为 null 时处理全部进行中的开发任务） */
    @Transactional
    public int refresh(LocalDate today, Collection<Long> projectIds) {
        if (projectIds != null && projectIds.isEmpty()) {
            return 0;
        }
        List<ScGapProjectDO> projects = projectMapper.selectList(new LambdaQueryWrapper<ScGapProjectDO>()
                .eq(ScGapProjectDO::getStatus, SupplyConstants.PROJECT_ACTIVE)
                .isNotNull(ScGapProjectDO::getSupplierTarget)
                .in(projectIds != null && !projectIds.isEmpty(), ScGapProjectDO::getId, projectIds)
                .isNull(ScGapProjectDO::getDeletedAt));
        if (projects.isEmpty()) {
            return 0;
        }
        List<ScDevCandidateDO> cands = candidateMapper.selectList(new LambdaQueryWrapper<ScDevCandidateDO>()
                .in(ScDevCandidateDO::getProjectId, projects.stream().map(ScGapProjectDO::getId).toList())
                .gt(ScDevCandidateDO::getSupplierId, 0L)
                .ne(ScDevCandidateDO::getStage, SupplyConstants.CAND_ABANDONED)
                .isNull(ScDevCandidateDO::getDeletedAt));
        if (cands.isEmpty()) {
            return 0;
        }
        DevPool.Snapshot snap = pool.load(today);
        Map<Long, ScGapProjectDO> byId = projects.stream().collect(Collectors.toMap(ScGapProjectDO::getId, Function.identity()));
        int n = 0;
        for (ScDevCandidateDO c : cands) {
            ScGapProjectDO p = byId.get(c.getProjectId());
            Change ch = apply(c, snap.qualified(p.getBrandId(), p.getCategoryId()));
            if (ch != null) {
                n++;
                log(c, p, ch);
            }
        }
        return n;
    }

    /** 判定一个候选并落库；没有变化返回 null（依据换成更新的一条时只更新依据，不算变化） */
    Change apply(ScDevCandidateDO c, Map<Long, DevPool.Evidence> qualified) {
        DevPool.Evidence e = qualified.get(c.getSupplierId());
        int from = c.getStage();
        LambdaUpdateWrapper<ScDevCandidateDO> u = new LambdaUpdateWrapper<ScDevCandidateDO>().eq(ScDevCandidateDO::getId, c.getId())
                .set(ScDevCandidateDO::getUpdateTime, LocalDateTime.now());
        if (e != null) {
            boolean same = from == SupplyConstants.CAND_QUALIFIED && Long.valueOf(e.id()).equals(c.getEvidenceId())
                    && Integer.valueOf(e.type()).equals(c.getEvidenceType());
            if (same) {
                return null;
            }
            u.set(ScDevCandidateDO::getStage, SupplyConstants.CAND_QUALIFIED)
                    .set(ScDevCandidateDO::getEvidenceType, e.type())
                    .set(ScDevCandidateDO::getEvidenceId, e.id())
                    .set(ScDevCandidateDO::getEvidenceDate, e.date());
            if (from != SupplyConstants.CAND_QUALIFIED) {
                u.set(ScDevCandidateDO::getNextAction, "").set(ScDevCandidateDO::getNextActionDate, null)
                        .set(ScDevCandidateDO::getLastAction, qualifyText(e))
                        .set(ScDevCandidateDO::getLastActionAt, LocalDateTime.now())
                        .set(ScDevCandidateDO::getLastActionBy, 0L);
            }
            candidateMapper.update(null, u);
            c.setEvidenceType(e.type());
            c.setEvidenceId(e.id());
            c.setEvidenceDate(e.date());
            c.setStage(SupplyConstants.CAND_QUALIFIED);
            return from == SupplyConstants.CAND_QUALIFIED ? null : new Change(c.getId(), from, SupplyConstants.CAND_QUALIFIED);
        }
        if (from != SupplyConstants.CAND_QUALIFIED) {
            return null;
        }
        candidateMapper.update(null, u.set(ScDevCandidateDO::getStage, SupplyConstants.CAND_QUOTED)
                .set(ScDevCandidateDO::getEvidenceType, SupplyConstants.EVIDENCE_NONE)
                .set(ScDevCandidateDO::getEvidenceId, 0L)
                .set(ScDevCandidateDO::getEvidenceDate, null)
                .set(ScDevCandidateDO::getQuotedAt, LocalDate.now())
                .set(ScDevCandidateDO::getLastAction, EXPIRED_TEXT)
                .set(ScDevCandidateDO::getLastActionAt, LocalDateTime.now())
                .set(ScDevCandidateDO::getLastActionBy, 0L));
        c.setStage(SupplyConstants.CAND_QUOTED);
        return new Change(c.getId(), from, SupplyConstants.CAND_QUOTED);
    }

    static final String EXPIRED_TEXT = "近 " + SupplyConstants.DEV_WINDOW_DAYS + " 天没有有效报价或采购订单，退回已报价";

    static String qualifyText(DevPool.Evidence e) {
        return "自动标为合格：" + (e.type() == SupplyConstants.EVIDENCE_PO ? "采购订单 " : "询价记录 ") + e.date();
    }

    private void log(ScDevCandidateDO c, ScGapProjectDO p, Change ch) {
        ScFollowupLogDO l = new ScFollowupLogDO();
        l.setTenantId(PiStore.tenantId());
        l.setRecordType(SupplyConstants.LOG_SYSTEM);
        l.setSourceType(SupplyConstants.LOG_SRC_MANUAL);
        l.setObjectType(SupplyConstants.OBJ_CANDIDATE);
        l.setContent((ch.to() == SupplyConstants.CAND_QUALIFIED ? "「" + c.getName() + "」" + qualifyText(
                new DevPool.Evidence(c.getEvidenceType(), c.getEvidenceId(), c.getEvidenceDate())) : "「" + c.getName() + "」" + EXPIRED_TEXT));
        l.setCandidateIds(json(List.of(c.getId())));
        l.setStatusChanges(json(List.of(Map.of("candidateId", c.getId(), "from", ch.from(), "to", ch.to()))));
        l.setGapProjectId(p.getId());
        l.setNextAction("");
        l.setOperatorId(0L);
        logMapper.insert(l);
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
