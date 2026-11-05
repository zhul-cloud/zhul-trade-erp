package com.zhul.erp.modules.crm.followup.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 客户经营作战卡的聚合查询（第三期 design D6、D7）。经过租户拦截器，每条 SQL 最多两张表。
 * 订单口径：未取消（status &lt;&gt; 2）、未删除；年度成交另外排除售后补发。
 */
@Mapper
public interface FollowupAccountMapper {

    String ORDER_LINES = " FROM sales_order o JOIN sales_order_item i ON i.so_id = o.id"
            + " WHERE o.deleted_at IS NULL AND i.deleted_at IS NULL AND o.status &lt;&gt; 2"
            + " AND i.category IS NOT NULL AND TRIM(i.category) &lt;&gt; ''";

    /** 客户买过的品类（去空白） */
    @Select("<script>SELECT DISTINCT TRIM(i.category)" + ORDER_LINES + " AND o.customer_id = #{customerId}</script>")
    List<String> categoriesOf(@Param("customerId") Long customerId);

    /** 买过这些品类中任一个的其他客户 */
    @Select("<script>SELECT DISTINCT o.customer_id" + ORDER_LINES + " AND o.customer_id &lt;&gt; #{customerId}"
            + " AND TRIM(i.category) IN <foreach collection='categories' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + "</script>")
    List<Long> peers(@Param("customerId") Long customerId, @Param("categories") Collection<String> categories);

    /** 这些客户还买过、但不在 categories 里的品类，按客户数倒序，至少 minCustomers 个客户买过，取前 limit 个 */
    @Select("<script>SELECT TRIM(i.category) AS category, COUNT(DISTINCT o.customer_id) AS customers" + ORDER_LINES
            + " AND o.customer_id IN <foreach collection='peers' item='p' open='(' separator=',' close=')'>#{p}</foreach>"
            + " AND TRIM(i.category) NOT IN <foreach collection='categories' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + " GROUP BY TRIM(i.category) HAVING COUNT(DISTINCT o.customer_id) &gt;= #{minCustomers}"
            + " ORDER BY customers DESC, category LIMIT #{limit}</script>")
    List<Map<String, Object>> crossSell(@Param("peers") Collection<Long> peers, @Param("categories") Collection<String> categories,
                                        @Param("minCustomers") int minCustomers, @Param("limit") int limit);

    /** 一个客户某段时间的成交（本位币）：未取消、不含售后补发 */
    @Select("SELECT COALESCE(SUM(o.total_amount_cny), 0) FROM sales_order o"
            + " WHERE o.customer_id = #{customerId} AND o.deleted_at IS NULL AND o.status <> 2"
            + " AND (o.after_sales_id IS NULL OR o.after_sales_id = 0) AND (o.original_so_id IS NULL OR o.original_so_id = 0)"
            + " AND o.sales_date >= #{from} AND o.sales_date < #{to}")
    BigDecimal dealt(@Param("customerId") Long customerId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
