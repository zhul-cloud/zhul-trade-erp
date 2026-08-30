package com.zhul.erp.modules.system.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeptVO {
    private Integer id;
    private Integer pid;
    private String code;
    private String name;
    private String allName;
    private Integer leaderId;
    private String leaderName;
    private String phone;
    private Integer level;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<DeptVO> children;
}
