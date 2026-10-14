package com.zhul.erp.modules.inquiry.customerinquiry.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryAppendDO;
import org.apache.ibatis.annotations.Mapper;

/** 询盘单追加型号批次 */
@Mapper
public interface CustomerInquiryAppendMapper extends BaseMapper<CustomerInquiryAppendDO> {
}
