package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceHistoryGroupVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceHistoryQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceMatchVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.PriceHistoryService;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceHistoryServiceImpl implements PriceHistoryService {

    private static final int MATCH_LIMIT = 50;

    private final SourcingQuoteMapper quoteMapper;
    private final InquiryItemMapper itemMapper;
    private final PriceKeys priceKeys;
    private final InquiryLookups lookups;
    private final PermissionChecker perm;

    @Override
    public PriceMatchVO match(String brand, String model) {
        PriceMatchVO vo = new PriceMatchVO();
        vo.setSameBrand(List.of());
        vo.setOtherBrands(List.of());
        String brandKey = priceKeys.brand(brand).brandKey();
        vo.setBrandKey(brandKey);
        String modelKey = PriceKeys.model(model);
        if (!StringUtils.hasText(modelKey)) {
            return vo;
        }
        List<SourcingQuoteDO> priced = quoteMapper.selectList(submitted()
                .eq(SourcingQuoteDO::getModelKey, modelKey)
                .eq(SourcingQuoteDO::getNoStock, 0)
                .orderByDesc(SourcingQuoteDO::getQuotedAt)
                .last("LIMIT " + MATCH_LIMIT));
        List<SourcingQuoteDO> same = priced.stream().filter(q -> brandKey.equals(q.getBrandKey())).toList();
        List<SourcingQuoteDO> others = priced.stream().filter(q -> !brandKey.equals(q.getBrandKey())).toList();
        vo.setSameBrand(toVos(same));
        vo.setOtherBrands(toVos(others));
        SourcingQuoteDO pick = pickDefault(same);
        vo.setDefaultQuoteId(pick == null ? null : pick.getId());
        return vo;
    }

    /** 默认选价：全新原装中最低价；没有全新原装时取最低价；没有有价记录时为空 */
    public static SourcingQuoteDO pickDefault(Collection<SourcingQuoteDO> quotes) {
        List<SourcingQuoteDO> priced = quotes.stream()
                .filter(q -> q.getUnitPriceCny() != null && !Objects.equals(q.getNoStock(), 1)).toList();
        Comparator<SourcingQuoteDO> cheapest = Comparator.comparing(SourcingQuoteDO::getUnitPriceCny)
                .thenComparing(SourcingQuoteDO::getId);
        return priced.stream()
                .filter(q -> Objects.equals(q.getItemCondition(), InquiryConstants.CONDITION_NEW))
                .min(cheapest)
                .orElseGet(() -> priced.stream().min(cheapest).orElse(null));
    }

    @Override
    public List<SourcingQuoteDO> records(String brandKey, String modelKey) {
        return quoteMapper.selectList(submitted()
                .eq(SourcingQuoteDO::getBrandKey, brandKey)
                .eq(SourcingQuoteDO::getModelKey, modelKey)
                .orderByDesc(SourcingQuoteDO::getQuotedAt));
    }

    @Override
    public PageResult<PriceHistoryGroupVO> query(PriceHistoryQuery q) {
        QueryWrapper<SourcingQuoteDO> w = new QueryWrapper<SourcingQuoteDO>()
                .eq("tenant_id", tenantId())
                .eq("status", InquiryConstants.QUOTE_SUBMITTED)
                .isNull("deleted_at");
        String modelKey = PriceKeys.model(q.getModel());
        if (StringUtils.hasText(modelKey)) {
            w.like("model_key", modelKey);
        }
        if (StringUtils.hasText(q.getBrand())) {
            w.eq("brand_key", priceKeys.brand(q.getBrand()).brandKey());
        }
        if (q.getItemCondition() != null) {
            w.eq("item_condition", q.getItemCondition());
        }
        // 没有货源权限时忽略渠道筛选，避免通过筛选结果反推货源
        if (q.getChannel() != null && perm.has(InquiryConstants.PERM_SUPPLIER_VIEW)) {
            w.eq("channel", q.getChannel());
        }
        if (q.getDateFrom() != null) {
            w.ge("quoted_at", q.getDateFrom().atStartOfDay());
        }
        if (q.getDateTo() != null) {
            w.lt("quoted_at", q.getDateTo().plusDays(1).atStartOfDay());
        }
        int size = Math.min(Math.max(q.getPageSize() == null ? 20 : q.getPageSize(), 1), 100);
        IPage<PriceHistoryGroupVO> page = quoteMapper.selectGroups(new Page<>(q.getPage() == null ? 1 : q.getPage(), size), w);
        List<PriceHistoryGroupVO> groups = page.getRecords();
        if (!groups.isEmpty()) {
            List<String> modelKeys = groups.stream().map(PriceHistoryGroupVO::getModelKey).distinct().toList();
            Map<String, List<SourcingQuoteDO>> byGroup = quoteMapper.selectList(submitted()
                            .in(SourcingQuoteDO::getModelKey, modelKeys)
                            .orderByDesc(SourcingQuoteDO::getQuotedAt))
                    .stream().collect(Collectors.groupingBy(r -> r.getBrandKey() + "|" + r.getModelKey(), LinkedHashMap::new, Collectors.toList()));
            Map<Long, String> categories = categories(byGroup.values().stream().flatMap(List::stream)
                    .map(SourcingQuoteDO::getInquiryItemId).toList());
            LocalDate today = LocalDate.now();
            for (PriceHistoryGroupVO g : groups) {
                List<SourcingQuoteDO> records = byGroup.getOrDefault(g.getBrandKey() + "|" + g.getModelKey(), List.of());
                g.setRecords(toVos(records));
                g.setCategory(records.stream().map(r -> categories.get(r.getInquiryItemId()))
                        .filter(StringUtils::hasText).findFirst().orElse(null));
                g.setDaysAgo(g.getLastQuotedAt() == null ? null : ChronoUnit.DAYS.between(g.getLastQuotedAt().toLocalDate(), today));
            }
        }
        return PageResult.of(page.getTotal(), groups);
    }

    @Override
    public List<PriceRecordVO> toVos(Collection<SourcingQuoteDO> quotes) {
        return new ArrayList<>(toVoMap(quotes).values());
    }

    @Override
    public Map<Long, PriceRecordVO> toVoMap(Collection<SourcingQuoteDO> quotes) {
        Map<Long, String> users = lookups.userNames(quotes.stream().map(SourcingQuoteDO::getQuotedBy).toList());
        Map<Long, String> codes = lookups.inquiryCodes(quotes.stream().map(SourcingQuoteDO::getCustomerInquiryId).toList());
        LocalDate today = LocalDate.now();
        // 询价平台、店铺属于供应链内部信息：没有「查看货源信息」权限时不返回
        boolean supplierVisible = !quotes.isEmpty() && perm.has(InquiryConstants.PERM_SUPPLIER_VIEW);
        Map<Long, PriceRecordVO> map = new LinkedHashMap<>(quotes.size() * 2);
        for (SourcingQuoteDO q : quotes) {
            PriceRecordVO vo = new PriceRecordVO();
            vo.setId(q.getId());
            vo.setBrand(q.getBrand());
            vo.setModel(q.getModel());
            vo.setChannel(supplierVisible ? q.getChannel() : null);
            vo.setShopName(supplierVisible ? q.getShopName() : null);
            vo.setNoStock(Objects.equals(q.getNoStock(), 1));
            vo.setCurrencyCode(q.getCurrencyCode());
            vo.setUnitPrice(q.getUnitPrice());
            vo.setUnitPriceCny(q.getUnitPriceCny());
            vo.setTaxIncluded(Objects.equals(q.getTaxIncluded(), 1));
            vo.setTaxRate(q.getTaxRate());
            vo.setItemCondition(q.getItemCondition());
            vo.setLeadTime(q.getLeadTime());
            vo.setNote(q.getNote());
            vo.setRecommended(Objects.equals(q.getRecommended(), 1));
            vo.setQuotedBy(q.getQuotedBy());
            vo.setQuotedByName(users.get(q.getQuotedBy()));
            vo.setQuotedAt(q.getQuotedAt());
            vo.setDaysAgo(q.getQuotedAt() == null ? null : ChronoUnit.DAYS.between(q.getQuotedAt().toLocalDate(), today));
            vo.setCustomerInquiryId(q.getCustomerInquiryId());
            vo.setCustomerInquiryCode(codes.get(q.getCustomerInquiryId()));
            map.put(q.getId(), vo);
        }
        return map;
    }

    /** 型号明细 ID → 品类；导入等没有关联明细的记录（ID 为 0）不查 */
    private Map<Long, String> categories(Collection<Long> itemIds) {
        List<Long> ids = itemIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                        .select(InquiryItemDO::getId, InquiryItemDO::getCategory)
                        .eq(InquiryItemDO::getTenantId, tenantId())
                        .in(InquiryItemDO::getId, ids))
                .stream().filter(i -> StringUtils.hasText(i.getCategory()))
                .collect(Collectors.toMap(InquiryItemDO::getId, InquiryItemDO::getCategory));
    }

    private LambdaQueryWrapper<SourcingQuoteDO> submitted() {
        return new LambdaQueryWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getTenantId, tenantId())
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                .isNull(SourcingQuoteDO::getDeletedAt);
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
