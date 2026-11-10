package com.zhul.erp.modules.system.notification.dto;

import lombok.Data;

import java.util.List;

/** 保存一个岗位的通知接收人 */
@Data
public class SaveReceiversRequest {
    /** 收货人岗位：按采购订单的采购员（此时忽略 userIds） */
    private boolean poBuyer;
    private List<Long> userIds;
}
