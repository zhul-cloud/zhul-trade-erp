package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 调整询盘等级 */
@Data
public class UpdateInquiryLevelRequest {
    /** 字典 inquiry_level 码值 */
    @NotNull(message = "请选择询盘等级")
    private Integer level;
}
