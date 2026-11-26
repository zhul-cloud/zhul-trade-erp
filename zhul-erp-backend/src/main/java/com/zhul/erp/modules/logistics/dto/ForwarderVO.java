package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

/** 货代（服务商） */
@Data
public class ForwarderVO {
    private Long id;
    private String name;
    private Integer volumeDivisor;
    /** 这家货代归属当前客户（客户指定货代），发货时排在前面 */
    private Boolean customerOwned;
    /** 归属客户：为空是公司货代。直发货代等按型号所属客户过滤时用 */
    private Long customerId;
    /** 交接方式：1-送到货代、2-上门取件 */
    private Integer handoverMode;
    private String courierCarrier;
    private String courierAccount;
    private String contactName;
}
