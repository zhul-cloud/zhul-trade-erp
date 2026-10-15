package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.SourcingStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 询价统计（spec inquiry/sourcing-stats）：询盘日期在范围内、已进入询价（状态 ≥ 询价中）、未取消的询盘单。
 * 按月份按询盘单归类：有待回价型号为「询价中」，否则至少一个有价为「询价完成」，全部无货为「无法报价」；
 * 按采购员按型号统计（询价任务的有效分配，分给多人时每人各算一次）。只读。
 */
@Service
@RequiredArgsConstructor
public class SourcingStatsService {

    private final CustomerInquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final InquiryLookups lookups;

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
        vo.setPurchasers(purchasers(items));
        return vo;
    }

    private List<SourcingStatsVO.PurchaserRow> purchasers(List<InquiryItemDO> items) {
        List<Long> taskIds = items.stream().map(InquiryItemDO::getSourcingTaskId).filter(Objects::nonNull).distinct().toList();
        if (taskIds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> assignees = new HashMap<>();
        assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                        .in(SourcingTaskAssigneeDO::getTaskId, taskIds)
                        .eq(SourcingTaskAssigneeDO::getActive, 1)
                        .isNull(SourcingTaskAssigneeDO::getDeletedAt))
                .forEach(a -> assignees.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(a.getAssigneeId()));
        Map<Long, int[]> byUser = new LinkedHashMap<>();
        for (InquiryItemDO i : items) {
            for (Long user : assignees.getOrDefault(i.getSourcingTaskId(), List.of())) {
                int[] c = byUser.computeIfAbsent(user, k -> new int[4]);
                c[0]++;
                int s = Objects.requireNonNullElse(i.getQuoteStatus(), InquiryConstants.ITEM_PENDING);
                c[s == InquiryConstants.ITEM_PRICED ? 1 : s == InquiryConstants.ITEM_NO_STOCK ? 2 : 3]++;
            }
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
            r.setPricedRate(rate(c[1], c[0]));
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
