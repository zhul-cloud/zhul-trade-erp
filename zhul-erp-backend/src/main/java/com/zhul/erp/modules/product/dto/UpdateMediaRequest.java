package com.zhul.erp.modules.product.dto;

import lombok.Data;

/** 修改标题、封面、来源和排序；文件地址和媒体类型创建后不可改。 */
@Data
public class UpdateMediaRequest {
    private String title;
    private String coverUrl;
    private String source;
    private Integer sortOrder;
}
