package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.repository.OpportunityMapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 跟进对象每晚核对（第一期 design D1；第二期 tasks 3.3 加订单履约、售后评价）：按单据全量重新推导，补上遗漏、移出已结束的、激活到期的培育、刷新系统节奏。
 * 每晚 02:40（客户等级重算之后）运行；应用启动完成后也运行一次，上线时由它生成存量单据的跟进对象，
 * 补生成的对象提醒节奏从当天起算。
 * <p>
 * 定时任务没有租户上下文：在平台范围查出租户，再逐个租户用 {@link TenantContext#runAs} 处理；
 * 一个客户失败只记 ERROR 并继续，一个租户失败不影响其他租户。每个客户的推导不共用事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowupReconcileJob {

    private final CustomerMapper customerMapper;
    private final FollowupItemMapper itemMapper;
    private final QuotationMapper quotationMapper;
    private final ProformaInvoiceMapper piMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final OpportunityMapper opportunityMapper;
    private final SalesOrderMapper orderMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final FollowupSync sync;

    @Scheduled(cron = "${zhul.followup.reconcile-cron:0 40 2 * * *}", zone = "Asia/Shanghai")
    public void nightly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        run();
    }

    public void run() {
        List<Object> tenants = TenantScope.platform(() -> customerMapper.selectObjs(
                new QueryWrapper<CustomerDO>().select("DISTINCT tenant_id").isNull("deleted_at")));
        for (Object t : tenants) {
            int tenantId = ((Number) t).intValue();
            try {
                TenantContext.runAs(tenantId, () -> reconcile(tenantId));
            } catch (RuntimeException e) {
                log.error("跟进对象核对失败，tenantId={}", tenantId, e);
            }
        }
    }

    /** 核对一个租户；调用方负责设置租户上下文 */
    public void reconcile(int tenantId) {
        Set<Long> customers = candidates();
        LocalDate today = LocalDate.now();
        int changes = 0;
        int failed = 0;
        for (Long customerId : customers) {
            try {
                changes += sync.resync(customerId, today, true);
            } catch (RuntimeException e) {
                failed++;
                log.error("跟进对象核对失败，tenantId={}，customerId={}", tenantId, customerId, e);
            }
        }
        log.info("跟进对象核对完成，tenantId={}，客户 {} 个，修正 {} 条，失败 {} 个", tenantId, customers.size(), changes, failed);
    }

    /**
     * 可能有跟进对象的客户：未结束的跟进对象、在跟的报价单 / PI、未报价的询盘、进行中的商机，
     * 以及未完成的销售订单和未结案的售后单（订单履约、售后评价；上线补生成规则在 {@code OrderFollowupSync} 里）
     */
    private Set<Long> candidates() {
        Set<Long> out = new TreeSet<>();
        add(out, itemMapper.selectObjs(new QueryWrapper<FollowupItemDO>().select("DISTINCT customer_id")
                .isNull("closed_at").isNull("deleted_at")));
        add(out, quotationMapper.selectObjs(new QueryWrapper<QuotationDO>().select("DISTINCT customer_id")
                .in("status", QuotationConstants.STATUS_SENT, QuotationConstants.STATUS_PARTIAL)
                .isNotNull("sent_at").isNull("deleted_at")));
        add(out, piMapper.selectObjs(new QueryWrapper<ProformaInvoiceDO>().select("DISTINCT customer_id")
                .in("status", SalesConstants.PI_SENT, SalesConstants.PI_CONVERTED).isNull("deleted_at")));
        add(out, inquiryMapper.selectObjs(new QueryWrapper<CustomerInquiryDO>().select("DISTINCT customer_id")
                .lt("status", InquiryConstants.STATUS_QUOTED).isNull("deleted_at")));
        add(out, opportunityMapper.selectObjs(new QueryWrapper<OpportunityDO>().select("DISTINCT customer_id")
                .isNull("closed_at").isNull("deleted_at")));
        add(out, orderMapper.selectObjs(new QueryWrapper<SalesOrderDO>().select("DISTINCT customer_id")
                .eq("status", SalesConstants.SO_ACTIVE).ne("progress_code", SalesConstants.PROGRESS_COMPLETED)
                .isNull("deleted_at")));
        add(out, afterSalesMapper.selectObjs(new QueryWrapper<AfterSalesDO>().select("DISTINCT customer_id")
                .in("status", AfterSalesConstants.AS_OPEN, AfterSalesConstants.AS_WAITING).isNull("deleted_at")));
        return out;
    }

    private static void add(Set<Long> out, List<Object> ids) {
        for (Object id : ids) {
            if (id instanceof Number n && n.longValue() > 0) {
                out.add(n.longValue());
            }
        }
    }
}
