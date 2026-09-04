package com.zhul.erp.modules.inquiry.inquiryorder.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InquiryOrderMapper extends BaseMapper<InquiryOrderDO> {

    /** 某客户询盘下已有询盘单数量，用于生成子询盘单的字母后缀（0->A, 25->Z, 26->AA...） */
    @Select("SELECT COUNT(*) FROM inquiry_order WHERE tenant_id = #{tenantId} AND customer_inquiry_id = #{customerInquiryId}")
    int countByCustomerInquiryId(@Param("tenantId") int tenantId, @Param("customerInquiryId") Long customerInquiryId);

    /** 当日已有"无父级手动创建"询盘单编号里最大的3位流水号，用于生成下一个独立编号。 */
    @Select("SELECT MAX(CAST(SUBSTRING(inquiry_code, #{seqStart}, 3) AS UNSIGNED)) FROM inquiry_order " +
            "WHERE tenant_id = #{tenantId} AND customer_inquiry_id IS NULL AND inquiry_code LIKE CONCAT(#{prefix}, '%')")
    Integer selectMaxStandaloneSequence(@Param("tenantId") int tenantId, @Param("prefix") String prefix,
                                         @Param("seqStart") int seqStart);
}
