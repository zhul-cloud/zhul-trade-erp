package com.zhul.erp.modules.crm.followup.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 团队视图的结果指标（第二期 design D10）：按跟进负责人（单据的负责人）与时间范围 [from, to) 聚合，
 * owners 为 null 时不限负责人（数据权限为全部）。团队视图按跟进负责人统计，销售统计按订单归属统计，两者不要求一致。
 * 租户条件由租户拦截器追加。
 */
@Mapper
public interface FollowupTeamMapper {

    /** 新商机：区间内新建的商机 */
    @Select("<script>SELECT COUNT(*) FROM opportunity o WHERE o.deleted_at IS NULL"
            + " AND o.create_time &gt;= #{from} AND o.create_time &lt; #{to}" + "<if test='owners != null'> AND o.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if></script>")
    long newOpportunities(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 发出报价：第一次发送（版本中最早的发送时间）落在区间内的报价单 */
    @Select("<script>SELECT COUNT(*) FROM quotation q JOIN (SELECT v.quotation_id, MIN(v.sent_at) AS first_sent FROM quotation_version v"
            + " WHERE v.sent_at IS NOT NULL AND v.deleted_at IS NULL GROUP BY v.quotation_id) f ON f.quotation_id = q.id"
            + " WHERE q.deleted_at IS NULL AND f.first_sent &gt;= #{from} AND f.first_sent &lt; #{to}"
            + "<if test='owners != null'> AND q.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if></script>")
    long firstSentQuotations(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 发出 PI：口径同报价 */
    @Select("<script>SELECT COUNT(*) FROM proforma_invoice p JOIN (SELECT v.pi_id, MIN(v.sent_at) AS first_sent FROM proforma_invoice_version v"
            + " WHERE v.sent_at IS NOT NULL AND v.deleted_at IS NULL GROUP BY v.pi_id) f ON f.pi_id = p.id"
            + " WHERE p.deleted_at IS NULL AND f.first_sent &gt;= #{from} AND f.first_sent &lt; #{to}"
            + "<if test='owners != null'> AND p.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if></script>")
    long firstSentPis(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 到账成交：区间内新建、未取消的销售订单（不含售后补发单）的单数和本位币合计；
     * repeat 为 true 时只算复购（客户在这张订单之前已有已完成的订单）
     */
    @Select("<script>SELECT COUNT(*) AS cnt, COALESCE(SUM(s.total_amount_cny), 0) AS amount FROM sales_order s"
            + " WHERE s.deleted_at IS NULL AND s.status = 1 AND (s.after_sales_id IS NULL OR s.after_sales_id = 0)"
            + " AND s.create_time &gt;= #{from} AND s.create_time &lt; #{to}"
            + "<if test='repeat'> AND EXISTS (SELECT 1 FROM sales_order p WHERE p.customer_id = s.customer_id"
            + " AND p.deleted_at IS NULL AND p.progress_code = 'COMPLETED' AND p.completed_at &lt; s.create_time)</if>"
            + "<if test='owners != null'> AND s.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if></script>")
    Map<String, Object> orders(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                               @Param("repeat") boolean repeat);

    /** 按时送达：签收日期在区间内的售后评价对象，签收不晚于首次估算的预计送达日的单数 / 总单数 */
    @Select("<script>SELECT COUNT(*) AS total,"
            + " COALESCE(SUM(CASE WHEN fi.eta_first IS NOT NULL AND fi.signed_date &lt;= fi.eta_first THEN 1 ELSE 0 END), 0) AS onTime"
            + " FROM followup_item fi WHERE fi.deleted_at IS NULL AND fi.board = 4"
            + " AND fi.signed_date &gt;= #{from} AND fi.signed_date &lt; #{to}"
            + "<if test='owners != null'> AND fi.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if></script>")
    Map<String, Object> onTime(@Param("owners") Collection<Long> owners, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 评价：区间内记跟进时选的评价状态（2-已邀请、3-已评价、4-婉拒）各多少条 */
    @Select("<script>SELECT r.review_status AS status, COUNT(*) AS cnt FROM customer_followup r"
            + " JOIN followup_item fi ON fi.id = r.followup_item_id"
            + " WHERE r.deleted_at IS NULL AND r.record_type = 1 AND r.review_status IN (2, 3, 4)"
            + " AND r.create_time &gt;= #{from} AND r.create_time &lt; #{to}"
            + "<if test='owners != null'> AND fi.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>"
            + " GROUP BY r.review_status</script>")
    List<Map<String, Object>> reviews(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    /**
     * 客户经营 · 已跟的到期（第三期 design D9）：业务员记的跟进里，记录当时对象的下一步日期（due_date）落在区间内的，
     * 按（对象, 到期日）去重；在到期日（含）之前记的算按时
     */
    @Select("<script>SELECT COUNT(DISTINCT r.followup_item_id, r.due_date) AS total,"
            + " COUNT(DISTINCT CASE WHEN DATE(r.create_time) &lt;= r.due_date THEN CONCAT(r.followup_item_id, '-', r.due_date) END) AS onTime"
            + " FROM customer_followup r JOIN followup_item fi ON fi.id = r.followup_item_id"
            + " WHERE r.deleted_at IS NULL AND r.record_type = 1 AND fi.board = 5"
            + " AND r.due_date &gt;= #{from} AND r.due_date &lt; #{to}"
            + "<if test='owners != null'> AND fi.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>" + "</script>")
    Map<String, Object> accountTouched(@Param("owners") Collection<Long> owners, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 客户经营 · 还没跟的到期：进行中、不在培育、下一步日期在 [from, to) 内的对象 */
    @Select("<script>SELECT COUNT(*) FROM followup_item fi WHERE fi.deleted_at IS NULL AND fi.closed_at IS NULL"
            + " AND fi.board = 5 AND fi.nurture_until IS NULL AND fi.next_action_date &gt;= #{from} AND fi.next_action_date &lt; #{to}"
            + "<if test='owners != null'> AND fi.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>" + "</script>")
    long accountPending(@Param("owners") Collection<Long> owners, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 老客户新询盘：区间内新建（不含迁移）、客户经营等级不为「未成交」的询盘，按询盘负责人 */
    @Select("<script>SELECT COUNT(*) FROM customer_inquiry ci JOIN customer c ON c.id = ci.customer_id"
            + " WHERE ci.deleted_at IS NULL AND (ci.migrated IS NULL OR ci.migrated = 0) AND c.biz_grade &gt; 0"
            + " AND ci.create_time &gt;= #{from} AND ci.create_time &lt; #{to}"
            + "<if test='owners != null'> AND ci.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if></script>")
    long accountInquiries(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 沉睡：当前沉睡的客户经营对象 */
    @Select("<script>SELECT COUNT(*) FROM followup_item fi WHERE fi.deleted_at IS NULL AND fi.closed_at IS NULL"
            + " AND fi.board = 5 AND fi.dormant_since IS NOT NULL"
            + "<if test='owners != null'> AND fi.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>" + "</script>")
    long dormant(@Param("owners") Collection<Long> owners);

    /** 激活：区间内取消沉睡时写的系统记录（标题前缀「沉睡激活」） */
    @Select("<script>SELECT COUNT(*) FROM customer_followup r JOIN followup_item fi ON fi.id = r.followup_item_id"
            + " WHERE r.deleted_at IS NULL AND r.record_type = 2 AND fi.board = 5 AND r.title LIKE CONCAT(#{prefix}, '%')"
            + " AND r.create_time &gt;= #{from} AND r.create_time &lt; #{to}"
            + "<if test='owners != null'> AND fi.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>" + "</script>")
    long activated(@Param("owners") Collection<Long> owners, @Param("prefix") String prefix, @Param("from") LocalDateTime from,
                   @Param("to") LocalDateTime to);

    /**
     * 主动开发指标（第四期 spec「团队视图的主动开发指标」）：区间内新增、首次触达、第一次回复、转商机、停止的开发记录数。
     * 区间为 [from, to)，日期字段按天比较。
     */
    @Select("<script>SELECT"
            + " COALESCE(SUM(CASE WHEN fi.create_time &gt;= #{from} AND fi.create_time &lt; #{to} THEN 1 ELSE 0 END), 0) AS created,"
            + " COALESCE(SUM(CASE WHEN fi.first_touch_date &gt;= #{from} AND fi.first_touch_date &lt; #{to} THEN 1 ELSE 0 END), 0) AS touched,"
            + " COALESCE(SUM(CASE WHEN fi.replied_at &gt;= #{from} AND fi.replied_at &lt; #{to} THEN 1 ELSE 0 END), 0) AS replied,"
            + " COALESCE(SUM(CASE WHEN fi.close_reason = 7 AND fi.closed_at &gt;= #{from} AND fi.closed_at &lt; #{to} THEN 1 ELSE 0 END), 0)"
            + " AS converted,"
            + " COALESCE(SUM(CASE WHEN fi.close_reason = 8 AND fi.closed_at &gt;= #{from} AND fi.closed_at &lt; #{to} THEN 1 ELSE 0 END), 0)"
            + " AS stopped"
            + " FROM followup_item fi WHERE fi.deleted_at IS NULL AND fi.board = 1"
            + "<if test='owners != null'> AND fi.owner_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>" + "</script>")
    Map<String, Object> develop(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /** 记跟进次数：业务员自己记的跟进（不含系统事件），按记录人 */
    @Select("<script>SELECT r.operator_id AS ownerId, COUNT(*) AS cnt FROM customer_followup r"
            + " WHERE r.deleted_at IS NULL AND r.record_type = 1 AND r.create_time &gt;= #{from} AND r.create_time &lt; #{to}"
            + "<if test='owners != null'> AND r.operator_id IN"
            + "<foreach collection='owners' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>"
            + " GROUP BY r.operator_id</script>")
    List<Map<String, Object>> recordsByOperator(@Param("owners") Collection<Long> owners, @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);
}
