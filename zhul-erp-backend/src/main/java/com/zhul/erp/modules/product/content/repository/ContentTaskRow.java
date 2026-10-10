package com.zhul.erp.modules.product.content.repository;

import lombok.Data;

import java.time.LocalDateTime;

/** 内容任务列表行：商品左连接本公司任务，没有任务的视为待生成 */
@Data
public class ContentTaskRow {
    private Long productId;
    private Long brandId;
    private Long categoryId;
    private String mpnRaw;
    private String mpnDisplay;
    private String productName;
    private Long taskId;
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
