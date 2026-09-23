package com.zhul.erp.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户套餐表。除新增/编辑租户时选择套餐外，套餐自身的增删改由租户套餐管理模块
 * （com.zhul.erp.modules.tenant.service.TenantPackageService）负责。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tenant_package")
public class TenantPackageDO extends BaseEntity {
    /** 套餐名称 */
    private String name;
    /** 可用菜单ID集合（JSON数组） */
    private String menuIds;
    /** 套餐描述 */
    private String remark;
    /** 状态（0-禁用、1-启用） */
    private Integer status;
    /** 软删除时间，NULL表示未删除 */
    private LocalDateTime deletedAt;
}
