package com.zhul.erp.modules.logistics.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboundOrderItemMapper extends BaseMapper<OutboundOrderItemDO> {}
