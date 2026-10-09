package com.zhul.erp.modules.warehouse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.warehouse.entity.ShootTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShootTaskMapper extends BaseMapper<ShootTaskDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM shoot_task WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
