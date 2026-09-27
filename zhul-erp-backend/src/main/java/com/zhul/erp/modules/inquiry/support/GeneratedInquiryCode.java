package com.zhul.erp.modules.inquiry.support;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 编号生成结果。fallbackUsed=true 表示查询当日最大流水号失败，使用了随机数兜底，
 * 调用方应把这条信息写入被创建记录的备注，提示待人工核实（design.md 决策9）。
 */
@Data
@AllArgsConstructor
public class GeneratedInquiryCode {
    private String code;
    private boolean fallbackUsed;
}
