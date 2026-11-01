package com.zhul.erp.modules.aftersales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AfterSalesMapper extends BaseMapper<AfterSalesDO> {
    @Select("SELECT id FROM after_sales WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
