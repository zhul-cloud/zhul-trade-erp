package com.zhul.erp.modules.inquiry.customerinquiry.service;

import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AppendRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AppendVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryDetailVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerTypeVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.DraftVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.UploadedFileVO;
import org.springframework.web.multipart.MultipartFile;

/** 询盘单：录入、AI 解析或手动录入、确认生成型号明细与询价任务、改选价格、取消 */
public interface CustomerInquiryService {

    UploadedFileVO uploadAttachment(MultipartFile file);

    /** 把来源商机的一个附件复制为询盘附件 */
    UploadedFileVO copyOpportunityAttachment(Long opportunityId, Long attachmentId);

    /** 新老客户判断：该客户此前有已成交询盘即为老客户 */
    CustomerTypeVO customerType(Long customerId);

    CustomerInquiryVO submit(SubmitCustomerInquiryRequest req);

    PageResult<CustomerInquiryVO> page(CustomerInquiryPageQuery query);

    CustomerInquiryDetailVO detail(Long id);

    /** 附件的临时访问链接：download=true 时强制下载，否则预览 */
    FileLink attachmentLink(Long id, Long attachmentId, boolean download);

    CustomerInquiryVO startParse(Long id);

    CustomerInquiryVO retryParse(Long id);

    DraftVO draft(Long id);

    void confirm(Long id, ConfirmRequest req);

    /** 追加型号：询价中、可报价、已报价的询盘单，AI 解析或手动录入（spec inquiry/inquiry-intake「追加型号」） */
    AppendVO startAppend(Long id, AppendRequest req);

    AppendVO retryAppend(Long id, Long appendId);

    DraftVO appendDraft(Long id, Long appendId);

    void confirmAppend(Long id, Long appendId, ConfirmRequest req);

    void discardAppend(Long id, Long appendId);

    void cancel(Long id);

    /** 调整询盘等级，记操作日志 */
    void updateLevel(Long id, Integer level);

    /** 业务员核实完被退回的存疑型号后清除提醒 */
    void markReviewed(Long id);

    void applyParseSuccess(Long aiTaskId, String outputJson);

    void applyParseFailure(Long aiTaskId, String errorMessage);

}
