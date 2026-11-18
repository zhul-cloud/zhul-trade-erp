package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量处理待确认品牌：设为别名时传 brandId；标为不是品牌时只传 pendingKeys。
 */
@Data
public class BatchPendingBrandRequest {
    @NotEmpty(message = "请选择待确认品牌")
    @Size(max = 200, message = "一次最多处理 200 个写法")
    private List<String> pendingKeys;
    private Long brandId;
}
