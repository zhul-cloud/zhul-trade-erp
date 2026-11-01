package com.zhul.erp.modules.aftersales.support;

import com.zhul.erp.modules.aftersales.entity.AfterSalesLogDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesLogMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 售后单跟进时间线（操作人为空时取当前用户）：退货、退款、费用等单据的动作也写一条，在调用方事务内执行 */
@Component
@RequiredArgsConstructor
public class AfterSalesLogs {

    private final AfterSalesLogMapper mapper;
    private final com.zhul.erp.modules.inquiry.support.CurrentUserResolver currentUser;

    public void add(Long afterSalesId, String type, String content, Long operatorId) {
        if (afterSalesId == null) {
            return;
        }
        AfterSalesLogDO x = new AfterSalesLogDO();
        x.setTenantId(PiStore.tenantId());
        x.setAfterSalesId(afterSalesId);
        x.setLogType(type);
        String c = content == null ? "" : content.trim();
        x.setContent(c.length() > 1000 ? c.substring(0, 1000) : c);
        Long op = operatorId != null ? operatorId : currentUser.resolve();
        x.setOperatorId(op == null ? 0L : op);
        mapper.insert(x);
    }
}
