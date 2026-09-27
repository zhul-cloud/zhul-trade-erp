package com.zhul.erp.modules.inquiry.customerinquiry.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AdvanceCustomerInquiryStatusRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryPreviewVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;

public interface CustomerInquiryService {

    /** 提交客户询盘：仅创建记录（status=待解析），不自动触发AI解析 */
    CustomerInquiryVO submit(SubmitCustomerInquiryRequest req);

    PageResult<CustomerInquiryVO> page(CustomerInquiryPageQuery query);

    CustomerInquiryVO getById(Long id);

    /** 仅"待解析"状态可调用：创建AI任务并提交，状态转为"解析中" */
    CustomerInquiryVO startAiParse(Long id);

    /** 仅"解析失败"状态可调用：复用原始内容重新发起一次AI解析，状态转为"解析中" */
    CustomerInquiryVO retryParse(Long id);

    /** 拆单预览：直接渲染 ai_task.output，不落地 inquiry_order（仅"待确认"状态可查看） */
    InquiryPreviewVO getPreview(Long id);

    /** 确认拆单：创建 inquiry_order + inquiry_order_item，状态转为"已确认" */
    void confirmSplit(Long id, ConfirmSplitRequest req);

    /** 取消询盘（拆单预览页"取消询盘"） */
    void cancel(Long id);

    /** 已确认之后的人工状态流转：待报价→报价中→已报价→已成交/已取消 */
    void advanceStatus(Long id, AdvanceCustomerInquiryStatusRequest req);

    /**
     * 供 InquiryParseResultHandler（任务4.4）使用：按 ai_task_id 反查客户询盘，
     * 写入解析结果统计并将状态置为"待确认"。找不到对应客户询盘时静默忽略并记录警告日志
     * （不应该发生，除非数据被误删），不抛异常导致回调整体失败。
     */
    void applyParseSuccess(Long aiTaskId, String outputJson);

    /** 同上，解析失败分支：状态置为"解析失败"并记录失败原因 */
    void applyParseFailure(Long aiTaskId, String errorMessage);
}
