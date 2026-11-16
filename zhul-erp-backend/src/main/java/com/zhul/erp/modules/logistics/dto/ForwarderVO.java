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
    private String contactName;
}
