package com.zhul.erp.modules.crm.followup.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一张销售订单推导订单履约、售后评价阶段要用到的单据事实（design D3–D7），由 {@link OrderFollowupSync} 从库里装好，
 * {@link FulfilDeriver}、{@link AfterSalesDeriver} 只做计算、不查库，便于单元测试。
 *
 * @param autoProgress 型号进度已换算成自动步骤（PENDING_PURCHASE / ORDERED / RECEIVED / TO_FORWARDER / SHIPPED），
 *                     手动步骤归入前面最近的自动步骤
 */
public record OrderFacts(Long soId, String soNo, Long customerId, Long ownerId, String currencyCode, BigDecimal totalAmount,
                         BigDecimal totalAmountCny, LocalDateTime createdAt, boolean completed, LocalDate completedDate,
                         List<Item> items, List<PoLine> poLines, boolean pendingDiscrepancy, LocalDate lastShotDate,
                         List<Outbound> outbounds, List<Shipment> shipments, List<AfterSales> afterSales) {

    /** 未全部取消的型号行；requirement：有有效的采购需求（需要向供应商采购） */
    public record Item(Long soItemId, String model, int quantity, String autoProgress, boolean requirement) {
    }

    /** 已下单的采购行 */
    public record PoLine(Long poId, String poNo, String supplierName, Long soItemId, LocalDate orderedDate,
                         LocalDate expectedShipDate) {
    }

    /** 销售出库单：status 1-待打包、2-已打包、3-已交货代、4-已撤回 */
    public record Outbound(Long id, int status, List<Long> soItemIds, Long logisticsId) {
    }

    /** 已出运的出运单 */
    public record Shipment(Long id, String shNo, String carrier, String waybillNo, LocalDate shippedDate) {
    }

    /** 关联这张订单的售后单：status 1-处理中、2-待客户确认、3-已结案、4-已撤销 */
    public record AfterSales(Long id, String asNo, int status, String nextAction, LocalDateTime nextUpdateAt,
                             LocalDateTime createdAt, LocalDateTime closedAt) {
    }
}
