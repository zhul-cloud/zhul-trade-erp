package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** PI 转成订单：销售日期不传时取最早的有效水单 / 到账日期，没有时取当天 */
@Data
public class ConvertOrderRequest {
    private LocalDate salesDate;
    /** 先采购后收款：PI 没有水单也没有到账时必须为 true，并填写原因；有水单或到账时忽略 */
    private Boolean payLater;
    @Size(max = 200, message = "先采购后收款的原因不能超过 200 个字")
    private String payLaterReason;
    /** 订单备注：不传时沿用 PI 当前版本的备注，传空串表示清空 */
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
    /** 客户 PO、合同等附件：先上传拿到 ID */
    @Size(max = 10, message = "附件最多 10 个")
    private List<Long> attachmentIds;
}
