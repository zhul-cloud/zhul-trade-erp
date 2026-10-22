package com.zhul.erp.modules.workbench.widget;

import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.inquiry.sourcing.service.MyTaskService;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderStatsVO;
import com.zhul.erp.modules.purchase.service.PurchaseOrderService;
import com.zhul.erp.modules.purchase.service.PurchaseRequirementService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.ShipmentPageQuery;
import com.zhul.erp.modules.warehouse.service.DiscrepancyService;
import com.zhul.erp.modules.warehouse.service.ShipmentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 采购员看板（spec「采购员看板」）：我的询价、回价统计、近 30 天回价（口径同原兼职看板，本人数据），
 * 以及我的采购（口径同采购需求、采购订单、供应商发货与到货差异页，按当前用户的数据范围）。
 */
@Configuration
public class PurchaserWidgets {

    static final String MY_TASKS = "/inquiry/my-tasks";
    static final String REQUIREMENTS = "/purchase/requirements";
    static final String ORDERS = "/purchase/orders";
    static final String SHIPMENTS = "/purchase/shipments";

    @Bean
    public WorkbenchWidget myInquiryWidget(MyTaskService tasks) {
        return widget("my-inquiry", List.of(MY_TASKS), tasks::quoteBoard);
    }

    @Bean
    public WorkbenchWidget quoteStatsWidget(MyTaskService tasks) {
        return widget("quote-stats", List.of(MY_TASKS), tasks::quoteBoard);
    }

    @Bean
    public WorkbenchWidget quoteTrendWidget(MyTaskService tasks) {
        return widget("quote-trend", List.of(MY_TASKS), tasks::quoteBoard);
    }

    /** 我的采购：只给有对应页面权限的数字，没有权限的为空 */
    @Bean
    public WorkbenchWidget myPurchaseWidget(PermissionChecker perm, PurchaseRequirementService requirements,
                                           PurchaseOrderService orders, ShipmentService shipments,
                                           DiscrepancyService discrepancies) {
        return widget("my-purchase", List.of(REQUIREMENTS, ORDERS, SHIPMENTS), () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            if (perm.canAccessMenu(REQUIREMENTS)) {
                out.put("openRequirements", requirements.stats(true).getOpen());
            }
            if (perm.canAccessMenu(ORDERS)) {
                PurchaseOrderStatsVO s = orders.stats();
                out.put("pendingShip", s.getPendingShip());
                out.put("overdueShip", s.getOverdueShip());
                out.put("drafts", s.getDrafts());
            }
            if (perm.canAccessMenu(SHIPMENTS)) {
                ShipmentPageQuery q = new ShipmentPageQuery();
                q.setPage(1);
                q.setPageSize(1);
                q.setStatus(WarehouseConstants.SHIP_IN_TRANSIT);
                out.put("inTransit", shipments.page(q, true, false).getTotal());
                out.put("discrepancies", discrepancies.countPending());
            }
            return out;
        });
    }

    static WorkbenchWidget widget(String key, List<String> menus, Supplier<Object> loader) {
        return new WorkbenchWidget() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public List<String> menus() {
                return menus;
            }

            @Override
            public Object load() {
                return loader.get();
            }
        };
    }
}
