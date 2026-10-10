package com.zhul.erp.modules.product.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 上传预览与确认写入共用：文件原文 + 预览里的修改与跳过；服务端每次都重新解析，不信任前端的解析结果 */
@Data
public class ContentFileRequest {

    @NotEmpty(message = "请选择文件")
    @Size(max = 50, message = "一次最多上传 50 个文件")
    @Valid
    private List<ContentFile> files;

    @Data
    public static class ContentFile {
        @NotBlank(message = "文件名不能为空")
        private String fileName;
        private String content;
        /** 跳过的章节（ContentSection 名称） */
        private List<String> skip;
        /** 修改过的章节：章节名称 → 新的章节正文 */
        private Map<String, String> edits;
    }
}
