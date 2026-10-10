package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 内容任务列表行；各语言 xxAt 为空表示该语言未写入 */
@Data
public class ContentTaskVO {
    private Long productId;
    private Long taskId;
    private String brandName;
    private String categoryName;
    private String mpn;
    private String productName;
    private Integer status;
    private LocalDateTime zhAt;
    private String zhBy;
    private LocalDateTime enAt;
    private String enBy;
    private LocalDateTime ruAt;
    private String ruBy;
    private LocalDateTime downloadedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
