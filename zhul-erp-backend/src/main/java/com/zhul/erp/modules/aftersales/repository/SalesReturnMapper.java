package com.zhul.erp.modules.aftersales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesReturnMapper extends BaseMapper<SalesReturnDO> {
    @Select("SELECT id FROM sales_return WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
