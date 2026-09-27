package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CustomerTransferRequest {
    @NotEmpty(message = "请选择要转移的客户")
    @Size(max = 200, message = "单次最多转移200个客户")
    private List<Long> ids;
    @NotNull(message = "请选择新负责人")
    private Long ownerId;
    @Size(max = 200, message = "转移原因不能超过200个字符")
    private String reason;
}
