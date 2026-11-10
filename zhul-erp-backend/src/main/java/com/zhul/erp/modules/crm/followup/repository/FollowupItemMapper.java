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

    String TABLES = " FROM followup_item fi JOIN customer c ON c.id = fi.customer_id";

    /** 主动开发排序用：每个客户最近一份已完成背调的跟进优先级 */
    String DEVELOP_TABLES = TABLES
            + " LEFT JOIN (SELECT r.customer_id, CAST(SUBSTRING_INDEX(GROUP_CONCAT(r.priority ORDER BY r.id DESC), ',', 1)"
            + " AS UNSIGNED) AS pr FROM customer_background_report r WHERE r.deleted_at IS NULL AND r.status = 2"
            + " AND r.priority BETWEEN 1 AND 4 GROUP BY r.customer_id) rp ON rp.customer_id = fi.customer_id";

    String WHERE = " WHERE fi.deleted_at IS NULL AND fi.closed_at IS NULL AND c.deleted_at IS NULL"
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
            + "<if test='f.country != null'> AND c.country = #{f.country}</if>"
            + "<if test='f.exceptionOnly != null and f.exceptionOnly'> AND fi.exception_flags &gt; 0</if>"
            + "<if test='f.dormantOnly != null and f.dormantOnly'> AND fi.dormant_since IS NOT NULL</if>"
            + "<if test='f.excludeAfterSalesNext != null and f.excludeAfterSalesNext'> AND fi.next_action_source &lt;&gt; 4</if>"
            + "<if test='f.dueFrom != null'> AND fi.next_action_date &gt;= #{f.dueFrom}</if>"
            + "<if test='f.dueTo != null'> AND fi.next_action_date &lt;= #{f.dueTo}</if>"
            + "<if test='f.customerIds != null'> AND fi.customer_id IN"
            + "<foreach collection='f.customerIds' item='i' open='(' separator=',' close=')'>#{i}</foreach></if>"
            + "<if test='f.ownerIds != null'> AND fi.owner_id IN"
            + "<foreach collection='f.ownerIds' item='o' open='(' separator=',' close=')'>#{o}</foreach></if>";
    String FROM = TABLES + WHERE;

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

    /**
     * 主动开发排序（第四期 design D9）：潜力 5→1、未评最后 → 背调优先级 1、2、未出结论、3、4
     * → 逾期天数多→少 → 下一步日期早→晚（没有日期的在最后）。
     */
    @Select("<script>SELECT fi.id" + DEVELOP_TABLES + WHERE
            + " ORDER BY CASE WHEN c.potential_rating BETWEEN 1 AND 5 THEN 6 - c.potential_rating ELSE 9 END,"
            + " CASE WHEN rp.pr IN (1, 2) THEN rp.pr WHEN rp.pr IS NULL THEN 3 ELSE rp.pr + 1 END,"
            + " CASE WHEN fi.next_action_date &lt; #{f.today} THEN DATEDIFF(#{f.today}, fi.next_action_date) ELSE 0 END DESC,"
            + " CASE WHEN fi.next_action_date IS NULL THEN 1 ELSE 0 END, fi.next_action_date, fi.id"
            + "<if test='f.size != null'> LIMIT #{f.offset}, #{f.size}</if></script>")
    List<Long> rankedDevelopIds(@Param("f") FollowupItemFilter f);

    /** 阶段序号，与 CustomerRollup.STAGE_ORDER 一致 */
    String STAGE_RANK = "FIELD(fi.stage_code, "
            + "'D1', 'D2', 'D3', 'D4', 'D5', 'D6', 'D7', "
            + "'S1', 'S2', 'S3', 'S4', 'S5', 'S6', 'S7', "
            + "'F1', 'F2', 'F3', 'F4', 'F5', 'F6', "
            + "'A1', 'A2', 'A3', 'A4', 'A5', 'A6', "
            + "'G4', 'G5', 'G6', 'G7')";

    /**
     * 按客户分组（change followup-group-by-customer）：调用方不传 group / stage / blocker / keyword / exceptionOnly / dormantOnly，
     * 改用 stageRanks（客户所处阶段）和 match*（名下有单据满足）在 HAVING 里筛选，所处阶段按客户在本板块的全部单据算。
     */
    String GROUPED = TABLES + WHERE + " GROUP BY fi.customer_id, c.biz_grade, c.potential_rating HAVING 1 = 1"
            + "<if test='f.stageRanks != null'> AND MAX(" + STAGE_RANK + ") IN"
            + "<foreach collection='f.stageRanks' item='r' open='(' separator=',' close=')'>#{r}</foreach></if>"
            + "<if test='f.matchBlocker != null'> AND SUM(CASE WHEN fi.blocker_code = #{f.matchBlocker} THEN 1 ELSE 0 END) &gt; 0</if>"
            + "<if test='f.matchKeyword != null'> AND SUM(CASE WHEN c.name LIKE CONCAT('%', #{f.matchKeyword}, '%')"
            + " OR c.name_cn LIKE CONCAT('%', #{f.matchKeyword}, '%') OR c.customer_code LIKE CONCAT('%', #{f.matchKeyword}, '%')"
            + " OR fi.doc_no LIKE CONCAT('%', #{f.matchKeyword}, '%') THEN 1 ELSE 0 END) &gt; 0</if>"
            + "<if test='f.matchException != null and f.matchException'> AND SUM(CASE WHEN fi.exception_flags &gt; 0 THEN 1 ELSE 0 END) &gt; 0</if>"
            + "<if test='f.matchDormant != null and f.matchDormant'> AND SUM(CASE WHEN fi.dormant_since IS NOT NULL THEN 1 ELSE 0 END) &gt; 0</if>";

    /**
     * 按客户价值给客户排序：经营等级 → 潜力 → 名下最高的金额档 → 有下一步日期的在前 → 最大逾期天数多→少 → 最早下一步日期 → 客户 ID
     */
    @Select("<script>SELECT fi.customer_id" + GROUPED
            + " ORDER BY CASE WHEN c.biz_grade BETWEEN 1 AND 4 THEN c.biz_grade ELSE 9 END,"
            + " CASE WHEN c.potential_rating BETWEEN 1 AND 5 THEN 6 - c.potential_rating ELSE 9 END,"
            + " MIN(CASE WHEN fi.amount_cny &gt;= #{f.tier1} THEN 0 WHEN fi.amount_cny &gt;= #{f.tier2} THEN 1"
            + " WHEN fi.amount_cny &gt;= #{f.tier3} THEN 2 ELSE 3 END),"
            + " MAX(CASE WHEN fi.next_action_date IS NULL THEN 0 ELSE 1 END) DESC,"
            + " MAX(CASE WHEN fi.next_action_date &lt; #{f.today} THEN DATEDIFF(#{f.today}, fi.next_action_date) ELSE 0 END) DESC,"
            + " MIN(fi.next_action_date), fi.customer_id"
            + "<if test='f.size != null'> LIMIT #{f.offset}, #{f.size}</if></script>")
    List<Long> rankedCustomerIds(@Param("f") FollowupItemFilter f);

    /** 按客户分组的客户数（customers）与单据数（docs） */
    @Select("<script>SELECT COUNT(*) AS customers, COALESCE(SUM(t.n), 0) AS docs FROM (SELECT fi.customer_id, COUNT(*) AS n"
            + GROUPED + ") t</script>")
    Map<String, Object> countCustomers(@Param("f") FollowupItemFilter f);

    @Select("<script>SELECT COUNT(*)" + FROM + "</script>")
    long countBy(@Param("f") FollowupItemFilter f);

    /** 未收合计（原币，按币种分别合计） */
    @Select("<script>SELECT fi.currency_code AS currencyCode, SUM(fi.amount - fi.received_amount) AS amount" + FROM
            + " GROUP BY fi.currency_code ORDER BY fi.currency_code</script>")
    List<Map<String, Object>> unpaidByCurrency(@Param("f") FollowupItemFilter f);
}
