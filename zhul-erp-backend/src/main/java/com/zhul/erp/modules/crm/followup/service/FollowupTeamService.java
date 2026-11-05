package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.FollowupTeamVO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.entity.SopStageDO;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupTeamMapper;
import com.zhul.erp.modules.crm.followup.repository.SopStageMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
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
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户跟进团队视图（第二期 design D10，spec「团队视图」）：结果、按负责人、卡在哪一步、需要主管看一眼。
 * 负责人范围为账号的数据权限，可再按部门收窄（部门须在数据权限内）；权限「查看团队」由接口注解校验。只读。
 */
@Service
@RequiredArgsConstructor
public class FollowupTeamService {

    public static final String PERIOD_WEEK = "week";
    public static final String PERIOD_LAST_WEEK = "last-week";
    public static final String PERIOD_MONTH = "month";
    /** 默认显示几条「需要主管看一眼」，「全部查看」最多几条 */
    private static final int EXCEPTION_TOP = 5;
    private static final int EXCEPTION_ALL = 100;
    private static final int OVERDUE_DAYS = 3;
    private static final int ATTENTION = 3;
    private static final int DISSATISFIED_DAYS = 30;
    /** 「需要主管看一眼」的类型 */
    private static final Map<String, String> TYPE_NAMES = Map.of("OVERDUE", "逾期", "SNOOZED", "多次延后", "FULFIL", "履约异常",
            "ISSUE_LONG", "售后处理久", "DISSATISFIED", "不满意");
    /** 卡在哪一步统计的板块 */
    private static final List<Integer> STAGE_BOARDS = List.of(FollowupConstants.BOARD_DEVELOP, FollowupConstants.BOARD_SALES,
            FollowupConstants.BOARD_FULFIL, FollowupConstants.BOARD_AFTER_SALES);

    private final FollowupItemMapper itemMapper;
    private final FollowupTeamMapper teamMapper;
    private final SopStageMapper sopStageMapper;
    private final CustomerMapper customerMapper;
    private final UserBasicMapper userBasicMapper;
    private final DataScopeResolver dataScopeResolver;

