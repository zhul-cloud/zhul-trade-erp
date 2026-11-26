package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 货代管理列表的一行 */
@Data
public class ForwarderRowVO {
    private Long id;
    private String code;
    private String name;
    private String shortName;
    /** 归属客户：空为公司货代 */
    private Long customerId;
    /** 交接方式：1-送到货代、2-上门取件 */
    private Integer handoverMode;
    private String courierCarrier;
    private String courierAccount;
    private String customerName;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Integer volumeDivisor;
    private Integer accountCount;
    /** 默认收款账户：开户行 ****后四位，没有时为空 */
    private String defaultAccount;
    /** 0-停用、1-启用 */
    private Integer status;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
