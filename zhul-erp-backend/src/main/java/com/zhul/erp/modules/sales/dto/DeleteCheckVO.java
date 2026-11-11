package com.zhul.erp.modules.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 删除销售订单前的检查：档位、挡住删除的单据、删除时一起处理的单据（spec sales/sales-order「删除销售订单」） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCheckVO {
    public static final String DIRECT = "DIRECT";
    public static final String CONFIRM = "CONFIRM";
    public static final String BLOCKED = "BLOCKED";

    /** DIRECT-直接删除、CONFIRM-列出关联单据确认后删除、BLOCKED-不能删除 */
    private String level;
    private List<Doc> blockers;
    private List<Doc> related;

    /**
     * 一张关联单据
     *
     * @param kind  单据类型，如「采购订单」
     * @param no    单号与摘要
     * @param path  可跳转的页面地址，没有详情页时为空
     * @param note  挡住时写怎么处理，一起处理时写删除后的结果
     */
    public record Doc(String kind, String no, String path, String note) {
    }
}
