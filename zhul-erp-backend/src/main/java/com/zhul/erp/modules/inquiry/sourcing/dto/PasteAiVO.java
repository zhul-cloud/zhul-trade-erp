package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 粘贴报价的 AI 识别进度与结果 */
@Data
public class PasteAiVO {
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    private Long aiTaskId;
    /** RUNNING-识别中、DONE-已完成、FAILED-失败（含超时、已取消） */
    private String status;
    private String errorMessage;
    /** 完成后才有 */
    private PastePreviewVO preview;
}
