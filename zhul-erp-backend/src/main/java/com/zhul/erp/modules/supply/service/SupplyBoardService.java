package com.zhul.erp.modules.supply.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.crm.followup.entity.SopStageDO;
import com.zhul.erp.modules.crm.followup.repository.SopStageMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.BoardVOs;
import com.zhul.erp.modules.supply.dto.DemandVO;
import com.zhul.erp.modules.supply.dto.SupplyRequests;
import com.zhul.erp.modules.supply.entity.ScCoverageChannelDO;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScDemandSignalDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.repository.ScCoverageChannelMapper;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScDemandSignalMapper;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.support.DemandAssembler;
import com.zhul.erp.modules.supply.support.DemandRecalc;
import com.zhul.erp.modules.supply.support.DemandRules;
import com.zhul.erp.modules.supply.support.SupplyLookups;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 供应链跟进页（spec supply-chain/followup；tasks 4.3、4.4）：卡片与阶段计数、按品牌 · 品类分组的列表、今日待办、延后、记一次处理、作战卡。
 * 数据只按租户隔离；「负责人」筛选默认当前用户（ownerId 为空），传 0 为全部。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyBoardService {

    /** 阶段 → 作战地图节点 */
    public static final Map<String, List<String>> STAGE_NODES = Map.of(
            "unclassified", List.of("C4"), "watching", List.of("C5"), "review", List.of("C5"), "paused", List.of("C5"),
            "1", List.of("V1"), "2", List.of("V3", "V4"), "3", List.of("Q1", "Q2", "Q3"), "4", List.of("M4"));

    private static final int STOCKOUT_DAYS = 30;

    private final ScDemandMapper demandMapper;
    private final ScDemandSignalMapper signalMapper;
    private final ScFollowupLogMapper logMapper;
    private final ScCoverageChannelMapper channelMapper;
    private final SopStageMapper sopStageMapper;
    private final DemandAssembler assembler;
    private final DemandRecalc recalc;
    private final CoverageService coverageService;
    private final SupplyLookups lookups;
    private final CurrentUserResolver currentUser;
    private final PermissionChecker perm;
    private final ObjectMapper objectMapper;

    /** 列表筛选 */
    public record Filter(String board, String stage, Boolean stockout, Integer stockoutDays, Boolean pending, Boolean single,
                         String keyword, Long brandId, Long categoryId, Integer priority, Long ownerId) {
    }

    public BoardVOs.Overview overview(Long ownerId) {
        Long owner = owner(ownerId);
        List<ScDemandDO> all = demands(owner);
        LocalDate today = LocalDate.now();
        Map<Long, Integer> stockout = stockouts(STOCKOUT_DAYS);
        BoardVOs.Overview o = new BoardVOs.Overview();
        Map<Integer, Integer> cov = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) {
            cov.put(i, 0);
        }
        Set<Long> singles = singles(all);
        for (ScDemandDO d : all) {
            int st = d.getStatus();
            if (st == SupplyConstants.DEMAND_PAUSED) {
                o.setPaused(o.getPaused() + 1);
                continue;
            }
            if (isReview(d)) {
                o.setReview(o.getReview() + 1);
            }
            if (st == SupplyConstants.DEMAND_WATCHING) {
                o.setWatching(o.getWatching() + 1);
            } else {
                o.setCoverTotal(o.getCoverTotal() + 1);
                cov.merge(d.getCoverageStatus(), 1, Integer::sum);
                if (stockout.containsKey(d.getId()) && d.getCoverageStatus() != SupplyConstants.COVER_READY) {
                    o.setStockoutOpen(o.getStockoutOpen() + 1);
                }
                if (d.getCoveragePending() == 1) {
                    o.setPending(o.getPending() + 1);
                }
                if (singles.contains(d.getId())) {
                    o.setSingle(o.getSingle() + 1);
                }
            }
        }
        o.setIntelTotal(o.getWatching() + o.getCoverTotal());
        o.setCoverage(cov);
        o.setUnclassified(Math.toIntExact(signalMapper.selectCount(new LambdaQueryWrapper<ScDemandSignalDO>()
                .eq(ScDemandSignalDO::getDemandId, 0L).eq(ScDemandSignalDO::getSourceCancelled, 0)
                .isNull(ScDemandSignalDO::getDeletedAt))));
        Long me = me();
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        for (ScDemandDO d : all) {
            if (!me.equals(d.getOwnerId()) || d.getStatus() == SupplyConstants.DEMAND_PAUSED || d.getNextActionDate() == null) {
                continue;
            }
            if (d.getNextActionDate().isBefore(today)) {
                o.setOverdue(o.getOverdue() + 1);
            } else if (d.getNextActionDate().isEqual(today)) {
                o.setDueToday(o.getDueToday() + 1);
            } else if (!d.getNextActionDate().isAfter(weekEnd)) {
                o.setWeekRest(o.getWeekRest() + 1);
            }
        }
        o.setTodayModels(o.getOverdue() + o.getDueToday());
        o.setCanEdit(perm.has(SupplyConstants.PERM_FOLLOWUP_EDIT));
        o.setCanTeam(perm.has(SupplyConstants.PERM_TEAM));
        o.setCanProject(perm.has(SupplyConstants.PERM_PROJECT_EDIT));
        o.setCanCoverage(perm.has(SupplyConstants.PERM_COVERAGE_EDIT));
        o.setCanSeeSupplier(perm.has(SupplyConstants.PERM_SUPPLIER_VIEW));
        o.setEvidenceDays(lookups.evidenceDays());
        return o;
    }

    /** 分组列表：组按「逾期在前，其次无货次数多、优先级高」排序，分批加载 */
    public BoardVOs.GroupPage groups(Filter f, int page, int size) {
        Long owner = owner(f.ownerId());
        int days = f.stockoutDays() != null && f.stockoutDays() == 90 ? 90 : STOCKOUT_DAYS;
        Map<Long, Integer> stockout = stockouts(days);
        List<ScDemandDO> rows = demands(owner).stream().filter(match(f, stockout)).toList();
        if (Boolean.TRUE.equals(f.single())) {
            Set<Long> singles = singles(rows);
            rows = rows.stream().filter(d -> singles.contains(d.getId())).toList();
        }
        LocalDate today = LocalDate.now();
        Map<String, List<ScDemandDO>> byKey = rows.stream().collect(Collectors.groupingBy(
                d -> d.getBrandId() + ":" + (d.getCategoryId() == null ? 0 : d.getCategoryId()), LinkedHashMap::new, Collectors.toList()));
        List<Map.Entry<String, List<ScDemandDO>>> ordered = new ArrayList<>(byKey.entrySet());
        ordered.sort(Comparator
                .comparing((Map.Entry<String, List<ScDemandDO>> e) -> -overdue(e.getValue(), today))
                .thenComparing(e -> -e.getValue().stream().mapToInt(d -> stockout.getOrDefault(d.getId(), 0)).sum())
                .thenComparing(e -> -e.getValue().stream().mapToInt(ScDemandDO::getPriority).max().orElse(0))
                .thenComparing(e -> -e.getValue().size())
                .thenComparing(Map.Entry::getKey));
        int from = Math.max(0, (page - 1) * size);
        List<Map.Entry<String, List<ScDemandDO>>> slice = ordered.subList(Math.min(from, ordered.size()),
                Math.min(ordered.size(), from + size));
        List<ScDemandDO> pageRows = slice.stream().flatMap(e -> e.getValue().stream()).toList();
        Map<Long, DemandVO> vos = new HashMap<>();
        assembler.toVOs(pageRows).forEach(v -> vos.put(v.getId(), v));
        Map<Long, int[]> projects = projectProgress(vos.values());
        List<BoardVOs.Group> groups = new ArrayList<>(slice.size());
        for (Map.Entry<String, List<ScDemandDO>> e : slice) {
            groups.add(group(e.getKey(), e.getValue(), vos, stockout, projects, today));
        }
        BoardVOs.GroupPage out = new BoardVOs.GroupPage();
        out.setTotalGroups(ordered.size());
        out.setTotalModels(rows.size());
        out.setGroups(groups);
        return out;
    }

    /** 今日待办：当前用户负责、下一步日期已到或已过的需求对象，按品牌 · 品类成卡片 */
    public BoardVOs.Today today() {
        Long me = me();
        LocalDate today = LocalDate.now();
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<ScDemandDO> mine = demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .eq(ScDemandDO::getOwnerId, me).ne(ScDemandDO::getStatus, SupplyConstants.DEMAND_PAUSED)
                .isNotNull(ScDemandDO::getNextActionDate).le(ScDemandDO::getNextActionDate, weekEnd)
                .isNull(ScDemandDO::getDeletedAt));
        List<ScDemandDO> due = mine.stream().filter(d -> !d.getNextActionDate().isAfter(today)).toList();
        Map<Long, DemandVO> vos = new HashMap<>();
        assembler.toVOs(due).forEach(v -> vos.put(v.getId(), v));
        Map<String, BoardVOs.Card> cards = cards();
        BoardVOs.Today t = new BoardVOs.Today();
        t.setOverdueCards(new ArrayList<>());
        t.setTodayCards(new ArrayList<>());
        Map<String, List<ScDemandDO>> byKey = due.stream().collect(Collectors.groupingBy(
                d -> d.getBrandId() + ":" + d.getCategoryId() + ":" + (d.getNextActionDate().isBefore(today) ? "o" : "t"),
                LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ScDemandDO>> e : byKey.entrySet()) {
            List<ScDemandDO> list = new ArrayList<>(e.getValue());
            list.sort(Comparator.comparing(ScDemandDO::getNextActionDate).thenComparing(d -> -d.getPriority()));
            ScDemandDO top = list.get(0);
            DemandVO tv = vos.get(top.getId());
            BoardVOs.TodayCard c = new BoardVOs.TodayCard();
            c.setKey(e.getKey());
            c.setBrandId(top.getBrandId());
            c.setCategoryId(top.getCategoryId());
            c.setBrandName(tv.getBrandName());
            c.setCategoryName(tv.getCategoryName());
            boolean inMatrix = top.getStatus() == SupplyConstants.DEMAND_IN_MATRIX;
            c.setBoard(inMatrix ? "cover" : "intel");
            c.setStage(inMatrix ? String.valueOf(top.getCoverageStatus()) : isReview(top) ? "review" : "watching");
            c.setOverdue(top.getNextActionDate().isBefore(today));
            c.setOverdueDays((int) ChronoUnit.DAYS.between(top.getNextActionDate(), today));
            c.setSource((top.getNextActionSource() == SupplyConstants.NEXT_SYSTEM ? "系统提醒：" : "我定的下一步：")
                    + top.getNextAction());
            BoardVOs.Card card = cards.get(STAGE_NODES.getOrDefault(c.getStage(), List.of("C5")).get(0));
            c.setSopHint(card == null || card.getChecklist().isEmpty() ? null : card.getChecklist().get(0));
            c.setItems(list.stream().map(d -> vos.get(d.getId())).toList());
            c.setProjectId(tv.getActiveProjectId());
            (c.isOverdue() ? t.getOverdueCards() : t.getTodayCards()).add(c);
        }
        t.getOverdueCards().sort(Comparator.comparing(BoardVOs.TodayCard::getOverdueDays).reversed());
        t.setOverdue((int) due.stream().filter(d -> d.getNextActionDate().isBefore(today)).count());
        t.setDueToday(due.size() - t.getOverdue());
        t.setWeekRest(mine.size() - due.size());
        return t;
    }

    @Transactional
    public int defer(SupplyRequests.Defer req) {
        if (!req.getDate().isAfter(LocalDate.now())) {
            throw new BizException("延后日期须晚于今天");
        }
        int n = 0;
        for (ScDemandDO d : demandMapper.selectBatchIds(req.getDemandIds())) {
            if (d.getDeletedAt() != null) {
                continue;
            }
            demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, d.getId())
                    .set(ScDemandDO::getNextActionDate, req.getDate())
                    .set(ScDemandDO::getDeferCount, (d.getDeferCount() == null ? 0 : d.getDeferCount()) + 1)
                    .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
            n++;
        }
        return n;
    }

    /**
     * 记一次处理（design 决策 15）：一个事务内 写记录 → 改所选型号的覆盖状态（事务内重新校验证据）→ 更新负责人与下一步 → 写操作日志。
     */
    @Transactional
    public BoardVOs.RecordResult record(SupplyRequests.Record req) {
        Long me = me();
        LocalDate today = LocalDate.now();
        List<ScDemandDO> rows = demandMapper.selectBatchIds(req.getDemandIds()).stream()
                .filter(d -> d.getDeletedAt() == null).toList();
        if (rows.isEmpty()) {
            throw new BizException("至少选一个型号");
        }
        String next = req.getNextAction() == null ? "" : req.getNextAction().trim();
        if (req.getNextActionDate() != null && req.getNextActionDate().isBefore(today)) {
            throw new BizException("下一步日期不能早于今天");
        }
        List<Long> skipped = new ArrayList<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        int changed = 0;
        for (ScDemandDO d : rows) {
            int from = d.getCoverageStatus();
            if (req.getCoverage() != null && d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX) {
                if (!coverageService.change(d, req.getCoverage(), null, null)) {
                    skipped.add(d.getId());
                    changes.add(Map.of("demandId", d.getId(), "from", from, "to", from, "skipped", true));
                    continue;
                }
                if (from != req.getCoverage()) {
                    changed++;
                    changes.add(Map.of("demandId", d.getId(), "from", from, "to", req.getCoverage()));
                }
            }
        }
        boolean nextOptional = rows.stream().allMatch(d -> d.getCoverageStatus() == SupplyConstants.COVER_READY)
                && singles(rows).isEmpty();
        if (!nextOptional && (next.isEmpty() || req.getNextActionDate() == null)) {
            throw new BizException("请填写下一步行动与日期");
        }
        ScFollowupLogDO l = new ScFollowupLogDO();
        l.setTenantId(PiStore.tenantId());
        l.setRecordType(SupplyConstants.LOG_MANUAL);
        l.setSourceType(SupplyConstants.LOG_SRC_MANUAL);
        l.setContent(req.getContent().trim());
        l.setDemandIds(json(rows.stream().map(ScDemandDO::getId).toList()));
        l.setStatusChanges(changes.isEmpty() ? null : json(changes));
        l.setGapProjectId(req.getGapProjectId() == null ? 0L : req.getGapProjectId());
        l.setNextAction(next);
        l.setNextActionDate(req.getNextActionDate());
        l.setOperatorId(me);
        l.setIdempotencyKey(StringUtils.hasText(req.getIdempotencyKey()) ? req.getIdempotencyKey() : null);
        try {
            logMapper.insert(l);
        } catch (DuplicateKeyException e) {
            // 重复提交（网络重试、连点）：另一次已经写入，本次不再处理，与客户跟进一致
            log.info("记处理重复提交，idempotencyKey={}", req.getIdempotencyKey());
            BoardVOs.RecordResult dup = new BoardVOs.RecordResult();
            dup.setDuplicate(true);
            dup.setSkipped(List.of());
            return dup;
        }
        for (ScDemandDO d : rows) {
            LambdaUpdateWrapper<ScDemandDO> u = new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, d.getId())
                    .set(ScDemandDO::getNextAction, next)
                    .set(ScDemandDO::getNextActionDate, next.isEmpty() ? null : req.getNextActionDate())
                    .set(ScDemandDO::getNextActionSource, next.isEmpty() ? SupplyConstants.NEXT_NONE : SupplyConstants.NEXT_MANUAL)
                    .set(ScDemandDO::getReminderType, SupplyConstants.REMIND_NONE)
                    .set(ScDemandDO::getDeferCount, 0)
                    .set(ScDemandDO::getUpdateTime, LocalDateTime.now());
            if (d.getOwnerId() == null || d.getOwnerId() == 0) {
                u.set(ScDemandDO::getOwnerId, me);
            }
            demandMapper.update(null, u);
        }
        recalc.run(today, Map.of(), rows.stream().map(ScDemandDO::getId).toList());
        BoardVOs.RecordResult r = new BoardVOs.RecordResult();
        r.setLogId(l.getId());
        r.setChanged(changed);
        r.setSkipped(skipped);
        return r;
    }

    /** 需求对象的负责人与下一步（详情页） */
    @Transactional
    public void setNext(Long id, SupplyRequests.NextStep req) {
        ScDemandDO d = demandMapper.selectById(id);
        if (d == null || d.getDeletedAt() != null) {
            throw new BizException("需求对象不存在或已合并");
        }
        String next = req.getNextAction() == null ? "" : req.getNextAction().trim();
        if (!next.isEmpty() && req.getNextActionDate() == null) {
            throw new BizException("请选择下一步日期");
        }
        demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, id)
                .set(req.getOwnerId() != null, ScDemandDO::getOwnerId, req.getOwnerId())
                .set(ScDemandDO::getNextAction, next)
                .set(ScDemandDO::getNextActionDate, next.isEmpty() ? null : req.getNextActionDate())
                .set(ScDemandDO::getNextActionSource, next.isEmpty() ? SupplyConstants.NEXT_NONE : SupplyConstants.NEXT_MANUAL)
                .set(ScDemandDO::getReminderType, SupplyConstants.REMIND_NONE)
                .set(ScDemandDO::getDeferCount, 0)
                .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
    }

    /** 作战卡：作战地图节点（不带 SC- 前缀）→ 内容，租户自己的覆盖平台的 */
    public Map<String, BoardVOs.Card> cards() {
        Map<String, BoardVOs.Card> out = new LinkedHashMap<>();
        sopStageMapper.selectList(new LambdaQueryWrapper<SopStageDO>()
                        .eq(SopStageDO::getBoard, SupplyConstants.SOP_BOARD).eq(SopStageDO::getStatus, 1))
                .stream().sorted(Comparator.comparing(SopStageDO::getTenantId).thenComparing(SopStageDO::getSortOrder))
                .forEach(s -> {
                    BoardVOs.Card c = new BoardVOs.Card();
                    c.setCode(s.getStageCode().replaceFirst("^SC-", ""));
                    c.setName(s.getName());
                    c.setGoal(s.getGoal());
                    c.setChecklist(list(s.getChecklist()));
                    c.setQuality(list(s.getQualityChecks()));
                    c.setGate(s.getGateText());
                    c.setAutoHint(s.getAutoHint());
                    out.put(c.getCode(), c);
                });
        return out;
    }

    // ---------------------------------------------------------------- 内部

    private BoardVOs.Group group(String key, List<ScDemandDO> list, Map<Long, DemandVO> vos, Map<Long, Integer> stockout,
                                 Map<Long, int[]> projects, LocalDate today) {
        BoardVOs.Group g = new BoardVOs.Group();
        ScDemandDO first = list.get(0);
        DemandVO fv = vos.get(first.getId());
        g.setKey(key);
        g.setBrandId(first.getBrandId());
        g.setBrandName(fv.getBrandName());
        g.setCategoryId(first.getCategoryId());
        g.setCategoryName(fv.getCategoryName());
        g.setModels(list.size());
        Map<Integer, Integer> cov = new LinkedHashMap<>();
        for (ScDemandDO d : list) {
            if (d.getCoverageStatus() > 0) {
                cov.merge(d.getCoverageStatus(), 1, Integer::sum);
            }
            int so = stockout.getOrDefault(d.getId(), 0);
            if (so > 0) {
                g.setStockoutModels(g.getStockoutModels() + 1);
                g.setStockoutTimes(g.getStockoutTimes() + so);
            }
        }
        g.setCoverage(cov);
        g.setOverdue(overdue(list, today));
        list.stream().filter(d -> d.getNextActionDate() != null).min(Comparator.comparing(ScDemandDO::getNextActionDate))
                .ifPresent(d -> {
                    g.setEarliestDue(d.getNextActionDate());
                    g.setUrgentAction(d.getNextAction());
                });
        Set<Long> owners = list.stream().map(ScDemandDO::getOwnerId).filter(o -> o != null && o > 0).collect(Collectors.toSet());
        g.setOwnerCount(owners.size());
        if (owners.size() == 1) {
            g.setOwnerName(fvOwner(list, vos));
        }
        List<DemandVO> items = list.stream().map(d -> vos.get(d.getId()))
                .sorted(Comparator.comparing((DemandVO v) -> v.getNextActionDate() == null ? LocalDate.MAX : v.getNextActionDate())
                        .thenComparing(v -> -v.getPriority()))
                .toList();
        g.setItems(items);
        items.stream().map(DemandVO::getActiveProjectId).filter(p -> p != null).findFirst().ifPresent(pid -> {
            g.setProjectId(pid);
            int[] pr = projects.get(pid);
            if (pr != null) {
                g.setProjectSolved(pr[0]);
                g.setProjectTotal(pr[1]);
            }
        });
        return g;
    }

    private static String fvOwner(List<ScDemandDO> list, Map<Long, DemandVO> vos) {
        return list.stream().map(d -> vos.get(d.getId()).getOwnerName()).filter(StringUtils::hasText).findFirst().orElse(null);
    }

    /** 专项进度：projectId → [已解决, 总数] */
    private Map<Long, int[]> projectProgress(java.util.Collection<DemandVO> vos) {
        Map<Long, int[]> out = new HashMap<>();
        Set<Long> ids = vos.stream().map(DemandVO::getActiveProjectId).filter(p -> p != null).collect(Collectors.toSet());
        for (Long pid : ids) {
            out.put(pid, projectCounter.apply(pid));
        }
        return out;
    }

    /** 由专项服务注入，避免循环依赖 */
    private java.util.function.Function<Long, int[]> projectCounter = pid -> new int[]{0, 0};

    public void setProjectCounter(java.util.function.Function<Long, int[]> f) {
        this.projectCounter = f;
    }

    private Predicate<ScDemandDO> match(Filter f, Map<Long, Integer> stockout) {
        boolean cover = "cover".equals(f.board());
        String kw = f.keyword() == null ? null : f.keyword().trim().toLowerCase();
        String kwKey = kw == null ? null : com.zhul.erp.modules.product.support.MpnNormalizer.normalize(kw);
        return d -> {
            if (cover) {
                if (d.getStatus() != SupplyConstants.DEMAND_IN_MATRIX) {
                    return false;
                }
                if (StringUtils.hasText(f.stage()) && !String.valueOf(d.getCoverageStatus()).equals(f.stage())) {
                    return false;
                }
                if (Boolean.TRUE.equals(f.stockout()) && !stockout.containsKey(d.getId())) {
                    return false;
                }
                if (Boolean.TRUE.equals(f.pending()) && d.getCoveragePending() != 1) {
                    return false;
                }
            } else {
                String st = StringUtils.hasText(f.stage()) ? f.stage() : "all";
                boolean ok = switch (st) {
                    case "paused" -> d.getStatus() == SupplyConstants.DEMAND_PAUSED;
                    case "watching" -> d.getStatus() == SupplyConstants.DEMAND_WATCHING;
                    case "review" -> d.getStatus() != SupplyConstants.DEMAND_PAUSED && isReview(d);
                    default -> d.getStatus() != SupplyConstants.DEMAND_PAUSED;
                };
                if (!ok) {
                    return false;
                }
            }
            if (f.brandId() != null && !f.brandId().equals(d.getBrandId())) {
                return false;
            }
            if (f.categoryId() != null && !f.categoryId().equals(d.getCategoryId())) {
                return false;
            }
            if (f.priority() != null && !f.priority().equals(d.getPriority())) {
                return false;
            }
            return kwKey == null || kwKey.isEmpty() || d.getModelKey() != null && d.getModelKey().contains(kwKey);
        };
    }

    private static boolean isReview(ScDemandDO d) {
        return d.getPriorityManual() != null && d.getPriorityManual() == 1 && !d.getPriority().equals(d.getSuggestedPriority());
    }

    private static int overdue(List<ScDemandDO> list, LocalDate today) {
        return (int) list.stream().filter(d -> d.getNextActionDate() != null && d.getNextActionDate().isBefore(today)).count();
    }

    /** owner 为 null 时当前用户，0 为全部 */
    private Long owner(Long ownerId) {
        return ownerId == null ? me() : ownerId;
    }

    private List<ScDemandDO> demands(Long owner) {
        return demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .eq(owner != null && owner > 0, ScDemandDO::getOwnerId, owner)
                .isNull(ScDemandDO::getDeletedAt));
    }

    /** 近 n 天无货次数：需求对象 → 次数 */
    public Map<Long, Integer> stockouts(int days) {
        Map<Long, Integer> out = new HashMap<>();
        signalMapper.stockoutCounts(LocalDate.now().minusDays(days - 1L).atStartOfDay())
                .forEach(m -> out.put(((Number) m.get("demandId")).longValue(), ((Number) m.get("n")).intValue()));
        return out;
    }

    /** 状态为可直接报价或需确认、且没有备选渠道的需求对象 */
    public Set<Long> singles(List<ScDemandDO> rows) {
        List<Long> ids = rows.stream().filter(d -> d.getCoverageStatus() != null && d.getCoverageStatus() >= SupplyConstants.COVER_CONFIRM)
                .map(ScDemandDO::getId).toList();
        Set<Long> withBackup = new HashSet<>();
        for (List<Long> part : DemandAssembler.chunks(ids)) {
            channelMapper.selectList(new LambdaQueryWrapper<ScCoverageChannelDO>()
                            .select(ScCoverageChannelDO::getDemandId)
                            .in(ScCoverageChannelDO::getDemandId, part)
                            .eq(ScCoverageChannelDO::getRole, SupplyConstants.CHANNEL_BACKUP)
                            .isNull(ScCoverageChannelDO::getDeletedAt))
                    .forEach(c -> withBackup.add(c.getDemandId()));
        }
        Set<Long> out = new HashSet<>(ids);
        out.removeAll(withBackup);
        return out;
    }

    private List<String> list(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            log.warn("作战卡内容格式不正确，已忽略：{}", e.getOriginalMessage());
            return List.of();
        }
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
