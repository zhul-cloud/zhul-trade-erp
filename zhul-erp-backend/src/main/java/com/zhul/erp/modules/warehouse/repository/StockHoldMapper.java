package com.zhul.erp.modules.warehouse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.warehouse.entity.StockHoldDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockHoldMapper extends BaseMapper<StockHoldDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM stock_hold WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
