package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/** 合并打包：同一客户、同一货代的几张待打包销售出库单一次装箱（openspec outbound-merge-pack） */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MergePackRequest extends PackRequest {
    @NotNull(message = "请选择要合并打包的销售出库单")
    @Size(min = 2, max = 20, message = "合并打包要选 2 到 20 张销售出库单")
    private List<Long> outboundIds;
}
