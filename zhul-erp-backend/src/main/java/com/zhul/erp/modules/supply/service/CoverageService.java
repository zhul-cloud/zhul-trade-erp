package com.zhul.erp.modules.supply.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.ChannelVO;
import com.zhul.erp.modules.supply.dto.SupplyRequests;
import com.zhul.erp.modules.supply.entity.ScCoverageChannelDO;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.repository.ScCoverageChannelMapper;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import com.zhul.erp.modules.supply.support.DemandAssembler;
import com.zhul.erp.modules.supply.support.DemandRecalc;
import com.zhul.erp.modules.supply.support.DemandRules;
import com.zhul.erp.modules.supply.support.EvidenceIndex;
import com.zhul.erp.modules.supply.support.SupplyLookups;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 覆盖状态与渠道（spec supply-chain/coverage-map；design 决策 7、8）。
 * 系统守的底线：没有有效期内证据不能标「可直接报价」，判断前在事务内重新读证据。
 */
@Service
@RequiredArgsConstructor
public class CoverageService {

    private final ScDemandMapper demandMapper;
    private final ScCoverageChannelMapper channelMapper;
    private final SupplierMapper supplierMapper;
    private final SupplySourceMapper sourceMapper;
    private final EvidenceIndex evidenceIndex;
    private final SupplyLookups lookups;
    private final DocLogs docLogs;
    private final PermissionChecker perm;

    @Transactional
    public void setCoverage(Long id, SupplyRequests.Coverage req) {
        ScDemandDO d = demandMapper.selectById(id);
        if (d == null || d.getDeletedAt() != null || d.getStatus() != SupplyConstants.DEMAND_IN_MATRIX) {
            throw new BizException("只有已进入覆盖矩阵的需求可以修改覆盖状态");
        }
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (!note.isEmpty() || req.getStatus().equals(d.getSuggestedCoverage())) {
            if (!change(d, req.getStatus(), note, null)) {
                throw new BizException("没有有效期内的价格证据，请先询价");
            }
            return;
        }
        throw new BizException("改为与建议不同的状态时请填写说明");
    }

