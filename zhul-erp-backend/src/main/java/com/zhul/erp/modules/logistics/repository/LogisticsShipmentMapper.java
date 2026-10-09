package com.zhul.erp.modules.logistics.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogisticsShipmentMapper extends BaseMapper<LogisticsShipmentDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM logistics_shipment WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
