package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 发货通知的表单：订单各型号的可出库数量 */
@Data
public class NoticeFormVO {
    private Long soId;
    private String soNo;
    private String customerName;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long soItemId;
        private String model;
        private String brand;
        private Integer quantity;
        /** 有采购需求（系统外采购为 false） */
        private Boolean tracked;
        private Integer received;
        /** 已在其他出库单上（未撤回） */
        private Integer occupied;
        /** 可出库数量（修改时含本单原数量） */
        private Integer available;
        /** 本单原数量（修改时） */
        private Integer current;
        private Integer inTransit;
        private Integer pendingShip;
        private LocalDate earliestArrival;
    }
}
