package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 实拍任务添加营销图（先上传附件拿到 ID） */
@Data
public class AddMediaRequest {
    @NotNull
    private Long attachmentId;
}
