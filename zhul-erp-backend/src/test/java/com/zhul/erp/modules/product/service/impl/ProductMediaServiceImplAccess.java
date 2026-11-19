package com.zhul.erp.modules.product.service.impl;

/** 测试用：访问包内的文件名取标题规则 */
public final class ProductMediaServiceImplAccess {

    private ProductMediaServiceImplAccess() {
    }

    public static String title(String fileName) {
        return ProductMediaServiceImpl.titleFromFileName(fileName);
    }
}
