package com.zhul.erp.modules.product.candidate.repository;

import com.zhul.erp.modules.product.repository.IdCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** 需求热度：按询盘型号、有效销售订单关联到商品的次数计数；tenantId 为空时统计全部租户（平台账号） */
@Mapper
public interface ProductHeatMapper {

    @Select("<script>SELECT ii.product_id AS id, COUNT(*) AS cnt FROM inquiry_item ii "
            + "WHERE ii.deleted_at IS NULL AND ii.product_id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>"
            + "<if test='tenantId != null'> AND ii.tenant_id = #{tenantId}</if> GROUP BY ii.product_id</script>")
    List<IdCount> inquiryCounts(@Param("ids") Collection<Long> ids, @Param("tenantId") Integer tenantId);

    @Select("<script>SELECT ii.product_id AS id, COUNT(DISTINCT soi.so_id) AS cnt FROM sales_order_item soi "
            + "JOIN inquiry_item ii ON ii.id = soi.inquiry_item_id JOIN sales_order so ON so.id = soi.so_id "
            + "WHERE soi.deleted_at IS NULL AND so.deleted_at IS NULL AND so.status = 1 AND ii.product_id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>"
            + "<if test='tenantId != null'> AND so.tenant_id = #{tenantId}</if> GROUP BY ii.product_id</script>")
    List<IdCount> dealCounts(@Param("ids") Collection<Long> ids, @Param("tenantId") Integer tenantId);
}
