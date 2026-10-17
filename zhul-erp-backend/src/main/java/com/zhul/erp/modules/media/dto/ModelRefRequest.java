package com.zhul.erp.modules.media.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 品牌 + 型号 */
@Data
public class ModelRefRequest {
    @Size(max = 100)
    private String brand;
    @Size(max = 150)
    private String model;
}
