package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改订单备注 */
@Data
public class OrderRemarkRequest {
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
