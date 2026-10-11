package com.zhul.erp.modules.product.content.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 保存本公司启用的营销平台 */
@Data
public class SavePlatformsRequest {
    @NotNull(message = "请选择平台")
    private List<String> platforms;
}
