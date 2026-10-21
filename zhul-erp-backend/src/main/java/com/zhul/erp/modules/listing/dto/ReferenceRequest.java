package com.zhul.erp.modules.listing.dto;

import lombok.Data;

/** 选择参考素材的型号；型号为空表示取消参考素材 */
@Data
public class ReferenceRequest {
    private String brand;
    private String model;
}
