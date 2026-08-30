package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("position")
public class PositionDO extends BaseEntity {
    private Integer tenantId;
    private String code;
    private String name;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime deletedAt;
}
