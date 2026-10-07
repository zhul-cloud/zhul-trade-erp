package com.zhul.erp.modules.quotation.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.quotation.dto.AddQuotationItemsRequest;
import com.zhul.erp.modules.quotation.dto.CreateQuotationRequest;
import com.zhul.erp.modules.quotation.dto.MarkLostRequest;
import com.zhul.erp.modules.quotation.dto.QuotationListVO;
import com.zhul.erp.modules.quotation.dto.QuotationPageQuery;
import com.zhul.erp.modules.quotation.dto.QuotationStatsVO;
import com.zhul.erp.modules.quotation.dto.QuotationVO;
import com.zhul.erp.modules.quotation.dto.SaveQuotationRequest;

import java.util.List;

/** 报价单：新建、编辑、状态流转（推进客户询盘状态）、列表 */
public interface QuotationService {

    QuotationVO create(CreateQuotationRequest req);

    QuotationVO addItems(Long id, AddQuotationItemsRequest req);

    QuotationVO detail(Long id);

    QuotationVO save(Long id, SaveQuotationRequest req);

    /** 草稿按当前系统汇率重算（各行保持毛利率） */
    QuotationVO recalcRate(Long id);

    void delete(Long id);

    QuotationVO markSent(Long id, int channel);

    QuotationVO copy(Long id);

    QuotationVO markLost(Long id, MarkLostRequest req);

    QuotationVO voidQuotation(Long id);

    PageResult<QuotationListVO> page(QuotationPageQuery q);

    QuotationStatsVO stats();

    /** 客户询盘详情中的「报价单」区域：包含该询盘型号的报价单 */
    List<QuotationListVO> byInquiry(Long inquiryId);
}
