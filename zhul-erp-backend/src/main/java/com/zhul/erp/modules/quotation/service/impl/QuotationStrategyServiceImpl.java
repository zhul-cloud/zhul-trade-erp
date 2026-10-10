package com.zhul.erp.modules.quotation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.dto.PriceHistoryVO;
import com.zhul.erp.modules.quotation.dto.StrategyTierDTO;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.quotation.service.QuotationStrategyService;
import com.zhul.erp.modules.quotation.support.PricingSettings;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuotationStrategyServiceImpl implements QuotationStrategyService {

    private static final String KIND_ORDER = "ORDER";
    private static final String KIND_QUOTATION = "QUOTATION";
    private static final int MAX_TIERS = 6;
    private static final BigDecimal MAX_MARGIN = BigDecimal.valueOf(95);
    /** 有历史价的报价单状态：发给过客户的（作废、草稿除外） */
    private static final Set<Integer> SENT_STATUSES = Set.of(QuotationConstants.STATUS_SENT, QuotationConstants.STATUS_WON,
            QuotationConstants.STATUS_PARTIAL, QuotationConstants.STATUS_LOST);

    private final QuotationStore store;
    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final PricingSettings settings;
    private final ObjectMapper objectMapper;
    private final LogService logService;

    // ---------------------------------------------------------------- 沿用历史价

    /** 历史价的一条候选：按日期取最近，成交优先于报价 */
    private record Hit(String kind, Long docId, String docNo, LocalDate date, String currency, BigDecimal price) {
    }

    @Override
    public List<PriceHistoryVO> priceHistory(Long quotationId, Integer versionNo) {
        QuotationStore.View view = store.view(quotationId, versionNo);
        QuotationDO q = view.header();
        Map<String, List<QuotationItemDO>> linesByKey = new HashMap<>();
        for (QuotationItemDO i : view.items()) {
            String key = MpnNormalizer.normalize(i.getModel());
            if (StringUtils.hasText(key)) {
                linesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }
        if (linesByKey.isEmpty()) {
            return List.of();
        }
        Map<String, Hit> orders = orderHits(q, linesByKey.keySet());
        Map<String, Hit> quotes = quotationHits(q, linesByKey.keySet());
        List<PriceHistoryVO> list = new ArrayList<>();
        for (QuotationItemDO i : view.items()) {
            String key = MpnNormalizer.normalize(i.getModel());
            Hit h = orders.containsKey(key) ? orders.get(key) : quotes.get(key);
            if (h == null) {
                continue;
            }
            PriceHistoryVO vo = new PriceHistoryVO();
            vo.setItemId(i.getId());
            vo.setKind(h.kind());
            vo.setDocId(h.docId());
            vo.setDocNo(h.docNo());
            vo.setDate(h.date());
            vo.setCurrencyCode(h.currency());
            vo.setUnitPrice(h.price());
            list.add(vo);
        }
        return list;
    }

    /** 同一客户有效销售订单里的同型号，取销售日期最近的一条 */
    private Map<String, Hit> orderHits(QuotationDO q, Set<String> keys) {
        Map<Long, SalesOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .select(SalesOrderDO::getId, SalesOrderDO::getSoNo, SalesOrderDO::getCurrencyCode, SalesOrderDO::getSalesDate,
                                SalesOrderDO::getCreateTime)
                        .eq(SalesOrderDO::getTenantId, q.getTenantId())
                        .eq(SalesOrderDO::getCustomerId, q.getCustomerId())
                        .eq(SalesOrderDO::getStatus, 1)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderDO::getId, Function.identity()));
        Map<String, Hit> hits = new HashMap<>();
        if (orders.isEmpty()) {
            return hits;
        }
        for (SalesOrderItemDO i : orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .select(SalesOrderItemDO::getSoId, SalesOrderItemDO::getModel, SalesOrderItemDO::getUnitPrice)
                .in(SalesOrderItemDO::getSoId, orders.keySet())
                .gt(SalesOrderItemDO::getUnitPrice, BigDecimal.ZERO)
                .isNull(SalesOrderItemDO::getDeletedAt))) {
            String key = MpnNormalizer.normalize(i.getModel());
            if (!keys.contains(key)) {
                continue;
            }
            SalesOrderDO o = orders.get(i.getSoId());
            LocalDate date = o.getSalesDate() != null ? o.getSalesDate() : o.getCreateTime().toLocalDate();
            keep(hits, key, new Hit(KIND_ORDER, o.getId(), o.getSoNo(), date, o.getCurrencyCode(), i.getUnitPrice()));
        }
        return hits;
    }

    /** 同一客户发给过客户的其他报价单（当前有效版本）里的同型号，取发送日期最近的一条 */
    private Map<String, Hit> quotationHits(QuotationDO q, Set<String> keys) {
        Map<Long, QuotationDO> quotations = quotationMapper.selectList(new LambdaQueryWrapper<QuotationDO>()
                        .select(QuotationDO::getId, QuotationDO::getQuotationNo, QuotationDO::getCurrencyCode, QuotationDO::getSentAt,
                                QuotationDO::getCreateTime)
                        .eq(QuotationDO::getTenantId, q.getTenantId())
                        .eq(QuotationDO::getCustomerId, q.getCustomerId())
                        .ne(QuotationDO::getId, q.getId())
                        .in(QuotationDO::getStatus, SENT_STATUSES)
                        .isNull(QuotationDO::getDeletedAt))
                .stream().collect(Collectors.toMap(QuotationDO::getId, Function.identity()));
        Map<String, Hit> hits = new HashMap<>();
        if (quotations.isEmpty()) {
            return hits;
        }
        for (QuotationItemDO i : quotationItemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .select(QuotationItemDO::getQuotationId, QuotationItemDO::getModel, QuotationItemDO::getUnitPrice)
                .in(QuotationItemDO::getQuotationId, quotations.keySet())
                .eq(QuotationItemDO::getIsCurrent, 1)
                .gt(QuotationItemDO::getUnitPrice, BigDecimal.ZERO)
                .isNull(QuotationItemDO::getDeletedAt))) {
            String key = MpnNormalizer.normalize(i.getModel());
            if (!keys.contains(key)) {
                continue;
            }
            QuotationDO x = quotations.get(i.getQuotationId());
            LocalDate date = (x.getSentAt() != null ? x.getSentAt() : x.getCreateTime()).toLocalDate();
            keep(hits, key, new Hit(KIND_QUOTATION, x.getId(), x.getQuotationNo(), date, x.getCurrencyCode(), i.getUnitPrice()));
        }
        return hits;
    }

    private static void keep(Map<String, Hit> hits, String key, Hit h) {
        Hit old = hits.get(key);
        if (old == null || h.date().isAfter(old.date())) {
            hits.put(key, h);
        }
    }

    // ---------------------------------------------------------------- 按金额分层毛利

    @Override
    public List<StrategyTierDTO> tiers() {
        String json = settings.value(tenantId(), QuotationConstants.CONFIG_STRATEGY_TIERS);
        if (!StringUtils.hasText(json)) {
            return defaults();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<StrategyTierDTO>>() { });
        } catch (JsonProcessingException e) {
            return defaults();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StrategyTierDTO> saveTiers(List<StrategyTierDTO> tiers) {
        List<StrategyTierDTO> clean = validate(tiers);
        List<StrategyTierDTO> before = tiers();
        try {
            settings.setConfig(tenantId(), QuotationConstants.CONFIG_STRATEGY_TIERS, objectMapper.writeValueAsString(clean));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("分层档位序列化失败", e);
        }
        logService.recordOperateLog(QuotationConstants.MENU, "修改报价策略分层档位", Map.of("tiers", describe(before)),
                Map.of("tiers", describe(clean)));
        return clean;
    }

    /** 1–6 档，上限递增，最后一档没有上限，毛利率 0–95% */
    private static List<StrategyTierDTO> validate(List<StrategyTierDTO> tiers) {
        if (tiers == null || tiers.isEmpty() || tiers.size() > MAX_TIERS) {
            throw new BizException("分层需要 1 到 " + MAX_TIERS + " 档");
        }
        List<StrategyTierDTO> clean = new ArrayList<>(tiers.size());
        BigDecimal prev = null;
        for (int n = 0; n < tiers.size(); n++) {
            StrategyTierDTO t = tiers.get(n);
            boolean last = n == tiers.size() - 1;
            if (t.getMarginRate() == null || t.getMarginRate().signum() < 0 || t.getMarginRate().compareTo(MAX_MARGIN) > 0) {
                throw new BizException("第 " + (n + 1) + " 档的毛利率需要在 0–95% 之间");
            }
            if (last != (t.getMaxCost() == null)) {
                throw new BizException(last ? "最后一档不设上限" : "第 " + (n + 1) + " 档请填写采购成本价上限");
            }
            if (t.getMaxCost() != null) {
                if (t.getMaxCost().signum() <= 0 || (prev != null && t.getMaxCost().compareTo(prev) <= 0)) {
                    throw new BizException("各档的采购成本价上限需要大于 0 且逐档递增");
                }
                prev = t.getMaxCost();
            }
            StrategyTierDTO c = new StrategyTierDTO();
            c.setMaxCost(t.getMaxCost() == null ? null : t.getMaxCost().setScale(2, RoundingMode.HALF_UP));
            c.setMarginRate(t.getMarginRate().setScale(1, RoundingMode.HALF_UP));
            clean.add(c);
        }
        return clean;
    }

    private static List<StrategyTierDTO> defaults() {
        return List.of(tier("300", "35"), tier("3000", "20"), tier(null, "12"));
    }

    private static StrategyTierDTO tier(String max, String margin) {
        StrategyTierDTO t = new StrategyTierDTO();
        t.setMaxCost(max == null ? null : new BigDecimal(max));
        t.setMarginRate(new BigDecimal(margin));
        return t;
    }

    private static String describe(List<StrategyTierDTO> tiers) {
        return tiers.stream().map(t -> (t.getMaxCost() == null ? "以上" : "≤" + t.getMaxCost().stripTrailingZeros().toPlainString())
                + " " + t.getMarginRate().stripTrailingZeros().toPlainString() + "%").collect(Collectors.joining("，"));
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
