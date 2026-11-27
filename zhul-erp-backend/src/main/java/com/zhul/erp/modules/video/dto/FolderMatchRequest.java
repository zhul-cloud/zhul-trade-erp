package com.zhul.erp.modules.video.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量上传预览：子文件夹名称 */
@Data
public class FolderMatchRequest {
    @NotEmpty(message = "文件夹里没有子文件夹")
    @Size(max = 200, message = "一次最多 200 个子文件夹")
    private List<String> folders;
}
