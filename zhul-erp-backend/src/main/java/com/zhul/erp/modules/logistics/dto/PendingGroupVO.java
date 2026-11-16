package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.util.List;

/** 待出运的货：按客户、货代分组 */
@Data
public class PendingGroupVO {
    private Long customerId;
    private String customerName;
    /** 客户国家 / 地区、主联系人、新老客户（取第一张出库单所属订单的判定），列表按统一客户列显示（audit UI-096） */
    private String customerCountry;
    private String customerContact;
    private Integer customerType;
    private Long forwarderId;
    private String forwarderName;
    private List<OutboundVO> outbounds;
    private List<DirectRef> directs;
}
