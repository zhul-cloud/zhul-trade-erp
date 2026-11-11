package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

/** 批量修改结果：已修改、跳过（已审核或不存在）的个数 */
@Data
public class BatchEditVO {
    private Integer updated;
    private Integer skipped;
}
