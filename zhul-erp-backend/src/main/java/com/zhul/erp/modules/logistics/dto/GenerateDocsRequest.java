package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 生成单证组 */
@Data
public class GenerateDocsRequest {
    /** MERGED-合成一组、PER_ORDER-每张订单各一组 */
    @NotBlank(message = "请选择怎么分组")
    private String mode;
    @Size(max = 200, message = "付款参考不能超过200个字")
    private String paymentRef;
}
