package com.zhul.erp.modules.system.notification.dto;

import lombok.Data;

import java.util.List;

/** 一个岗位的通知接收人 */
@Data
public class NotificationReceiverVO {
    private String post;
    private String postName;
    /** 收货人岗位：按采购订单的采购员 */
    private boolean poBuyer;
    private List<User> users;
    /** 这个岗位接收的通知 */
    private List<String> nodes;

    public record User(Long id, String name) {
    }
}
