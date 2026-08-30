package com.zhul.erp.modules.system.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Integer id;
    private String name;
    private String username;
    private String phone;
    private String email;
    private String nickname;
    private String avatarUrl;
    private Integer deptId;
    private String deptName;
    private Integer positionId;
    private String positionName;
    private String roleCode;
    private String roleName;
    private Integer status;
    private LocalDateTime createTime;
}
