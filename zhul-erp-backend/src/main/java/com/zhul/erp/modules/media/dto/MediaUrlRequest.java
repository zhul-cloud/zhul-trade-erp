package com.zhul.erp.modules.media.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量取素材的访问链接：一次最多 50 个 */
@Data
public class MediaUrlRequest {
    @NotEmpty
    @Size(max = 50, message = "一次最多下载 50 个文件")
    @Valid
    private List<MediaRef> refs;
    /** true 下载（只允许可发客户的素材），false 预览 */
    private boolean download;
}
