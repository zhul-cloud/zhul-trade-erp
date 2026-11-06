package com.zhul.erp.modules.quotation.service;

import com.zhul.erp.modules.quotation.dto.PickCustomerVO;
import com.zhul.erp.modules.quotation.dto.PickInquiryQuery;
import com.zhul.erp.modules.quotation.dto.PickInquiryVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryPageVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryQuery;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryVO;
import com.zhul.erp.common.result.PageResult;

import com.zhul.erp.modules.quotation.entity.QuotationDO;

import java.util.List;
import java.util.Map;

/** 新建报价单时的候选：按询盘报价（待报价询盘）与挑选型号报价（客户 → 询盘 → 型号） */
public interface QuoteCandidateService {

    QuoteInquiryPageVO quoteInquiries(QuoteInquiryQuery q);

    List<PickCustomerVO> pickCustomers(String keyword);

    PageResult<PickInquiryVO> pickInquiries(PickInquiryQuery q);

    /** 某客户在数据范围内可报价、没有草稿报价单的询盘（「全选该客户可报价询盘」） */
    List<QuoteInquiryVO> readyInquiries(Long customerId);

    /** 询盘 → 包含其型号的草稿报价单 */
    Map<Long, QuotationDO> draftsByInquiry(List<Long> inquiryIds);
}
