package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("department")
public class DepartmentDO extends BaseEntity {
    private Integer tenantId;
    private Integer pid;
    private String code;
    private String name;
    private String allName;
    private Integer leaderId;
    private Integer level;
    private Integer headcount;
    private String phone;
    private String remark;
    private Integer sort;
    private Integer status;
    private LocalDateTime deletedAt;
}
