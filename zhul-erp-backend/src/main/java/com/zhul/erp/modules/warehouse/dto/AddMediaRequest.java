package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 拍摄任务添加素材（先上传附件拿到 ID） */
@Data
public class AddMediaRequest {
    @NotNull
    private Long attachmentId;
    /** 1-拆箱视频、2-验货视频、3-实物图 */
    @NotNull(message = "请选择素材类型")
    private Integer mediaType;
}
