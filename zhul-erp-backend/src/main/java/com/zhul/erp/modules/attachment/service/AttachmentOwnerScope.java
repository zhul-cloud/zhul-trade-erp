package com.zhul.erp.modules.attachment.service;

import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.AfterSalesLogDO;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesLogMapper;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.ReturnReceiptMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnMapper;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.RefundRequestDO;
import com.zhul.erp.modules.finance.repository.CustomerCreditEntryMapper;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.RefundRequestMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO;
import com.zhul.erp.modules.purchase.purchasereturn.repository.PurchaseReturnMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.SupplierShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 附件跟随所属单据的数据范围（audit SEC-005）：签发访问链接前，按附件类型检查当前用户能否看到所属单据。
 * 规则与各模块单据详情一致；仓库、财务付款等不按负责人限制的模块，租户校验已足够。
 * 每种附件类型都要在这里明确归类，新增类型没有归类时一律拒绝，不能默认放行。
 */
@Component
@RequiredArgsConstructor
public class AttachmentOwnerScope {

    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;
    private final PermissionChecker perm;
    private final SalesOrderMapper salesOrderMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final AfterSalesLogMapper afterSalesLogMapper;
    private final CustomerRefundMapper refundMapper;
    private final CustomerCreditEntryMapper creditEntryMapper;
    private final CustomerMapper customerMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SupplierShipmentMapper shipmentMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final OutboundOrderMapper outboundMapper;
    private final PaymentReceiptMapper paymentReceiptMapper;
    private final ProformaInvoiceMapper piMapper;
    private final SalesReturnMapper salesReturnMapper;
    private final ReturnReceiptMapper returnReceiptMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final LogisticsShipmentMapper logisticsShipmentMapper;

    public boolean canSee(BizAttachmentDO a) {
        if (a.getOwnerId() == null || a.getOwnerId() == 0) {
            // 还没挂到单据上（owner_id = 0）的附件只有上传人能看
            Long me = currentUser.resolve();
            return me != null && Objects.equals(a.getUploadedBy(), me);
        }
        Long id = a.getOwnerId();
        return switch (a.getOwnerType()) {
            case AttachmentService.SALES_ORDER -> orderVisible(salesOrderMapper.selectById(id));
            case AttachmentService.AFTER_SALES -> afterSalesVisible(afterSalesMapper.selectById(id));
            case AttachmentService.AFTER_SALES_LOG -> anyMenu("/warehouse/returns", "/finance/refunds")
                    || afterSalesVisible(afterSalesOf(afterSalesLogMapper.selectById(id)));
            case AttachmentService.REFUND -> anyMenu("/finance/refunds", "/finance/credits", "/finance/receipts")
                    || refundVisible(refundMapper.selectById(id));
            case AttachmentService.CREDIT -> anyMenu("/finance/credits")
                    || customerVisible(creditCustomer(creditEntryMapper.selectById(id)));
            case AttachmentService.PO_INVOICE -> anyMenu("/finance/payables")
                    || purchaseVisible(purchaseOrderMapper.selectById(id));
            // 发货单与采购入库单的附件：采购与仓库菜单看全部，只有采购订单菜单时按采购订单的采购员
            case AttachmentService.SHIPMENT -> anyMenu("/purchase/shipments", "/warehouse/receipts")
                    || purchaseVisible(poOfShipment(shipmentMapper.selectById(id)));
            case AttachmentService.RECEIPT, AttachmentService.RECEIPT_DEFECT -> anyMenu("/warehouse/receipts", "/purchase/shipments")
                    || purchaseVisible(poOfReceipt(receiptMapper.selectById(id)));
            // 销售出库单的附件与打包照片：仓库看全部，业务员按销售订单负责人
            case AttachmentService.OUTBOUND, AttachmentService.PACKING -> anyMenu("/warehouse/outbounds")
                    || orderVisible(orderOfOutbound(outboundMapper.selectById(id)));
            // 收款单补充的附件：财务看全部，业务员按 PI 或订单的负责人，未认领的看不到
            case AttachmentService.PAYMENT_RECEIPT -> anyMenu("/finance/receipts")
                    || paymentReceiptVisible(paymentReceiptMapper.selectById(id));
            // 销售退货单：与详情一致，能看到它的售后单才能看
            case AttachmentService.SALES_RETURN ->
                    afterSalesVisible(afterSalesOfReturn(salesReturnMapper.selectById(id)));
            // 退货入库单：仓库看全部，业务员按销售退货单的售后单
            case AttachmentService.RETURN_RECEIPT -> anyMenu("/warehouse/returns")
                    || afterSalesVisible(afterSalesOfReceipt(returnReceiptMapper.selectById(id)));
            case AttachmentService.PURCHASE_RETURN -> purchaseReturnVisible(purchaseReturnMapper.selectById(id));
            // 退款申请：财务看全部，申请侧按订单负责人
            case AttachmentService.REFUND_REQUEST -> anyMenu("/finance/refunds")
                    || orderVisible(orderOfRequest(refundRequestMapper.selectById(id)));
            // 出运单面单与单据：与出运单详情一致，按出运单负责人
            case AttachmentService.LOGISTICS, AttachmentService.LOGISTICS_DOC ->
                    logisticsVisible(logisticsShipmentMapper.selectById(id));
            // 以下单据不按负责人限制，详情只校验租户（附件服务已校验租户）
            case AttachmentService.SHOOT, AttachmentService.NETWORK, AttachmentService.SOURCING_QUOTE,
                 AttachmentService.PAYMENT, AttachmentService.COURIER_PROOF, AttachmentService.RETURN_PROOF,
                 AttachmentService.EXPENSE, AttachmentService.REIMBURSE, AttachmentService.OTHER_INCOME,
                 AttachmentService.PAYABLE, AttachmentService.FX_SETTLEMENT -> true;
            default -> false;
        };
    }

