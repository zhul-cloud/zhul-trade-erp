package com.zhul.erp.modules.system.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.system.entity.SysLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysLogMapper extends BaseMapper<SysLogDO> {

    @Select("SELECT DISTINCT menu FROM sys_log " +
            "WHERE tenant_id = #{tenantId} AND type = #{type} AND deleted_at IS NULL AND menu != '' " +
            "ORDER BY menu")
    List<String> selectDistinctMenus(@Param("tenantId") Integer tenantId, @Param("type") Integer type);
}
