package com.zhul.erp.modules.video.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 任务里图片、视频、封面的预览或下载链接 */
@Data
public class LinksRequest {
    @NotEmpty
    private List<Long> attachmentIds;
    private boolean download;
}
