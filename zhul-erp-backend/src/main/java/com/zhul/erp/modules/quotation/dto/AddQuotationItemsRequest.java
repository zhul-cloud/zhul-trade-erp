package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 草稿报价单追加型号 */
@Data
public class AddQuotationItemsRequest {
    @NotEmpty(message = "请选择型号")
    @Size(max = 300, message = "一张报价单最多 300 个型号")
    private List<Long> itemIds;
}
