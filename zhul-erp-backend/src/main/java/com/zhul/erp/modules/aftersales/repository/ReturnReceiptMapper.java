package com.zhul.erp.modules.aftersales.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.aftersales.entity.ReturnReceiptDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReturnReceiptMapper extends BaseMapper<ReturnReceiptDO> {
    @Select("SELECT id FROM return_receipt WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
