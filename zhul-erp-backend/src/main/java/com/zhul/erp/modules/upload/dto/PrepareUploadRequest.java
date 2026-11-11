package com.zhul.erp.modules.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 准备直传：场景、原文件名与声明大小；existingBytes 为单据上已有附件的合计（用于合计上限的提前提示，保存时仍会再校验） */
@Data
public class PrepareUploadRequest {

    @NotBlank(message = "缺少上传场景")
    private String scene;

    @NotBlank(message = "缺少文件名")
    @Size(max = 255, message = "文件名过长")
    private String fileName;

    @NotNull(message = "缺少文件大小")
    @Positive(message = "文件是空的")
    private Long size;

    @PositiveOrZero(message = "已有附件大小不正确")
    private Long existingBytes;
}
