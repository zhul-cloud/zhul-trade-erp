package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveDeptRequest {
    @NotBlank(message = "部门名称不能为空")
    private String name;
    private String allName;
    private Integer pid = 0;
    private Integer leaderId = 0;
    private String phone;
    private String remark;
    private Integer sort = 0;
    private Integer status = 1;
}
