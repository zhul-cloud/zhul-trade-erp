package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.modules.masterdata.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时把存量供应商的自由文本主营品牌迁移为主营产品。归一要用品牌名称和别名，迁移脚本不好做，所以放在启动时；
 * 只处理还没有任何主营产品行的供应商，重复执行无副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierProductScopeBackfill {

    private final SupplierService supplierService;

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        int count = supplierService.backfillProductScopes();
        if (count > 0) {
            log.info("供应商主营品牌迁移完成，count={}", count);
        }
    }
}
