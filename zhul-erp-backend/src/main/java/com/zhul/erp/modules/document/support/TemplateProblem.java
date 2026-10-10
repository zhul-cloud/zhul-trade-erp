package com.zhul.erp.modules.document.support;

/** 模版校验问题：位置（单元格如 C4，或「第 3 行」）+ 说明 */
public record TemplateProblem(String location, String message) {

    @Override
    public String toString() {
        return location == null || location.isEmpty() ? message : location + "：" + message;
    }
}
