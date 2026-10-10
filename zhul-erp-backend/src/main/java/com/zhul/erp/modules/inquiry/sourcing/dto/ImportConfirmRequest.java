package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 确认导入询价结果 */
@Data
public class ImportConfirmRequest {
    @Valid
    @NotEmpty(message = "没有可入库的文件")
    private List<ImportConfirmFileRequest> files;
}
