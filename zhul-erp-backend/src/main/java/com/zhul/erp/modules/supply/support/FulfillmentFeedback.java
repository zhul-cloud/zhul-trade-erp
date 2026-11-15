package com.zhul.erp.modules.supply.support;

import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import com.zhul.erp.modules.supply.repository.ScFollowupLogMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import com.zhul.erp.modules.supply.service.CoverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 履约异常回写需求对象（OpenSpec supply-chain-phase3-quote-fulfillment design 决策 5–7）。
 * <p>
 * 每天扫描近 30 天的采购延期、采购取消、到货少发 / 不良，按品牌 + 归一化型号找到需求对象：写系统处理记录（带采购单号，不写供应商名称）、
 * 「可直接报价」降为「需确认」、写履约异常提醒（人定的下一步未到期时只在时间线留一条）。
 * 处理记录按「来源类型 + 来源ID」唯一，同一异常只处理一次；品牌识别不到或没有需求对象的跳过。
 * 事务边界：每条异常一个事务，一条失败只记日志。调用方负责租户上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentFeedback {

    /** 只看最近这么多天的异常；首次运行同样不回溯更早的历史 */
    public static final int WINDOW_DAYS = 30;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private final SupplySourceMapper sourceMapper;
    private final DemandRegistry registry;
    private final BrandResolver brandResolver;
    private final ScFollowupLogMapper logMapper;
    private final CoverageService coverageService;
    private final DemandRecalc recalc;
    private final TransactionTemplate tx;

    /** 一条履约异常 */
    record Issue(int sourceType, long sourceId, String poNo, String brand, String model, String logText, String remindText) {
    }

    /** 扫描结果，供日志与测试 */
    public record Summary(int found, int recorded, int downgraded, int reminded) {
    }

    public Summary scan(LocalDate today) {
        List<Issue> issues = collect(today);
        int[] c = new int[3];
        for (Issue i : issues) {
            try {
                tx.executeWithoutResult(s -> apply(i, today, c));
            } catch (RuntimeException e) {
                log.error("履约异常回写失败，sourceType={}，sourceId={}", i.sourceType(), i.sourceId(), e);
            }
        }
        return new Summary(issues.size(), c[0], c[1], c[2]);
    }

    List<Issue> collect(LocalDate today) {
        LocalDate from = today.minusDays(WINDOW_DAYS);
        List<Issue> out = new ArrayList<>();
        for (Map<String, Object> m : sourceMapper.delayedPoLines(from, today)) {
            String po = text(m.get("poNo"));
            LocalDate expected = date(m.get("expectedShipDate"));
            long late = expected == null ? 0 : ChronoUnit.DAYS.between(expected, today);
            out.add(new Issue(SupplyConstants.LOG_SRC_PO_DELAY, id(m), po, text(m.get("brand")), text(m.get("model")),
                    "采购订单 " + po + " 延期：预计 " + (expected == null ? "" : expected.format(MD)) + " 发货，已过 " + late
                            + " 天，没有供应商发货记录",
                    "采购 " + po + " 延期，确认渠道是否可靠"));
        }
        for (Map<String, Object> m : sourceMapper.cancelledPoLines(from.atStartOfDay())) {
            String po = text(m.get("poNo"));
            String reason = text(m.get("cancelReason"));
            out.add(new Issue(SupplyConstants.LOG_SRC_PO_CANCEL, id(m), po, text(m.get("brand")), text(m.get("model")),
                    "采购订单 " + po + " 已取消 · 数量 " + num(m.get("quantity")) + (StringUtils.hasText(reason) ? " · " + reason : ""),
                    "采购 " + po + " 取消，确认渠道是否可靠"));
        }
        for (Map<String, Object> m : sourceMapper.recentDiscrepancies(from.atStartOfDay())) {
            String po = text(m.get("poNo"));
            String what = num(m.get("type")) == 1 ? "少发" : "不良";
            out.add(new Issue(SupplyConstants.LOG_SRC_DISCREPANCY, id(m), po, text(m.get("brand")), text(m.get("model")),
                    "采购订单 " + po + " 到货" + what + " " + num(m.get("quantity")) + " 件",
                    "采购 " + po + " 到货" + what + "，确认渠道是否可靠"));
        }
        return out;
    }

    private void apply(Issue i, LocalDate today, int[] c) {
        Long brandId = brandResolver.resolve(i.brand());
        String modelKey = MpnNormalizer.normalize(i.model());
        if (brandId == null || !StringUtils.hasText(modelKey)) {
            return;
        }
        ScDemandDO d = registry.find(brandId, DemandRegistry.scopeKey(modelKey, null));
        if (d == null || !log(d, i)) {
            return;
        }
        c[0]++;
        if (d.getStatus() == SupplyConstants.DEMAND_IN_MATRIX && d.getCoverageStatus() != null
                && d.getCoverageStatus() == SupplyConstants.COVER_READY) {
            coverageService.change(d, SupplyConstants.COVER_CONFIRM, null, "履约异常");
            c[1]++;
        }
        if (d.getStatus() != SupplyConstants.DEMAND_PAUSED
                && recalc.remindAndSave(d, SupplyConstants.REMIND_FULFILLMENT, i.remindText(), today)) {
            c[2]++;
        }
    }

    /** 写系统处理记录；同一异常已登记过返回 false */
    private boolean log(ScDemandDO d, Issue i) {
        ScFollowupLogDO l = new ScFollowupLogDO();
        l.setTenantId(PiStore.tenantId());
        l.setRecordType(SupplyConstants.LOG_SYSTEM);
        l.setSourceType(i.sourceType());
        l.setSourceId(i.sourceId());
        l.setSourceDocNo(i.poNo().length() > 32 ? i.poNo().substring(0, 32) : i.poNo());
        l.setContent(i.logText().length() > 1000 ? i.logText().substring(0, 1000) : i.logText());
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

    private static long id(Map<String, Object> m) {
        return ((Number) m.get("id")).longValue();
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    private static LocalDate date(Object o) {
        return o instanceof Date d ? d.toLocalDate() : o instanceof LocalDate l ? l : null;
    }
}
