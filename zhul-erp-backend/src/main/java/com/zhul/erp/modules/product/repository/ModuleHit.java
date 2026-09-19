package com.zhul.erp.modules.product.repository;

import lombok.Data;

/** 某个商品某个档案模块已完成的一行命中记录。 */
@Data
public class ModuleHit {
    private Long productId;
    private String moduleKey;
}
