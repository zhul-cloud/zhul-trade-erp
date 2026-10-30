package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 修改询盘单：客户、询盘内容与型号不在这里改 */
@Data
public class UpdateCustomerInquiryRequest {
    @NotNull(message = "请选择来源")
    private Integer source;
    @NotNull(message = "请选择询盘日期")
    private LocalDate inquiryDate;
    /** 不能早于询盘日期 */
    @NotNull(message = "请选择报价截止")
    private LocalDate quoteDeadline;
    private Boolean urgent;
    /** 不传为不关联商机 */
    private Long opportunityId;
    /** 保留的已有附件；不在里面的已有附件软删除 */
    private List<Long> keepAttachmentIds;
    /** 补传的附件：只作为资料保存，不解析 */
    @Valid
    @Size(max = 10, message = "附件最多 10 个")
    private List<AttachmentRef> attachments;
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
