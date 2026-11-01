package com.zhul.erp.modules.aftersales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.aftersales.entity.AfterSalesLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AfterSalesLogMapper extends BaseMapper<AfterSalesLogDO> {
    @Select("SELECT id FROM after_sales_log WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
