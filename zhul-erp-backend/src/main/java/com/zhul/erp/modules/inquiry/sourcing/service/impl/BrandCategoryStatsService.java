package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.BrandCategoryStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.inquiry.support.InquiryCategories;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 品牌品类统计（spec inquiry/sourcing-stats「品牌品类统计」）：所选一期与上一期，询盘日期落在期内、已进入询价、未取消的询盘单的
 * 型号行，按品牌、一级品类汇总询价型号、有价、无货、待回价、有价率与平均回价时长。只读。
 */
@Service
@RequiredArgsConstructor
public class BrandCategoryStatsService {

    static final Set<String> PERIODS = Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");
    private static final String NO_BRAND = "未填品牌";
    private static final String UNCATEGORIZED = "未分类";

    private final CustomerInquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;
    private final SourcingTaskMapper taskMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final InquiryCategories inquiryCategories;
    private final PriceKeys priceKeys;

    /** 一期的起止日期 */
    record Range(LocalDate from, LocalDate to) {
    }

    static Range range(String period, LocalDate day) {
        return switch (period) {
            case "DAY" -> new Range(day, day);
            case "WEEK" -> {
                LocalDate from = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Range(from, from.plusDays(6));
            }
            case "QUARTER" -> {
                LocalDate from = day.withDayOfMonth(1).withMonth((day.getMonthValue() - 1) / 3 * 3 + 1);
                yield new Range(from, from.plusMonths(3).minusDays(1));
            }
            case "YEAR" -> new Range(day.withDayOfYear(1), day.with(TemporalAdjusters.lastDayOfYear()));
            default -> new Range(day.withDayOfMonth(1), day.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }

    /** 型号行与它的回价用时（分钟，没有已提交回价为空） */
    private record Item(InquiryItemDO item, Long minutes) {
    }

    public BrandCategoryStatsVO stats(String period, LocalDate date) {
        String p = period == null ? "MONTH" : period.trim().toUpperCase();
        if (!PERIODS.contains(p)) {
            throw new BizException("统计周期只能按日、周、月、季度或年");
        }
        Range cur = range(p, date == null ? LocalDate.now() : date);
        Range prev = range(p, cur.from().minusDays(1));

        List<CustomerInquiryDO> inquiries = inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                .select(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryDate)
                .eq(CustomerInquiryDO::getTenantId, PiStore.tenantId())
                .isNull(CustomerInquiryDO::getDeletedAt)
                .ge(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_SOURCING)
                .ne(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_CANCELLED)
                .ge(CustomerInquiryDO::getInquiryDate, prev.from())
                .le(CustomerInquiryDO::getInquiryDate, cur.to()));
        List<Long> inquiryIds = inquiries.stream().map(CustomerInquiryDO::getId).toList();
        List<InquiryItemDO> items = inquiryIds.isEmpty() ? List.of() : itemMapper.selectList(
                new LambdaQueryWrapper<InquiryItemDO>()
                        .select(InquiryItemDO::getId, InquiryItemDO::getCustomerInquiryId, InquiryItemDO::getBrand,
                                InquiryItemDO::getBrandId, InquiryItemDO::getCategoryId, InquiryItemDO::getCategory,
                                InquiryItemDO::getQuoteStatus, InquiryItemDO::getSourcingTaskId)
                        .in(InquiryItemDO::getCustomerInquiryId, inquiryIds)
                        .isNull(InquiryItemDO::getDeletedAt));
        Map<Long, Long> minutes = minutes(items, inquiryIds);
        Map<Long, LocalDate> dateOf = inquiries.stream()
                .collect(Collectors.toMap(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryDate));
        List<Item> current = new ArrayList<>();
        List<Item> previous = new ArrayList<>();
        for (InquiryItemDO i : items) {
            Item x = new Item(i, minutes.get(i.getId()));
            (dateOf.get(i.getCustomerInquiryId()).isBefore(cur.from()) ? previous : current).add(x);
        }

        BrandCategoryStatsVO vo = new BrandCategoryStatsVO();
        vo.setPeriod(p);
        vo.setDateFrom(cur.from());
        vo.setDateTo(cur.to());
        vo.setTotals(row("", "合计", current));
        vo.setPrevious(row("", "合计", previous));
        // 品牌按品牌库识别后归并（存量 brand_key 有空值、也有建档前的名字），名称用正式品牌名（audit BUS-097）
        Map<String, PriceKeys.BrandRef> refs = new HashMap<>();
        Function<InquiryItemDO, PriceKeys.BrandRef> brandOf = i -> refs.computeIfAbsent(
                i.getBrand() == null ? "" : i.getBrand().trim().toLowerCase(), k -> priceKeys.brand(i.getBrand()));
        vo.setBrands(groups(current,
                x -> brandKey(x.item(), brandOf),
                x -> brandKey(x.item(), brandOf).isEmpty() ? NO_BRAND
                        : priceKeys.brandText(x.item().getBrandId() != null
                                ? new PriceKeys.BrandRef(x.item().getBrandId(), "#" + x.item().getBrandId())
                                : brandOf.apply(x.item()), x.item().getBrand())));
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        Map<Long, String> names = new HashMap<>();
        categories.forEach(c -> names.put(c.getId(), InquiryCategories.name(c)));
        Map<Long, Long> rootOf = new HashMap<>();
        for (Item x : current) {
            Long root = InquiryCategories.rootOfItem(x.item().getCategoryId(), x.item().getCategory(), categories);
            if (root != null && names.containsKey(root)) {
                rootOf.put(x.item().getId(), root);
            }
        }
        vo.setCategories(groups(current,
                x -> rootOf.containsKey(x.item().getId()) ? String.valueOf(rootOf.get(x.item().getId())) : "",
                x -> rootOf.containsKey(x.item().getId()) ? names.get(rootOf.get(x.item().getId())) : UNCATEGORIZED));
        vo.getCategories().forEach(r -> r.setCategoryId(r.getKey().isEmpty() ? null : Long.valueOf(r.getKey())));
        return vo;
    }

    /** 品牌归并键：型号行已关联品牌时用它，否则按品牌名识别（识别不到为小写品牌名）；没填品牌为空串 */
    private static String brandKey(InquiryItemDO i, Function<InquiryItemDO, PriceKeys.BrandRef> brandOf) {
        if (i.getBrandId() != null) {
            return "#" + i.getBrandId();
        }
        if (i.getBrand() == null || i.getBrand().isBlank()) {
            return "";
        }
        String k = brandOf.apply(i).brandKey();
        return k == null ? "" : k;
    }

    /** 每个已回价型号：任务首次分配到第一条已提交回价的分钟数，回价早于分配按 0 */
    private Map<Long, Long> minutes(List<InquiryItemDO> items, List<Long> inquiryIds) {
        List<Long> taskIds = items.stream().map(InquiryItemDO::getSourcingTaskId).filter(Objects::nonNull)
                .distinct().toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDateTime> assigned = new HashMap<>();
        taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                        .select(SourcingTaskDO::getId, SourcingTaskDO::getFirstAssignedAt)
                        .in(SourcingTaskDO::getId, taskIds)
                        .isNotNull(SourcingTaskDO::getFirstAssignedAt))
                .forEach(t -> assigned.put(t.getId(), t.getFirstAssignedAt()));
        Map<Long, LocalDateTime> first = new HashMap<>();
        quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                        .select(SourcingQuoteDO::getInquiryItemId, SourcingQuoteDO::getQuotedAt)
                        .eq(SourcingQuoteDO::getTenantId, PiStore.tenantId())
                        .in(SourcingQuoteDO::getCustomerInquiryId, inquiryIds)
                        .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                        .isNotNull(SourcingQuoteDO::getQuotedAt)
                        .isNull(SourcingQuoteDO::getDeletedAt))
                .forEach(q -> first.merge(q.getInquiryItemId(), q.getQuotedAt(), (a, b) -> a.isBefore(b) ? a : b));
        Map<Long, Long> out = new HashMap<>();
        for (InquiryItemDO i : items) {
            LocalDateTime from = assigned.get(i.getSourcingTaskId());
            LocalDateTime to = first.get(i.getId());
            if (from != null && to != null) {
                out.put(i.getId(), Math.max(0, Duration.between(from, to).toMinutes()));
            }
        }
        return out;
    }

    private static List<BrandCategoryStatsVO.Row> groups(List<Item> items, Function<Item, String> key,
                                                         Function<Item, String> name) {
        Map<String, List<Item>> byKey = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Item x : items) {
            String k = key.apply(x);
            byKey.computeIfAbsent(k, n -> new ArrayList<>()).add(x);
            names.computeIfAbsent(k, n -> name.apply(x));
        }
        List<BrandCategoryStatsVO.Row> rows = new ArrayList<>(byKey.size());
        byKey.forEach((k, list) -> rows.add(row(k, names.get(k), list)));
        rows.sort(Comparator.comparingInt(BrandCategoryStatsVO.Row::getItems).reversed()
                .thenComparing(BrandCategoryStatsVO.Row::getName));
        return rows;
    }

    /** 有价率 HALF_UP 保留 2 位（百分比），平均回价时长 HALF_UP 保留 1 位（小时） */
    private static BrandCategoryStatsVO.Row row(String key, String name, List<Item> items) {
        BrandCategoryStatsVO.Row r = new BrandCategoryStatsVO.Row();
        r.setKey(key);
        r.setName(name);
        r.setItems(items.size());
        long total = 0;
        int answered = 0;
        for (Item x : items) {
            Integer s = x.item().getQuoteStatus();
            if (Objects.equals(s, InquiryConstants.ITEM_PRICED)) {
                r.setPriced(r.getPriced() + 1);
            } else if (Objects.equals(s, InquiryConstants.ITEM_NO_STOCK)) {
                r.setNoStock(r.getNoStock() + 1);
            } else {
                r.setPending(r.getPending() + 1);
            }
            if (x.minutes() != null) {
                total += x.minutes();
                answered++;
            }
        }
        r.setPricedRate(r.getItems() == 0 ? null : BigDecimal.valueOf(r.getPriced() * 100L)
                .divide(BigDecimal.valueOf(r.getItems()), 2, RoundingMode.HALF_UP));
        r.setAvgHours(answered == 0 ? null : BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(60L * answered), 1, RoundingMode.HALF_UP));
        return r;
    }
}
