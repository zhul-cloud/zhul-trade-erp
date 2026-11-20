package com.zhul.erp.modules.inventory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InvBatchMapper extends BaseMapper<InvBatchDO> {
}
