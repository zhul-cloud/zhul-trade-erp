package com.zhul.erp.modules.inquiry.customerinquiry.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerInquiryMapper extends BaseMapper<CustomerInquiryDO> {

    /** 当日已有询盘编号里最大的3位流水号（不存在时返回 NULL），用于生成下一个编号。 */
    @Select("SELECT MAX(CAST(SUBSTRING(inquiry_code, -3) AS UNSIGNED)) FROM customer_inquiry " +
            "WHERE tenant_id = #{tenantId} AND inquiry_code LIKE CONCAT(#{prefix}, '%')")
    Integer selectMaxSequenceByPrefix(@Param("tenantId") int tenantId, @Param("prefix") String prefix);
}
