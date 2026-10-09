package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.util.List;

/** 待出运的货：按客户、货代分组 */
@Data
public class PendingGroupVO {
    private Long customerId;
    private String customerName;
    private Long forwarderId;
    private String forwarderName;
    private List<OutboundVO> outbounds;
    private List<DirectRef> directs;
}
