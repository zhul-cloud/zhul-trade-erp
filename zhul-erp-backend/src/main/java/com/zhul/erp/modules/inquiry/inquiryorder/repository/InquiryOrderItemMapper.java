package com.zhul.erp.modules.inquiry.inquiryorder.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InquiryOrderItemMapper extends BaseMapper<InquiryOrderItemDO> {

    /** 某询盘单下已有明细数量，用于生成明细编号 {询盘单编号}{NN}。 */
    @Select("SELECT COUNT(*) FROM inquiry_order_item WHERE tenant_id = #{tenantId} AND inquiry_order_id = #{inquiryOrderId}")
    int countByInquiryOrderId(@Param("tenantId") int tenantId, @Param("inquiryOrderId") Long inquiryOrderId);
}
