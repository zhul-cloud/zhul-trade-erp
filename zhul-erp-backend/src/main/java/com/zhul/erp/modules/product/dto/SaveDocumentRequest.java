package com.zhul.erp.modules.product.dto;

import lombok.Data;

@Data
public class SaveDocumentRequest {
    /** 1-Datasheet、2-Manual、3-Installation Guide、4-User Manual、5-CAD、6-Drawing、7-Brochure、8-Certificate */
    private Integer documentType;
    private String title;
    /** 仅允许 http://、https:// 或以单个 / 开头的站内路径 */
    private String fileUrl;
    /** 缺省 en */
    private String language;
    private String version;
    private String source;
    /** 是否已核实（0/1），仅为标记 */
    private Integer verified;
    private Integer sortOrder;
}
