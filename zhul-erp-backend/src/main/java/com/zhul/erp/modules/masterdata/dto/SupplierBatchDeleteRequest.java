package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SupplierBatchDeleteRequest {
    @NotEmpty(message = "请选择要删除的供应商")
    @Size(max = 200, message = "单次最多删除200条")
    private List<Long> ids;
}
