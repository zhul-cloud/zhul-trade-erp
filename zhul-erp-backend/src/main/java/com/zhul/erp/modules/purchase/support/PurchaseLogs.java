package com.zhul.erp.modules.purchase.support;

import com.zhul.erp.modules.purchase.entity.PurchaseOrderLogDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderLogMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 采购单详情里的操作日志 */
@Component
@RequiredArgsConstructor
public class PurchaseLogs {

    private static final int MAX = 1000;

    private final PurchaseOrderLogMapper logMapper;

    /** operatorId 为空表示系统自动操作 */
    public void add(Long poId, String action, String content, Long operatorId) {
        PurchaseOrderLogDO x = new PurchaseOrderLogDO();
        x.setTenantId(PiStore.tenantId());
        x.setPoId(poId);
        x.setAction(action);
        String c = content == null ? "" : content;
        x.setContent(c.length() > MAX ? c.substring(0, MAX) : c);
        x.setOperatorId(operatorId);
        logMapper.insert(x);
    }
}
