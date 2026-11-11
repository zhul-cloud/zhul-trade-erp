package com.zhul.erp.modules.upload.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 完成直传：每个分片的编号与上传后返回的 ETag（本地存储时可以不传） */
@Data
public class CompleteUploadRequest {

    @Valid
    private List<Part> parts;

    @Data
    public static class Part {
        @NotNull(message = "缺少分片编号")
        private Integer n;
        private String etag;
    }
}
