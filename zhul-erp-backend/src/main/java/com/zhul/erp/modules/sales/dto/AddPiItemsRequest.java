package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/** 编辑中的 PI 追加型号 */
@Data
public class AddPiItemsRequest {
    @NotEmpty(message = "请选择型号")
    @Valid
    private List<PiLineRequest> items;
}
