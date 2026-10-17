package com.zhul.erp.modules.media.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/** 一个附件标注的型号（只能选单据里的型号）；不提交时沿用已有标注，没有标注的默认为单据的全部型号 */
@Data
public class AttachmentModelsRequest {
    private Long attachmentId;
    @Valid
    private List<ModelRefRequest> models;
}
