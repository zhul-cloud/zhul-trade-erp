package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

/** 待关联清单批量 AI 识别型号的提交结果与进度 */
@Data
public class UnlinkedAiVO {
    private Long taskId;
    /** AI 任务状态：1-排队中、2-处理中、3-已完成、4-失败 */
    private Integer status;
    /** 提交时交给 AI 的组数、跳过的组数（已关联完或不在清单里） */
    private Integer total;
    private Integer skipped;
    /** 完成后：识别为商品型号、费用或非商品、看不出的组数 */
    private Integer models;
    private Integer fees;
    private Integer unknown;
    private String errorMessage;
}
