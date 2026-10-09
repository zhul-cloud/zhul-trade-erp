package com.zhul.erp.modules.warehouse.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/** 登记 / 修改发货单的表单：采购单各行与可发数量 */
@Data
public class ShipmentFormVO {
    private Long poId;
    private String poNo;
    private String supplierName;
    private Long shipmentId;
    private String carrier;
    private String trackingNo;
    private LocalDate shipDate;
    private String note;
    private List<AttachmentVO> attachments;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long poItemId;
        private String model;
        private String brand;
        private String category;
        private Integer orderedQty;
        private Integer shippedQty;
        /** 最多可发：未发数量（修改时加上本单原数量） */
        private Integer maxQuantity;
        /** 默认数量：登记时为未发数量，修改时为本单原数量 */
        private Integer quantity;
    }
}
