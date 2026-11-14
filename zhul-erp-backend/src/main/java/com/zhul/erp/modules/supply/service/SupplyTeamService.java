package com.zhul.erp.modules.supply.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.ProjectVO;
import com.zhul.erp.modules.supply.dto.TeamVO;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScDemandSignalMapper;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.support.DemandRules;
import com.zhul.erp.modules.supply.support.SupplyLookups;
import com.zhul.erp.modules.system.doclog.DocumentLogDO;
import com.zhul.erp.modules.system.doclog.DocumentLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 团队视图（tasks 4.5）。统计口径见 spec：无货按统计期（近 30 / 90 天）、按型号去重；三个覆盖率按当前状态；
 * 比率保留一位小数、四舍五入，分母为 0 返回 null。只读，数据只按租户隔离。
 */
@Service
@RequiredArgsConstructor
public class SupplyTeamService {

    private static final int TOP = 10;
    private static final int ATTENTION_OVERDUE_DAYS = 3;
    /** 操作日志里「补上缺口」的写法：无渠道或有线索未验证 → 需确认或可直接报价 */
    private static final Pattern FILLED = Pattern.compile("(无渠道|有线索未验证) → (需确认|可直接报价)");

    private final ScDemandMapper demandMapper;
    private final ScDemandSignalMapper signalMapper;
    private final ScFollowupLogMapper logMapper;
    private final DocumentLogMapper docLogMapper;
    private final SupplyBoardService boardService;
    private final GapProjectService projectService;
    private final SupplyLookups lookups;
    private final InquiryLookups inquiryLookups;

