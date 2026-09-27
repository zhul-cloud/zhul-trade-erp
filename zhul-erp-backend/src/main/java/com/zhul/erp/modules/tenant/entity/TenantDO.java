package com.zhul.erp.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 租户表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tenant")
public class TenantDO extends BaseEntity {
    /** 租户编码（TN+主键，如TN1000），全局唯一，不可修改 */
    private String code;
    /** 租户名称，平台内唯一 */
    private String name;
    /** 套餐ID，关联 tenant_package.id */
    private Integer packageId;
    /** 联系人姓名 */
    private String contactName;
    /** 联系人手机号，平台内唯一 */
    private String contactPhone;
    /** 到期时间 */
    private LocalDateTime expireTime;
    /** 状态（0-禁用、1-启用） */
    private Integer status;
    /** 备注，平台内部可见，租户侧不可见 */
    private String remark;
}
