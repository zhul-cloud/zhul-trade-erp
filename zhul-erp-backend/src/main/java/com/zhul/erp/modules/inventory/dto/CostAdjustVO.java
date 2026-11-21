package com.zhul.erp.modules.inventory.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 成本调整单（spec finance/inventory-cost「成本调整页」）；金额为人民币，带正负号 */
@Data
public class CostAdjustVO {
    private Long id;
    private String adjustNo;
    private LocalDate adjustDate;
    private String periodMonth;
    private Integer reason;
    private String reasonName;
    private String sourceDocType;
    private Long sourceDocId;
    private String sourceDocNo;
    private Integer batchCount;
    private String firstModel;
    private String currencyCode;
    private BigDecimal stockAmount;
    private BigDecimal cogsAmount;
    private BigDecimal otherAmount;
    private BigDecimal totalAmount;
    private String remark;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
    /** 详情：每个批次一行 */
    private List<Batch> batches;
    private List<AttachmentVO> attachments;
    private List<DocLogVO> logs;

    @Data
    public static class Batch {
        private Long batchId;
        private String batchNo;
        /** 批次来源入库单（采购入库单 ID），点批次号打开 */
        private Long receiptId;
        private Long productId;
        private String model;
        private String brand;
        private String category;
        private BigDecimal oldUnitCost;
        private BigDecimal newUnitCost;
        private BigDecimal diffGoods;
        private BigDecimal diffInvoice;
        private BigDecimal diffFee;
        private BigDecimal diffClaim;
        private Integer quantity;
        private BigDecimal amountCny;
        private List<Part> parts;
    }

    /** 批次的一部分：在库（仓库）、已出运（出运单、订单）、其他出库（出库单据） */
    @Data
    public static class Part {
        private Long id;
        private Integer part;
        private String partName;
        private Long warehouseId;
        private String warehouseName;
        private String docType;
        private Long docId;
        private String docNo;
        private Long soItemId;
        private Long soId;
        private String soNo;
        private Integer quantity;
        private BigDecimal amountGoods;
        private BigDecimal amountInvoice;
        private BigDecimal amountFee;
        private BigDecimal amountClaim;
        private BigDecimal amountCny;
    }
}
