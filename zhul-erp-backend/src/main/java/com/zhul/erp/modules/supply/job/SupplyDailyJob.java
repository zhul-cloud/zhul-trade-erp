package com.zhul.erp.modules.supply.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandSignalDO;
import com.zhul.erp.modules.supply.repository.ScDemandSignalMapper;
import com.zhul.erp.modules.supply.support.DemandRecalc;
import com.zhul.erp.modules.supply.support.SignalImporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应链每日任务（design 决策 2、12、16；tasks 3.2）：按租户逐个执行 汇入信号 → 重算等级与优先级 → 建议状态与过期降级 → 系统提醒 → 询价联动补登
 * → 开发任务候选按证据判定合格（第二期，含采购订单与证据过期退回）。
 * 每天 03:10 运行，应用启动完成后也运行一次（上线后首次执行回溯 180 天）。
 * <p>
 * 没有登录上下文：在平台范围查出有询盘的租户，再逐个 {@link TenantContext#runAs}；单个租户失败只记 ERROR，不影响其他租户。
 * 事务边界：汇入每种来源一个事务；重算与提醒同一租户一个事务。全部步骤可重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplyDailyJob {

    /** 增量汇入回看天数：来源单据最近两天内有改动的都再看一遍，补上取消与漏跑 */
    private static final int LOOKBACK_DAYS = 2;

    private final CustomerInquiryMapper inquiryMapper;
    private final ScDemandSignalMapper signalMapper;
    private final SignalImporter importer;
    private final DemandRecalc recalc;
    private final com.zhul.erp.modules.supply.support.QuoteLinkage linkage;
    private final com.zhul.erp.modules.supply.support.CandidateQualifier qualifier;
    private final TransactionTemplate tx;

    @Scheduled(cron = "${zhul.supply.daily-cron:0 10 3 * * *}", zone = "Asia/Shanghai")
    public void nightly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        run();
    }

    public void run() {
        List<Object> tenants = TenantScope.platform(() -> inquiryMapper.selectObjs(
                new QueryWrapper<CustomerInquiryDO>().select("DISTINCT tenant_id")));
        for (Object t : tenants) {
            int tenantId = ((Number) t).intValue();
            try {
                TenantContext.runAs(tenantId, () -> runTenant(tenantId, LocalDate.now()));
            } catch (RuntimeException e) {
                log.error("供应链每日任务失败，tenantId={}", tenantId, e);
            }
        }
    }

    /** 处理一个租户；调用方负责租户上下文 */
    public void runTenant(int tenantId, LocalDate today) {
        // 按来源判断是否首次：每种来源一个事务，中途重启时没写完的来源整体回滚，下次仍按首次回溯 180 天
        java.util.Set<Integer> backfill = new java.util.HashSet<>(4);
        for (int type = SupplyConstants.SRC_INQUIRY; type <= SupplyConstants.SRC_LOST; type++) {
            if (!signalMapper.exists(new QueryWrapper<ScDemandSignalDO>().eq("source_type", type))) {
                backfill.add(type);
            }
        }
        boolean first = !backfill.isEmpty();
        LocalDateTime floor = today.minusDays(SupplyConstants.FIRST_BACKFILL_DAYS).atStartOfDay();
        SignalImporter.Result r = importer.importAll(today.minusDays(LOOKBACK_DAYS).atStartOfDay(), floor, backfill);
        DemandRecalc.Summary s = tx.execute(st -> recalc.run(today, r.stockoutDemands));
        // 询价联动按询价记录 id 补登（处理记录唯一键去重），补上需求对象当时还不存在或线程池丢弃的
        linkage.backfill(today.minusDays(LOOKBACK_DAYS).atStartOfDay());
        int qualified = qualifier.refresh(today);
        log.info("供应链每日任务完成，tenantId={}，{}新信号 {} 条、更新 {} 条，需求对象 {} 个，进入矩阵 {} 个，过期降级 {} 个，提醒 {} 条，候选阶段变化 {} 个",
                tenantId, first ? "首次回溯，" : "", r.created, r.updated, s.demands(), s.entered(), s.downgraded(), s.reminders(),
                qualified);
    }
}
