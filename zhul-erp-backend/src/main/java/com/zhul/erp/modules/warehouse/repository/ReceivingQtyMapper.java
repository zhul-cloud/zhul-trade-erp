package com.zhul.erp.modules.warehouse.repository;

import com.zhul.erp.modules.warehouse.dto.IdQty;
import com.zhul.erp.modules.warehouse.dto.TransitRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** 发货、入库数量的实时汇总（不落库），口径见 design.md「数量口径」 */
@Mapper
public interface ReceivingQtyMapper {

    String IDS = "<foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach>";

    /**
     * 采购单行的已发数量：在途发货单的发货数量 + 已入库发货单的实收（不超过发货数量，少发的回到未发）
     * − 已处理为「退货换货」「退货不补」的不良数量（退回去的不算已发）
     */
    @Select("<script>SELECT x.id, SUM(x.q) AS qty FROM ("
            + "SELECT si.po_item_id AS id, si.quantity AS q FROM supplier_shipment_item si JOIN supplier_shipment s ON s.id = si.shipment_id "
            + "WHERE si.deleted_at IS NULL AND s.deleted_at IS NULL AND s.status = 1 AND si.po_item_id IN " + IDS
            + " UNION ALL SELECT ri.po_item_id, LEAST(ri.received_qty, ri.shipped_qty) FROM purchase_receipt_item ri "
            + "JOIN purchase_receipt r ON r.id = ri.receipt_id "
            + "WHERE ri.deleted_at IS NULL AND r.deleted_at IS NULL AND r.status = 1 AND ri.po_item_id IN " + IDS
            + " UNION ALL SELECT d.po_item_id, -d.quantity FROM receiving_discrepancy d "
            + "WHERE d.deleted_at IS NULL AND d.status = 2 AND d.resolution IN (3, 4) AND d.po_item_id IN " + IDS
            + ") x GROUP BY x.id</script>")
    List<IdQty> shippedByPoItem(@Param("ids") Collection<Long> ids);

    /** 采购单行的合格入库数量：有效入库单的合格数（多发部分不算）+ 已处理为「折价接收」的不良数量 */
    @Select("<script>SELECT x.id, SUM(x.q) AS qty FROM ("
            + "SELECT ri.po_item_id AS id, LEAST(ri.qualified_qty, ri.shipped_qty) AS q FROM purchase_receipt_item ri "
            + "JOIN purchase_receipt r ON r.id = ri.receipt_id "
            + "WHERE ri.deleted_at IS NULL AND r.deleted_at IS NULL AND r.status = 1 AND ri.po_item_id IN " + IDS
            + " UNION ALL SELECT d.po_item_id, d.quantity FROM receiving_discrepancy d "
            + "WHERE d.deleted_at IS NULL AND d.status = 2 AND d.resolution = 5 AND d.po_item_id IN " + IDS
            + ") x GROUP BY x.id</script>")
    List<IdQty> qualifiedByPoItem(@Param("ids") Collection<Long> ids);

    /** 订单型号行的已发数量，口径同 shippedByPoItem */
    @Select("<script>SELECT x.id, SUM(x.q) AS qty FROM ("
            + "SELECT si.so_item_id AS id, si.quantity AS q FROM supplier_shipment_item si JOIN supplier_shipment s ON s.id = si.shipment_id "
            + "WHERE si.deleted_at IS NULL AND s.deleted_at IS NULL AND s.status = 1 AND si.so_item_id IN " + IDS
            + " UNION ALL SELECT ri.so_item_id, LEAST(ri.received_qty, ri.shipped_qty) FROM purchase_receipt_item ri "
            + "JOIN purchase_receipt r ON r.id = ri.receipt_id "
            + "WHERE ri.deleted_at IS NULL AND r.deleted_at IS NULL AND r.status = 1 AND ri.so_item_id IN " + IDS
            + " UNION ALL SELECT ri.so_item_id, -d.quantity FROM receiving_discrepancy d "
            + "JOIN purchase_receipt_item ri ON ri.id = d.receipt_item_id "
            + "WHERE d.deleted_at IS NULL AND d.status = 2 AND d.resolution IN (3, 4) AND ri.so_item_id IN " + IDS
            + ") x GROUP BY x.id</script>")
    List<IdQty> shippedBySoItem(@Param("ids") Collection<Long> ids);

    /** 订单型号行在在途发货单上的数量（每张发货单一行） */
    @Select("<script>SELECT si.so_item_id AS soItemId, s.id AS shipmentId, s.sd_no AS sdNo, s.carrier, SUM(si.quantity) AS quantity, "
            + "s.expected_arrival_date AS expectedArrivalDate FROM supplier_shipment_item si JOIN supplier_shipment s ON s.id = si.shipment_id "
            + "WHERE si.deleted_at IS NULL AND s.deleted_at IS NULL AND s.status = 1 AND si.so_item_id IN " + IDS
            + " GROUP BY si.so_item_id, s.id, s.sd_no, s.carrier, s.expected_arrival_date ORDER BY s.expected_arrival_date, s.id</script>")
    List<TransitRow> inTransitBySoItem(@Param("ids") Collection<Long> ids);

    /** 订单型号行的合格入库数量，口径同上 */
    @Select("<script>SELECT x.id, SUM(x.q) AS qty FROM ("
            + "SELECT ri.so_item_id AS id, LEAST(ri.qualified_qty, ri.shipped_qty) AS q FROM purchase_receipt_item ri "
            + "JOIN purchase_receipt r ON r.id = ri.receipt_id "
            + "WHERE ri.deleted_at IS NULL AND r.deleted_at IS NULL AND r.status = 1 AND ri.so_item_id IN " + IDS
            + " UNION ALL SELECT ri.so_item_id, d.quantity FROM receiving_discrepancy d "
            + "JOIN purchase_receipt_item ri ON ri.id = d.receipt_item_id "
            + "WHERE d.deleted_at IS NULL AND d.status = 2 AND d.resolution = 5 AND ri.so_item_id IN " + IDS
            + ") x GROUP BY x.id</script>")
    List<IdQty> qualifiedBySoItem(@Param("ids") Collection<Long> ids);
}
