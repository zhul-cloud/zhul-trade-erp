package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 批量删除结果：deleted 实际删除条数，skipped 已不存在（或已被删除）而跳过的条数 */
@Data
public class SupplierBatchDeleteResultVO {
    private int deleted;
    private int skipped;
}
