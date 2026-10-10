package com.zhul.erp.modules.purchase.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.purchase.dto.QtyRow;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItemDO> {

    /** 每条需求在草稿、已下单采购单上的数量（已取消、已删除的不算） */
    @Select("<script>SELECT i.requirement_id AS id, "
            + "SUM(CASE WHEN o.status = 1 THEN i.quantity ELSE 0 END) AS draft_qty, "
            + "SUM(CASE WHEN o.status = 2 THEN i.quantity ELSE 0 END) AS ordered_qty "
            + "FROM purchase_order_item i JOIN purchase_order o ON o.id = i.po_id "
            + "WHERE i.deleted_at IS NULL AND o.deleted_at IS NULL AND o.status IN (1, 2) AND i.requirement_id IN "
            + "<foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach> "
            + "GROUP BY i.requirement_id</script>")
    List<QtyRow> qtyByRequirement(@Param("ids") Collection<Long> ids);

    /** 每个订单型号行在草稿、已下单采购单上的数量 */
    @Select("<script>SELECT i.so_item_id AS id, "
            + "SUM(CASE WHEN o.status = 1 THEN i.quantity ELSE 0 END) AS draft_qty, "
            + "SUM(CASE WHEN o.status = 2 THEN i.quantity ELSE 0 END) AS ordered_qty "
            + "FROM purchase_order_item i JOIN purchase_order o ON o.id = i.po_id "
            + "WHERE i.deleted_at IS NULL AND o.deleted_at IS NULL AND o.status IN (1, 2) AND i.so_item_id IN "
            + "<foreach collection='ids' item='x' open='(' separator=',' close=')'>#{x}</foreach> "
            + "GROUP BY i.so_item_id</script>")
    List<QtyRow> qtyBySoItem(@Param("ids") Collection<Long> ids);
}
