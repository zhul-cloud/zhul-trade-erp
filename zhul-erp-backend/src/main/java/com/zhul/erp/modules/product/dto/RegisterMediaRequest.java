package com.zhul.erp.modules.product.dto;

import lombok.Data;

/** 登记外部链接的图片或视频（不下载文件）。 */
@Data
public class RegisterMediaRequest {
    /** 1-图片、2-视频 */
    private Integer mediaType;
    /** 仅允许 http://、https:// 或以单个 / 开头的站内路径 */
    private String fileUrl;
    /** 视频封面地址，规则同 fileUrl */
    private String coverUrl;
    /** 标题；图片时同时作为替代文字 */
    private String title;
    private String source;
    private Integer sortOrder;
    /** 登记后是否设为主图（仅图片有效） */
    private Boolean setMain;
}
