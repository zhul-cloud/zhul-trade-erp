package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_type")
public class DictTypeDO extends BaseEntity {
    private Integer tenantId;
    private String dictType;
    private String dictName;
    private Integer isBuiltin;
    private Integer status;
    private String remark;
    private LocalDateTime deletedAt;
}
