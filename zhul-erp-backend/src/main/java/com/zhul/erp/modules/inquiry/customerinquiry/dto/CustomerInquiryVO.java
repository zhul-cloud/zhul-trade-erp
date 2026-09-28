package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CustomerInquiryVO {
    private Long id;
    private String inquiryCode;
    private Long customerId;
    /** 来源商机ID（可空） */
    private Long opportunityId;
    /** 来源商机摘要：详情接口且在数据权限内时才有，否则为空 */
    private String opportunityCode;
    private String opportunityCustomerName;
    private Integer opportunitySourceChannel;
    private String opportunityStageName;
    private Integer source;
    private String rawContent;
    private String rawAttachmentUrl;
    private LocalDate inquiryDate;
    private LocalDate expectedReplyDate;
    private Integer status;
    private Integer totalOrderCount;
    private Integer totalItemCount;
    private Integer pendingVerifyCount;
    private Long ownerId;
    private Long aiTaskId;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
