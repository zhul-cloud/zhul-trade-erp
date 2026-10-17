package com.zhul.erp.modules.attachment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 附件标注的一个型号 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTagVO {
    private String brand;
    private String model;
}
