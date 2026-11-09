package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 手动调整阶段（spec「手动调整阶段」）：只能在同一判断段内前进或后退 */
@Data
public class AdjustStageRequest {

    @NotBlank(message = "请选择要调整到的阶段")
    private String toStage;

    /** HISTORY-历史数据阶段不对、LAGGING-系统判断滞后、CHANGED-客户情况变化、OTHER-其他（必须补充说明） */
    @NotBlank(message = "请选择调整原因")
    private String reason;

    @Size(max = 200, message = "说明不能超过200个字")
    private String note;
}
