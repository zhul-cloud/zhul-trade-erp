package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.quotation.event.QuoteSupplyConfirmedEvent;
import com.zhul.erp.modules.quotation.support.QuoteSupplyHints;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报价行供应提示与发送确认留痕（OpenSpec supply-chain-phase3-quote-fulfillment design 决策 1–4）。
 * <p>
 * 提示实时计算不入库：成本来源询价记录超过证据有效天数提示「成本 N 天前」；需求对象在覆盖矩阵且不是「可直接报价」时提示货源状态。
 * 发送确认在报价事务提交后，在当前请求线程另起事务按行写人工处理记录（来源 5，来源ID 为报价行ID，唯一键去重），失败只记日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuoteSupplyCheck implements QuoteSupplyHints {

    private final SupplySourceMapper sourceMapper;
    private final ScDemandMapper demandMapper;
    private final ScFollowupLogMapper logMapper;
    private final BrandResolver brandResolver;
    private final SupplyLookups lookups;
    private final PlatformTransactionManager txManager;

    @Override
    public Map<Long, List<String>> hints(List<Line> lines) {
        return hints(lines, LocalDate.now());
    }

    /** 调用方负责租户上下文 */
    public Map<Long, List<String>> hints(List<Line> lines, LocalDate today) {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        if (lines.isEmpty()) {
            return out;
        }
        int days = lookups.evidenceDays();
        Map<Long, LocalDate> quoted = quoteDates(lines);
        Map<String, ScDemandDO> demands = demands(lines);
        Map<String, java.util.Optional<Long>> brands = new HashMap<>();
        for (Line l : lines) {
            List<String> hs = new ArrayList<>(2);
            LocalDate at = l.costQuoteId() == null ? null : quoted.get(l.costQuoteId());
            if (at != null && !DemandRules.evidenceValid(at, today, days)) {
                hs.add("成本 " + ChronoUnit.DAYS.between(at, today) + " 天前");
            }
            String cover = coverHint(demands.get(key(l, brands)));
            if (cover != null) {
                hs.add(cover);
            }
            if (!hs.isEmpty()) {
                out.put(l.itemId(), hs);
            }
        }
        return out;
    }

    /** 发送确认：报价事务提交后，按行写人工处理记录；找不到需求对象的行跳过 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(QuoteSupplyConfirmedEvent e) {
        if (e.lines() == null || e.lines().isEmpty()) {
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            TenantContext.runAs(e.tenantId(), () -> tx.executeWithoutResult(s -> record(e)));
        } catch (RuntimeException ex) {
            // 提交后的监听里抛出会让已成功的发送请求报错，所以只记日志
            log.warn("报价发送确认写供应链时间线失败，tenantId={}，quotationNo={}", e.tenantId(), e.quotationNo(), ex);
        }
    }

    /** 调用方负责租户上下文与事务；返回写入条数 */
    public int record(QuoteSupplyConfirmedEvent e) {
        List<Line> lines = e.lines().stream().map(x -> new Line(x.itemId(), x.brand(), x.model(), null)).toList();
        Map<String, ScDemandDO> demands = demands(lines);
        Map<String, java.util.Optional<Long>> brands = new HashMap<>();
        int n = 0;
        for (QuoteSupplyConfirmedEvent.Line x : e.lines()) {
            ScDemandDO d = demands.get(key(new Line(x.itemId(), x.brand(), x.model(), null), brands));
            if (d == null) {
                continue;
            }
            ScFollowupLogDO l = new ScFollowupLogDO();
            l.setTenantId(PiStore.tenantId());
            l.setRecordType(SupplyConstants.LOG_MANUAL);
            l.setSourceType(SupplyConstants.LOG_SRC_QUOTE_CONFIRM);
            l.setSourceId(x.itemId());
            l.setSourceDocNo(e.quotationNo());
            l.setContent("报价单 " + e.quotationNo() + " 发送前确认了成本和交期（提示：" + String.join("、", x.hints()) + "）");
            l.setDemandIds("[" + d.getId() + "]");
            l.setGapProjectId(0L);
            l.setOperatorId(e.operatorId() == null ? 0L : e.operatorId());
            try {
                logMapper.insert(l);
                n++;
            } catch (DuplicateKeyException dup) {
                log.debug("报价行已登记过发送确认，itemId={}", x.itemId());
            }
        }
        return n;
    }

    private Map<Long, LocalDate> quoteDates(List<Line> lines) {
        List<Long> ids = lines.stream().map(Line::costQuoteId).filter(Objects::nonNull).distinct().toList();
        Map<Long, LocalDate> out = new HashMap<>(ids.size() * 2);
        if (ids.isEmpty()) {
            return out;
        }
        for (Map<String, Object> m : sourceMapper.quoteDates(ids)) {
            Object at = m.get("quotedAt");
            LocalDateTime t = at instanceof Timestamp ts ? ts.toLocalDateTime() : at instanceof LocalDateTime ldt ? ldt : null;
            if (t != null) {
                out.put(((Number) m.get("id")).longValue(), t.toLocalDate());
            }
        }
        return out;
    }

    /** 品牌 + 归一化型号 → 需求对象（批量读一次）；品牌识别不到的行没有 */
    private Map<String, ScDemandDO> demands(List<Line> lines) {
        Map<String, ScDemandDO> out = new HashMap<>();
        List<String> scopes = lines.stream().map(l -> MpnNormalizer.normalize(l.model())).filter(StringUtils::hasText)
                .distinct().map(k -> DemandRegistry.scopeKey(k, null)).toList();
        if (scopes.isEmpty()) {
            return out;
        }
        for (ScDemandDO d : demandMapper.selectList(new LambdaQueryWrapper<ScDemandDO>()
                .in(ScDemandDO::getScopeKey, scopes).isNull(ScDemandDO::getDeletedAt))) {
            out.put(d.getBrandId() + "|" + d.getModelKey(), d);
        }
        return out;
    }

    /** 同一次调用里品牌只识别一次 */
    private String key(Line l, Map<String, java.util.Optional<Long>> brands) {
        Long brandId = brands.computeIfAbsent(BrandResolver.key(l.brand()),
                k -> java.util.Optional.ofNullable(brandResolver.resolve(l.brand()))).orElse(null);
        String model = MpnNormalizer.normalize(l.model());
        return brandId == null || !StringUtils.hasText(model) ? "" : brandId + "|" + model;
    }

    private static String coverHint(ScDemandDO d) {
        if (d == null || d.getStatus() == null || d.getStatus() != SupplyConstants.DEMAND_IN_MATRIX || d.getCoverageStatus() == null) {
            return null;
        }
        return switch (d.getCoverageStatus()) {
            case SupplyConstants.COVER_CONFIRM -> "货源需确认";
            case SupplyConstants.COVER_LEAD -> "货源未验证";
            case SupplyConstants.COVER_NO_CHANNEL -> "无稳定渠道";
            default -> null;
        };
    }
}
