package com.zhul.erp.modules.workbench.widget;

import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.finance.dto.PayableStatsVO;
import com.zhul.erp.modules.finance.service.PayableQueryService;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.SourcingStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingTaskService;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.SourcingStatsService;
import com.zhul.erp.modules.purchase.dto.PurchaseOrderStatsVO;
import com.zhul.erp.modules.purchase.service.PurchaseOrderService;
import com.zhul.erp.modules.purchase.service.PurchaseRequirementService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.ShipmentListVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentPageQuery;
import com.zhul.erp.modules.warehouse.service.DiscrepancyService;
import com.zhul.erp.modules.warehouse.service.ShipmentService;
import com.zhul.erp.modules.workbench.dto.WidgetData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 采购负责人看板（spec「采购负责人看板」）：询价、采购员效率、采购执行、砍价成果、应付。
 * 口径同询价分配、询价统计、采购需求、采购订单、供应商发货、应付单页，按当前用户的数据范围。
 */
@Configuration
public class PurchaseLeadWidgets {

    static final String BOARD = "/inquiry/sourcing-board";
    static final String SOURCING_STATS = "/inquiry/sourcing-stats";
    static final String PAYABLES = "/finance/payables";
    static final String BARGAIN = "/purchase/bargain-stats";

    @Bean
    public WorkbenchWidget sourcingOverviewWidget(SourcingTaskService tasks) {
        return W.widget("sourcing-overview", List.of(BOARD), () -> {
            BoardStatsVO s = tasks.board(null).getStats();
            long wait = s.getLongestWaitingMinutes() == null ? 0 : s.getLongestWaitingMinutes();
            return W.of(W.metrics(
                    W.link(W.tone(W.metric("待分配的任务", s.getUnassigned(), "count",
                            wait > 0 ? "最早的已等 " + wait / 60 + " 小时 " + wait % 60 + " 分" : "没有在等分配的任务"),
                            wait >= 60 ? "orange" : null), BOARD),
                    W.link(W.tone(W.metric("超时未回价", s.getTimeout(), "count", "询价中 " + s.getSourcing() + " 个任务"),
                            W.num(s.getTimeout()).signum() > 0 ? "red" : null), BOARD),
                    W.link(W.metric("待审核兼职回价", s.getPendingReviewItems(), "count",
                            "来自 " + s.getPendingReview() + " 个任务"), BOARD)));
        });
    }

    @Bean
    public WorkbenchWidget buyerEfficiencyWidget(SourcingStatsService stats) {
        return W.widget("buyer-efficiency", List.of(SOURCING_STATS), () -> {
            SourcingStatsVO vo = stats.stats(W.monthStart(), LocalDate.now());
            WidgetData.Table t = new WidgetData.Table();
            t.getColumns().add(WidgetData.Column.of("采购员", "l", null));
            t.getColumns().add(WidgetData.Column.of("型号", "r", "count"));
            t.getColumns().add(WidgetData.Column.of("有价", "r", "count"));
            t.getColumns().add(WidgetData.Column.of("无货", "r", "count"));
            t.getColumns().add(WidgetData.Column.of("待回价", "r", "count"));
            t.getColumns().add(WidgetData.Column.of("有价率", "r", "percent"));
            t.setEmpty("本月还没有分配的询价");
            vo.getPurchasers().stream()
                    .sorted(Comparator.comparingInt(SourcingStatsVO.PurchaserRow::getItems).reversed())
                    .forEach(r -> t.getRows().add(List.of(r.getPurchaserName(), r.getItems(), r.getPriced(), r.getNoStock(),
                            r.getPending(), r.getPricedRate() == null ? "—" : r.getPricedRate())));
            return W.of(W.table(null, t));
        });
    }

    @Bean
    public WorkbenchWidget purchaseExecutionWidget(PermissionChecker perm, PurchaseRequirementService requirements,
                                                   PurchaseOrderService orders, ShipmentService shipments,
                                                   DiscrepancyService discrepancies) {
        return W.widget("purchase-execution", List.of(PurchaserWidgets.REQUIREMENTS, PurchaserWidgets.ORDERS,
                PurchaserWidgets.SHIPMENTS), () -> {
            List<WidgetData.Todo> todos = new ArrayList<>();
            if (perm.canAccessMenu(PurchaserWidgets.REQUIREMENTS)) {
                todos.add(W.todo("inbox", "blue", "待下单的采购需求", "销售订单已确认，还没全部下单",
                        W.num(requirements.stats(false).getOpen()).longValue(), true, PurchaserWidgets.REQUIREMENTS));
            }
            if (perm.canAccessMenu(PurchaserWidgets.ORDERS)) {
                PurchaseOrderStatsVO s = orders.stats();
                todos.add(W.todo("send", "violet", "待供应商发货", "已下单、还有未发数量，其中逾期未发 "
                        + W.num(s.getOverdueShip()).longValue() + " 张", W.num(s.getPendingShip()).longValue(), false,
                        PurchaserWidgets.ORDERS));
            }
            if (perm.canAccessMenu(PurchaserWidgets.SHIPMENTS)) {
                ShipmentPageQuery q = new ShipmentPageQuery();
                q.setPage(1);
                q.setPageSize(1000);
                q.setStatus(WarehouseConstants.SHIP_IN_TRANSIT);
                List<ShipmentListVO> transit = shipments.page(q, true, false).getRecords();
                long late = transit.stream().filter(x -> Boolean.TRUE.equals(x.getArrivalOverdue())).count();
                todos.add(W.todo("truck", "green", "在途", "供应商已发货，还没到仓", transit.size(), false,
                        PurchaserWidgets.SHIPMENTS));
                todos.add(W.todo("clock", "red", "超期未到货", "在途且已过预计到货日期", late, true, PurchaserWidgets.SHIPMENTS));
                todos.add(W.todo("warning", "orange", "到货差异待处理", "少发、错发、不良", discrepancies.countPending(), true,
                        PurchaserWidgets.SHIPMENTS + "?tab=diffs"));
            }
            return W.of(W.todos(todos));
        });
    }

    @Bean
    public WorkbenchWidget bargainResultWidget(PurchaseOrderService orders) {
        return W.widget("bargain-result", List.of(PurchaserWidgets.ORDERS, BARGAIN), () -> {
            PurchaseOrderStatsVO s = orders.stats();
            return W.of(W.metrics(
                    W.link(W.metric("本月砍价节省", s.getMonthBargain(), "money", "本月下单 " + W.num(s.getMonthOrderedCount()).longValue()
                            + " 张"), BARGAIN),
                    W.metric("砍价率", s.getMonthBargainRate(), "percent", "下单价比首次报价低的比例")));
        });
    }

    @Bean
    public WorkbenchWidget payablesWidget(PayableQueryService payables) {
        return W.widget("payables", List.of(PAYABLES), () -> {
            PayableStatsVO s = payables.stats();
            return W.of(W.metrics(
                    W.link(W.metric("本月到期应付", s.getMonthDue(), "money", "共 " + s.getMonthDueCount() + " 笔"), PAYABLES),
                    W.link(W.tone(W.metric("已逾期", s.getOverdue(), "money", "共 " + s.getOverdueCount() + " 笔"),
                            s.getOverdueCount() > 0 ? "red" : null), PAYABLES)));
        });
    }
}
