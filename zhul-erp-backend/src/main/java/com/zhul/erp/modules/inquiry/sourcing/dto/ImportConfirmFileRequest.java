package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 确认入库的一个文件 */
@Data
public class ImportConfirmFileRequest {
    @NotNull(message = "缺少任务")
    private Long taskId;
    private String fileKey;
    private String fileName;
    @Valid
    @NotEmpty(message = "没有可入库的行")
    private List<ImportConfirmRowRequest> rows;
}