    /**
     * 改覆盖状态（记一次处理也用它）：重新读证据并刷新建议，标「可直接报价」须有有效证据，否则返回 false 不改。
     * 改后清掉待确认标记和「新进入矩阵」「证据过期」这类关于状态的系统提醒。调用方负责事务。
     *
     * @param systemReason 不为空时操作日志记为系统（如「询价回价」）
     */
    public boolean change(ScDemandDO d, int status, String note, String systemReason) {
        LocalDate today = LocalDate.now();
        int days = lookups.evidenceDays();
        EvidenceIndex.Snapshot ev = evidenceIndex.load(List.of(d));
        DemandRecalc.applyCoverage(d, ev.latest(d, null), ev.leadSuppliers(d), today, days);
        if (status == SupplyConstants.COVER_READY && !DemandRules.evidenceValid(d.getEvidenceDate(), today, days)) {
            return false;
        }
        int from = d.getCoverageStatus() == null ? 0 : d.getCoverageStatus();
        boolean clearRemind = d.getNextActionSource() != null && d.getNextActionSource() == SupplyConstants.NEXT_SYSTEM
                && (d.getReminderType() == SupplyConstants.REMIND_NEW_IN_MATRIX
                || d.getReminderType() == SupplyConstants.REMIND_EXPIRED);
        LambdaUpdateWrapper<ScDemandDO> u = new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, d.getId())
                .set(ScDemandDO::getCoverageStatus, status)
                .set(ScDemandDO::getCoveragePending, 0)
                .set(ScDemandDO::getSuggestedCoverage, d.getSuggestedCoverage())
                .set(ScDemandDO::getCoverageBasis, d.getCoverageBasis())
                .set(ScDemandDO::getEvidenceType, d.getEvidenceType())
                .set(ScDemandDO::getEvidenceId, d.getEvidenceId())
                .set(ScDemandDO::getEvidenceDate, d.getEvidenceDate())
                .set(note != null, ScDemandDO::getCoverageNote, note)
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now());
        if (clearRemind) {
            u.set(ScDemandDO::getNextAction, "").set(ScDemandDO::getNextActionDate, null)
                    .set(ScDemandDO::getNextActionSource, SupplyConstants.NEXT_NONE)
                    .set(ScDemandDO::getReminderType, SupplyConstants.REMIND_NONE).set(ScDemandDO::getDeferCount, 0);
            d.setNextAction("");
            d.setNextActionDate(null);
            d.setNextActionSource(SupplyConstants.NEXT_NONE);
            d.setReminderType(SupplyConstants.REMIND_NONE);
        }
        demandMapper.update(null, u);
        boolean wasPending = d.getCoveragePending() != null && d.getCoveragePending() == 1;
        d.setCoverageStatus(status);
        d.setCoveragePending(0);
        if (from != status || wasPending) {
            String who = systemReason != null ? "系统：" + systemReason + "，" : "";
            String text = from == status ? "确认为「" + DemandRules.coverageName(status) + "」"
                    : DemandRules.coverageName(from) + " → " + DemandRules.coverageName(status);
            docLogs.add(DocumentType.SC, d.getId(), "覆盖状态", who + text + (StringUtils.hasText(note) ? "：" + note : ""));
        }
        return true;
    }

    @Transactional
    public void setChannels(Long id, SupplyRequests.Channels req) {
        ScDemandDO d = demandMapper.selectById(id);
        if (d == null || d.getDeletedAt() != null) {
            throw new BizException("需求对象不存在或已合并");
        }
        List<Long> backups = req.getBackupSupplierIds() == null ? List.of() : req.getBackupSupplierIds();
        if (backups.size() > SupplyConstants.MAX_BACKUP_CHANNELS) {
            throw new BizException("备选渠道最多 5 个");
        }
        if (req.getPrimarySupplierId() == null && !backups.isEmpty()) {
            throw new BizException("请先指定主渠道");
        }
        Map<Long, Integer> want = new LinkedHashMap<>();
        if (req.getPrimarySupplierId() != null) {
            want.put(req.getPrimarySupplierId(), SupplyConstants.CHANNEL_PRIMARY);
        }
        for (Long b : backups) {
            if (want.putIfAbsent(b, SupplyConstants.CHANNEL_BACKUP) != null) {
                throw new BizException("同一供应商不能重复");
            }
        }
        if (!want.isEmpty()) {
            Set<Long> found = new HashSet<>();
            supplierMapper.selectBatchIds(want.keySet()).stream().filter(s -> s.getDeletedAt() == null)
                    .forEach(s -> found.add(s.getId()));
            if (found.size() != want.size()) {
                throw new BizException("供应商不存在或已删除");
            }
        }
        channelMapper.update(null, new LambdaUpdateWrapper<ScCoverageChannelDO>()
                .eq(ScCoverageChannelDO::getDemandId, id).isNull(ScCoverageChannelDO::getDeletedAt)
                .set(ScCoverageChannelDO::getDeletedAt, LocalDateTime.now())
                .set(ScCoverageChannelDO::getUpdateTime, LocalDateTime.now()));
        int sort = 0;
        for (Map.Entry<Long, Integer> e : want.entrySet()) {
            ScCoverageChannelDO c = new ScCoverageChannelDO();
            c.setTenantId(PiStore.tenantId());
            c.setDemandId(id);
            c.setSupplierId(e.getKey());
            c.setRole(e.getValue());
            c.setSortOrder(sort++);
            channelMapper.insert(c);
        }
        demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, id)
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
        docLogs.add(DocumentType.SC, id, "渠道", want.isEmpty() ? "清空渠道"
                : "主渠道 1 个，备选 " + (want.size() - 1) + " 个");
    }

    /** 渠道与各自最近证据；没有「查看货源信息」时返回空 */
    public List<ChannelVO> channels(ScDemandDO d) {
        if (!perm.has(SupplyConstants.PERM_SUPPLIER_VIEW)) {
            return List.of();
        }
        List<ScCoverageChannelDO> rows = channelMapper.selectList(new LambdaQueryWrapper<ScCoverageChannelDO>()
                .eq(ScCoverageChannelDO::getDemandId, d.getId()).isNull(ScCoverageChannelDO::getDeletedAt)
                .orderByAsc(ScCoverageChannelDO::getRole).orderByAsc(ScCoverageChannelDO::getSortOrder));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        supplierMapper.selectBatchIds(rows.stream().map(ScCoverageChannelDO::getSupplierId).toList())
                .forEach(s -> names.put(s.getId(), DemandAssembler.supplierName(s)));
        List<ChannelVO> out = new ArrayList<>(rows.size());
        for (ScCoverageChannelDO c : rows) {
            ChannelVO v = new ChannelVO();
            v.setSupplierId(c.getSupplierId());
            v.setSupplierName(names.get(c.getSupplierId()));
            v.setRole(c.getRole());
            if (StringUtils.hasText(d.getModelKey())) {
                fillEvidence(v, d.getModelKey(), c.getSupplierId());
            }
            out.add(v);
        }
        return out;
    }

    /** 最近证据的来源单据与价格（详情页依据）；没有「查看货源信息」时只返回日期和类型 */
    public Map<String, Object> evidence(ScDemandDO d) {
        if (d.getEvidenceId() == null || d.getEvidenceId() == 0) {
            return null;
        }
        Map<String, Object> m = d.getEvidenceType() == SupplyConstants.EVIDENCE_PO
                ? sourceMapper.poLine(d.getEvidenceId()) : sourceMapper.quoteLine(d.getEvidenceId());
        if (m == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", d.getEvidenceType());
        out.put("date", d.getEvidenceDate());
        out.put("docNo", m.get("docNo"));
        out.put("docId", m.get("docId"));
        if (perm.has(SupplyConstants.PERM_SUPPLIER_VIEW)) {
            out.put("unitPrice", m.get("unitPrice"));
            out.put("currencyCode", m.get("currencyCode"));
            out.put("taxIncluded", m.get("taxIncluded"));
            Number sid = (Number) m.get("supplierId");
            SupplierDO s = sid == null || sid.longValue() == 0 ? null : supplierMapper.selectById(sid.longValue());
            out.put("supplierName", s != null ? DemandAssembler.supplierName(s) : m.get("shopName"));
        }
        return out;
    }

    private void fillEvidence(ChannelVO v, String modelKey, Long supplierId) {
        Map<String, Object> q = sourceMapper.latestQuote(supplierId, modelKey);
        Map<String, Object> po = sourceMapper.poBySupplier(supplierId).stream()
                .filter(r -> modelKey.equals(MpnNormalizer.normalize((String) r.get("model")))).findFirst().orElse(null);
        LocalDate qd = date(q);
        LocalDate pd = date(po);
        Map<String, Object> best = qd == null ? po : pd == null || !pd.isAfter(qd) ? q : po;
        if (best == null) {
            return;
        }
        v.setEvidenceType(best == po ? SupplyConstants.EVIDENCE_PO : SupplyConstants.EVIDENCE_QUOTE);
        v.setEvidenceDate(date(best));
        v.setUnitPrice((BigDecimal) best.get("unitPrice"));
        v.setCurrencyCode((String) best.get("currencyCode"));
        Object tax = best.get("taxIncluded");
        v.setTaxIncluded(tax instanceof Number n ? n.intValue() == 1 : tax instanceof Boolean b ? b : null);
        v.setItemCondition(best.get("itemCondition") instanceof Number n ? n.intValue() : null);
        v.setLeadTime(best.get("leadTime") instanceof Number n ? n.intValue() : null);
    }

    private static LocalDate date(Map<String, Object> m) {
        Object o = m == null ? null : m.get("evidenceDate");
        return o instanceof Date d ? d.toLocalDate() : o instanceof LocalDate l ? l : null;
    }
}
