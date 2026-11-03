package com.zhul.erp.modules.masterdata.customerprofile.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 补全向导：标记或取消「已经问了，等回复」 */
@Data
public class AskedRequest {
    @NotEmpty(message = "请选择字段")
    private List<String> keys;
    @NotNull(message = "请选择是否已问")
    private Boolean asked;
}
