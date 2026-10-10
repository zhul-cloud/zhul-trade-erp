package com.zhul.erp.modules.aitask.translation;

import lombok.Data;

import java.util.List;

/** 生成英文描述的进度与结果 */
@Data
public class TranslationVO {
    private Long taskId;
    /** 1-排队中、2-处理中、3-已完成、4-失败 */
    private Integer status;
    private String errorMessage;
    /** 已完成时的译文（key 为调用方的行标识） */
    private List<Result> results;

    @Data
    public static class Result {
        private String key;
        private String text;
    }
}
