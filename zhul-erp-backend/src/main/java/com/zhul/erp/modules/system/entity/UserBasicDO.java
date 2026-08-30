package com.zhul.erp.modules.system.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_basic")
public class UserBasicDO extends BaseEntity {
    private Integer tenantId;
    private Integer pid;
    private String name;
    private Integer type;
    private String username;
    private String phone;
    private Integer deptId;
    private Integer positionId;
    private String roleCode;
    private String email;
    private String avatarUrl;
    private String nickname;
    private Integer status;
}
