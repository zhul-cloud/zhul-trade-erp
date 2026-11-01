package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 只带原因的请求（撤销、作废、驳回、撤回入库） */
@Data
public class ReasonBody {
    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;
    /** 改状态用：1-处理中、2-待客户确认 */
    private Integer status;
}
