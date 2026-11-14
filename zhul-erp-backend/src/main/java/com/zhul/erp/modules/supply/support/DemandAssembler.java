package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.DemandVO;
import com.zhul.erp.modules.supply.entity.ScCoverageChannelDO;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScGapProjectDO;
import com.zhul.erp.modules.supply.entity.ScGapProjectItemDO;
import com.zhul.erp.modules.supply.repository.ScCoverageChannelMapper;
import com.zhul.erp.modules.supply.repository.ScGapProjectItemMapper;
import com.zhul.erp.modules.supply.repository.ScGapProjectMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 需求对象转成页面行：补品牌、品类、负责人名称，渠道数、单一渠道、询价中、进行中的专项 */
@Component
@RequiredArgsConstructor
public class DemandAssembler {

    private final SupplyLookups lookups;
    private final InquiryLookups inquiryLookups;
    private final ScCoverageChannelMapper channelMapper;
    private final ScGapProjectItemMapper projectItemMapper;
    private final ScGapProjectMapper projectMapper;
    private final SupplierMapper supplierMapper;
    private final SupplySourceMapper sourceMapper;
    private final PermissionChecker perm;

    public List<DemandVO> toVOs(List<ScDemandDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ScDemandDO::getId).toList();
        Map<Long, String> brands = lookups.brandNames(rows.stream().map(ScDemandDO::getBrandId).toList());
        Map<Long, String> cats = lookups.categoryNames(rows.stream().map(ScDemandDO::getCategoryId).toList());
        Map<Long, String> users = inquiryLookups.userNames(rows.stream().map(ScDemandDO::getOwnerId).filter(i -> i != null && i > 0).toList());
        Map<Long, List<ScCoverageChannelDO>> channels = new HashMap<>();
        Set<Long> supplierIds = new HashSet<>();
        for (List<Long> part : chunks(ids)) {
            channelMapper.selectList(new LambdaQueryWrapper<ScCoverageChannelDO>()
                            .in(ScCoverageChannelDO::getDemandId, part).isNull(ScCoverageChannelDO::getDeletedAt))
                    .forEach(c -> {
                        channels.computeIfAbsent(c.getDemandId(), k -> new ArrayList<>()).add(c);
                        supplierIds.add(c.getSupplierId());
                    });
        }
        boolean showSupplier = perm.has(SupplyConstants.PERM_SUPPLIER_VIEW);
        Map<Long, String> suppliers = new HashMap<>();
        if (showSupplier && !supplierIds.isEmpty()) {
            supplierMapper.selectBatchIds(supplierIds).forEach(s -> suppliers.put(s.getId(), supplierName(s)));
        }
        Map<Long, Long> activeProject = activeProjects(ids);
        Map<String, String> sourcing = new HashMap<>();
        sourceMapper.sourcingModels().forEach(m -> sourcing.putIfAbsent((String) m.get("modelKey"), (String) m.get("taskCode")));
        int days = lookups.evidenceDays();
        LocalDate today = LocalDate.now();
        List<DemandVO> out = new ArrayList<>(rows.size());
        for (ScDemandDO d : rows) {
            DemandVO v = new DemandVO();
            org.springframework.beans.BeanUtils.copyProperties(d, v);
            v.setPriorityManual(d.getPriorityManual() != null && d.getPriorityManual() == 1);
            v.setCoveragePending(d.getCoveragePending() != null && d.getCoveragePending() == 1);
            v.setEvidenceValid(DemandRules.evidenceValid(d.getEvidenceDate(), today, days));
            v.setEvidenceExpireDate(d.getEvidenceDate() == null ? null : d.getEvidenceDate().plusDays(days));
            v.setBrandName(brands.getOrDefault(d.getBrandId(), ""));
            v.setCategoryName(cats.get(d.getCategoryId()));
            v.setOwnerName(users.get(d.getOwnerId()));
            v.setModel(d.getModel() == null || d.getModel().isEmpty() ? null : d.getModel());
            List<ScCoverageChannelDO> ch = channels.getOrDefault(d.getId(), List.of());
            v.setChannelCount(ch.size());
            boolean quotable = d.getCoverageStatus() != null && d.getCoverageStatus() >= SupplyConstants.COVER_CONFIRM;
            v.setSingleChannel(quotable && ch.stream().noneMatch(c -> c.getRole() == SupplyConstants.CHANNEL_BACKUP));
            if (showSupplier) {
                ch.stream().filter(c -> c.getRole() == SupplyConstants.CHANNEL_PRIMARY).findFirst()
                        .ifPresent(c -> v.setPrimarySupplier(suppliers.get(c.getSupplierId())));
            }
            v.setSourcingTask(d.getModelKey() == null || d.getModelKey().isEmpty() ? null : sourcing.get(d.getModelKey()));
            v.setActiveProjectId(activeProject.get(d.getId()));
            out.add(v);
        }
        return out;
    }

    /** 需求对象 → 所在进行中的专项 */
    public Map<Long, Long> activeProjects(List<Long> demandIds) {
        Map<Long, Long> out = new HashMap<>();
        List<ScGapProjectItemDO> items = new ArrayList<>();
        for (List<Long> part : chunks(demandIds)) {
            items.addAll(projectItemMapper.selectList(new LambdaQueryWrapper<ScGapProjectItemDO>()
                    .in(ScGapProjectItemDO::getDemandId, part).isNull(ScGapProjectItemDO::getDeletedAt)));
        }
        if (items.isEmpty()) {
            return out;
        }
        Set<Long> active = new HashSet<>();
        projectMapper.selectList(new LambdaQueryWrapper<ScGapProjectDO>()
                        .select(ScGapProjectDO::getId)
                        .in(ScGapProjectDO::getId, items.stream().map(ScGapProjectItemDO::getProjectId).distinct().toList())
                        .eq(ScGapProjectDO::getStatus, SupplyConstants.PROJECT_ACTIVE)
                        .isNull(ScGapProjectDO::getDeletedAt))
                .forEach(p -> active.add(p.getId()));
        items.stream().filter(i -> active.contains(i.getProjectId())).forEach(i -> out.put(i.getDemandId(), i.getProjectId()));
        return out;
    }

    public static String supplierName(SupplierDO s) {
        return s.getShortName() != null && !s.getShortName().isBlank() ? s.getShortName() : s.getName();
    }

    public static <T> List<List<T>> chunks(List<T> list) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += 500) {
            out.add(list.subList(i, Math.min(list.size(), i + 500)));
        }
        return out;
    }
}
