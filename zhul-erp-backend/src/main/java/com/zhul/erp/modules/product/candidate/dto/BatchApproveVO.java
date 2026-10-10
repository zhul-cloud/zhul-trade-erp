package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

import java.util.List;

/** 批量通过结果 */
@Data
public class BatchApproveVO {
    private Integer approved;
    private List<Skipped> skipped;

    public record Skipped(Long id, String label, String reason) {
    }
}
