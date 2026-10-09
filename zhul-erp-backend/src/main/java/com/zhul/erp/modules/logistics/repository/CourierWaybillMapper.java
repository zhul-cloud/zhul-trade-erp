package com.zhul.erp.modules.logistics.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.logistics.entity.CourierWaybillDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourierWaybillMapper extends BaseMapper<CourierWaybillDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM courier_waybill WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
