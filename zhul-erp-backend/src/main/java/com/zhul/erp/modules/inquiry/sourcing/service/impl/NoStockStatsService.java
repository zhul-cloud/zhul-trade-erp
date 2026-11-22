package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.NoStockStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.support.InquiryCategories;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** 询价统计 → 无货分析（spec inquiry/sourcing-stats「无货分析」），只读 */
@Service
@RequiredArgsConstructor
public class NoStockStatsService {

    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";
    private static final int TREND = 8;
    private static final String UNCATEGORIZED = "未分类";

    private final SourcingQuoteMapper quoteMapper;
    private final InquiryItemMapper itemMapper;
    private final InquiryCategories inquiryCategories;
    private final QuoteDicts quoteDicts;

    /** 一期：起止日期与名称 */
    record Range(LocalDate from, LocalDate to, String label) {
    }

    static Range range(String period, LocalDate day) {
        if (WEEK.equals(period)) {
            LocalDate from = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new Range(from, from.plusDays(6), from.toString().substring(5));
        }
        LocalDate from = day.withDayOfMonth(1);
        return new Range(from, from.with(TemporalAdjusters.lastDayOfMonth()), from.toString().substring(0, 7));
    }

    static Range previous(String period, Range r) {
        return range(period, WEEK.equals(period) ? r.from().minusWeeks(1) : r.from().minusMonths(1));
    }

    /** 每个型号在一期里最后一次回价 */
    private record Model(String brandKey, String brand, Long itemId, boolean noStock, Integer reason) {
    }

    public NoStockStatsVO stats(String period, LocalDate date) {
        String p = period == null ? WEEK : period.trim().toUpperCase();
        if (!WEEK.equals(p) && !MONTH.equals(p)) {
            throw new BizException("统计周期只能按周或按月");
        }
        Range cur = range(p, date == null ? LocalDate.now() : date);
        List<Range> periods = new ArrayList<>(TREND);
        Range r = cur;
        for (int i = 0; i < TREND; i++) {
            periods.add(0, r);
            r = previous(p, r);
        }
        List<SourcingQuoteDO> rows = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .select(SourcingQuoteDO::getId, SourcingQuoteDO::getInquiryItemId, SourcingQuoteDO::getBrand,
                        SourcingQuoteDO::getBrandKey, SourcingQuoteDO::getModelKey, SourcingQuoteDO::getNoStock,
                        SourcingQuoteDO::getNoStockReason, SourcingQuoteDO::getQuotedAt)
                .eq(SourcingQuoteDO::getTenantId, PiStore.tenantId())
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                .isNull(SourcingQuoteDO::getDeletedAt)
                .ge(SourcingQuoteDO::getQuotedAt, periods.get(0).from().atStartOfDay())
                .lt(SourcingQuoteDO::getQuotedAt, cur.to().plusDays(1).atStartOfDay()));

