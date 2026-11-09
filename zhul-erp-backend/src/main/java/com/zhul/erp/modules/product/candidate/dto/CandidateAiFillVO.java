package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

/** 批量 AI 解析的进度与结果；全部跳过时不产生任务，taskId 为空 */
@Data
public class CandidateAiFillVO {
    private Long taskId;
    private String mode;
    /** AI 任务状态：1-排队中、2-处理中、3-已完成、4-失败 */
    private Integer status;
    /** 交给 AI 的条数（提交时）、跳过的条数（提交时，已有细分品类或品牌已确认、已审核、看不到的） */
    private Integer total;
    private Integer skipped;
    /** 完成后：品类填了几个；品牌匹配到几个、未识别几个；AI 判断不出或运行期间被人改过而没写入的个数 */
    private Integer filled;
    private Integer matched;
    private Integer unmatched;
    private Integer unchanged;
    private String errorMessage;
}
