package com.zhul.erp.modules.inquiry.customerinquiry.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客户询盘：一个客户的一次采购需求 */
@Data
@TableName("customer_inquiry")
public class CustomerInquiryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String inquiryCode;
    private Long customerId;
    /** 来源商机ID，从商机创建时写入，可空 */
    private Long opportunityId;
    /** 来源渠道，见 InquiryConstants.SOURCE_* */
    private Integer source;
    private String rawContent;
    private LocalDate inquiryDate;
    private LocalDate expectedReplyDate;
    private Integer urgent;
    /** 询盘等级，字典 inquiry_level 码值，越小越优先 */
    private Integer level;
    private LocalDate quoteDeadline;
    /** 新老客户快照，见 InquiryConstants.CUSTOMER_* */
    private Integer customerType;
    /** 型号来源，见 InquiryConstants.PARSE_MODE_* */
    private Integer parseMode;
    /** 状态，见 InquiryConstants.STATUS_* */
    private Integer status;
    /** 询价任务数 */
    private Integer totalOrderCount;
    private Integer totalItemCount;
    /** 所有型号数量之和（不区分单位）；待确认时为 AI 识别结果 */
    private Integer totalQuantity;
    private Integer pricedItemCount;
    private Integer pendingVerifyCount;
    private Integer needsReview;
    private Long ownerId;
    private Long aiTaskId;
    private String parseError;
    private String remark;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
