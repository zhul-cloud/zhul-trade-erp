package com.zhul.erp.modules.inquiry.sourcing.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import org.apache.ibatis.annotations.Mapper;

/** 询价任务 */
@Mapper
public interface SourcingTaskMapper extends BaseMapper<SourcingTaskDO> {
}
