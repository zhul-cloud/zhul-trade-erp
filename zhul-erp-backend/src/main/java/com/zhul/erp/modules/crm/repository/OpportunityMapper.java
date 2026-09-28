package com.zhul.erp.modules.crm.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.zhul.erp.modules.crm.dto.OpportunityStatRow;
import com.zhul.erp.modules.crm.dto.LinkedInquiryVO;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OpportunityMapper extends BaseMapper<OpportunityDO> {

    /** 当日已有商机编号里最大的 3 位流水号（不存在时返回 NULL） */
    @Select("SELECT MAX(CAST(SUBSTRING(opportunity_code, -3) AS UNSIGNED)) FROM opportunity "
            + "WHERE tenant_id = #{tenantId} AND opportunity_code LIKE CONCAT(#{prefix}, '%')")
    Integer selectMaxSequenceByPrefix(@Param("tenantId") int tenantId, @Param("prefix") String prefix);

    String STAT_COLUMNS = "COUNT(*) AS total, SUM(stage_code = 'INVALID') AS invalid, SUM(reached_valid) AS valid, "
            + "SUM(stage_code = 'WON') AS won, SUM(stage_code = 'LOST') AS lost FROM opportunity ";

    /** 按来源渠道统计；ew 由服务层构造（租户、日期范围、数据权限、未删除） */
    @Select("SELECT CAST(source_channel AS CHAR) AS groupKey, " + STAT_COLUMNS
            + "${ew.customSqlSegment} GROUP BY source_channel")
    List<OpportunityStatRow> statsByChannel(@Param(Constants.WRAPPER) Wrapper<OpportunityDO> ew);

    @Select("SELECT CAST(owner_id AS CHAR) AS groupKey, " + STAT_COLUMNS
            + "${ew.customSqlSegment} GROUP BY owner_id")
    List<OpportunityStatRow> statsByOwner(@Param(Constants.WRAPPER) Wrapper<OpportunityDO> ew);

    @Select("SELECT DATE_FORMAT(first_contact_date, '%Y-%m-%d') AS groupKey, " + STAT_COLUMNS
            + "${ew.customSqlSegment} GROUP BY first_contact_date")
    List<OpportunityStatRow> statsByDate(@Param(Constants.WRAPPER) Wrapper<OpportunityDO> ew);

    /** 关联该商机的客户询盘（跨模块只读，避免 crm 依赖询盘服务） */
    @Select("SELECT id, inquiry_code AS inquiryCode, status, inquiry_date AS inquiryDate, "
            + "total_order_count AS totalOrderCount FROM customer_inquiry "
            + "WHERE tenant_id = #{tenantId} AND opportunity_id = #{opportunityId} AND deleted_at IS NULL "
            + "ORDER BY id DESC")
    List<LinkedInquiryVO> selectLinkedInquiries(@Param("tenantId") int tenantId, @Param("opportunityId") long opportunityId);
}
