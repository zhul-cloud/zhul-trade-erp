package com.zhul.erp.modules.product.repository;

import lombok.Data;

/** 按某个维度（品牌/品类/系列 ID）分组统计的结果行。 */
@Data
public class IdCount {
    private Long id;
    private Long cnt;
}
