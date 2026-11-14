package com.zhul.erp.modules.supply.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.supply.entity.ScDemandSignalDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScDemandSignalMapper extends BaseMapper<ScDemandSignalDO> {

    /**
     * 每个需求对象的信号统计（来源已取消、已忽略的不计）：近 90 天各来源次数（搜索词按出现次数累加），
     * 以及全部时间的有无，用于需求等级。supplyCodes 为属于「交期/物流」分类的未成交原因编码，不能为空。
     */
    @Select("<script>SELECT s.demand_id AS demandId,"
            + " SUM(s.source_type = 1 AND s.occurred_at &gt;= #{from}) AS inquiry,"
            + " SUM(s.source_type = 2 AND s.occurred_at &gt;= #{from}) AS orders,"
            + " SUM(s.source_type = 3 AND s.occurred_at &gt;= #{from}) AS stockout,"
            + " SUM(s.source_type = 4 AND s.occurred_at &gt;= #{from}) AS lost,"
            + " SUM(s.source_type = 4 AND s.occurred_at &gt;= #{from} AND s.reason_code IN <foreach collection='supplyCodes' item='c' open='(' separator=',' close=')'>#{c}</foreach>) AS lostSupply,"
            + " SUM(CASE WHEN s.source_type = 5 AND s.occurred_at &gt;= #{from} THEN s.occur_count ELSE 0 END) AS search,"
            + " SUM(s.source_type = 6 AND s.occurred_at &gt;= #{from}) AS research,"
            + " SUM(s.source_type = 1) AS inquiryAll, SUM(s.source_type IN (2, 4)) AS commercialAll,"
            + " SUM(CASE WHEN s.source_type IN (5, 6) THEN s.occur_count ELSE 0 END) AS observeAll,"
            + " COUNT(DISTINCT CASE WHEN s.source_type IN (5, 6) THEN CONCAT(s.source_type, ':', s.platform) END) AS observeSources,"
            + " MAX(s.occurred_at) AS lastAt"
            + " FROM sc_demand_signal s WHERE s.demand_id &gt; 0 AND s.deleted_at IS NULL AND s.source_cancelled = 0"
            + "<if test='ids != null'> AND s.demand_id IN <foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></if>"
            + " GROUP BY s.demand_id</script>")
    List<Map<String, Object>> stats(@Param("from") LocalDateTime from, @Param("supplyCodes") List<String> supplyCodes,
                                    @Param("ids") java.util.Collection<Long> ids);

    /** 统计期内各需求对象的询价无货次数（来源已取消、已忽略的不计） */
    @Select("SELECT s.demand_id AS demandId, COUNT(*) AS n FROM sc_demand_signal s"
            + " WHERE s.source_type = 3 AND s.demand_id > 0 AND s.occurred_at >= #{from}"
            + " AND s.source_cancelled = 0 AND s.deleted_at IS NULL GROUP BY s.demand_id")
    List<Map<String, Object>> stockoutCounts(@Param("from") LocalDateTime from);

    /** 统计期内各无货原因的次数 */
    @Select("SELECT s.reason_code AS code, MAX(s.reason_text) AS text, COUNT(*) AS n FROM sc_demand_signal s"
            + " WHERE s.source_type = 3 AND s.demand_id > 0 AND s.occurred_at >= #{from}"
            + " AND s.source_cancelled = 0 AND s.deleted_at IS NULL GROUP BY s.reason_code ORDER BY n DESC")
    List<Map<String, Object>> stockoutReasons(@Param("from") LocalDateTime from);

    /** 统计期内询过价的型号数（有已提交询价记录的需求对象，含有价与无货）：按归一化型号匹配 */
    @Select("SELECT COUNT(DISTINCT d.id) FROM sc_demand d JOIN sourcing_quote q ON q.model_key = d.model_key"
            + " WHERE d.deleted_at IS NULL AND d.model_key <> '' AND q.status = 2 AND q.deleted_at IS NULL"
            + " AND q.quoted_at >= #{from} AND q.quoted_at < #{to}")
    long quotedModels(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 统计期内无货型号数与次数（上一期对比用） */
    @Select("SELECT COUNT(DISTINCT s.demand_id) AS models, COUNT(*) AS times FROM sc_demand_signal s"
            + " WHERE s.source_type = 3 AND s.demand_id > 0 AND s.occurred_at >= #{from} AND s.occurred_at < #{to}"
            + " AND s.source_cancelled = 0 AND s.deleted_at IS NULL")
    Map<String, Object> stockoutTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
