package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 保存草稿或提交回价 */
@Data
public class SaveQuotesRequest {
    @Valid
    @NotEmpty(message = "没有要保存的内容")
    private List<ItemQuotesRequest> items;
    /** true 为提交回价，false 为保存草稿 */
    private Boolean submit;
}
