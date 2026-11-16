package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 放入公海（spec master-data/customer-pool「放入公海」） */
@Data
public class CustomerPoolRequest {
    @NotEmpty(message = "请选择要放入公海的客户")
    @Size(max = 200, message = "一次最多放入 200 个客户")
    private List<Long> ids;
    @NotBlank(message = "请填写放入原因")
    @Size(max = 200, message = "放入原因不能超过200个字符")
    private String reason;
}
