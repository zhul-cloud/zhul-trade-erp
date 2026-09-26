package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CustomerBatchDeleteRequest {
    @NotEmpty(message = "请选择要删除的客户")
    @Size(max = 200, message = "单次最多删除200个客户")
    private List<Long> ids;
}
