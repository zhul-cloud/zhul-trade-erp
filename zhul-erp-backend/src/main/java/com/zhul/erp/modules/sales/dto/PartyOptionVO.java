package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户单证主体选项（开 PI 时改选买方 / 收货人） */
@Data
@EqualsAndHashCode(callSuper = true)
public class PartyOptionVO extends PartyDTO {
    /** 1-收货人、2-通知方、3-发票抬头 */
    private Integer partyType;
    private Boolean isDefault;
    /** 取自客户注册信息（不是单证主体） */
    private Boolean registration;
}
