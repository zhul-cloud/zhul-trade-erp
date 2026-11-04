package com.zhul.erp.modules.crm.followup.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface FollowupItemMapper extends BaseMapper<FollowupItemDO> {

    /**
     * 询盘预估价值（本位币）：已有采购报价的明细「数量 × 最低单价」合计，没有采购报价的明细不计。
     * 采购报价只取已提交、有货、本位币单价大于 0 的；单价已是本位币，不再换算。
     */
    @Select("SELECT COALESCE(SUM(i.quantity * m.min_price), 0) FROM inquiry_item i"
            + " JOIN (SELECT q.inquiry_item_id, MIN(q.unit_price_cny) AS min_price FROM sourcing_quote q"
            + "       WHERE q.customer_inquiry_id = #{inquiryId} AND q.deleted_at IS NULL AND q.status = 2"
            + "         AND q.no_stock = 0 AND q.unit_price_cny > 0 GROUP BY q.inquiry_item_id) m"
            + " ON m.inquiry_item_id = i.id"
            + " WHERE i.customer_inquiry_id = #{inquiryId} AND i.deleted_at IS NULL")
    BigDecimal inquiryEstimateCny(@Param("inquiryId") Long inquiryId);

    String FROM = " FROM followup_item fi JOIN customer c ON c.id = fi.customer_id"
            + " WHERE fi.deleted_at IS NULL AND fi.closed_at IS NULL AND c.deleted_at IS NULL"
            + "<if test='f.board != null'> AND fi.board = #{f.board}</if>"
            + "<if test='f.group != null'> AND fi.stage_group = #{f.group}</if>"
            + "<if test='f.stage != null'> AND fi.stage_code = #{f.stage}</if>"
            + "<if test='f.nurture != null and f.nurture'> AND fi.nurture_until IS NOT NULL</if>"
            + "<if test='f.nurture != null and !f.nurture'> AND fi.nurture_until IS NULL</if>"
            + "<if test='f.customerId != null'> AND fi.customer_id = #{f.customerId}</if>"
            + "<if test='f.blocker != null'> AND fi.blocker_code = #{f.blocker}</if>"
            + "<if test='f.keyword != null'> AND (c.name LIKE CONCAT('%', #{f.keyword}, '%')"
            + " OR c.name_cn LIKE CONCAT('%', #{f.keyword}, '%') OR c.customer_code LIKE CONCAT('%', #{f.keyword}, '%')"
            + " OR fi.doc_no LIKE CONCAT('%', #{f.keyword}, '%'))</if>"
            + "<if test='f.exceptionOnly != null and f.exceptionOnly'> AND fi.exception_flags &gt; 0</if>"
            + "<if test='f.excludeAfterSalesNext != null and f.excludeAfterSalesNext'> AND fi.next_action_source &lt;&gt; 4</if>"
            + "<if test='f.dueFrom != null'> AND fi.next_action_date &gt;= #{f.dueFrom}</if>"
            + "<if test='f.dueTo != null'> AND fi.next_action_date &lt;= #{f.dueTo}</if>"
            + "<if test='f.ownerIds != null'> AND fi.owner_id IN"
            + "<foreach collection='f.ownerIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>";

    /**
     * 客户价值排序（design D7）：经营等级 S→C、未成交最后 → 潜力 5→1、未评最后 → 金额档高→低
     * → 逾期天数多→少 → 下一步日期早→晚（没有日期的在最后）。只关联客户一张表。
     */
    @Select("<script>SELECT fi.id" + FROM
            + " ORDER BY CASE WHEN c.biz_grade BETWEEN 1 AND 4 THEN c.biz_grade ELSE 9 END,"
            + " CASE WHEN c.potential_rating BETWEEN 1 AND 5 THEN 6 - c.potential_rating ELSE 9 END,"
            + " CASE WHEN fi.amount_cny &gt;= #{f.tier1} THEN 0 WHEN fi.amount_cny &gt;= #{f.tier2} THEN 1"
            + " WHEN fi.amount_cny &gt;= #{f.tier3} THEN 2 ELSE 3 END,"
            + " CASE WHEN fi.next_action_date IS NULL THEN 1 ELSE 0 END,"
            + " CASE WHEN fi.next_action_date &lt; #{f.today} THEN DATEDIFF(#{f.today}, fi.next_action_date) ELSE 0 END DESC,"
            + " fi.next_action_date, fi.id"
            + "<if test='f.size != null'> LIMIT #{f.offset}, #{f.size}</if></script>")
    List<Long> rankedIds(@Param("f") FollowupItemFilter f);

    @Select("<script>SELECT COUNT(*)" + FROM + "</script>")
    long countBy(@Param("f") FollowupItemFilter f);

    /** 未收合计（原币，按币种分别合计） */
    @Select("<script>SELECT fi.currency_code AS currencyCode, SUM(fi.amount - fi.received_amount) AS amount" + FROM
            + " GROUP BY fi.currency_code ORDER BY fi.currency_code</script>")
    List<Map<String, Object>> unpaidByCurrency(@Param("f") FollowupItemFilter f);
}
