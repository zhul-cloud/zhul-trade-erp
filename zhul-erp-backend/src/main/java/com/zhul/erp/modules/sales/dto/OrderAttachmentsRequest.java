package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 订单详情里补传附件：先上传拿到附件 ID，再挂到订单上 */
@Data
public class OrderAttachmentsRequest {
    @NotEmpty(message = "请上传附件")
    @Size(max = 10, message = "附件最多 10 个")
    private List<Long> attachmentIds;
}