    public TeamVO team(Integer period) {
        int days = period != null && period == 90 ? 90 : 30;
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        LocalDateTime prevFrom = from.minusDays(days);
        TeamVO v = new TeamVO();
        v.setDays(days);
        List<ScDemandDO> all = demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>().isNull(ScDemandDO::getDeletedAt));
        Map<Long, ScDemandDO> byId = new HashMap<>();
        all.forEach(d -> byId.put(d.getId(), d));
        Map<Long, Integer> stockout = boardService.stockouts(days);
        stockout.keySet().removeIf(id -> !byId.containsKey(id));
        v.setStockoutModels(stockout.size());
        v.setStockoutTimes(stockout.values().stream().mapToInt(Integer::intValue).sum());
        Map<String, Object> prev = signalMapper.stockoutTotals(prevFrom, from);
        v.setPrevStockoutModels(num(prev, "models"));
        v.setPrevStockoutTimes(num(prev, "times"));
        v.setStockoutRate(DemandRules.rate(stockout.size(), signalMapper.quotedModels(from, to)));
        rates(v, all);
        v.setFilledGaps(filled(from));
        List<ProjectVO> projects = projectService.active(null);
        Map<String, Long> projectByScope = new HashMap<>();
        projects.forEach(p -> projectByScope.put(p.getBrandId() + ":" + p.getCategoryId(), p.getId()));
        Map<Long, String> brands = lookups.brandNames(all.stream().map(ScDemandDO::getBrandId).toList());
        Map<Long, String> cats = lookups.categoryNames(all.stream().map(ScDemandDO::getCategoryId).toList());
        v.setByBrand(rank(stockout, byId, d -> d.getBrandId() + ":0", d -> brands.getOrDefault(d.getBrandId(), ""), projectByScope));
        v.setByCategory(rank(stockout, byId, d -> "0:" + d.getCategoryId(),
                d -> cats.getOrDefault(d.getCategoryId(), "未分类"), projectByScope));
        v.setCells(rank(stockout, byId, d -> d.getBrandId() + ":" + d.getCategoryId(),
                d -> brands.getOrDefault(d.getBrandId(), "") + " · " + cats.getOrDefault(d.getCategoryId(), "未分类"), projectByScope));
        List<TeamVO.Rank> reasons = new ArrayList<>();
        signalMapper.stockoutReasons(from).forEach(m -> {
            TeamVO.Rank r = new TeamVO.Rank();
            String text = (String) m.get("text");
            r.setLabel(text == null || text.isBlank() ? "未填原因" : text.split("：")[0]);
            r.setTimes(num(m, "n"));
            reasons.add(r);
        });
        v.setReasons(reasons);
        v.setProjects(projects);
        owners(v, all, today);
        attention(v, all, projects, today);
        return v;
    }

    private void rates(TeamVO v, List<ScDemandDO> all) {
        List<ScDemandDO> key = all.stream().filter(d -> d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX
                && d.getPriority() >= SupplyConstants.PRIORITY_MID).toList();
        long ready = key.stream().filter(d -> d.getCoverageStatus() == SupplyConstants.COVER_READY).count();
        List<ScDemandDO> quotable = key.stream().filter(d -> d.getCoverageStatus() >= SupplyConstants.COVER_CONFIRM).toList();
        v.setCoverRate(DemandRules.rate(ready, key.size()));
        v.setQuotableRate(DemandRules.rate(quotable.size(), key.size()));
        long withBackup = quotable.size() - boardService.singles(quotable).size();
        v.setBackupRate(DemandRules.rate(withBackup, quotable.size()));
    }

    private int filled(LocalDateTime from) {
        Set<Long> ids = new HashSet<>();
        docLogMapper.selectList(new LambdaQueryWrapper<DocumentLogDO>()
                        .eq(DocumentLogDO::getDocType, "SC").eq(DocumentLogDO::getAction, "覆盖状态")
                        .ge(DocumentLogDO::getCreateTime, from).isNull(DocumentLogDO::getDeletedAt))
                .forEach(l -> {
                    if (l.getContent() != null && FILLED.matcher(l.getContent()).find()) {
                        ids.add(l.getDocId());
                    }
                });
        return ids.size();
    }

    private List<TeamVO.Rank> rank(Map<Long, Integer> stockout, Map<Long, ScDemandDO> byId, Function<ScDemandDO, String> key,
                                   Function<ScDemandDO, String> label, Map<String, Long> projects) {
        Map<String, TeamVO.Rank> out = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> e : stockout.entrySet()) {
            ScDemandDO d = byId.get(e.getKey());
            String k = key.apply(d);
            TeamVO.Rank r = out.computeIfAbsent(k, x -> {
                TeamVO.Rank n = new TeamVO.Rank();
                String[] p = x.split(":");
                n.setBrandId("0".equals(p[0]) ? null : Long.valueOf(p[0]));
                n.setCategoryId("0".equals(p[1]) ? null : Long.valueOf(p[1]));
                n.setLabel(label.apply(d));
                n.setProjectId(projects.get(x));
                return n;
            });
            r.setModels(r.getModels() + 1);
            r.setTimes(r.getTimes() + e.getValue());
            if (d.getCoverageStatus() != SupplyConstants.COVER_READY) {
                r.setOpen(r.getOpen() + 1);
            }
        }
        return out.values().stream().sorted(Comparator.comparing(TeamVO.Rank::getModels).reversed()
                .thenComparing(Comparator.comparing(TeamVO.Rank::getTimes).reversed())).limit(TOP).toList();
    }

    private void owners(TeamVO v, List<ScDemandDO> all, LocalDate today) {
        Map<Long, TeamVO.OwnerRow> rows = new LinkedHashMap<>();
        for (ScDemandDO d : all) {
            if (d.getOwnerId() == null || d.getOwnerId() == 0 || d.getStatus() == SupplyConstants.DEMAND_PAUSED) {
                continue;
            }
            TeamVO.OwnerRow r = rows.computeIfAbsent(d.getOwnerId(), id -> {
                TeamVO.OwnerRow n = new TeamVO.OwnerRow();
                n.setOwnerId(id);
                return n;
            });
            r.setModels(r.getModels() + 1);
            switch (d.getCoverageStatus()) {
                case SupplyConstants.COVER_NO_CHANNEL -> r.setNoChannel(r.getNoChannel() + 1);
                case SupplyConstants.COVER_LEAD -> r.setLead(r.getLead() + 1);
                case SupplyConstants.COVER_CONFIRM -> r.setConfirm(r.getConfirm() + 1);
                default -> { }
            }
            if (d.getNextActionDate() != null) {
                if (d.getNextActionDate().isEqual(today)) {
                    r.setDueToday(r.getDueToday() + 1);
                } else if (d.getNextActionDate().isBefore(today)) {
                    r.setOverdue(r.getOverdue() + 1);
                }
            }
            if (d.getDeferCount() != null && d.getDeferCount() >= SupplyConstants.MULTI_DEFER) {
                r.setMultiDefer(r.getMultiDefer() + 1);
            }
        }
        LocalDateTime monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        logMapper.selectList(new LambdaQueryWrapper<ScFollowupLogDO>()
                        .select(ScFollowupLogDO::getOperatorId)
                        .eq(ScFollowupLogDO::getRecordType, SupplyConstants.LOG_MANUAL)
                        .ge(ScFollowupLogDO::getCreateTime, monday).isNull(ScFollowupLogDO::getDeletedAt))
                .forEach(l -> {
                    TeamVO.OwnerRow r = rows.get(l.getOperatorId());
                    if (r != null) {
                        r.setRecords(r.getRecords() + 1);
                    }
                });
        Map<Long, String> names = inquiryLookups.userNames(rows.keySet());
        rows.values().forEach(r -> r.setOwnerName(names.get(r.getOwnerId())));
        v.setOwners(rows.values().stream().sorted(Comparator.comparing(TeamVO.OwnerRow::getOverdue).reversed()).toList());
    }

    private void attention(TeamVO v, List<ScDemandDO> all, List<ProjectVO> projects, LocalDate today) {
        List<TeamVO.Attention> out = new ArrayList<>();
        Map<Long, String> brands = lookups.brandNames(all.stream().map(ScDemandDO::getBrandId).toList());
        Map<Long, String> users = inquiryLookups.userNames(all.stream().map(ScDemandDO::getOwnerId)
                .filter(o -> o != null && o > 0).toList());
        for (ScDemandDO d : all) {
            if (d.getStatus() == SupplyConstants.DEMAND_PAUSED) {
                continue;
            }
            String title = brands.getOrDefault(d.getBrandId(), "") + " " + (d.getModel() == null ? "" : d.getModel());
            boolean noOwner = d.getOwnerId() == null || d.getOwnerId() == 0;
            if (d.getNextActionDate() != null && ChronoUnit.DAYS.between(d.getNextActionDate(), today) >= ATTENTION_OVERDUE_DAYS) {
                out.add(att("overdue", d.getId(), title, "逾期 " + ChronoUnit.DAYS.between(d.getNextActionDate(), today) + " 天",
                        users.get(d.getOwnerId())));
            } else if (d.getDeferCount() != null && d.getDeferCount() >= SupplyConstants.MULTI_DEFER) {
                out.add(att("defer", d.getId(), title, "连续延后 " + d.getDeferCount() + " 次", users.get(d.getOwnerId())));
            } else if (noOwner && d.getPriority() == SupplyConstants.PRIORITY_HIGH) {
                out.add(att("no-owner", d.getId(), title, "高优先级，没有负责人"
                        + (d.getNextAction() != null && !d.getNextAction().isEmpty() ? "：" + d.getNextAction() : ""), null));
            }
        }
        for (ProjectVO p : projects) {
            if (p.getOverdueDays() > 0) {
                TeamVO.Attention a = att("project", null, p.getName(), "专项超期 " + p.getOverdueDays() + " 天，已解决 "
                        + p.getSolved() + " / " + p.getTotal(), p.getOwnerName());
                a.setProjectId(p.getId());
                out.add(a);
            }
        }
        v.setAttention(out);
    }

    private static TeamVO.Attention att(String kind, Long demandId, String title, String detail, String owner) {
        TeamVO.Attention a = new TeamVO.Attention();
        a.setKind(kind);
        a.setDemandId(demandId);
        a.setTitle(title.trim());
        a.setDetail(detail);
        a.setOwnerName(owner);
        return a;
    }

    private static int num(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k);
        return o instanceof Number n ? n.intValue() : 0;
    }
}
