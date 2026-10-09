package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/** 直接收货可选的采购单 */
@Data
public class ReceivableOrderVO {
    private Long poId;
    private String poNo;
    private String supplierName;
    private String purchaserName;
    private LocalDate orderDate;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long poItemId;
        private String model;
        private String brand;
        private String category;
        private Integer orderedQty;
        private Integer unshippedQty;
    }
}
