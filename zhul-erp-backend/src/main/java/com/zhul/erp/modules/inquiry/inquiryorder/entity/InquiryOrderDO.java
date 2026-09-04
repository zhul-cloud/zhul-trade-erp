package com.zhul.erp.modules.inquiry.inquiryorder.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inquiry_order")
public class InquiryOrderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String inquiryCode;
    /** 客户询盘ID；手动创建时为空 */
    private Long customerInquiryId;
    /** 客户ID，关联customer.id；仅手动创建且选择了客户时有值，与customerInquiryId相互独立 */
    private Long customerId;
    private String brand;
    private String category;
    private Integer itemCount;
    /** 采购员ID；初始为空，分配后有值 */
    private Long assigneeId;
    /** 状态，见 InquiryOrderStatus */
    private Integer status;
    private String inquiryTemplate;
    private String emailTemplateCn;
    private String emailTemplateEn;
    private Long aiTaskId;
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
