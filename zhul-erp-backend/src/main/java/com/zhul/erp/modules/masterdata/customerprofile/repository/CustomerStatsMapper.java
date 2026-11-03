package com.zhul.erp.modules.masterdata.customerprofile.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 客户画像的只读统计：询盘常用品牌、订单汇总（RFM）。已取消的订单（status=2）不计 */
@Mapper
public interface CustomerStatsMapper {

    @Select("SELECT MAX(ii.brand) AS name, COUNT(*) AS cnt FROM inquiry_item ii "
            + "JOIN customer_inquiry ci ON ci.id = ii.customer_inquiry_id "
            + "WHERE ci.customer_id = #{customerId} AND ci.deleted_at IS NULL AND ii.deleted_at IS NULL AND ii.brand <> '' "
            + "GROUP BY ii.brand_key ORDER BY cnt DESC, name LIMIT #{limit}")
    List<Map<String, Object>> topBrands(@Param("customerId") Long customerId, @Param("limit") int limit);

    @Select("SELECT customer_id, MIN(sales_date) AS first_date, MAX(sales_date) AS last_date FROM sales_order "
            + "WHERE tenant_id = #{tenantId} AND status <> 2 AND deleted_at IS NULL GROUP BY customer_id")
    List<Map<String, Object>> orderSpan(@Param("tenantId") int tenantId);

    @Select("SELECT customer_id, currency_code, COUNT(*) AS cnt, SUM(total_amount) AS amt, SUM(total_amount_cny) AS cny "
            + "FROM sales_order WHERE tenant_id = #{tenantId} AND status <> 2 AND deleted_at IS NULL AND sales_date >= #{since} "
            + "GROUP BY customer_id, currency_code")
    List<Map<String, Object>> yearByCurrency(@Param("tenantId") int tenantId, @Param("since") LocalDate since);

    @Select("SELECT currency_code, SUM(total_amount) AS amt, SUM(total_amount_cny) AS cny FROM sales_order "
            + "WHERE customer_id = #{customerId} AND status <> 2 AND deleted_at IS NULL GROUP BY currency_code")
    List<Map<String, Object>> totalByCurrency(@Param("customerId") Long customerId);
}
