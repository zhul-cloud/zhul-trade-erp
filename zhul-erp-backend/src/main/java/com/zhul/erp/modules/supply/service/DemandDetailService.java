package com.zhul.erp.modules.supply.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.BoardVOs;
import com.zhul.erp.modules.supply.dto.DemandDetailVO;
import com.zhul.erp.modules.supply.dto.DemandVO;
import com.zhul.erp.modules.supply.dto.ProjectVO;
import com.zhul.erp.modules.supply.dto.SignalVO;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.support.DemandRules;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 需求对象详情与时间线（spec supply-chain/demand-pool「需求对象详情」），专项详情的处理记录也用这里的时间线 */
@Service
@RequiredArgsConstructor
public class DemandDetailService {

    private static final int TIMELINE_LIMIT = 200;

    private final DemandService demandService;
    private final CoverageService coverageService;
    private final GapProjectService projectService;
    private final ScFollowupLogMapper logMapper;
    private final InquiryLookups inquiryLookups;
    private final PermissionChecker perm;
    private final DocLogs docLogs;
    private final ObjectMapper objectMapper;

    public DemandDetailVO detail(Long id) {
        ScDemandDO d = demandService.live(id);
        DemandVO v = demandService.row(id);
        DemandDetailVO out = new DemandDetailVO();
        out.setDemand(v);
        out.setEvidence(coverageService.evidence(d));
        out.setChannels(coverageService.channels(d));
        List<BoardVOs.TimelineItem> timeline = new ArrayList<>(records(List.of(id)));
        for (SignalVO s : demandService.signalsOf(id)) {
            BoardVOs.TimelineItem t = new BoardVOs.TimelineItem();
            t.setKind("signal");
            t.setTime(s.getOccurredAt());
            t.setSignal(s);
            timeline.add(t);
        }
        timeline.sort(Comparator.comparing(BoardVOs.TimelineItem::getTime, Comparator.nullsLast(Comparator.reverseOrder())));
        out.setTimeline(timeline);
        out.setLogs(docLogs.list(DocumentType.SC, id));
        List<ProjectVO> projects = v.getActiveProjectId() == null ? List.of()
                : List.of(projectService.detailBrief(v.getActiveProjectId()));
        out.setProjects(projects);
        String stage = d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX ? String.valueOf(d.getCoverageStatus())
                : d.getStatus() == SupplyConstants.DEMAND_PAUSED ? "paused" : "watching";
        out.setNodes(SupplyBoardService.STAGE_NODES.getOrDefault(stage, List.of("C5")));
        out.setCanEdit(perm.has(SupplyConstants.PERM_FOLLOWUP_EDIT));
        out.setCanCoverage(perm.has(SupplyConstants.PERM_COVERAGE_EDIT));
        out.setCanSeeSupplier(perm.has(SupplyConstants.PERM_SUPPLIER_VIEW));
        return out;
    }

    /** 涉及这些需求对象的处理记录；没有「查看货源信息」时系统回价记录只显示「询价任务 … 已回价」 */
    public List<BoardVOs.TimelineItem> records(List<Long> demandIds) {
        if (demandIds.isEmpty()) {
            return List.of();
        }
        List<ScFollowupLogDO> rows = logMapper.touching(json(demandIds), TIMELINE_LIMIT);
        Map<Long, String> users = inquiryLookups.userNames(rows.stream().map(ScFollowupLogDO::getOperatorId)
                .filter(o -> o != null && o > 0).collect(Collectors.toSet()));
        boolean supplier = perm.has(SupplyConstants.PERM_SUPPLIER_VIEW);
        List<BoardVOs.TimelineItem> out = new ArrayList<>(rows.size());
        for (ScFollowupLogDO l : rows) {
            BoardVOs.TimelineItem t = new BoardVOs.TimelineItem();
            t.setKind("log");
            t.setTime(l.getCreateTime());
            t.setLogId(l.getId());
            t.setSystem(l.getRecordType() == SupplyConstants.LOG_SYSTEM);
            t.setOperatorName(t.getSystem() ? "系统" : users.get(l.getOperatorId()));
            t.setContent(!supplier && l.getSourceType() == SupplyConstants.LOG_SRC_QUOTED
                    ? "询价任务 " + l.getSourceDocNo() + " 已回价" : l.getContent());
            t.setNextAction(l.getNextAction());
            t.setNextActionDate(l.getNextActionDate());
            t.setStatusChange(statusText(l.getStatusChanges()));
            out.add(t);
        }
        return out;
    }

    private String statusText(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
            long changed = list.stream().filter(m -> !Boolean.TRUE.equals(m.get("skipped"))).count();
            long skipped = list.size() - changed;
            if (list.isEmpty()) {
                return null;
            }
            Map<String, Object> first = list.get(0);
            String to = DemandRules.coverageName(((Number) first.get("to")).intValue());
            return changed > 0 ? changed + " 个型号改为「" + to + "」" + (skipped > 0 ? "，" + skipped + " 个保持原状态" : "")
                    : skipped + " 个型号没有有效证据，保持原状态";
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
