package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {
    private Long id;
    private Long productId;
    private Integer documentType;
    private String title;
    private String fileUrl;
    private String language;
    private String version;
    private String source;
    private Integer verified;
    private LocalDateTime verifiedAt;
    private Integer sortOrder;
}
