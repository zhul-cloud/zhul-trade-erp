package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量修改订单型号的跟单信息：进度、采购员或现货 / 期货（每次只改一项） */
@Data
public class OrderItemsRequest {
    @NotEmpty(message = "请选择型号")
    private List<Long> itemIds;
    /** 更新进度：字典 sales_order_status 的 item_code */
    private String progressCode;
    /** 指定采购员；为空表示清除 */
    private Long purchaserId;
    /** 1-现货、2-期货 */
    private Integer stockType;
    @Size(max = 200, message = "说明不能超过 200 字")
    private String note;
}
