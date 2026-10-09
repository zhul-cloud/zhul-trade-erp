package com.zhul.erp.modules.logistics.repository;

import com.zhul.erp.modules.logistics.dto.OutboundQtyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** 出库与出运数量的实时汇总（不落库） */
@Mapper
public interface LogisticsQtyMapper {

    /**
     * 订单型号行：occupied = 未撤回出库单上的数量；handed = 已交货代、所在出运单还没出运的数量；
     * shipped = 已交货代且所在出运单已出运的数量
     */
    @Select("<script>SELECT oi.so_item_id AS id, "
            + "SUM(CASE WHEN o.status IN (1, 2, 3) THEN oi.quantity ELSE 0 END) AS occupied, "
            + "SUM(CASE WHEN o.status = 3 AND (s.id IS NULL OR s.status &lt;&gt; 2) THEN oi.quantity ELSE 0 END) AS handed, "
            + "SUM(CASE WHEN o.status = 3 AND s.status = 2 THEN oi.quantity ELSE 0 END) AS shipped "
            + "FROM outbound_order_item oi JOIN outbound_order o ON o.id = oi.outbound_id "
            + "LEFT JOIN logistics_shipment s ON s.id = o.logistics_id AND s.deleted_at IS NULL AND s.status &lt;&gt; 3 "
            + "WHERE oi.deleted_at IS NULL AND o.deleted_at IS NULL AND oi.so_item_id IN "
            + "<foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach> "
            + "GROUP BY oi.so_item_id</script>")
    List<OutboundQtyRow> bySoItem(@Param("ids") Collection<Long> ids);
}
