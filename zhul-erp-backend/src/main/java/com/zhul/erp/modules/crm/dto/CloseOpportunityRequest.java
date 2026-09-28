package com.zhul.erp.modules.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 结束商机：result 为 WON（赢单）、LOST（输单）或 INVALID（无效）；输单与无效必须给原因 */
@Data
public class CloseOpportunityRequest {

    @NotBlank(message = "请选择结果")
    private String result;

    private Integer reason;

    @Size(max = 500, message = "说明不能超过500个字符")
    private String note;
}
