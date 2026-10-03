package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 按推荐 / 按规则分配 */
@Data
public class TaskIdsRequest {
    @NotEmpty(message = "请选择任务")
    @Size(max = 200, message = "一次最多 200 个任务")
    private List<Long> taskIds;
}
