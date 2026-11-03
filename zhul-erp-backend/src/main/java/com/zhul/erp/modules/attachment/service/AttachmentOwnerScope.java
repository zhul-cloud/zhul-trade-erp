package com.zhul.erp.modules.attachment.service;

import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.AfterSalesLogDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesLogMapper;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.repository.CustomerCreditEntryMapper;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 附件跟随所属单据的数据范围（audit SEC-005）：签发访问链接前，按附件类型检查当前用户能否看到所属单据。
 * 规则与各模块单据详情一致；仓库、物流、财务付款等不按负责人限制的模块，租户校验已足够。
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
            default -> true;
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
}
