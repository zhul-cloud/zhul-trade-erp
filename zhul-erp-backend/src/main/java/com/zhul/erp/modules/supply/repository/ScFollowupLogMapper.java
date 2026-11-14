package com.zhul.erp.modules.supply.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.supply.entity.ScFollowupLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScFollowupLogMapper extends BaseMapper<ScFollowupLogDO> {

    /** 涉及这些需求对象的处理记录，新的在前；demandIdsJson 为 JSON 数组，如 [1,2] */
    @Select("SELECT id, tenant_id, record_type, source_type, source_id, source_doc_no, content, demand_ids, status_changes,"
            + " gap_project_id, next_action, next_action_date, operator_id, create_time, create_by, update_time, update_by"
            + " FROM sc_followup_log WHERE deleted_at IS NULL"
            + " AND JSON_OVERLAPS(demand_ids, CAST(#{demandIdsJson} AS JSON)) ORDER BY id DESC LIMIT #{limit}")
    List<ScFollowupLogDO> touching(@Param("demandIdsJson") String demandIdsJson, @Param("limit") int limit);
}
