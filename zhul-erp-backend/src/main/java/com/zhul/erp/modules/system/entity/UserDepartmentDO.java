package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户附属部门（主部门在 user_basic.dept_id） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_department")
public class UserDepartmentDO extends BaseEntity {
    private Integer userId;
    private String deptCode;
}
