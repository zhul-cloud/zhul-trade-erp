package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogVO {
    private Long id;
    private String operatorName;
    private String ip;
    private String location;
    private String browser;
    private String os;
    private Integer result;
    /** 当前是否在线（实时查询 Redis 会话得出，result=0 时恒为 false） */
    private Boolean online;
    /** 强制下线可勾选：result=1 且 online=true */
    private Boolean checkable;
    /** 用于强制下线的 Token 标识；result=0 或已下线时为 null */
    private String tokenId;
    private LocalDateTime operateTime;
}
