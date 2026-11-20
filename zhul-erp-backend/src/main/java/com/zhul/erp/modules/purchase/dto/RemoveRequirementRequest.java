package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 删除采购需求（openspec purchase-requirement-delete）：只删还没下单的数量 */
@Data
public class RemoveRequirementRequest {
    /** stock-仓库有库存、outside-在系统外采购、other-其他 */
    @NotBlank(message = "请选择删除原因")
    @Pattern(regexp = "stock|outside|other", message = "删除原因不正确")
    private String reason;
    /** 说明；选「其他」时必填 */
    @Size(max = 180, message = "说明最多 180 字")
    private String note;
}
