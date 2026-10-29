package com.zhul.erp.modules.system.migration.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.system.migration.entity.MigrationBatchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MigrationBatchMapper extends BaseMapper<MigrationBatchDO> {
}
