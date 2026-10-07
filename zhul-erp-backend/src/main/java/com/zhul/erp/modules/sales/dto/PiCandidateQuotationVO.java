package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 可开 PI 的报价单及其型号（标出已在其他 PI 中、已成交的行） */
@Data
public class PiCandidateQuotationVO {
    private Long quotationId;
    private String quotationNo;
    private Integer status;
    private String statusName;
    private String currencyCode;
    private BigDecimal totalAmount;
    private LocalDateTime sentAt;
    private List<Line> items;
    private List<PiFeeVO> fees;

    @Data
    public static class Line {
        private Long quotationItemId;
        private Integer lineNo;
        private String model;
        private String brand;
        private String category;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private Boolean won;
        /** 已在另一张未作废的 PI 中时的编号 */
        private String inPiNo;
    }
}
