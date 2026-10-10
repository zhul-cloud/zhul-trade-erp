package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 挑选型号报价：某客户的询盘与型号 */
@Data
public class PickInquiryQuery {
    @NotNull(message = "请先选择客户")
    private Long customerId;
    /** 型号、询盘编号 */
    private String keyword;
    private Boolean readyOnly;
    /** 只看未报过价的型号 */
    private Boolean unquotedOnly;
    private Integer page = 1;
    private Integer pageSize = 10;
}
