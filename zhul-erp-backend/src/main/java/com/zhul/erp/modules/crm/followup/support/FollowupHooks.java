package com.zhul.erp.modules.crm.followup.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 单据事件 → 客户跟进（spec crm/customer-followup「跟进对象自动生成」、crm/opportunity 自动推进与自动赢单）。
 * 各单据服务在自己的事务里、状态写入之后调用这里。这里的每个方法都不抛异常：
 * 跟进对象、商机自动推进和自动赢单出错只记 WARN，不影响报价、PI、收款本身，由每晚核对补上。
 * 本类不声明事务；被调用的方法也不声明事务，异常不会把原单据事务标记为回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowupHooks {

    private final FollowupSync sync;
    private final OpportunityService opportunityService;
    private final SalesOrderMapper orderMapper;
    private final PurchaseOrderItemMapper poItemMapper;

    /** 老客户登记询盘（从商机创建的询盘由商机推进带动同步） */
    public void inquiryCreated(CustomerInquiryDO inq) {
        sync.event(inq.getCustomerId(), FollowupConstants.TARGET_INQUIRY, inq.getId(), "创建询盘 " + inq.getInquiryCode());
    }

    /** 报价单发送（含发送新版本） */
    public void quotationSent(QuotationDO q, boolean revision) {
        String title = (revision ? "发送报价单新版本 " : "发送报价单 ") + q.getQuotationNo()
                + " · " + q.getCurrencyCode() + " " + plain(q.getTotalAmount());
        sync.event(q.getCustomerId(), FollowupConstants.TARGET_QUOTATION, q.getId(), title);
    }

    /** 报价单、PI 的其他状态变化（未成交、作废、关闭、重新打开、收款作废等） */
    public void customerChanged(Long customerId) {
        sync.customerChanged(customerId);
    }

    /**
     * 销售订单及其下游单据变化（第二期 design D3）：按订单所属客户重新推导订单履约、售后评价。
     * 订单创建、取消、部分取消、客户已收货，实拍任务、销售出库单、售后单都走这里。
     */
    public void orderChanged(Long soId) {
        if (soId != null) {
            ordersChanged(List.of(soId));
        }
    }

    public void ordersChanged(Collection<Long> soIds) {
        List<Long> ids = soIds == null ? List.of() : soIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        List<Long> customers;
        try {
            customers = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                            .select(SalesOrderDO::getId, SalesOrderDO::getCustomerId).in(SalesOrderDO::getId, ids))
                    .stream().map(SalesOrderDO::getCustomerId).filter(Objects::nonNull).distinct().toList();
        } catch (RuntimeException e) {
            log.warn("查询订单所属客户失败，soIds={}", ids, e);
            return;
        }
        customers.forEach(sync::customerChanged);
    }

    /** 采购单及其到货登记、入库、到货差异变化：按采购行关联的销售订单重新推导 */
    public void purchaseChanged(Long poId) {
        if (poId == null) {
            return;
        }
        List<Long> soIds;
        try {
            soIds = poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                            .select(PurchaseOrderItemDO::getSoId).eq(PurchaseOrderItemDO::getPoId, poId))
                    .stream().map(PurchaseOrderItemDO::getSoId).toList();
        } catch (RuntimeException e) {
            log.warn("查询采购单关联订单失败，poId={}", poId, e);
            return;
        }
        ordersChanged(soIds);
    }

    /** 售后单新建、修改下一步、改状态、补发、撤销：按客户重新推导（售后单可能没有关联订单） */
    public void afterSalesChanged(AfterSalesDO a) {
        sync.customerChanged(a.getCustomerId());
    }

    /** 售后单结案：重新推导后写一条「售后单结案」节点，案例沉淀作为节点正文（spec sales/after-sales「结案时案例沉淀」） */
    public void afterSalesClosed(AfterSalesDO a) {
        sync.customerChanged(a.getCustomerId());
        if (a.getCustomerId() == null) {
            return;
        }
        try {
            String note = a.getCaseNote() == null || a.getCaseNote().isBlank() ? null : "案例沉淀：" + a.getCaseNote();
            sync.writeSystemRecord(a.getCustomerId(), FollowupConstants.TARGET_ORDER, a.getSoId() == null || a.getSoId() == 0 ? null
                    : a.getSoId(), "售后单 " + a.getAsNo() + " 结案 · " + a.getResult(), note);
        } catch (RuntimeException e) {
            log.warn("写入售后结案的跟进时间线失败，asNo={}", a.getAsNo(), e);
        }
    }

    /** PI 发送：第一次发送时把客户进行中的商机推进到 S7 */
    public void piSent(ProformaInvoiceDO pi, boolean first) {
        if (first) {
            try {
                opportunityService.autoAdvance(pi.getCustomerId(), FollowupConstants.S7, null,
                        "发送 PI " + pi.getPiNo() + "，自动推进");
            } catch (RuntimeException e) {
                // 推进失败不影响 PI 发送（spec crm/opportunity「PI 发送后自动推进到成交推进」）
                log.warn("PI 发送后商机自动推进失败，piNo={}", pi.getPiNo(), e);
            }
        }
        String title = (first ? "发送 PI " : "发送 PI 新版本 ") + pi.getPiNo() + " · " + pi.getCurrencyCode() + " "
                + plain(pi.getTotalAmount());
        sync.event(pi.getCustomerId(), FollowupConstants.TARGET_PI, pi.getId(), title);
    }

    /** PI 收款确认到账：已收金额大于 0 时客户进行中的商机自动赢单 */
    public void receiptConfirmed(ProformaInvoiceDO pi, BigDecimal amount) {
        Long won = null;
        if (pi.getReceivedAmount() != null && pi.getReceivedAmount().signum() > 0) {
            try {
                won = opportunityService.autoWin(pi.getCustomerId(), pi.getPiNo());
            } catch (RuntimeException e) {
                // 赢单失败不影响收款确认（spec crm/opportunity「收款到账自动赢单」）
                log.warn("收款到账后商机自动赢单失败，piNo={}", pi.getPiNo(), e);
            }
        }
        sync.event(pi.getCustomerId(), FollowupConstants.TARGET_PI, pi.getId(),
                "PI " + pi.getPiNo() + " 收款到账 · " + pi.getCurrencyCode() + " " + plain(amount));
        if (won != null) {
            try {
                sync.writeSystemRecord(pi.getCustomerId(), FollowupConstants.TARGET_OPPORTUNITY, won,
                        "PI " + pi.getPiNo() + " 收款到账，商机自动赢单");
            } catch (RuntimeException e) {
                log.warn("写入自动赢单的跟进时间线失败，piNo={}", pi.getPiNo(), e);
            }
        }
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
