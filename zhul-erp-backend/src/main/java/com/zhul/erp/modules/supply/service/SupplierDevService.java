package com.zhul.erp.modules.supply.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.BoardVOs;
import com.zhul.erp.modules.supply.dto.DevVOs;
import com.zhul.erp.modules.supply.dto.MatrixVO;
import com.zhul.erp.modules.supply.dto.SupplyRequests;
import com.zhul.erp.modules.supply.entity.ScDevCandidateDO;
import com.zhul.erp.modules.supply.entity.ScExpansionScopeDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.entity.ScGapProjectDO;
import com.zhul.erp.modules.supply.repository.ScDevCandidateMapper;
import com.zhul.erp.modules.supply.repository.ScExpansionScopeMapper;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.repository.ScGapProjectMapper;
import com.zhul.erp.modules.supply.support.CandidateQualifier;
import com.zhul.erp.modules.supply.support.DemandAssembler;
import com.zhul.erp.modules.supply.support.DevPool;
import com.zhul.erp.modules.supply.support.SupplyLookups;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商开发（OpenSpec supply-chain-phase2-supplier-development；spec supply-chain/supplier-development、followup、
 * gap-project、coverage-map）：开发清单、主动拓展、候选供应商、今日待办与「按供应商」矩阵。
 * <p>
 * 所有数字由 {@link DevPool} 在一次请求内实时算出，卡片、阶段导航、列表与矩阵用同一份数据。
 * 「负责人」筛选：ownerId 为空为当前用户，0 为全部；范围的负责人为开发任务、主动拓展或任一候选的负责人。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierDevService {

    private static final int STOCKOUT_DAYS = 30;
    private static final int TIMELINE_LIMIT = 50;
    private static final Map<Integer, String> STAGE_NAMES = Map.of(
            SupplyConstants.CAND_TODO, "待联系", SupplyConstants.CAND_CONTACTED, "已联系", SupplyConstants.CAND_QUOTED, "已报价",
            SupplyConstants.CAND_QUALIFIED, "合格", SupplyConstants.CAND_ABANDONED, "已放弃");
    private static final Map<Integer, String> HEAT_NAMES = Map.of(
            SupplyConstants.HEAT_LOW, "低", SupplyConstants.HEAT_MID, "中", SupplyConstants.HEAT_HIGH, "高");

    private final DevPool pool;
    private final CandidateQualifier qualifier;
    private final ScDevCandidateMapper candidateMapper;
    private final ScExpansionScopeMapper expansionMapper;
    private final ScGapProjectMapper projectMapper;
    private final ScFollowupLogMapper logMapper;
    private final SupplierMapper supplierMapper;
    private final SupplyBoardService boardService;
    private final SupplyLookups lookups;
    private final InquiryLookups inquiryLookups;
    private final CurrentUserResolver currentUser;
    private final PermissionChecker perm;
    private final DocLogs docLogs;
    private final ObjectMapper objectMapper;

    /** 列表筛选；stage 为 gap（默认，待开发 + 开发中）、todo、doing、done、all */
    public record Filter(String stage, Long brandId, Long parentCategoryId, Long categoryId, Integer heat, String source,
                         Long ownerId, String keyword) {
    }

    /** 一个范围连同它的专项与候选 */
    record Row(DevPool.Range range, ScGapProjectDO project, List<ScDevCandidateDO> candidates) {

        boolean devTask() {
            return project != null && project.getSupplierTarget() != null;
        }

        String stage() {
            if (range.gap() == 0) {
                return "done";
            }
            return devTask() ? "doing" : "todo";
        }

        boolean ownedBy(Long owner) {
            if (owner == null || owner == 0) {
                return true;
            }
            return project != null && owner.equals(project.getOwnerId())
                    || range.expansion() != null && owner.equals(range.expansion().getOwnerId())
                    || candidates.stream().anyMatch(c -> owner.equals(c.getOwnerId()));
        }
    }

    // ---------------------------------------------------------------- 清单

    public DevVOs.Overview overview(Long ownerId) {
        Long owner = owner(ownerId);
        DevVOs.Overview o = new DevVOs.Overview();
        for (Row r : rows(LocalDate.now())) {
            if (!r.ownedBy(owner)) {
                continue;
            }
            switch (r.stage()) {
                case "todo" -> o.setTodo(o.getTodo() + 1);
                case "doing" -> o.setDoing(o.getDoing() + 1);
                default -> o.setDone(o.getDone() + 1);
            }
            o.setGapTotal(o.getGapTotal() + r.range().gap());
        }
        o.setGapGroups(o.getTodo() + o.getDoing());
        return o;
    }

    /** 分组列表：逾期在前，其次热度高、缺口大 */
    public DevVOs.GroupPage groups(Filter f, int page, int size) {
        LocalDate today = LocalDate.now();
        Long owner = owner(f.ownerId());
        List<Row> all = rows(today);
        Map<Long, String> brands = lookups.brandNames(all.stream().map(r -> r.range().brandId()).toList());
        Map<Long, String> cats = lookups.categoryNames(all.stream().map(r -> r.range().categoryId()).toList());
        Map<Long, Long> parents = f.parentCategoryId() == null ? Map.of() : lookups.categoryParents();
        String kw = f.keyword() == null ? "" : f.keyword().trim().toLowerCase();
        String stage = StringUtils.hasText(f.stage()) ? f.stage() : "gap";
        List<Row> rows = all.stream().filter(r -> r.ownedBy(owner)
                && stageMatch(stage, r.stage())
                && (f.brandId() == null || f.brandId() == r.range().brandId())
                && (f.categoryId() == null || f.categoryId() == r.range().categoryId())
                && (f.parentCategoryId() == null || f.parentCategoryId().equals(parents.get(r.range().categoryId())))
                && (f.heat() == null || f.heat() == r.range().heat())
                && (!StringUtils.hasText(f.source()) || r.range().source().equals(f.source()) || "both".equals(r.range().source()))
                && (kw.isEmpty() || brands.getOrDefault(r.range().brandId(), "").toLowerCase().contains(kw)
                        || cats.getOrDefault(r.range().categoryId(), "").toLowerCase().contains(kw)))
                .sorted(Comparator.comparing((Row r) -> -overdue(r, today))
                        .thenComparing(r -> -r.range().heat())
                        .thenComparing(r -> -r.range().gap())
                        .thenComparing(r -> brands.getOrDefault(r.range().brandId(), ""))
                        .thenComparing(r -> r.range().categoryId()))
                .toList();
        int from = Math.max(0, (page - 1) * size);
        List<Row> slice = rows.subList(Math.min(from, rows.size()), Math.min(rows.size(), from + size));
        DevVOs.GroupPage out = new DevVOs.GroupPage();
        out.setTotal(rows.size());
        out.setGroups(groupVOs(slice, brands, cats, today));
        return out;
    }

    /** 阶段筛选：gap 为待开发 + 开发中，all 为全部 */
    static boolean stageMatch(String filter, String stage) {
        return switch (filter) {
            case "all" -> true;
            case "gap" -> !"done".equals(stage);
            default -> filter.equals(stage);
        };
    }

    /** 弹窗用的范围信息：热度、合格、缺口、主动拓展、进行中的专项 */
    public DevVOs.Scope scope(Long brandId, Long categoryId) {
        LocalDate today = LocalDate.now();
        DevPool.Snapshot snap = pool.load(today);
        long cat = categoryId == null ? 0 : categoryId;
        DevPool.Range r = snap.listed(brandId, cat);
        DevVOs.Scope s = new DevVOs.Scope();
        s.setBrandId(brandId);
        s.setCategoryId(cat);
        s.setBrandName(lookups.brandNames(List.of(brandId)).getOrDefault(brandId, ""));
        s.setCategoryName(cat == 0 ? "未分类" : lookups.categoryNames(List.of(cat)).getOrDefault(cat, ""));
        int qualified = snap.qualified(brandId, cat).size();
        int heat = r == null ? SupplyConstants.HEAT_LOW : r.heat();
        int required = SupplyConstants.requiredSuppliers(heat);
        s.setHeat(heat);
        s.setRequired(required);
        s.setQualified(qualified);
        s.setGap(Math.max(0, required - qualified));
        ScExpansionScopeDO e = r == null ? null : r.expansion();
        if (e != null) {
            s.setExpansionId(e.getId());
            s.setExpansionHeat(e.getHeat());
            s.setExpansionReason(e.getReason());
            s.setExpansionOwnerId(e.getOwnerId());
        }
        ScGapProjectDO p = projectMapper.selectOne(new LambdaQueryWrapper<ScGapProjectDO>()
                .eq(ScGapProjectDO::getActiveScope, DevPool.Range.key(brandId, cat)).last("LIMIT 1"));
        if (p != null) {
            s.setActiveProjectId(p.getId());
            s.setActiveProjectName(p.getName());
            s.setActiveProjectTarget(p.getSupplierTarget());
        }
        s.setDefaultName(s.getBrandName() + " · " + s.getCategoryName() + " 供应商开发");
        return s;
    }

    // ---------------------------------------------------------------- 主动拓展

    @Transactional
    public Long addExpansion(SupplyRequests.Expansion req) {
        if (!lookups.brandExists(req.getBrandId())) {
            throw new BizException("品牌不存在");
        }
        if (!lookups.isLevel2(req.getCategoryId())) {
            throw new BizException("请选择细分品类");
        }
        ScExpansionScopeDO e = new ScExpansionScopeDO();
        e.setTenantId(PiStore.tenantId());
        e.setBrandId(req.getBrandId());
        e.setCategoryId(req.getCategoryId());
        e.setHeat(req.getHeat());
        e.setReason(req.getReason().trim());
        e.setOwnerId(req.getOwnerId() == null ? 0L : req.getOwnerId());
        e.setActiveScope(DevPool.Range.key(req.getBrandId(), req.getCategoryId()));
        e.setRemoveReason("");
        try {
            expansionMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException("这个品牌品类已在主动拓展里，可以直接修改热度和理由", ex);
        }
        docLogs.add(DocumentType.SX, e.getId(), "加入", "热度" + HEAT_NAMES.get(e.getHeat()) + "，" + e.getReason());
        return e.getId();
    }

    @Transactional
    public void updateExpansion(Long id, SupplyRequests.ExpansionUpdate req) {
        ScExpansionScopeDO e = expansion(id);
        expansionMapper.update(null, new LambdaUpdateWrapper<ScExpansionScopeDO>().eq(ScExpansionScopeDO::getId, id)
                .set(ScExpansionScopeDO::getHeat, req.getHeat())
                .set(ScExpansionScopeDO::getReason, req.getReason().trim())
                .set(req.getOwnerId() != null, ScExpansionScopeDO::getOwnerId, req.getOwnerId())
                .set(ScExpansionScopeDO::getUpdateTime, LocalDateTime.now()));
        docLogs.add(DocumentType.SX, id, "修改", "热度" + HEAT_NAMES.get(e.getHeat()) + " → " + HEAT_NAMES.get(req.getHeat())
                + "，" + req.getReason().trim());
    }

    @Transactional
    public void removeExpansion(Long id, SupplyRequests.Reason req) {
        expansion(id);
        expansionMapper.update(null, new LambdaUpdateWrapper<ScExpansionScopeDO>().eq(ScExpansionScopeDO::getId, id)
                .set(ScExpansionScopeDO::getActiveScope, null)
                .set(ScExpansionScopeDO::getRemoveReason, req.getReason().trim())
                .set(ScExpansionScopeDO::getDeletedAt, LocalDateTime.now())
                .set(ScExpansionScopeDO::getUpdateTime, LocalDateTime.now()));
        docLogs.add(DocumentType.SX, id, "移出", req.getReason().trim());
    }

    // ---------------------------------------------------------------- 候选

    @Transactional
    public Long addCandidate(Long projectId, SupplyRequests.Candidate req) {
        ScGapProjectDO p = devProject(projectId);
        LocalDate today = LocalDate.now();
        SupplierDO s = req.getSupplierId() == null ? null : supplier(req.getSupplierId());
        String name = s != null ? DemandAssembler.supplierName(s) : req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw new BizException("请填写候选名称");
        }
        String next = StringUtils.hasText(req.getNextAction()) ? req.getNextAction().trim() : "首次联系";
        LocalDate nextDate = req.getNextActionDate() == null ? today : req.getNextActionDate();
        if (nextDate.isBefore(today)) {
            throw new BizException("下一步日期不能早于今天");
        }
        Long me = me();
        ScDevCandidateDO c = new ScDevCandidateDO();
        c.setTenantId(PiStore.tenantId());
        c.setProjectId(projectId);
        c.setSupplierId(s == null ? 0L : s.getId());
        c.setLinkKey(s == null ? null : projectId + ":" + s.getId());
        c.setName(name);
        c.setContactName(trim(req.getContactName()));
        c.setContactInfo(trim(req.getContactInfo()));
        c.setSource(req.getSource() != null ? req.getSource()
                : s != null ? SupplyConstants.CAND_SRC_SUPPLIER_LIB : SupplyConstants.CAND_SRC_OTHER);
        c.setStage(SupplyConstants.CAND_TODO);
        c.setAbandonReason("");
        c.setNote(trim(req.getNote()));
        c.setEvidenceType(SupplyConstants.EVIDENCE_NONE);
        c.setEvidenceId(0L);
        c.setNextAction(next);
        c.setNextActionDate(nextDate);
        c.setOwnerId(req.getOwnerId() != null ? req.getOwnerId() : me);
        c.setLastAction("加为候选");
        c.setLastActionAt(LocalDateTime.now());
        c.setLastActionBy(me);
        try {
            candidateMapper.insert(c);
        } catch (DuplicateKeyException e) {
            throw new BizException("这家供应商已是该开发任务的候选", e);
        }
        writeLog(p.getId(), List.of(c.getId()), "加候选「" + name + "」", null, next, nextDate, me, null);
        if (s != null) {
            qualifier.refresh(today, List.of(projectId));
        }
        return c.getId();
    }

    /** 候选关联供应商库；关联后名称以供应商库为准，之后的证据可以让它自动合格 */
    @Transactional
    public void link(Long id, SupplyRequests.Link req) {
        ScDevCandidateDO c = candidate(id);
        devProject(c.getProjectId());
        if (c.getSupplierId() != null && c.getSupplierId() > 0) {
            throw new BizException("这个候选已关联供应商库");
        }
        SupplierDO s = supplier(req.getSupplierId());
        String name = DemandAssembler.supplierName(s);
        try {
            candidateMapper.update(null, new LambdaUpdateWrapper<ScDevCandidateDO>().eq(ScDevCandidateDO::getId, id)
                    .set(ScDevCandidateDO::getSupplierId, s.getId())
                    .set(ScDevCandidateDO::getLinkKey, c.getProjectId() + ":" + s.getId())
                    .set(ScDevCandidateDO::getName, name)
                    .set(ScDevCandidateDO::getUpdateTime, LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            throw new BizException("这家供应商已是该开发任务的候选", e);
        }
        writeLog(c.getProjectId(), List.of(id), "「" + c.getName() + "」关联供应商库：" + name, null, "", null, me(), null);
        qualifier.refresh(LocalDate.now(), List.of(c.getProjectId()));
    }

    /**
     * 候选记一次处理（design 决策 7）：一个事务内 写处理记录 → 改候选阶段 → 更新候选的下一步与负责人 → 按证据重新判定合格。
     * 合格只能由系统标上；已合格的候选只能改为已放弃，其他阶段变更跳过。
     */
    @Transactional
    public DevVOs.RecordResult record(SupplyRequests.CandidateRecord req) {
        LocalDate today = LocalDate.now();
        List<ScDevCandidateDO> rows = candidateMapper.selectBatchIds(req.getCandidateIds()).stream()
                .filter(c -> c.getDeletedAt() == null).toList();
        if (rows.isEmpty()) {
            throw new BizException("至少选一个候选");
        }
        Set<Long> projectIds = rows.stream().map(ScDevCandidateDO::getProjectId).collect(Collectors.toSet());
        if (projectIds.size() > 1) {
            throw new BizException("一次只能记同一个开发任务下的候选");
        }
        ScGapProjectDO p = devProject(projectIds.iterator().next());
        Integer stage = req.getStage();
        if (stage != null && stage == SupplyConstants.CAND_QUALIFIED) {
            throw new BizException("合格由系统根据近 " + SupplyConstants.DEV_WINDOW_DAYS + " 天的有效报价或采购订单自动标上，不能手动选");
        }
        String reason = trim(req.getAbandonReason());
        if (stage != null && stage == SupplyConstants.CAND_ABANDONED && reason.isEmpty()) {
            throw new BizException("请填写放弃原因");
        }
        String next = trim(req.getNextAction());
        if (req.getNextActionDate() != null && req.getNextActionDate().isBefore(today)) {
            throw new BizException("下一步日期不能早于今天");
        }
        Map<Long, Integer> target = new LinkedHashMap<>();
        List<Long> skipped = new ArrayList<>();
        for (ScDevCandidateDO c : rows) {
            int to = targetStage(c.getStage(), stage);
            if (stage != null && to != stage) {
                skipped.add(c.getId());
            }
            target.put(c.getId(), to);
        }
        boolean nextOptional = target.values().stream().allMatch(SupplierDevService::closedStage);
        if (!nextOptional && (next.isEmpty() || req.getNextActionDate() == null)) {
            throw new BizException("请填写下一步行动与日期");
        }
        Long me = me();
        List<Map<String, Object>> changes = new ArrayList<>();
        for (ScDevCandidateDO c : rows) {
            if (target.get(c.getId()) != c.getStage().intValue()) {
                changes.add(Map.of("candidateId", c.getId(), "name", c.getName(), "from", c.getStage(), "to", target.get(c.getId())));
            }
        }
        Long logId = writeLog(p.getId(), rows.stream().map(ScDevCandidateDO::getId).toList(), req.getContent().trim(),
                changes.isEmpty() ? null : changes, next, req.getNextActionDate(), me, req.getIdempotencyKey());
        DevVOs.RecordResult r = new DevVOs.RecordResult();
        if (logId == null) {
            log.info("候选记处理重复提交，idempotencyKey={}", req.getIdempotencyKey());
            r.setDuplicate(true);
            r.setSkipped(List.of());
            return r;
        }
        for (ScDevCandidateDO c : rows) {
            int to = target.get(c.getId());
            boolean closed = closedStage(to);
            LambdaUpdateWrapper<ScDevCandidateDO> u = new LambdaUpdateWrapper<ScDevCandidateDO>().eq(ScDevCandidateDO::getId, c.getId())
                    .set(ScDevCandidateDO::getStage, to)
                    .set(ScDevCandidateDO::getNextAction, closed ? "" : next)
                    .set(ScDevCandidateDO::getNextActionDate, closed ? null : req.getNextActionDate())
                    .set(ScDevCandidateDO::getLastAction, req.getContent().trim())
                    .set(ScDevCandidateDO::getLastActionAt, LocalDateTime.now())
                    .set(ScDevCandidateDO::getLastActionBy, me)
                    .set(ScDevCandidateDO::getUpdateTime, LocalDateTime.now());
            if (to == SupplyConstants.CAND_ABANDONED && c.getStage() != SupplyConstants.CAND_ABANDONED) {
                u.set(ScDevCandidateDO::getAbandonReason, reason);
            }
            if (to == SupplyConstants.CAND_QUOTED && c.getStage() != SupplyConstants.CAND_QUOTED) {
                u.set(ScDevCandidateDO::getQuotedAt, today);
            }
            if (req.getOwnerId() != null) {
                u.set(ScDevCandidateDO::getOwnerId, req.getOwnerId());
            } else if (c.getOwnerId() == null || c.getOwnerId() == 0) {
                u.set(ScDevCandidateDO::getOwnerId, me);
            }
            candidateMapper.update(null, u);
        }
        qualifier.refresh(today, List.of(p.getId()));
        r.setLogId(logId);
        r.setChanged(changes.size());
        r.setSkipped(skipped);
        return r;
    }

    /** 记处理后的阶段：不改时保持；已合格的只能改为已放弃 */
    static int targetStage(int current, Integer requested) {
        if (requested == null) {
            return current;
        }
        if (current == SupplyConstants.CAND_QUALIFIED && requested != SupplyConstants.CAND_ABANDONED) {
            return current;
        }
        return requested;
    }

    static boolean closedStage(int stage) {
        return stage == SupplyConstants.CAND_QUALIFIED || stage == SupplyConstants.CAND_ABANDONED;
    }

    // ---------------------------------------------------------------- 专项详情

    /** 专项详情的开发供应商区；不带开发目标、也没有候选时返回 null */
    public DevVOs.ProjectDev projectDev(Long projectId) {
        ScGapProjectDO p = projectMapper.selectById(projectId);
        if (p == null || p.getDeletedAt() != null) {
            return null;
        }
        List<ScDevCandidateDO> cands = candidatesOf(List.of(projectId));
        if (p.getSupplierTarget() == null && cands.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        DevPool.Snapshot snap = pool.load(today);
        Map<Long, DevPool.Evidence> q = snap.qualified(p.getBrandId(), p.getCategoryId());
        DevPool.Range r = snap.listed(p.getBrandId(), p.getCategoryId());
        int required = r == null ? SupplyConstants.requiredSuppliers(SupplyConstants.HEAT_LOW) : r.required();
        DevVOs.ProjectDev d = new DevVOs.ProjectDev();
        d.setTarget(p.getSupplierTarget() == null ? 0 : p.getSupplierTarget());
        d.setQualified(qualifiedCount(cands));
        d.setRangeQualified(q.size());
        d.setRequired(required);
        d.setRangeGap(Math.max(0, required - q.size()));
        d.setSuggestClose(p.getStatus() == SupplyConstants.PROJECT_ACTIVE && p.getSupplierTarget() != null
                && (d.getQualified() >= d.getTarget() || d.getRangeGap() == 0));
        d.setCandidates(candidateVOs(cands, today));
        Set<Long> mine = cands.stream().map(ScDevCandidateDO::getSupplierId).filter(i -> i != null && i > 0).collect(Collectors.toSet());
        Map<Long, DevPool.Evidence> others = new LinkedHashMap<>();
        q.entrySet().stream().filter(e -> !mine.contains(e.getKey()))
                .sorted(Map.Entry.comparingByValue((a, b) -> b.date().compareTo(a.date())))
                .forEach(e -> others.put(e.getKey(), e.getValue()));
        d.setOthers(suppliers(others, List.of()));
        d.setRecords(records(projectId));
        d.setCanEdit(perm.has(SupplyConstants.PERM_DEV_EDIT));
        d.setCanSeeSupplier(perm.has(SupplyConstants.PERM_SUPPLIER_VIEW));
        return d;
    }

    // ---------------------------------------------------------------- 今日待办

    /**
     * 当前用户的开发待办：候选下一步到期或逾期、开发任务超期，以及按需算出的提醒
     * （候选全部放弃、已报价超过 7 天未关联供应商库、自己负责的高热度主动拓展有缺口还没有任务）。提醒不写库，不覆盖人定的下一步。
     */
    public DevVOs.Today today() {
        LocalDate today = LocalDate.now();
        Long me = me();
        List<Row> rows = rows(today);
        Map<Long, String> brands = lookups.brandNames(rows.stream().map(r -> r.range().brandId()).toList());
        Map<Long, String> cats = lookups.categoryNames(rows.stream().map(r -> r.range().categoryId()).toList());
        List<DevVOs.TodayItem> items = new ArrayList<>();
        Set<Long> seenProjects = new HashSet<>();
        for (Row r : rows) {
            ScGapProjectDO p = r.project();
            if (r.devTask()) {
                seenProjects.add(p.getId());
                items.addAll(projectItems(p, r.candidates(), me, today));
            } else if (highGapReminder(r, me)) {
                items.add(item("remind", r, null, "高热度范围缺 " + r.range().gap() + " 家供应商，还没有开发任务", today, today));
            }
        }
        // 范围不在清单里（需求对象被暂停、主动拓展被移出）的开发任务也要提醒
        List<ScGapProjectDO> rest = devProjects().stream().filter(p -> !seenProjects.contains(p.getId())).toList();
        Map<Long, List<ScDevCandidateDO>> restCands = candidatesOf(rest.stream().map(ScGapProjectDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ScDevCandidateDO::getProjectId));
        for (ScGapProjectDO p : rest) {
            items.addAll(projectItems(p, restCands.getOrDefault(p.getId(), List.of()), me, today));
        }
        Map<Long, String> moreBrands = lookups.brandNames(items.stream().map(DevVOs.TodayItem::getBrandId).filter(Objects::nonNull).toList());
        Map<Long, String> moreCats = lookups.categoryNames(items.stream().map(DevVOs.TodayItem::getCategoryId).filter(Objects::nonNull).toList());
        for (DevVOs.TodayItem i : items) {
            i.setBrandName(brands.getOrDefault(i.getBrandId(), moreBrands.get(i.getBrandId())));
            i.setCategoryName(i.getCategoryId() == null || i.getCategoryId() == 0 ? "未分类"
                    : cats.getOrDefault(i.getCategoryId(), moreCats.get(i.getCategoryId())));
        }
        if (!perm.has(SupplyConstants.PERM_SUPPLIER_VIEW)) {
            items.forEach(i -> i.setCandidateName(null));
        }
        items.sort(Comparator.comparing(DevVOs.TodayItem::getOverdueDays).reversed().thenComparing(DevVOs.TodayItem::getKey));
        DevVOs.Today t = new DevVOs.Today();
        t.setItems(items);
        t.setOverdue((int) items.stream().filter(DevVOs.TodayItem::isOverdue).count());
        t.setDueToday(items.size() - t.getOverdue());
        return t;
    }

    /**
     * 高热度缺口还没有任务：只提醒主动拓展的负责人。需求推出的缺口范围很多且长期存在，逐个提醒会让每个人每天都有一堆待办，
     * 这类范围靠板块排序（热度高、缺口大在前）和「待开发」计数提示。
     */
    static boolean highGapReminder(Row r, Long me) {
        ScExpansionScopeDO e = r.range().expansion();
        return r.project() == null && r.range().heat() == SupplyConstants.HEAT_HIGH && r.range().gap() > 0
                && e != null && me.equals(e.getOwnerId());
    }

    private List<DevVOs.TodayItem> projectItems(ScGapProjectDO p, List<ScDevCandidateDO> cands, Long me, LocalDate today) {
        List<DevVOs.TodayItem> out = new ArrayList<>();
        Row r = new Row(new DevPool.Range(p.getBrandId(), p.getCategoryId()), p, cands);
        boolean ownsProject = me.equals(p.getOwnerId());
        for (ScDevCandidateDO c : cands) {
            boolean mine = me.equals(c.getOwnerId()) || (c.getOwnerId() == null || c.getOwnerId() == 0) && ownsProject;
            if (!mine || closedStage(c.getStage())) {
                continue;
            }
            if (c.getNextActionDate() != null && !c.getNextActionDate().isAfter(today)) {
                DevVOs.TodayItem i = item("candidate", r, c, c.getNextAction(), c.getNextActionDate(), today);
                out.add(i);
            } else if (linkReminder(c, today)) {
                out.add(item("remind", r, c, "已报价超过 " + SupplyConstants.DEV_UNLINKED_REMIND_DAYS
                        + " 天还没关联供应商库，关联后才能自动标为合格", today, today));
            }
        }
        if (!ownsProject) {
            return out;
        }
        if (p.getDueDate() != null && p.getDueDate().isBefore(today)) {
            out.add(item("project", r, null, "开发任务「" + p.getName() + "」已超期", p.getDueDate(), today));
        }
        if (!cands.isEmpty() && cands.stream().allMatch(c -> c.getStage() == SupplyConstants.CAND_ABANDONED)
                && qualifiedCount(cands) < p.getSupplierTarget()) {
            out.add(item("remind", r, null, "候选已全部放弃，补充候选供应商", today, today));
        }
        return out;
    }

    private static DevVOs.TodayItem item(String kind, Row r, ScDevCandidateDO c, String text, LocalDate date, LocalDate today) {
        DevVOs.TodayItem i = new DevVOs.TodayItem();
        i.setKind(kind);
        i.setKey(kind + ":" + r.range().key() + ":" + (c != null ? c.getId() : r.project() != null ? r.project().getId() : 0));
        i.setBrandId(r.range().brandId());
        i.setCategoryId(r.range().categoryId());
        if (r.project() != null) {
            i.setProjectId(r.project().getId());
            i.setProjectName(r.project().getName());
        }
        if (c != null) {
            i.setCandidateId(c.getId());
            i.setCandidateName(c.getName());
        }
        i.setText(text);
        i.setDate(date);
        i.setOverdue(date.isBefore(today));
        i.setOverdueDays(date.isBefore(today) ? (int) ChronoUnit.DAYS.between(date, today) : 0);
        return i;
    }

    static boolean linkReminder(ScDevCandidateDO c, LocalDate today) {
        return c.getStage() == SupplyConstants.CAND_QUOTED && (c.getSupplierId() == null || c.getSupplierId() == 0)
                && c.getQuotedAt() != null
                && !c.getQuotedAt().isAfter(today.minusDays(SupplyConstants.DEV_UNLINKED_REMIND_DAYS));
    }

    // ---------------------------------------------------------------- 覆盖矩阵「按供应商」

    public MatrixVO matrix(Long parentCategoryId, Long brandId, Integer heat, Boolean gapsOnly) {
        LocalDate today = LocalDate.now();
        Map<Long, Long> parents = lookups.categoryParents();
        List<Row> rows = rows(today).stream()
                .filter(r -> (brandId == null || brandId == r.range().brandId())
                        && (heat == null || heat == r.range().heat())
                        && (parentCategoryId == null || parentCategoryId.equals(parents.get(r.range().categoryId())))
                        && (!Boolean.TRUE.equals(gapsOnly) || r.range().gap() > 0))
                .toList();
        List<MatrixVO.Cell> cells = new ArrayList<>(rows.size());
        for (Row r : rows) {
            MatrixVO.Cell c = new MatrixVO.Cell();
            c.setBrandId(r.range().brandId());
            c.setCategoryId(r.range().categoryId());
            c.setQualified(r.range().qualified().size());
            c.setRequired(r.range().required());
            c.setGap(r.range().gap());
            c.setHeat(r.range().heat());
            c.setLeads(r.range().leads().size());
            if (r.project() != null) {
                c.setProjectId(r.project().getId());
                c.setProjectName(r.project().getName());
                if (r.devTask()) {
                    c.setDevProjectId(r.project().getId());
                }
            }
            cells.add(c);
        }
        Map<Long, String> brands = lookups.brandNames(cells.stream().map(MatrixVO.Cell::getBrandId).toList());
        Map<Long, String> cats = lookups.categoryNames(cells.stream().map(MatrixVO.Cell::getCategoryId).toList());
        MatrixVO v = new MatrixVO();
        v.setMode("supplier");
        v.setDays(SupplyConstants.DEV_WINDOW_DAYS);
        v.setCells(cells);
        v.setBrands(axis(cells, true, brands));
        v.setCategories(axis(cells, false, cats));
        return v;
    }

    private static List<MatrixVO.Axis> axis(List<MatrixVO.Cell> cells, boolean brand, Map<Long, String> names) {
        Map<Long, MatrixVO.Axis> out = new LinkedHashMap<>();
        for (MatrixVO.Cell c : cells) {
            Long id = brand ? c.getBrandId() : c.getCategoryId();
            MatrixVO.Axis a = out.computeIfAbsent(id, k -> {
                MatrixVO.Axis n = new MatrixVO.Axis();
                n.setId(k);
                n.setName(names.getOrDefault(k, brand ? "" : "未分类"));
                return n;
            });
            a.setTotal(a.getTotal() + c.getGap());
        }
        return out.values().stream().sorted(Comparator.comparing(MatrixVO.Axis::getTotal).reversed()
                .thenComparing(MatrixVO.Axis::getName)).toList();
    }

    // ---------------------------------------------------------------- 内部

    /** 清单里的全部范围，连同进行中的专项和它的候选 */
    List<Row> rows(LocalDate today) {
        DevPool.Snapshot snap = pool.load(today);
        Map<String, ScGapProjectDO> projects = new HashMap<>();
        projectMapper.selectList(new LambdaQueryWrapper<ScGapProjectDO>()
                        .eq(ScGapProjectDO::getStatus, SupplyConstants.PROJECT_ACTIVE)
                        .isNull(ScGapProjectDO::getDeletedAt))
                .forEach(p -> projects.put(DevPool.Range.key(p.getBrandId(), p.getCategoryId() == null ? 0 : p.getCategoryId()), p));
        List<Long> devIds = projects.values().stream().filter(p -> p.getSupplierTarget() != null).map(ScGapProjectDO::getId).toList();
        Map<Long, List<ScDevCandidateDO>> cands = candidatesOf(devIds).stream()
                .collect(Collectors.groupingBy(ScDevCandidateDO::getProjectId));
        List<Row> out = new ArrayList<>();
        for (DevPool.Range r : snap.listed()) {
            ScGapProjectDO p = projects.get(r.key());
            out.add(new Row(r, p, p == null ? List.of() : cands.getOrDefault(p.getId(), List.of())));
        }
        return out;
    }

    private List<DevVOs.Group> groupVOs(List<Row> slice, Map<Long, String> brands, Map<Long, String> cats, LocalDate today) {
        Map<Long, Integer> stockout = boardService.stockouts(STOCKOUT_DAYS);
        boolean names = perm.has(SupplyConstants.PERM_SUPPLIER_VIEW);
        Set<Long> supplierIds = new HashSet<>();
        if (names) {
            slice.forEach(r -> {
                supplierIds.addAll(r.range().qualified().keySet());
                supplierIds.addAll(r.range().leads());
            });
        }
        Map<Long, String> supplierNames = supplierNames(supplierIds);
        Map<Long, String> owners = inquiryLookups.userNames(slice.stream().map(Row::project).filter(Objects::nonNull)
                .map(ScGapProjectDO::getOwnerId).toList());
        List<DevVOs.Group> out = new ArrayList<>(slice.size());
        for (Row r : slice) {
            DevPool.Range range = r.range();
            DevVOs.Group g = new DevVOs.Group();
            g.setKey(range.key());
            g.setBrandId(range.brandId());
            g.setBrandName(brands.get(range.brandId()));
            g.setCategoryId(range.categoryId());
            g.setCategoryName(range.categoryId() == 0 ? "未分类" : cats.get(range.categoryId()));
            g.setHeat(range.heat());
            g.setSource(range.source());
            if (range.expansion() != null) {
                g.setExpansionId(range.expansion().getId());
                g.setExpansionHeat(range.expansion().getHeat());
                g.setExpansionReason(range.expansion().getReason());
            }
            g.setRequired(range.required());
            g.setQualified(range.qualified().size());
            g.setGap(range.gap());
            g.setStage(r.stage());
            g.setLeads(range.leads().size());
            g.setStockoutModels((int) range.demandIds().stream().filter(stockout::containsKey).count());
            g.setStockoutTimes(range.demandIds().stream().mapToInt(id -> stockout.getOrDefault(id, 0)).sum());
            ScGapProjectDO p = r.project();
            if (p != null) {
                g.setProjectId(p.getId());
                g.setProjectName(p.getName());
                g.setProjectTarget(p.getSupplierTarget());
                g.setProjectQualified(qualifiedCount(r.candidates()));
                g.setProjectOwnerId(p.getOwnerId());
                g.setProjectOwnerName(owners.get(p.getOwnerId()));
                g.setProjectDueDate(p.getDueDate());
                if (p.getDueDate() != null && p.getDueDate().isBefore(today)) {
                    g.setProjectOverdueDays((int) ChronoUnit.DAYS.between(p.getDueDate(), today));
                }
            }
            g.setActiveCandidates((int) r.candidates().stream().filter(c -> !closedStage(c.getStage())).count());
            r.candidates().stream().filter(c -> !closedStage(c.getStage()) && c.getNextActionDate() != null)
                    .min(Comparator.comparing(ScDevCandidateDO::getNextActionDate)).ifPresent(c -> {
                        g.setUrgentAction(c.getNextAction());
                        g.setUrgentDate(c.getNextActionDate());
                    });
            g.setOverdue(overdue(r, today));
            g.setQualifiedSuppliers(names ? suppliers(range.qualified(), List.of(), supplierNames) : List.of());
            g.setLeadSuppliers(names ? suppliers(Map.of(), range.leads(), supplierNames) : List.of());
            g.setCandidates(candidateVOs(r.candidates(), today));
            out.add(g);
        }
        return out;
    }

    private List<DevVOs.Supplier> suppliers(Map<Long, DevPool.Evidence> qualified, Collection<Long> leads) {
        if (!perm.has(SupplyConstants.PERM_SUPPLIER_VIEW)) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>(qualified.keySet());
        ids.addAll(leads);
        return suppliers(qualified, leads, supplierNames(ids));
    }

    private static List<DevVOs.Supplier> suppliers(Map<Long, DevPool.Evidence> qualified, Collection<Long> leads,
                                                   Map<Long, String> names) {
        List<DevVOs.Supplier> out = new ArrayList<>(qualified.size() + leads.size());
        qualified.forEach((id, e) -> {
            DevVOs.Supplier s = new DevVOs.Supplier();
            s.setId(id);
            s.setName(names.get(id));
            s.setEvidenceType(e.type());
            s.setEvidenceDate(e.date());
            out.add(s);
        });
        for (Long id : leads) {
            DevVOs.Supplier s = new DevVOs.Supplier();
            s.setId(id);
            s.setName(names.get(id));
            out.add(s);
        }
        return out;
    }

    private Map<Long, String> supplierNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        for (List<Long> part : DemandAssembler.chunks(new ArrayList<>(ids))) {
            supplierMapper.selectBatchIds(part).forEach(s -> out.put(s.getId(), DemandAssembler.supplierName(s)));
        }
        return out;
    }

    private List<DevVOs.Candidate> candidateVOs(List<ScDevCandidateDO> rows, LocalDate today) {
        boolean names = perm.has(SupplyConstants.PERM_SUPPLIER_VIEW);
        Set<Long> users = new HashSet<>();
        rows.forEach(c -> {
            users.add(c.getOwnerId());
            users.add(c.getLastActionBy());
        });
        Map<Long, String> userNames = inquiryLookups.userNames(users.stream().filter(u -> u != null && u > 0).toList());
        List<DevVOs.Candidate> out = new ArrayList<>(rows.size());
        rows.stream().sorted(Comparator.comparing((ScDevCandidateDO c) -> closedStage(c.getStage()))
                        .thenComparing(c -> c.getNextActionDate() == null ? LocalDate.MAX : c.getNextActionDate())
                        .thenComparing(ScDevCandidateDO::getId))
                .forEach(c -> {
                    DevVOs.Candidate v = new DevVOs.Candidate();
                    org.springframework.beans.BeanUtils.copyProperties(c, v);
                    if (!names) {
                        v.setName(null);
                        v.setContactName(null);
                        v.setContactInfo(null);
                    }
                    v.setOwnerName(userNames.get(c.getOwnerId()));
                    v.setLastActionByName(c.getLastActionBy() == null || c.getLastActionBy() == 0 ? "系统" : userNames.get(c.getLastActionBy()));
                    if (!closedStage(c.getStage()) && c.getNextActionDate() != null && c.getNextActionDate().isBefore(today)) {
                        v.setOverdueDays((int) ChronoUnit.DAYS.between(c.getNextActionDate(), today));
                    }
                    v.setLinkReminder(linkReminder(c, today));
                    out.add(v);
                });
        return out;
    }

    /** 开发任务的处理记录（对象类型为候选），新的在前 */
    private List<BoardVOs.TimelineItem> records(Long projectId) {
        List<ScFollowupLogDO> rows = logMapper.selectList(new LambdaQueryWrapper<ScFollowupLogDO>()
                .eq(ScFollowupLogDO::getGapProjectId, projectId)
                .eq(ScFollowupLogDO::getObjectType, SupplyConstants.OBJ_CANDIDATE)
                .isNull(ScFollowupLogDO::getDeletedAt)
                .orderByDesc(ScFollowupLogDO::getId)
                .last("LIMIT " + TIMELINE_LIMIT));
        Map<Long, String> users = inquiryLookups.userNames(rows.stream().map(ScFollowupLogDO::getOperatorId)
                .filter(o -> o != null && o > 0).collect(Collectors.toSet()));
        boolean names = perm.has(SupplyConstants.PERM_SUPPLIER_VIEW);
        List<BoardVOs.TimelineItem> out = new ArrayList<>(rows.size());
        for (ScFollowupLogDO l : rows) {
            BoardVOs.TimelineItem t = new BoardVOs.TimelineItem();
            t.setKind("log");
            t.setTime(l.getCreateTime());
            t.setLogId(l.getId());
            t.setSystem(l.getRecordType() == SupplyConstants.LOG_SYSTEM);
            t.setOperatorName(t.getSystem() ? "系统" : users.get(l.getOperatorId()));
            t.setContent(names ? l.getContent() : t.getSystem() ? "候选阶段由系统更新" : l.getContent());
            t.setNextAction(l.getNextAction());
            t.setNextActionDate(l.getNextActionDate());
            t.setStatusChange(stageText(l.getStatusChanges(), names));
            out.add(t);
        }
        return out;
    }

    private String stageText(String json, boolean names) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
            return list.stream().map(m -> (names && m.get("name") != null ? "「" + m.get("name") + "」" : "")
                            + STAGE_NAMES.get(((Number) m.get("from")).intValue()) + " → " + STAGE_NAMES.get(((Number) m.get("to")).intValue()))
                    .collect(Collectors.joining("；"));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 写一条候选的处理记录；幂等键重复时返回 null */
    private Long writeLog(Long projectId, List<Long> candidateIds, String content, List<Map<String, Object>> changes,
                          String next, LocalDate nextDate, Long me, String idempotencyKey) {
        ScFollowupLogDO l = new ScFollowupLogDO();
        l.setTenantId(PiStore.tenantId());
        l.setRecordType(SupplyConstants.LOG_MANUAL);
        l.setSourceType(SupplyConstants.LOG_SRC_MANUAL);
        l.setObjectType(SupplyConstants.OBJ_CANDIDATE);
        l.setContent(content);
        l.setCandidateIds(json(candidateIds));
        l.setStatusChanges(changes == null ? null : json(changes));
        l.setGapProjectId(projectId);
        l.setNextAction(next);
        l.setNextActionDate(next.isEmpty() ? null : nextDate);
        l.setOperatorId(me);
        l.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
        try {
            logMapper.insert(l);
        } catch (DuplicateKeyException e) {
            return null;
        }
        return l.getId();
    }

    private static int overdue(Row r, LocalDate today) {
        int n = (int) r.candidates().stream().filter(c -> !closedStage(c.getStage()) && c.getNextActionDate() != null
                && c.getNextActionDate().isBefore(today)).count();
        ScGapProjectDO p = r.project();
        return n + (r.devTask() && p.getDueDate() != null && p.getDueDate().isBefore(today) ? 1 : 0);
    }

    private static int qualifiedCount(List<ScDevCandidateDO> cands) {
        return (int) cands.stream().filter(c -> c.getStage() == SupplyConstants.CAND_QUALIFIED && c.getSupplierId() > 0).count();
    }

    private List<ScDevCandidateDO> candidatesOf(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return candidateMapper.selectList(new LambdaQueryWrapper<ScDevCandidateDO>()
                .in(ScDevCandidateDO::getProjectId, projectIds)
                .isNull(ScDevCandidateDO::getDeletedAt));
    }

    private List<ScGapProjectDO> devProjects() {
        return projectMapper.selectList(new LambdaQueryWrapper<ScGapProjectDO>()
                .eq(ScGapProjectDO::getStatus, SupplyConstants.PROJECT_ACTIVE)
                .isNotNull(ScGapProjectDO::getSupplierTarget)
                .isNull(ScGapProjectDO::getDeletedAt));
    }

    private ScGapProjectDO devProject(Long id) {
        ScGapProjectDO p = projectMapper.selectById(id);
        if (p == null || p.getDeletedAt() != null) {
            throw new BizException("专项不存在");
        }
        if (p.getStatus() != SupplyConstants.PROJECT_ACTIVE) {
            throw new BizException("专项已结束，只能查看");
        }
        if (p.getSupplierTarget() == null) {
            throw new BizException("这个专项没有开发供应商目标，请先加上目标");
        }
        return p;
    }

    private ScDevCandidateDO candidate(Long id) {
        ScDevCandidateDO c = candidateMapper.selectById(id);
        if (c == null || c.getDeletedAt() != null) {
            throw new BizException("候选不存在");
        }
        return c;
    }

    private ScExpansionScopeDO expansion(Long id) {
        ScExpansionScopeDO e = expansionMapper.selectById(id);
        if (e == null || e.getDeletedAt() != null) {
            throw new BizException("主动拓展记录不存在或已移出");
        }
        return e;
    }

    private SupplierDO supplier(Long id) {
        if (!perm.has(SupplyConstants.PERM_SUPPLIER_VIEW)) {
            throw new BizException("没有「查看货源信息」权限，不能选择供应商库里的供应商");
        }
        SupplierDO s = supplierMapper.selectById(id);
        if (s == null || s.getDeletedAt() != null) {
            throw new BizException("供应商不存在");
        }
        return s;
    }

    private Long owner(Long ownerId) {
        return ownerId == null ? me() : ownerId;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new BizException("处理记录序列化失败", e);
        }
    }

    private Long me() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }
}