        NoStockStatsVO vo = new NoStockStatsVO();
        vo.setPeriod(p);
        vo.setDateFrom(cur.from());
        vo.setDateTo(cur.to());
        Map<String, Model> current = Map.of();
        for (Range x : periods) {
            Map<String, Model> models = latest(rows, x);
            NoStockStatsVO.Trend t = new NoStockStatsVO.Trend();
            t.setLabel(x.label());
            t.setDateFrom(x.from());
            t.setDateTo(x.to());
            t.setModels(models.size());
            t.setNoStock((int) models.values().stream().filter(Model::noStock).count());
            t.setRate(rate(t.getNoStock(), t.getModels()));
            vo.getTrend().add(t);
            if (x.equals(cur)) {
                current = models;
                vo.setTotals(totals(models));
            } else if (x.equals(previous(p, cur))) {
                vo.setPrevious(totals(models));
            }
        }
        var reasons = quoteDicts.noStockReasons();
        vo.setBrands(groups(current, m -> m.brandKey(), m -> m.brand(), m -> reasons.label(m.reason())));
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        Map<Long, Long> itemCategory = new HashMap<>();
        List<Long> itemIds = current.values().stream().map(Model::itemId).filter(Objects::nonNull).distinct().toList();
        if (!itemIds.isEmpty()) {
            itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                            .select(InquiryItemDO::getId, InquiryItemDO::getCategoryId, InquiryItemDO::getCategory)
                            .in(InquiryItemDO::getId, itemIds))
                    .forEach(i -> {
                        Long root = InquiryCategories.rootOfItem(i.getCategoryId(), i.getCategory(), categories);
                        if (root != null && names(categories).containsKey(root)) {
                            itemCategory.put(i.getId(), root);
                        }
                    });
        }
        Map<Long, String> names = names(categories);
        vo.setCategories(groups(current, m -> {
            Long c = m.itemId() == null ? null : itemCategory.get(m.itemId());
            return c == null ? "" : String.valueOf(c);
        }, m -> {
            Long c = m.itemId() == null ? null : itemCategory.get(m.itemId());
            return c == null ? UNCATEGORIZED : names.get(c);
        }, null));
        vo.getCategories().forEach(g -> g.setCategoryId(g.getKey().isEmpty() ? null : Long.valueOf(g.getKey())));
        return vo;
    }

    private static Map<Long, String> names(List<ProductCategoryDO> categories) {
        Map<Long, String> names = new HashMap<>();
        categories.forEach(c -> names.put(c.getId(), InquiryCategories.name(c)));
        return names;
    }

    /** 期内每个型号（品牌 + 归一化型号）最后一次回价 */
    private static Map<String, Model> latest(List<SourcingQuoteDO> rows, Range r) {
        Map<String, SourcingQuoteDO> last = new LinkedHashMap<>();
        for (SourcingQuoteDO q : rows) {
            if (q.getQuotedAt() == null) {
                continue;
            }
            LocalDate d = q.getQuotedAt().toLocalDate();
            if (d.isBefore(r.from()) || d.isAfter(r.to())) {
                continue;
            }
            last.merge(q.getBrandKey() + "|" + q.getModelKey(), q, (a, b) ->
                    Comparator.comparing(SourcingQuoteDO::getQuotedAt).thenComparing(SourcingQuoteDO::getId).compare(a, b) >= 0 ? a : b);
        }
        Map<String, Model> out = new LinkedHashMap<>();
        last.forEach((k, q) -> out.put(k, new Model(q.getBrandKey() == null ? "" : q.getBrandKey(),
                q.getBrand() == null ? "" : q.getBrand(), q.getInquiryItemId(), Objects.equals(q.getNoStock(), 1),
                q.getNoStockReason())));
        return out;
    }

    private static NoStockStatsVO.Totals totals(Map<String, Model> models) {
        NoStockStatsVO.Totals t = new NoStockStatsVO.Totals();
        t.setModels(models.size());
        Map<String, Integer> byBrand = new HashMap<>();
        Map<String, String> brandNames = new HashMap<>();
        for (Model m : models.values()) {
            if (m.noStock()) {
                t.setNoStock(t.getNoStock() + 1);
                byBrand.merge(m.brandKey(), 1, Integer::sum);
                brandNames.putIfAbsent(m.brandKey(), m.brand());
            }
        }
        t.setRate(rate(t.getNoStock(), t.getModels()));
        t.setBrandCount(byBrand.size());
        byBrand.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> {
            t.setTopBrand(brandNames.get(e.getKey()));
            t.setTopBrandNoStock(e.getValue());
        });
        return t;
    }

    private static List<NoStockStatsVO.Group> groups(Map<String, Model> models, Function<Model, String> key,
                                                     Function<Model, String> name, Function<Model, String> reason) {
        Map<String, NoStockStatsVO.Group> out = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> reasons = new HashMap<>();
        for (Model m : models.values()) {
            String k = key.apply(m);
            NoStockStatsVO.Group g = out.computeIfAbsent(k, x -> {
                NoStockStatsVO.Group n = new NoStockStatsVO.Group();
                n.setKey(x);
                n.setName(name.apply(m));
                return n;
            });
            g.setModels(g.getModels() + 1);
            if (m.noStock()) {
                g.setNoStock(g.getNoStock() + 1);
                if (reason != null) {
                    String why = m.reason() == null || m.reason() == 0 ? "未填原因" : reason.apply(m);
                    reasons.computeIfAbsent(k, x -> new LinkedHashMap<>()).merge(why == null || why.isEmpty() ? "未填原因" : why, 1, Integer::sum);
                }
            }
        }
        List<NoStockStatsVO.Group> list = new ArrayList<>();
        for (NoStockStatsVO.Group g : out.values()) {
            if (g.getNoStock() == 0) {
                continue;
            }
            g.setRate(rate(g.getNoStock(), g.getModels()));
            reasons.getOrDefault(g.getKey(), Map.of()).entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> {
                        NoStockStatsVO.Reason x = new NoStockStatsVO.Reason();
                        x.setName(e.getKey());
                        x.setCount(e.getValue());
                        g.getReasons().add(x);
                    });
            list.add(g);
        }
        list.sort(Comparator.comparingInt(NoStockStatsVO.Group::getNoStock).reversed()
                .thenComparing(NoStockStatsVO.Group::getRate, Comparator.reverseOrder()));
        return list;
    }

    /** 无货率：百分比，HALF_UP 保留 1 位 */
    static BigDecimal rate(int noStock, int models) {
        return models == 0 ? null : BigDecimal.valueOf(noStock * 100L).divide(BigDecimal.valueOf(models), 1, RoundingMode.HALF_UP);
    }
}
