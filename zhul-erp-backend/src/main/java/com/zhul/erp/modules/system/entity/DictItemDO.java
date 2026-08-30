package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_item")
public class DictItemDO extends BaseEntity {
    private Integer tenantId;
    private Integer dictTypeId;
    private String dictType;
    private String itemCode;
    private String itemName;
    private String itemValue;
    private String cssClass;
    private String listClass;
    private Integer sortOrder;
    private Integer isDefault;
    private Integer status;
    private String remark;
    private LocalDateTime deletedAt;
}
