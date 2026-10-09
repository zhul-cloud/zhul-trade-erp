package com.zhul.erp.modules.purchase.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrderDO> {

    @Select("SELECT id FROM purchase_order WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
