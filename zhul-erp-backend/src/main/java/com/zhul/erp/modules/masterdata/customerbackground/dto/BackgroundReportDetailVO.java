package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/** 背调报告详情：核心结论、正文、字段建议、用量 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BackgroundReportDetailVO extends BackgroundReportVO {
    private String reportMarkdown;
    /** 正文渲染成的 HTML（原始 HTML 已转义、链接已清理），页面直接展示 */
    private String reportHtml;
    private List<BackgroundSuggestion> suggestions;
    /** 用量：为空表示无记录 */
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer webSearches;
    private Integer durationSeconds;
    /** 上传的 PDF 原件名，没有原件为空 */
    private String uploadFileName;
    /** 当前用户能否删除 */
    private Boolean canDelete;
}
