package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 新建客户询盘 */
@Data
public class SubmitCustomerInquiryRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    @NotNull(message = "请选择来源")
    private Integer source;
    /** 默认今天 */
    private LocalDate inquiryDate;
    /** 默认今天，不能早于询盘日期 */
    private LocalDate quoteDeadline;
    private Boolean urgent;
    /** 询盘等级（字典 inquiry_level 码值），不传按 B */
    private Integer level;
    @Size(max = 20000, message = "询盘内容不能超过 20000 字")
    private String rawContent;
    @Valid
    @Size(max = 10, message = "附件最多 10 个")
    private List<AttachmentRef> attachments;
    /** 从商机创建时传入 */
    private Long opportunityId;
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
