package com.zhul.erp.modules.system.notification.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 一条通知 */
@Data
public class NotificationVO {
    private Long id;
    private String node;
    private String nodeName;
    /** TODO-待办、INFO-知会 */
    private String kind;
    private String title;
    private String content;
    private String bizNo;
    private String link;
    private boolean urgent;
    private boolean read;
    private boolean done;
    private LocalDateTime time;
}
