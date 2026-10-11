package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 导入候选：预览与确认共用，服务端每次都重新解析 */
@Data
public class CandidateImportRequest {

    @NotEmpty(message = "请选择文件")
    @Size(max = 200, message = "一次最多上传 200 个文件")
    @Valid
    private List<ImportFile> files;

    @Data
    public static class ImportFile {
        @NotBlank(message = "文件名不能为空")
        private String fileName;
        private String content;
    }
}
