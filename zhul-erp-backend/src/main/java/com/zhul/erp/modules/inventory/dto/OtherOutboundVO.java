package com.zhul.erp.modules.inventory.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 其他出库单（列表与详情） */
@Data
public class OtherOutboundVO {
    private Long id;
    private String outboundNo;
    private Integer outboundType;
    private String outboundTypeName;
    private Long warehouseId;
    private String warehouseName;
    private LocalDate outboundDate;
    private String reason;
    private Integer status;
    private String statusName;
    private String currencyCode;
    private BigDecimal amountCny;
    /** 来源（退货去向报废自动生成时为退货入库单） */
    private String sourceDocType;
    private Long sourceDocId;
    private String sourceDocNo;
    /** 自动生成的不能单独作废 */
    private boolean voidable;
    private String voidReason;
    private String remark;
    /** 列表：第一个型号、型号数、总数量 */
    private String firstModel;
    private String firstBrand;
    private String firstCategory;
    private Integer modelCount;
    private Integer totalQuantity;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
    private List<Item> items;
    private List<AttachmentVO> attachments;
    private List<InvMoveVO> moves;
    private List<DocLogVO> logs;

    @Data
    public static class Item {
        private Long id;
        private Long productId;
        private String model;
        private String brand;
        private String category;
        private Long batchId;
        private String batchNo;
        private Integer stockStatus;
        private String stockStatusName;
        private Integer quantity;
        private String currencyCode;
        private BigDecimal unitPriceCny;
        private BigDecimal amountCny;
    }
}
