package com.zhul.erp.modules.finance.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.finance.entity.RefundRequestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefundRequestMapper extends BaseMapper<RefundRequestDO> {
    @Select("SELECT id FROM fin_refund_request WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
