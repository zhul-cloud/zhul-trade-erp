package com.zhul.erp.modules.warehouse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReceivingDiscrepancyMapper extends BaseMapper<ReceivingDiscrepancyDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM receiving_discrepancy WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
