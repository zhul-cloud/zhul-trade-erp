package com.zhul.erp.modules.sales.dto;

import lombok.Data;

/** 到账登记列表查询 */
@Data
public class ReceiptDeskQuery {
    /** PI 编号（带不带前缀都可）、客户 */
    private String keyword;
    private Integer receiptStatus;
    /** true 时列出全部已发送 / 已转订单的 PI；默认只列待处理的 */
    private Boolean all;
    private Integer page = 1;
    private Integer pageSize = 20;
}
