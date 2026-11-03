package com.zhul.erp.modules.masterdata.customerbackground.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 采纳或忽略字段建议：建议在报告里的序号（从 0 开始） */
@Data
public class SuggestionIndexesRequest {
    @NotEmpty(message = "请选择要处理的建议")
    private List<Integer> indexes;
}
