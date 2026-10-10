package com.zhul.erp.modules.warehouse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.warehouse.entity.SupplierShipmentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupplierShipmentMapper extends BaseMapper<SupplierShipmentDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM supplier_shipment WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
