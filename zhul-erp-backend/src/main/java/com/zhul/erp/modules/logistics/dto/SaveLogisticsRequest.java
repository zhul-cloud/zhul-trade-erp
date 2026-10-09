package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新建出运单 / 修改出运单里的货 */
@Data
public class SaveLogisticsRequest {
    /** 新建时必填；修改时忽略 */
    private Long customerId;
    /** 新建时必填；修改时忽略 */
    private Long forwarderId;
    private List<Long> outboundIds;
    private List<Long> directShipmentIds;
    @Size(max = 300, message = "备注不能超过300个字")
    private String note;
}
