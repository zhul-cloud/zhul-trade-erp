package com.zhul.erp.modules.supply.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.sourcing.event.SourcingQuoteEvent;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.dto.SourceRow;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import com.zhul.erp.modules.supply.service.CoverageService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 与询价任务联动（design 决策 16；tasks 4.4a）：询价记录生效或撤回后，给对应需求对象写处理记录、改覆盖状态、清提醒。
 * <p>
 * 事务边界：询价事务提交后（AFTER_COMMIT）交给命名线程池 supply-quote-pool，在新事务里用 {@link TenantContext#runAs} 处理；
 * 供应链处理失败只记日志，不影响回价。处理记录按「来源类型 + 询价记录 id」唯一，重复触发不重复写；
 * 需求对象还不存在时跳过，由每天的定时任务建好后调用 {@link #backfill} 补登。
 */
@Slf4j
@Component
public class QuoteLinkage {

    private final SupplySourceMapper sourceMapper;
    private final ScDemandMapper demandMapper;
    private final ScFollowupLogMapper logMapper;
    private final DemandRegistry registry;
    private final BrandResolver brandResolver;
    private final SignalImporter importer;
    private final DemandRecalc recalc;
    private final CoverageService coverageService;
    private final SupplierMapper supplierMapper;
    private final InquiryLookups inquiryLookups;
    private final SupplyLookups lookups;
    private final TransactionTemplate tx;
    private final ThreadPoolExecutor pool;

    public QuoteLinkage(SupplySourceMapper sourceMapper, ScDemandMapper demandMapper, ScFollowupLogMapper logMapper,
                        DemandRegistry registry, BrandResolver brandResolver, SignalImporter importer, DemandRecalc recalc,
                        CoverageService coverageService, SupplierMapper supplierMapper, InquiryLookups inquiryLookups,
                        SupplyLookups lookups, TransactionTemplate tx) {
        this.sourceMapper = sourceMapper;
        this.demandMapper = demandMapper;
        this.logMapper = logMapper;
        this.registry = registry;
        this.brandResolver = brandResolver;
        this.importer = importer;
        this.recalc = recalc;
        this.coverageService = coverageService;
        this.supplierMapper = supplierMapper;
        this.inquiryLookups = inquiryLookups;
        this.lookups = lookups;
        this.tx = tx;
        AtomicInteger seq = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), r -> {
            Thread t = new Thread(r, "supply-quote-pool-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.DiscardPolicy());
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuote(SourcingQuoteEvent e) {
        if (e.quoteIds() == null || e.quoteIds().isEmpty()) {
            return;
        }
        pool.execute(() -> handleAsync(e));
    }

    /** 线程池里执行；队列满被丢弃的由每天的补登兜底 */
    public void handleAsync(SourcingQuoteEvent e) {
        try {
            TenantContext.runAs(e.tenantId(), () -> handle(e.quoteIds(), e.withdrawn()));
        } catch (RuntimeException ex) {
            log.error("供应链询价联动失败，tenantId={}，taskId={}", e.tenantId(), e.taskId(), ex);
        }
    }

    /** 每天补登：最近提交或撤回过的询价记录；调用方负责租户上下文 */
    public void backfill(LocalDateTime since) {
        List<Long> ids = sourceMapper.recentQuoteIds(since);
        for (List<Long> part : DemandAssembler.chunks(ids)) {
            handle(part, false);
        }
    }

    /** 处理一批询价记录；按记录当前状态判断是生效还是已撤回。调用方负责租户上下文 */
    public void handle(Collection<Long> ids, boolean withdrawn) {
        Map<String, String> leadTimes = lookups.dict("inquiry_lead_time", true);
        for (Map<String, Object> q : sourceMapper.quotesForLinkage(ids)) {
            try {
                tx.executeWithoutResult(s -> one(q, withdrawn, leadTimes));
            } catch (RuntimeException ex) {
                log.error("供应链询价联动失败，quoteId={}", q.get("id"), ex);
            }
        }
    }

    private void one(Map<String, Object> q, boolean withdrawnEvent, Map<String, String> leadTimes) {
        int status = num(q.get("status"));
        if (q.get("deletedAt") != null || q.get("quotedAt") == null) {
            return;
        }
        boolean effective = status == InquiryConstants.QUOTE_SUBMITTED;
        boolean withdrawn = status == InquiryConstants.QUOTE_DRAFT;
        if (!effective && !withdrawn || withdrawnEvent && !withdrawn) {
            return;
        }
        Long brandId = q.get("brandId") instanceof Number n ? n.longValue() : brandResolver.resolve((String) q.get("brand"));
        String modelKey = (String) q.get("modelKey");
        if (brandId == null || !StringUtils.hasText(modelKey)) {
            return;
        }
        boolean noStock = num(q.get("noStock")) == 1;
        long quoteId = ((Number) q.get("id")).longValue();
        String task = (String) q.get("taskCode");
        if (effective && noStock) {
            // 无货：先实时写信号（会按需建需求对象），再登记处理记录与提醒
            SignalImporter.Result r = new SignalImporter.Result();
            importer.importOne(SupplyConstants.SRC_STOCKOUT, stockoutRow(q), r);
            ScDemandDO d = registry.find(brandId, DemandRegistry.scopeKey(modelKey, null));
            if (d == null || !log(d, SupplyConstants.LOG_SRC_NO_STOCK, quoteId, task,
                    "询价任务 " + task + " 回价：无货" + (StringUtils.hasText((String) q.get("note")) ? " · " + q.get("note") : ""))) {
                return;
            }
            recalc.run(LocalDate.now(), r.stockoutDemands.isEmpty() ? Map.of(d.getId(), text(q.get("note"))) : r.stockoutDemands,
                    List.of(d.getId()));
            return;
        }
        ScDemandDO d = registry.find(brandId, DemandRegistry.scopeKey(modelKey, null));
        if (d == null) {
            return;
        }
        if (withdrawn) {
            if (!log(d, SupplyConstants.LOG_SRC_WITHDRAWN, quoteId, task, "询价任务 " + task + " 回价已撤回")) {
                return;
            }
            if (d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX && d.getCoverageStatus() == SupplyConstants.COVER_READY) {
                // change 会重读证据：仍有其他有效证据就保持，否则改回刷新后的建议状态
                if (!coverageService.change(d, SupplyConstants.COVER_READY, null, "回价撤回")) {
                    coverageService.change(d, d.getSuggestedCoverage(), null, "回价撤回");
                }
            }
            return;
        }
        if (!log(d, SupplyConstants.LOG_SRC_QUOTED, quoteId, task, quotedText(q, task, leadTimes))) {
            return;
        }
        if (d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX && d.getCoverageStatus() != SupplyConstants.COVER_READY) {
            coverageService.change(d, SupplyConstants.COVER_READY, null, "询价回价");
        }
        // 清掉该型号到期的系统提醒，人定的下一步保留
        if (d.getNextActionSource() != null && d.getNextActionSource() == SupplyConstants.NEXT_SYSTEM) {
            demandMapper.update(null, new LambdaUpdateWrapper<ScDemandDO>().eq(ScDemandDO::getId, d.getId())
                    .set(ScDemandDO::getNextAction, "").set(ScDemandDO::getNextActionDate, null)
                    .set(ScDemandDO::getNextActionSource, SupplyConstants.NEXT_NONE)
                    .set(ScDemandDO::getReminderType, SupplyConstants.REMIND_NONE)
                    .set(ScDemandDO::getDeferCount, 0)
                    .set(ScDemandDO::getUpdateTime, LocalDateTime.now()));
        }
    }

    /** 写系统处理记录；同一来源已登记过返回 false */
    private boolean log(ScDemandDO d, int sourceType, long quoteId, String task, String content) {
        ScFollowupLogDO l = new ScFollowupLogDO();
        l.setTenantId(PiStore.tenantId());
        l.setRecordType(SupplyConstants.LOG_SYSTEM);
        l.setSourceType(sourceType);
        l.setSourceId(quoteId);
        l.setSourceDocNo(task == null ? "" : task.length() > 32 ? task.substring(0, 32) : task);
        l.setContent(content.length() > 1000 ? content.substring(0, 1000) : content);
        l.setDemandIds("[" + d.getId() + "]");
        l.setGapProjectId(0L);
        l.setOperatorId(0L);
        try {
            logMapper.insert(l);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private String quotedText(Map<String, Object> q, String task, Map<String, String> leadTimes) {
        String buyer = q.get("quotedBy") instanceof Number n ? inquiryLookups.userNames(List.of(n.longValue())).get(n.longValue()) : null;
        String where = (String) q.get("shopName");
        if (q.get("supplierId") instanceof Number s && s.longValue() > 0) {
            SupplierDO sup = supplierMapper.selectById(s.longValue());
            if (sup != null) {
                where = DemandAssembler.supplierName(sup);
            }
        }
        BigDecimal price = (BigDecimal) q.get("unitPrice");
        String cur = (String) q.get("currencyCode");
        String lead = q.get("leadTime") instanceof Number n && n.intValue() > 0 ? leadTimes.get(String.valueOf(n.intValue())) : null;
        StringBuilder b = new StringBuilder("询价任务 ").append(task).append(" 回价：");
        b.append(buyer == null ? "采购" : buyer);
        if (StringUtils.hasText(where)) {
            b.append(" · ").append(where);
        }
        if (price != null) {
            b.append(" · ").append("CNY".equals(cur) || cur == null ? "¥" : cur + " ").append(price.toPlainString());
        }
        if (StringUtils.hasText(lead)) {
            b.append(" · ").append(lead);
        }
        return b.toString();
    }

    private static SourceRow stockoutRow(Map<String, Object> q) {
        SourceRow r = new SourceRow();
        r.setSourceId(((Number) q.get("id")).longValue());
        r.setDocId(((Number) q.get("taskId")).longValue());
        r.setDocNo((String) q.get("taskCode"));
        r.setBrandId(q.get("brandId") instanceof Number n ? n.longValue() : null);
        r.setBrandRaw((String) q.get("brand"));
        r.setCategoryId(q.get("categoryId") instanceof Number n ? n.longValue() : null);
        r.setProductId(q.get("productId") instanceof Number n ? n.longValue() : null);
        r.setModelRaw((String) q.get("model"));
        r.setModelKey((String) q.get("modelKey"));
        r.setQuantity(q.get("quantity") instanceof Number n ? n.intValue() : 0);
        r.setReasonCode(q.get("noStockReason") == null ? null : String.valueOf(q.get("noStockReason")));
        r.setReasonText((String) q.get("note"));
        Object at = q.get("quotedAt");
        r.setOccurredAt(at instanceof Timestamp t ? t.toLocalDateTime() : at instanceof LocalDateTime l ? l : LocalDateTime.now());
        r.setCancelled(false);
        return r;
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : o instanceof Boolean b ? (b ? 1 : 0) : 0;
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }
}