    /**
     * @param period     week（本周，默认）、last-week（上周）、month（本月）
     * @param deptId     可选，只看这个部门的负责人
     * @param exceptions all 时「需要主管看一眼」返回全部（最多 100 条），否则前 5 条
     */
    public FollowupTeamVO team(String period, Integer deptId, String exceptions) {
        LocalDate today = LocalDate.now();
        String p = period == null || period.isBlank() ? PERIOD_WEEK : period.trim();
        LocalDate[] range = range(p, today);
        FollowupTeamVO vo = new FollowupTeamVO();
        vo.setPeriod(p);
        vo.setFrom(range[0]);
        vo.setTo(range[1]);
        vo.setPrevFrom(range[2]);
        vo.setPrevTo(range[3]);
        Collection<Long> owners = owners(deptId);
        Map<String, SopStageDO> stages = stages();
        fillStagesSkeleton(vo, stages);
        if (owners != null && owners.isEmpty()) {
            zeroResults(vo.getResults());
            return vo;
        }
        results(vo.getResults(), owners, range);
        List<FollowupItemDO> open = itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .select(FollowupItemDO::getId, FollowupItemDO::getCustomerId, FollowupItemDO::getBoard, FollowupItemDO::getStageCode,
                        FollowupItemDO::getOwnerId, FollowupItemDO::getNextAction, FollowupItemDO::getNextActionDate,
                        FollowupItemDO::getNextActionSource, FollowupItemDO::getSnoozeCount, FollowupItemDO::getNurtureUntil,
                        FollowupItemDO::getStageSince, FollowupItemDO::getCreateTime, FollowupItemDO::getExceptionFlags,
                        FollowupItemDO::getCurrencyCode, FollowupItemDO::getAmount, FollowupItemDO::getAmountCny)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt)
                .isNull(FollowupItemDO::getNurtureUntil)
                .in(owners != null, FollowupItemDO::getOwnerId, owners));
        ownerRows(vo, open, owners, range, today);
        stageRows(vo, open, today);
        exceptionRows(vo, open, owners, stages, today, "all".equals(exceptions) ? EXCEPTION_ALL : EXCEPTION_TOP);
        return vo;
    }

    /** [from, to, prevFrom, prevTo]，均含两端 */
    static LocalDate[] range(String period, LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        switch (period) {
            case PERIOD_WEEK:
                return new LocalDate[]{monday, monday.plusDays(6), monday.minusWeeks(1), monday.minusDays(1)};
            case PERIOD_LAST_WEEK: {
                LocalDate m = monday.minusWeeks(1);
                return new LocalDate[]{m, m.plusDays(6), m.minusWeeks(1), m.minusDays(1)};
            }
            case PERIOD_MONTH: {
                LocalDate first = today.withDayOfMonth(1);
                LocalDate prevFirst = first.minusMonths(1);
                return new LocalDate[]{first, first.plusMonths(1).minusDays(1), prevFirst, first.minusDays(1)};
            }
            default:
                throw new BizException("时间范围不正确");
        }
    }

    /** 数据权限内的负责人，按部门收窄；null 表示不限 */
    private Collection<Long> owners(Integer deptId) {
        DataScope scope = dataScopeResolver.current();
        Set<Long> base = scope.isAll() ? null : new HashSet<>(scope.ownerIds());
        if (base != null && scope.selfId() != null) {
            base.add(scope.selfId());
        }
        if (deptId == null) {
            return base;
        }
        Set<Long> inDept = userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                        .select(UserBasicDO::getId).eq(UserBasicDO::getDeptId, deptId)).stream()
                .map(u -> u.getId().longValue()).collect(Collectors.toSet());
        if (base == null) {
            return inDept;
        }
        Set<Long> both = inDept.stream().filter(base::contains).collect(Collectors.toSet());
        if (!inDept.isEmpty() && both.isEmpty()) {
            throw new BizException("没有权限查看这个部门的跟进");
        }
        return both;
    }

    // ---------------------------------------------------------------- 结果

    private void results(FollowupTeamVO.Results r, Collection<Long> owners, LocalDate[] range) {
        LocalDateTime from = range[0].atStartOfDay();
        LocalDateTime to = range[1].plusDays(1).atStartOfDay();
        LocalDateTime prevFrom = range[2].atStartOfDay();
        LocalDateTime prevTo = range[3].plusDays(1).atStartOfDay();
        r.getNewOpportunities().setValue(teamMapper.newOpportunities(owners, from, to));
        r.getNewOpportunities().setPrev(teamMapper.newOpportunities(owners, prevFrom, prevTo));
        r.getQuotationsSent().setValue(teamMapper.firstSentQuotations(owners, from, to));
        r.getQuotationsSent().setPrev(teamMapper.firstSentQuotations(owners, prevFrom, prevTo));
        r.getPisSent().setValue(teamMapper.firstSentPis(owners, from, to));
        r.getPisSent().setPrev(teamMapper.firstSentPis(owners, prevFrom, prevTo));
        money(r.getOrders(), teamMapper.orders(owners, from, to, false));
        money(r.getRepeat(), teamMapper.orders(owners, from, to, true));
        Map<String, Object> onTime = teamMapper.onTime(owners, range[0], range[1].plusDays(1));
        r.setDelivered(num(onTime.get("total")));
        r.setOnTime(num(onTime.get("onTime")));
        Map<Integer, Integer> reviews = new HashMap<>(4);
        for (Map<String, Object> row : teamMapper.reviews(owners, from, to)) {
            reviews.put(num(row.get("status")), num(row.get("cnt")));
        }
        r.setReviewed(reviews.getOrDefault(FollowupConstants.REVIEW_DONE, 0));
        r.setInvited(reviews.getOrDefault(FollowupConstants.REVIEW_INVITED, 0));
        r.setDeclined(reviews.getOrDefault(FollowupConstants.REVIEW_DECLINED, 0));
        accountResults(r, owners, range, LocalDate.now());
        Map<String, Object> dev = teamMapper.develop(owners, from, to);
        r.setDevelopNew(num(dev.get("created")));
        r.setDevelopTouched(num(dev.get("touched")));
        r.setDevelopReplied(num(dev.get("replied")));
        r.setDevelopReplyPercent(replyPercent(r.getDevelopReplied(), r.getDevelopTouched()));
        r.setDevelopConverted(num(dev.get("converted")));
        r.setDevelopStopped(num(dev.get("stopped")));
    }

    /** 回复率：回复 ÷ 首次触达，HALF_UP 保留整数百分比；首次触达为 0 时为 null */
    static Integer replyPercent(int replied, int touched) {
        return touched == 0 ? null : BigDecimal.valueOf(replied * 100L)
                .divide(BigDecimal.valueOf(touched), 0, RoundingMode.HALF_UP).intValue();
    }

    /** 客户经营指标（第三期 spec「团队视图的客户经营指标」）；还没跟的只算到今天为止已到期的 */
    private void accountResults(FollowupTeamVO.Results r, Collection<Long> owners, LocalDate[] range, LocalDate today) {
        LocalDate to = range[1].plusDays(1);
        Map<String, Object> touched = teamMapper.accountTouched(owners, range[0], to);
        LocalDate pendingTo = today.plusDays(1).isBefore(to) ? today.plusDays(1) : to;
        long pending = pendingTo.isAfter(range[0]) ? teamMapper.accountPending(owners, range[0], pendingTo) : 0;
        int due = num(touched.get("total")) + (int) pending;
        int onTime = num(touched.get("onTime"));
        r.setAccountDue(due);
        r.setAccountOnTime(onTime);
        r.setAccountOnTimePercent(due == 0 ? null : BigDecimal.valueOf(onTime * 100L)
                .divide(BigDecimal.valueOf(due), 0, RoundingMode.HALF_UP).intValue());
        r.getAccountInquiries().setValue(teamMapper.accountInquiries(owners, range[0].atStartOfDay(), to.atStartOfDay()));
        r.getAccountInquiries().setPrev(teamMapper.accountInquiries(owners, range[2].atStartOfDay(),
                range[3].plusDays(1).atStartOfDay()));
        r.setDormant((int) teamMapper.dormant(owners));
        r.setActivated((int) teamMapper.activated(owners, FollowupConstants.ACTIVATED_PREFIX, range[0].atStartOfDay(),
                to.atStartOfDay()));
    }

    private static void zeroResults(FollowupTeamVO.Results r) {
        for (FollowupTeamVO.Compare c : List.of(r.getNewOpportunities(), r.getQuotationsSent(), r.getPisSent())) {
            c.setValue(0L);
            c.setPrev(0L);
        }
        for (FollowupTeamVO.Money m : List.of(r.getOrders(), r.getRepeat())) {
            m.setCount(0L);
            m.setAmountCny(BigDecimal.ZERO);
        }
        r.setDelivered(0);
        r.setOnTime(0);
        r.setReviewed(0);
        r.setInvited(0);
        r.setDeclined(0);
        r.setAccountDue(0);
        r.setAccountOnTime(0);
        r.getAccountInquiries().setValue(0L);
        r.getAccountInquiries().setPrev(0L);
        r.setDormant(0);
        r.setActivated(0);
        r.setDevelopNew(0);
        r.setDevelopTouched(0);
        r.setDevelopReplied(0);
        r.setDevelopConverted(0);
        r.setDevelopStopped(0);
    }

    private static void money(FollowupTeamVO.Money m, Map<String, Object> row) {
        m.setCount((long) num(row.get("cnt")));
        Object a = row.get("amount");
        // 本位币合计入库时已按 HALF_UP 保留 2 位，这里只统一精度
        m.setAmountCny((a instanceof BigDecimal d ? d : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    // ---------------------------------------------------------------- 按负责人

    private void ownerRows(FollowupTeamVO vo, List<FollowupItemDO> open, Collection<Long> owners, LocalDate[] range, LocalDate today) {
        Map<Long, Integer> records = recordCounts(owners, range[0], range[1]);
        Map<Long, Integer> prevRecords = recordCounts(owners, range[2], range[3]);
        Set<Long> ids = new TreeSet<>();
        open.forEach(i -> ids.add(i.getOwnerId()));
        ids.addAll(records.keySet());
        ids.removeIf(i -> i == null || i <= 0);
        Map<Long, String> names = userNames(ids);
        Map<Long, List<FollowupItemDO>> byOwner = open.stream().collect(Collectors.groupingBy(FollowupItemDO::getOwnerId));
        for (Long id : ids) {
            List<FollowupItemDO> mine = byOwner.getOrDefault(id, List.of());
            FollowupTeamVO.Owner o = new FollowupTeamVO.Owner();
            o.setOwnerId(id);
            o.setOwnerName(names.getOrDefault(id, ""));
            for (int b = 1; b <= FollowupConstants.BOARD_NAMES.size(); b++) {
                int board = b;
                o.getBoards().add((int) mine.stream().filter(i -> i.getBoard() == board).count());
            }
            List<FollowupItemDO> todo = mine.stream().filter(i -> i.getNextActionDate() != null
                    && nz(i.getNextActionSource()) != FollowupConstants.NEXT_AFTER_SALES).toList();
            o.setTodayDue((int) todo.stream().filter(i -> today.equals(i.getNextActionDate())).count());
            o.setOverdue((int) todo.stream().filter(i -> i.getNextActionDate().isBefore(today)).count());
            o.setManySnoozes((int) mine.stream().filter(i -> nz(i.getSnoozeCount()) >= FollowupConstants.SNOOZE_WARN_TIMES).count());
            o.setRecords(records.getOrDefault(id, 0));
            o.setPrevRecords(prevRecords.getOrDefault(id, 0));
            o.setAttention(o.getOverdue() >= ATTENTION || o.getManySnoozes() >= ATTENTION);
            vo.getOwners().add(o);
        }
        // 需要关注的在前，再按逾期多→少
        vo.getOwners().sort(Comparator.comparing((FollowupTeamVO.Owner o) -> !o.getAttention())
                .thenComparing(FollowupTeamVO.Owner::getOverdue, Comparator.reverseOrder())
                .thenComparing(FollowupTeamVO.Owner::getOwnerId));
    }

    private Map<Long, Integer> recordCounts(Collection<Long> owners, LocalDate from, LocalDate to) {
        Map<Long, Integer> out = new HashMap<>(16);
        for (Map<String, Object> row : teamMapper.recordsByOperator(owners, from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            out.put(((Number) row.get("ownerId")).longValue(), num(row.get("cnt")));
        }
        return out;
    }

    // ---------------------------------------------------------------- 卡在哪一步

    private static void fillStagesSkeleton(FollowupTeamVO vo, Map<String, SopStageDO> stages) {
        stages.values().stream().filter(s -> STAGE_BOARDS.contains(s.getBoard()))
                .sorted(Comparator.comparing(SopStageDO::getBoard).thenComparing(SopStageDO::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(SopStageDO::getStageCode))
                .forEach(s -> {
                    FollowupTeamVO.Stage v = new FollowupTeamVO.Stage();
                    v.setBoard(s.getBoard());
                    v.setStageCode(s.getStageCode());
                    v.setStageName(s.getName());
                    v.setCount(0);
                    v.setRefDays(s.getRefDays());
                    v.setOver(false);
                    vo.getStages().add(v);
                });
    }

    private static void stageRows(FollowupTeamVO vo, List<FollowupItemDO> open, LocalDate today) {
        for (FollowupTeamVO.Stage s : vo.getStages()) {
            List<FollowupItemDO> in = open.stream().filter(i -> Objects.equals(i.getBoard(), s.getBoard())
                    && s.getStageCode().equals(i.getStageCode())).toList();
            s.setCount(in.size());
            if (in.isEmpty()) {
                continue;
            }
            double avg = in.stream().mapToLong(i -> stayDays(i, today)).average().orElse(0);
            s.setAvgDays((int) Math.round(avg));
            s.setOver(s.getRefDays() != null && s.getRefDays() > 0 && avg > s.getRefDays());
        }
    }

    /** 停留天数 = 今天 − 进入当前阶段的日期；上线前的对象以创建时间起算 */
    private static long stayDays(FollowupItemDO i, LocalDate today) {
        LocalDateTime since = i.getStageSince() != null ? i.getStageSince() : i.getCreateTime();
        return since == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(since.toLocalDate(), today));
    }

    // ---------------------------------------------------------------- 需要主管看一眼

    private void exceptionRows(FollowupTeamVO vo, List<FollowupItemDO> open, Collection<Long> owners, Map<String, SopStageDO> stages,
                               LocalDate today, int limit) {
        int a3Ref = Objects.requireNonNullElse(stages.containsKey("A3") ? stages.get("A3").getRefDays() : null, 0);
        List<Object[]> hits = new ArrayList<>(32);
        for (FollowupItemDO i : open) {
            boolean viaAfterSales = nz(i.getNextActionSource()) == FollowupConstants.NEXT_AFTER_SALES;
            if (!viaAfterSales && i.getNextActionDate() != null && !i.getNextActionDate().isAfter(today.minusDays(OVERDUE_DAYS))) {
                hits.add(new Object[]{"OVERDUE", i, "逾期 " + ChronoUnit.DAYS.between(i.getNextActionDate(), today) + " 天："
                        + nvl(i.getNextAction())});
            }
            if (nz(i.getSnoozeCount()) >= FollowupConstants.SNOOZE_WARN_TIMES) {
                hits.add(new Object[]{"SNOOZED", i, "连续延后 " + i.getSnoozeCount() + " 次"});
            }
            int ex = nz(i.getExceptionFlags());
            if (Objects.equals(i.getBoard(), FollowupConstants.BOARD_FULFIL) && ex > 0) {
                hits.add(new Object[]{"FULFIL", i, FollowupConstants.EXCEPTION_NAMES.entrySet().stream()
                        .filter(e -> (ex & e.getKey()) != 0).sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue)
                        .collect(Collectors.joining("、"))});
            }
            if ("A3".equals(i.getStageCode()) && a3Ref > 0 && stayDays(i, today) > a3Ref) {
                hits.add(new Object[]{"ISSUE_LONG", i, "售后处理已 " + stayDays(i, today) + " 天，参考 " + a3Ref + " 天"});
            }
        }
        // 近 30 天 A5 不满意（售后已结束的对象）
        for (FollowupItemDO i : itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_AFTER_SALES)
                .eq(FollowupItemDO::getSatisfaction, FollowupConstants.DISSATISFIED)
                .ge(FollowupItemDO::getClosedAt, today.minusDays(DISSATISFIED_DAYS).atStartOfDay())
                .isNull(FollowupItemDO::getDeletedAt)
                .in(owners != null, FollowupItemDO::getOwnerId, owners))) {
            hits.add(new Object[]{"DISSATISFIED", i, "不满意 · 原因："
                    + FollowupConstants.DISSATISFIED_REASONS.getOrDefault(nvl(i.getDissatisfiedReason()), "其他")});
        }
        vo.getExceptions().setTotal(hits.size());
        if (hits.isEmpty()) {
            return;
        }
        Map<Long, CustomerDO> customers = customerMapper.selectBatchIds(hits.stream()
                        .map(h -> ((FollowupItemDO) h[1]).getCustomerId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CustomerDO::getId, Function.identity()));
        Comparator<Object[]> byValue = Comparator.comparingInt((Object[] h) -> grade(customers.get(((FollowupItemDO) h[1]).getCustomerId())))
                .thenComparingInt(h -> potential(customers.get(((FollowupItemDO) h[1]).getCustomerId())))
                .thenComparing(h -> nvl(((FollowupItemDO) h[1]).getAmountCny()), Comparator.reverseOrder())
                .thenComparing(h -> ((FollowupItemDO) h[1]).getId());
        List<Object[]> top = hits.stream().sorted(byValue).limit(limit).toList();
        Map<Long, String> names = userNames(top.stream().map(h -> ((FollowupItemDO) h[1]).getOwnerId()).collect(Collectors.toSet()));
        for (Object[] h : top) {
            FollowupItemDO i = (FollowupItemDO) h[1];
            FollowupTeamVO.ExceptionItem e = new FollowupTeamVO.ExceptionItem();
            e.setType((String) h[0]);
            e.setTypeName(TYPE_NAMES.get((String) h[0]));
            e.setItemId(i.getId());
            e.setBoard(i.getBoard());
            e.setCustomerId(i.getCustomerId());
            CustomerDO c = customers.get(i.getCustomerId());
            e.setCustomerName(c == null ? "" : c.getName());
            e.setStageCode(i.getStageCode());
            SopStageDO st = stages.get(i.getStageCode());
            e.setStageName(st == null ? i.getStageCode() : st.getName());
            e.setOwnerId(i.getOwnerId());
            e.setOwnerName(names.getOrDefault(i.getOwnerId(), ""));
            e.setCurrencyCode(i.getCurrencyCode());
            e.setAmount(i.getAmount());
            e.setText((String) h[2]);
            vo.getExceptions().getItems().add(e);
        }
    }

    /** 与列表的客户价值排序一致：经营等级 S→C、未成交最后；潜力 5→1、未评最后 */
    private static int grade(CustomerDO c) {
        Integer g = c == null ? null : c.getBizGrade();
        return g != null && g >= 1 && g <= 4 ? g : 9;
    }

    private static int potential(CustomerDO c) {
        Integer p = c == null ? null : c.getPotentialRating();
        return p != null && p >= 1 && p <= 5 ? 6 - p : 9;
    }

    // ---------------------------------------------------------------- 工具

    /** 阶段编码 → SOP 阶段（租户自己的覆盖平台的） */
    private Map<String, SopStageDO> stages() {
        Map<String, SopStageDO> out = new LinkedHashMap<>(32);
        sopStageMapper.selectList(new LambdaQueryWrapper<SopStageDO>().eq(SopStageDO::getStatus, 1)).stream()
                .sorted(Comparator.comparing(SopStageDO::getTenantId))
                .forEach(s -> out.put(s.getStageCode(), s));
        return out;
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        Set<Integer> keys = ids.stream().filter(i -> i != null && i > 0).map(Long::intValue).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            for (UserBasicDO u : userBasicMapper.selectBatchIds(keys)) {
                names.put(u.getId().longValue(), u.getName());
            }
        }
        return names;
    }

    private static int num(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
