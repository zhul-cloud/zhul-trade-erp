package com.zhul.erp.modules.product.content.repository;

import lombok.Data;

import java.time.LocalDateTime;

/** 内容任务列表行：商品左连接本公司任务（营销）与资料任务（平台级） */
@Data
public class ContentTaskRow {
    private Long productId;
    private Long brandId;
    private Long categoryId;
    private String mpnRaw;
    private String mpnDisplay;
    private String productName;
    private Long taskId;
    /** 任务阶段，见 ContentConstants.STAGE_* */
    private Integer status;
    private LocalDateTime enAt;
    private String enBy;
    private LocalDateTime ruAt;
    private String ruBy;
    private LocalDateTime downloadedAt;
    private LocalDateTime factImportedAt;
    private String factImportedBy;
    private LocalDateTime factVerifiedAt;
    private String factVerifiedBy;
    private LocalDateTime factDownloadedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
