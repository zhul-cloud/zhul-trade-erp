package com.zhul.erp.modules.quotation.service;

import com.zhul.erp.modules.quotation.dto.PickCustomerVO;
import com.zhul.erp.modules.quotation.dto.PickInquiryQuery;
import com.zhul.erp.modules.quotation.dto.PickInquiryVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryPageVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryQuery;
import com.zhul.erp.common.result.PageResult;

import java.util.List;

/** 新建报价单时的候选：按询盘报价（待报价询盘）与挑选型号报价（客户 → 询盘 → 型号） */
public interface QuoteCandidateService {

    QuoteInquiryPageVO quoteInquiries(QuoteInquiryQuery q);

    List<PickCustomerVO> pickCustomers(String keyword);

    PageResult<PickInquiryVO> pickInquiries(PickInquiryQuery q);
}
