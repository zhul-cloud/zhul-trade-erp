package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FaqVO {
    private Long id;
    private Long productId;
    private String question;
    private String answer;
    /** 1-品类通用模板生成、2-人工撰写或已人工审核确认、3-AI 辅助生成待审核（租户账号读不到） */
    private Integer source;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private Integer sortOrder;
}