    private boolean anyMenu(String... menus) {
        for (String m : menus) {
            if (perm.canAccessMenu(m)) {
                return true;
            }
        }
        return false;
    }

    private boolean paymentReceiptVisible(PaymentReceiptDO r) {
        if (r == null) {
            return false;
        }
        if (r.getSoId() != null) {
            return orderVisible(salesOrderMapper.selectById(r.getSoId()));
        }
        ProformaInvoiceDO pi = r.getPiId() == null ? null : piMapper.selectById(r.getPiId());
        return pi != null && dataScopeResolver.current().canSee(pi.getOwnerId());
    }

    private boolean orderVisible(SalesOrderDO o) {
        return o != null && dataScopeResolver.current().canSee(o.getOwnerId());
    }

    /** 与售后单详情一致：售后单负责人或关联订单负责人在数据范围内即可 */
    private boolean afterSalesVisible(AfterSalesDO s) {
        if (s == null) {
            return false;
        }
        DataScope scope = dataScopeResolver.current();
        if (scope.canSee(s.getOwnerId())) {
            return true;
        }
        SalesOrderDO o = s.getSoId() == null ? null : salesOrderMapper.selectById(s.getSoId());
        return o != null && scope.canSee(o.getOwnerId());
    }

    private AfterSalesDO afterSalesOf(AfterSalesLogDO log) {
        return log == null || log.getAfterSalesId() == null ? null : afterSalesMapper.selectById(log.getAfterSalesId());
    }

    private AfterSalesDO afterSalesOfReturn(SalesReturnDO r) {
        return r == null || r.getAfterSalesId() == null ? null : afterSalesMapper.selectById(r.getAfterSalesId());
    }

    private AfterSalesDO afterSalesOfReceipt(ReturnReceiptDO rr) {
        return rr == null || rr.getAfterSalesId() == null ? null : afterSalesMapper.selectById(rr.getAfterSalesId());
    }

    private boolean purchaseReturnVisible(PurchaseReturnDO p) {
        return p != null && dataScopeResolver.current().canSee(p.getOwnerId());
    }

    private SalesOrderDO orderOfRequest(RefundRequestDO q) {
        return q == null || q.getSoId() == null ? null : salesOrderMapper.selectById(q.getSoId());
    }

    private boolean logisticsVisible(LogisticsShipmentDO sh) {
        return sh != null && dataScopeResolver.current().canSee(sh.getOwnerId());
    }

    private boolean refundVisible(CustomerRefundDO f) {
        if (f == null) {
            return false;
        }
        if (f.getSoId() != null) {
            return orderVisible(salesOrderMapper.selectById(f.getSoId()));
        }
        return customerVisible(f.getCustomerId() == null ? null : customerMapper.selectById(f.getCustomerId()));
    }

    private CustomerDO creditCustomer(CustomerCreditEntryDO e) {
        return e == null || e.getCustomerId() == null ? null : customerMapper.selectById(e.getCustomerId());
    }

    private boolean customerVisible(CustomerDO c) {
        return c != null && dataScopeResolver.current().canSee(c.getOwnerId());
    }

    private boolean purchaseVisible(PurchaseOrderDO p) {
        return p != null && dataScopeResolver.current().canSee(p.getPurchaserId());
    }

    private PurchaseOrderDO poOfShipment(SupplierShipmentDO s) {
        return s == null || s.getPoId() == null ? null : purchaseOrderMapper.selectById(s.getPoId());
    }

    private PurchaseOrderDO poOfReceipt(PurchaseReceiptDO r) {
        return r == null || r.getPoId() == null ? null : purchaseOrderMapper.selectById(r.getPoId());
    }

    private SalesOrderDO orderOfOutbound(OutboundOrderDO o) {
        return o == null || o.getSoId() == null ? null : salesOrderMapper.selectById(o.getSoId());
    }
}
