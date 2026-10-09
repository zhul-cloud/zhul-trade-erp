package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

import java.time.LocalDate;

/** 预计到货日期的估算结果 */
@Data
public class ArrivalEstimateVO {
    private LocalDate date;
    private Integer days;
    /** 估算依据，如「顺丰 · 上海 → 福州 2 天」 */
    private String basis;
}
