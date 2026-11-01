package com.zhul.erp.modules.aftersales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesReturnDispositionMapper extends BaseMapper<SalesReturnDispositionDO> {
    @Select("SELECT id FROM sales_return_disposition WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
