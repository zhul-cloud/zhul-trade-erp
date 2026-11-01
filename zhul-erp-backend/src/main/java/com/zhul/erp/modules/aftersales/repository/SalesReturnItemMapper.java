package com.zhul.erp.modules.aftersales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.aftersales.entity.SalesReturnItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesReturnItemMapper extends BaseMapper<SalesReturnItemDO> {
    @Select("SELECT id FROM sales_return_item WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
