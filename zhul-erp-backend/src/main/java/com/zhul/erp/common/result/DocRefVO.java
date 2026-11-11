package com.zhul.erp.common.result;

/**
 * 单据引用：编号 + ID，前端据此显示可点开的来源单号（spec system/navigation「来源单号」）。
 */
public record DocRefVO(Long id, String no) {
}
