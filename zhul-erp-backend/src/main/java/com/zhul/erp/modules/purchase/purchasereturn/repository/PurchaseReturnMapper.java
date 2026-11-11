package com.zhul.erp.modules.purchase.purchasereturn.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.purchase.purchasereturn.entity.PurchaseReturnDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PurchaseReturnMapper extends BaseMapper<PurchaseReturnDO> {
    @Select("SELECT id FROM purchase_return WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
