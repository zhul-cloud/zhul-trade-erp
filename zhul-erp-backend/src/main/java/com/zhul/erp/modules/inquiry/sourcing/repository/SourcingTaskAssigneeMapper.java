package com.zhul.erp.modules.inquiry.sourcing.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 询价任务分配 */
@Mapper
public interface SourcingTaskAssigneeMapper extends BaseMapper<SourcingTaskAssigneeDO> {

    record Count(Long assigneeId, String brandKey, Long cnt) {
    }

    /** 熟悉度：某段时间以来各采购在各品牌上已回价的任务数 */
    @Select("SELECT a.assignee_id AS assigneeId, t.brand_key AS brandKey, COUNT(DISTINCT t.id) AS cnt "
            + "FROM sourcing_task_assignee a JOIN sourcing_task t ON t.id = a.task_id "
            + "WHERE a.tenant_id = #{tenantId} AND t.status = 3 AND a.assigned_at >= #{since} "
            + "AND a.deleted_at IS NULL AND t.deleted_at IS NULL GROUP BY a.assignee_id, t.brand_key")
    List<Count> familiarity(@Param("tenantId") int tenantId, @Param("since") LocalDateTime since);

    /** 负载：各采购手上进行中（有效分配、任务询价中）的任务数 */
    @Select("SELECT a.assignee_id AS assigneeId, '' AS brandKey, COUNT(*) AS cnt "
            + "FROM sourcing_task_assignee a JOIN sourcing_task t ON t.id = a.task_id "
            + "WHERE a.tenant_id = #{tenantId} AND a.active = 1 AND t.status = 2 "
            + "AND a.deleted_at IS NULL AND t.deleted_at IS NULL GROUP BY a.assignee_id")
    List<Count> load(@Param("tenantId") int tenantId);
}
