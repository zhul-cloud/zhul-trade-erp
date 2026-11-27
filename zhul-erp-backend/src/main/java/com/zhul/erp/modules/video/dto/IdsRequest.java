package com.zhul.erp.modules.video.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class IdsRequest {
    @NotEmpty(message = "请选择任务")
    private List<Long> ids;
}
