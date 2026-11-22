package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 上传或更换收款码：fileKey 为上传附件接口返回的值 */
@Data
public class SavePayQrRequest {
    @NotBlank(message = "请先上传收款码图片")
    @Size(max = 200, message = "附件标识不正确")
    private String fileKey;
    @Size(max = 200, message = "文件名不能超过200个字符")
    private String fileName;
}
