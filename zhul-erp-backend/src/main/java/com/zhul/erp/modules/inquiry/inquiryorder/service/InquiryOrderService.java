package com.zhul.erp.modules.inquiry.inquiryorder.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AddInquiryOrderSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AdvanceInquiryOrderStatusRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AssignPurchaserRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.ConvertToSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.CreateInquiryOrderManualRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderItemVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderPageQuery;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderSupplierVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryTemplateVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteComparisonVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.RecordQuoteRequest;
import com.zhul.erp.modules.inquiry.support.ConfirmedGroupDTO;

import java.util.List;

public interface InquiryOrderService {

    /** AI拆单确认后调用：为每个非空分组创建一条询盘单+明细，返回创建结果 */
    List<InquiryOrderVO> createFromConfirmedGroups(Long customerInquiryId, String customerInquiryCode,
                                                    Long aiTaskId, List<ConfirmedGroupDTO> groups);

    /**
     * 手动创建（跳过AI）：customerInquiryId 恒为空（手动创建不产生任何 customer_inquiry
     * 记录），若请求携带了 customerId 则直接写入 inquiry_order.customer_id，见 design.md 决策12。
     */
    InquiryOrderVO createManual(CreateInquiryOrderManualRequest req);

    PageResult<InquiryOrderVO> page(InquiryOrderPageQuery query);

    InquiryOrderVO getById(Long id);

    List<InquiryOrderVO> listByCustomerInquiryId(Long customerInquiryId);

    List<InquiryOrderItemVO> listItems(Long inquiryOrderId);

    /** 分配/重新分配采购员 */
    void assign(Long id, AssignPurchaserRequest req);

    /** 添加报价来源（正式供应商或电商询价渠道） */
    InquiryOrderSupplierVO addSupplier(Long inquiryOrderId, AddInquiryOrderSupplierRequest req);

    List<InquiryOrderSupplierVO> listSuppliers(Long inquiryOrderId);

    /** 电商询价渠道转为正式供应商 */
    InquiryOrderSupplierVO convertToFormalSupplier(Long inquiryOrderSupplierId, ConvertToSupplierRequest req);

    /** 录入报价：本位币金额按HALF_UP规则计算，汇率未维护时留空 */
    QuoteVO recordQuote(Long inquiryOrderId, RecordQuoteRequest req);

    QuoteComparisonVO getQuoteComparison(Long inquiryOrderId);

    /** 已分配之后的人工状态流转 */
    void advanceStatus(Long id, AdvanceInquiryOrderStatusRequest req);

    InquiryTemplateVO getTemplates(Long inquiryOrderId);
}
