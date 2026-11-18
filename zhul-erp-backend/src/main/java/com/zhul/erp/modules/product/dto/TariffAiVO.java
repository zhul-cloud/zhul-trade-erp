package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI 补全关税参考的进度与结果：完成后返回建议，由人核对后再保存 */
@Data
public class TariffAiVO {
    private Long taskId;
    /** AI 任务状态：1-排队中、2-处理中、3-完成、4-失败 */
    private Integer status;
    private String errorMessage;
    private List<TariffVO.Rate> rates = new ArrayList<>();
}
