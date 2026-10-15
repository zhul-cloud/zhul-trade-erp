package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 我的询价里型号的「历史询价」（spec inquiry/sourcing-quote「我的询价任务」）：
 * 成交 = 同品牌键 + 型号键的询盘型号转成的销售订单行，被下到已下单（未取消）的采购订单上，取不含税人民币单价与下单日期；
 * 询价 = 询价记录里同品牌键 + 型号键、已提交、有价、不属于本任务的回价。
 */
@Component
@RequiredArgsConstructor
public class ItemPriceHistory {

    public static final String DEAL = "DEAL";
    public static final String QUOTE = "QUOTE";
    private static final int LIMIT = 5;

    private final InquiryItemMapper itemMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final PurchaseOrderMapper poMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final SupplierMapper supplierMapper;
    private final QuoteDicts quoteDicts;

    /** 列表里显示的一条：最近成交，没有时最近询价 */
    public record Summary(String kind, BigDecimal price, LocalDate date) {
    }

    public record Deal(BigDecimal price, LocalDate date, String poNo, String supplierName) {
    }

    public record Quote(BigDecimal price, LocalDate date, String channelName, String shopName, String conditionName,
                        String leadTimeName, boolean freeShipping) {
    }

    public record Detail(List<Deal> deals, List<Quote> quotes) {
    }

    public Summary summary(InquiryItemDO item, Long taskId) {
        List<Deal> deals = deals(item, 1, false);
        if (!deals.isEmpty()) {
            return new Summary(DEAL, deals.get(0).price(), deals.get(0).date());
        }
        List<SourcingQuoteDO> quotes = quotes(item, taskId, 1);
        return quotes.isEmpty() ? null
                : new Summary(QUOTE, quotes.get(0).getUnitPriceCny(), quotes.get(0).getQuotedAt().toLocalDate());
    }

    public Detail detail(InquiryItemDO item, Long taskId, boolean showSupplier) {
        QuoteDictSnapshot dicts = quoteDicts.snapshot();
        List<Quote> quotes = quotes(item, taskId, LIMIT).stream().map(q -> new Quote(q.getUnitPriceCny(), q.getQuotedAt().toLocalDate(),
                InquiryConstants.CHANNELS.getOrDefault(q.getChannel(), ""), showSupplier ? q.getShopName() : null,
                dicts.conditions().label(q.getItemCondition()), dicts.leadTimes().label(q.getLeadTime()),
                !Objects.equals(q.getFreeShipping(), 0))).toList();
        return new Detail(deals(item, LIMIT, showSupplier), quotes);
    }

    private List<Deal> deals(InquiryItemDO item, int limit, boolean showSupplier) {
        if (!StringUtils.hasText(item.getBrandKey()) || !StringUtils.hasText(item.getModelKey())) {
            return List.of();
        }
        List<Long> inquiryItems = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                        .select(InquiryItemDO::getId)
                        .eq(InquiryItemDO::getTenantId, item.getTenantId())
                        .eq(InquiryItemDO::getBrandKey, item.getBrandKey())
                        .eq(InquiryItemDO::getModelKey, item.getModelKey())
                        .isNull(InquiryItemDO::getDeletedAt))
                .stream().map(InquiryItemDO::getId).toList();
        List<Long> soItems = inquiryItems.isEmpty() ? List.of() : soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .select(SalesOrderItemDO::getId)
                        .in(SalesOrderItemDO::getInquiryItemId, inquiryItems)
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().map(SalesOrderItemDO::getId).toList();
        List<PurchaseOrderItemDO> poItems = soItems.isEmpty() ? List.of() : poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .in(PurchaseOrderItemDO::getSoItemId, soItems)
                .isNull(PurchaseOrderItemDO::getDeletedAt));
        if (poItems.isEmpty()) {
            return List.of();
        }
        Map<Long, PurchaseOrderDO> pos = poMapper.selectBatchIds(poItems.stream().map(PurchaseOrderItemDO::getPoId).distinct().toList())
                .stream().filter(p -> p.getDeletedAt() == null && Objects.equals(p.getStatus(), PurchaseConstants.PO_ORDERED))
                .collect(Collectors.toMap(PurchaseOrderDO::getId, Function.identity()));
        List<PurchaseOrderItemDO> ordered = poItems.stream().filter(i -> pos.containsKey(i.getPoId()))
                .sorted(Comparator.comparing((PurchaseOrderItemDO i) -> orderDate(pos.get(i.getPoId())),
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(PurchaseOrderItemDO::getId, Comparator.reverseOrder()))
                .limit(limit).toList();
        List<Long> supplierIds = ordered.stream().map(i -> pos.get(i.getPoId()).getSupplierId())
                .filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, String> suppliers = !showSupplier || supplierIds.isEmpty() ? Map.of()
                : supplierMapper.selectBatchIds(supplierIds).stream().collect(Collectors.toMap(SupplierDO::getId, SupplierDO::getName));
        return ordered.stream().map(i -> {
            PurchaseOrderDO po = pos.get(i.getPoId());
            String supplier = !showSupplier ? null
                    : po.getSupplierId() != null && po.getSupplierId() > 0 ? suppliers.get(po.getSupplierId()) : po.getShopName();
            return new Deal(price(i, po), orderDate(po), po.getPoNo(), supplier);
        }).toList();
    }

    /** 不含税人民币单价；老数据没有时按单价 × 汇率 */
    private static BigDecimal price(PurchaseOrderItemDO i, PurchaseOrderDO po) {
        if (i.getNetPriceCny() != null) {
            return i.getNetPriceCny();
        }
        BigDecimal rate = po.getExchangeRate() == null ? BigDecimal.ONE : po.getExchangeRate();
        return i.getUnitPrice() == null ? null : i.getUnitPrice().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate orderDate(PurchaseOrderDO po) {
        return po.getOrderDate() != null ? po.getOrderDate() : po.getOrderedAt() == null ? null : po.getOrderedAt().toLocalDate();
    }

    private List<SourcingQuoteDO> quotes(InquiryItemDO item, Long taskId, int limit) {
        if (!StringUtils.hasText(item.getBrandKey()) || !StringUtils.hasText(item.getModelKey())) {
            return List.of();
        }
        return quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getTenantId, item.getTenantId())
                .eq(SourcingQuoteDO::getBrandKey, item.getBrandKey())
                .eq(SourcingQuoteDO::getModelKey, item.getModelKey())
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                .eq(SourcingQuoteDO::getNoStock, 0)
                .isNotNull(SourcingQuoteDO::getUnitPriceCny)
                .isNotNull(SourcingQuoteDO::getQuotedAt)
                .ne(SourcingQuoteDO::getTaskId, taskId)
                .isNull(SourcingQuoteDO::getDeletedAt)
                .orderByDesc(SourcingQuoteDO::getQuotedAt)
                .last("LIMIT " + limit));
    }
}
