package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 一个附件：带 id 表示保留已有附件（可改类型），不带 id 时 fileKey 为上传接口返回的值 */
@Data
public class SupplierAttachmentRequest {

    private Long id;

    /** 附件类型（1-营业执照、2-开户许可证、3-资质证书、4-合同、5-其他） */
    @NotNull(message = "请选择附件类型")
    @Min(value = 1, message = "附件类型不正确")
    @Max(value = 5, message = "附件类型不正确")
    private Integer category;

    @Size(max = 200, message = "文件名不能超过200个字符")
    private String fileName;

    @Size(max = 200, message = "附件标识不正确")
    private String fileKey;
}
