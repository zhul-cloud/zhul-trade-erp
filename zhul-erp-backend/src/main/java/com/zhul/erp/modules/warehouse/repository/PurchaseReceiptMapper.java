package com.zhul.erp.modules.warehouse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseReceiptMapper extends BaseMapper<PurchaseReceiptDO> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM purchase_receipt WHERE id = #{id} FOR UPDATE")
    Long lockById(@org.apache.ibatis.annotations.Param("id") Long id);
}
