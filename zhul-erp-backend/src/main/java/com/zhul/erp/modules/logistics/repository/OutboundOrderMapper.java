package com.zhul.erp.modules.logistics.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboundOrderMapper extends BaseMapper<OutboundOrderDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM outbound_order WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
