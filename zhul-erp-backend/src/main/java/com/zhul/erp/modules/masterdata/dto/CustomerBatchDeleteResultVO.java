package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 批量删除结果：deleted 实际删除数，referenced 因已有询盘记录跳过数，missing 已不存在或无权操作数 */
@Data
public class CustomerBatchDeleteResultVO {
    private int deleted;
    private int referenced;
    private int missing;
}
