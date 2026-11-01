package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.SourcingStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingItemHandoffDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingItemHandoffMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskAssigneeMapper;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 询价统计（spec inquiry/sourcing-stats）：询盘日期在范围内、已进入询价（状态 ≥ 询价中）、未取消的询盘单。
 * 按月份按询盘单归类：有待回价型号为「询价中」，否则至少一个有价为「询价完成」，全部无货为「无法报价」；
 * 按人员按型号统计（询价任务的有效分配与转出人，分给多人时每人各算一次）；询价工作量按月对比业务员自询与采购询价。只读。
 */
@Service
@RequiredArgsConstructor
public class SourcingStatsService {

    private final CustomerInquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final InquiryLookups lookups;
    private final SourcingItemHandoffMapper handoffMapper;
    private final SourcingQuoteMapper quoteMapper;

    public SourcingStatsVO stats(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate from = dateFrom != null ? dateFrom : LocalDate.now().withDayOfYear(1);
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        List<CustomerInquiryDO> inquiries = inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getTenantId, PiStore.tenantId())
                .isNull(CustomerInquiryDO::getDeletedAt)
                .ge(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_SOURCING)
                .ne(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_CANCELLED)
                .ge(CustomerInquiryDO::getInquiryDate, from)
                .le(CustomerInquiryDO::getInquiryDate, to));
        List<InquiryItemDO> items = inquiries.isEmpty() ? List.of() : itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .in(InquiryItemDO::getCustomerInquiryId, inquiries.stream().map(CustomerInquiryDO::getId).toList())
                .isNull(InquiryItemDO::getDeletedAt));
        Map<Long, List<InquiryItemDO>> byInquiry = items.stream().collect(Collectors.groupingBy(InquiryItemDO::getCustomerInquiryId));

        Map<String, int[]> months = new TreeMap<>();
        int[] total = new int[4];
        for (CustomerInquiryDO i : inquiries) {
            List<InquiryItemDO> list = byInquiry.getOrDefault(i.getId(), List.of());
            boolean pending = list.isEmpty() || list.stream().anyMatch(x -> Objects.equals(x.getQuoteStatus(), InquiryConstants.ITEM_PENDING));
            boolean priced = list.stream().anyMatch(x -> Objects.equals(x.getQuoteStatus(), InquiryConstants.ITEM_PRICED));
            int kind = pending ? 3 : priced ? 1 : 2;
            int[] m = months.computeIfAbsent(i.getInquiryDate().toString().substring(0, 7), k -> new int[4]);
            m[0]++;
            m[kind]++;
            total[0]++;
            total[kind]++;
        }
        SourcingStatsVO vo = new SourcingStatsVO();
        List<SourcingStatsVO.MonthRow> rows = new ArrayList<>();
        months.forEach((k, m) -> rows.add(monthRow(k,
                Integer.parseInt(k.substring(0, 4)) + " 年 " + Integer.parseInt(k.substring(5)) + " 月", m)));
        vo.setMonths(rows);
        vo.setTotal(monthRow("", "合计", total));
        Map<Long, String> monthOfInquiry = inquiries.stream().collect(Collectors.toMap(CustomerInquiryDO::getId,
                i -> i.getInquiryDate().toString().substring(0, 7)));
        Participants participants = participants(items);
        vo.setPurchasers(purchasers(items, participants));
        workload(vo, items, participants, monthOfInquiry);
        return vo;
    }

    /** 每个任务的处理人：有效分配，加上退回时转出过型号的人（分配已失效，但他做过这个任务） */
    private record Participants(Map<Long, Map<Long, Boolean>> byTask, Map<Long, Map<Long, Set<Long>>> handedByTask,
                                Map<Long, Map<Long, List<SourcingQuoteDO>>> quotes) {
        Map<Long, Boolean> of(Long taskId) {
            return byTask.getOrDefault(taskId, Map.of());
        }

        boolean handed(Long taskId, Long user, Long itemId) {
            return handedByTask.getOrDefault(taskId, Map.of()).getOrDefault(user, Set.of()).contains(itemId);
        }

        List<SourcingQuoteDO> own(Long itemId, Long user) {
            return quotes.getOrDefault(itemId, Map.of()).getOrDefault(user, List.of());
        }

        /** 任务转交过时，接手人不算没交给他、他也没报过价的型号（这些型号上一个人已经有结果） */
        boolean counts(InquiryItemDO item, Long user) {
            Map<Long, Set<Long>> handed = handedByTask.getOrDefault(item.getSourcingTaskId(), Map.of());
            return handed.isEmpty() || handed.values().stream().anyMatch(items -> items.contains(item.getId()))
                    || !own(item.getId(), user).isEmpty();
        }
    }

    private Participants participants(List<InquiryItemDO> items) {
        List<Long> taskIds = items.stream().map(InquiryItemDO::getSourcingTaskId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Map<Long, Set<Long>>> handed = new HashMap<>();
        Map<Long, Map<Long, Boolean>> byTask = new HashMap<>();
        if (taskIds.isEmpty()) {
            return new Participants(byTask, handed, Map.of());
        }
        handoffMapper.selectList(new LambdaQueryWrapper<SourcingItemHandoffDO>()
                        .in(SourcingItemHandoffDO::getTaskId, taskIds)
                        .isNull(SourcingItemHandoffDO::getDeletedAt))
                .forEach(h -> handed.computeIfAbsent(h.getTaskId(), k -> new HashMap<>())
                        .computeIfAbsent(h.getFromUserId(), k -> new HashSet<>()).add(h.getInquiryItemId()));
        List<SourcingTaskAssigneeDO> assignees = assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .in(SourcingTaskAssigneeDO::getTaskId, taskIds)
                .isNull(SourcingTaskAssigneeDO::getDeletedAt));
        for (SourcingTaskAssigneeDO a : assignees) {
            boolean didWork = Objects.equals(a.getActive(), 1)
                    || handed.getOrDefault(a.getTaskId(), Map.of()).containsKey(a.getAssigneeId());
            if (didWork) {
                byTask.computeIfAbsent(a.getTaskId(), k -> new HashMap<>())
                        .merge(a.getAssigneeId(), Objects.equals(a.getAssignMode(), InquiryConstants.ASSIGN_SELF), Boolean::logicalOr);
            }
        }
        Map<Long, Map<Long, List<SourcingQuoteDO>>> quotes = handed.isEmpty() ? Map.of() : quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                        .in(SourcingQuoteDO::getTaskId, handed.keySet())
                        .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                        .isNull(SourcingQuoteDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(SourcingQuoteDO::getInquiryItemId,
                        Collectors.groupingBy(SourcingQuoteDO::getQuotedBy)));
        return new Participants(byTask, handed, quotes);
    }

    private void workload(SourcingStatsVO vo, List<InquiryItemDO> items, Participants p, Map<Long, String> monthOfInquiry) {
        Map<String, int[]> months = new TreeMap<>();
        int[] total = new int[2];
        for (InquiryItemDO i : items) {
            p.of(i.getSourcingTaskId()).forEach((user, self) -> {
                if (!p.counts(i, user)) {
                    return;
                }
                int[] m = months.computeIfAbsent(monthOfInquiry.get(i.getCustomerInquiryId()), k -> new int[2]);
                int k = Boolean.TRUE.equals(self) ? 0 : 1;
                m[k]++;
                total[k]++;
            });
        }
        List<SourcingStatsVO.WorkloadRow> rows = new ArrayList<>();
        months.forEach((k, m) -> rows.add(workloadRow(k,
                Integer.parseInt(k.substring(0, 4)) + " 年 " + Integer.parseInt(k.substring(5)) + " 月", m)));
        vo.setWorkload(rows);
        vo.setWorkloadTotal(workloadRow("", "合计", total));
    }

    private static SourcingStatsVO.WorkloadRow workloadRow(String key, String label, int[] m) {
        SourcingStatsVO.WorkloadRow r = new SourcingStatsVO.WorkloadRow();
        r.setKey(key);
        r.setLabel(label);
        r.setSelfItems(m[0]);
        r.setPurchaseItems(m[1]);
        r.setSelfRate(rate(m[0], m[0] + m[1]));
        return r;
    }

    /**
     * 按人员（型号口径）：转出的型号只计入型号数和转出；转交过的型号按这个人自己的结果算，
     * 否则比价后成本价仍用上一轮价格时，新一轮无货的人会被算成有价。
     */
    private List<SourcingStatsVO.PurchaserRow> purchasers(List<InquiryItemDO> items, Participants p) {
        Map<Long, int[]> byUser = new LinkedHashMap<>();
        Set<Long> salesUsers = new HashSet<>();
        for (InquiryItemDO i : items) {
            p.of(i.getSourcingTaskId()).forEach((user, self) -> {
                if (Boolean.TRUE.equals(self)) {
                    salesUsers.add(user);
                }
                if (!p.counts(i, user)) {
                    return;
                }
                int[] c = byUser.computeIfAbsent(user, k -> new int[5]);
                c[0]++;
                if (p.handed(i.getSourcingTaskId(), user, i.getId())) {
                    c[4]++;
                    return;
                }
                List<SourcingQuoteDO> mine = p.own(i.getId(), user);
                int s = Objects.requireNonNullElse(i.getQuoteStatus(), InquiryConstants.ITEM_PENDING);
                if (!mine.isEmpty()) {
                    s = mine.stream().anyMatch(q -> !Objects.equals(q.getNoStock(), 1))
                            ? InquiryConstants.ITEM_PRICED : InquiryConstants.ITEM_NO_STOCK;
                }
                c[s == InquiryConstants.ITEM_PRICED ? 1 : s == InquiryConstants.ITEM_NO_STOCK ? 2 : 3]++;
            });
        }
        Map<Long, String> names = lookups.userNames(byUser.keySet());
        List<SourcingStatsVO.PurchaserRow> out = new ArrayList<>();
        byUser.forEach((user, c) -> {
            SourcingStatsVO.PurchaserRow r = new SourcingStatsVO.PurchaserRow();
            r.setPurchaserId(user);
            r.setPurchaserName(names.getOrDefault(user, String.valueOf(user)));
            r.setItems(c[0]);
            r.setPriced(c[1]);
            r.setNoStock(c[2]);
            r.setPending(c[3]);
            r.setTransferred(c[4]);
            r.setPricedRate(rate(c[1], c[0] - c[4]));
            r.setRole(salesUsers.contains(user) ? "SALES" : lookups.isPartTime(user) ? "PART_TIME" : "PURCHASER");
            out.add(r);
        });
        out.sort(Comparator.comparingInt(SourcingStatsVO.PurchaserRow::getItems).reversed());
        return out;
    }

    private static SourcingStatsVO.MonthRow monthRow(String key, String label, int[] m) {
        SourcingStatsVO.MonthRow r = new SourcingStatsVO.MonthRow();
        r.setKey(key);
        r.setLabel(label);
        r.setTotal(m[0]);
        r.setCompleted(m[1]);
        r.setUnquotable(m[2]);
        r.setInProgress(m[3]);
        r.setCompletedRate(rate(m[1], m[0]));
        r.setUnquotableRate(rate(m[2], m[0]));
        return r;
    }

    private static BigDecimal rate(int n, int d) {
        return d == 0 ? null
                : BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(d), 2, RoundingMode.HALF_UP);
    }
}
