package com.zhul.erp.modules.warehouse.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import lombok.Data;
import java.util.List;

/** 采购入库单详情 */
@Data
public class ReceiptVO {
    private ReceiptListVO receipt;
    private List<Item> items;
    private List<DiscrepancyVO> discrepancies;
    private List<AttachmentVO> attachments;
    /** 不良品照片（到货异常） */
    private List<AttachmentVO> defectAttachments;
    private List<AttachmentVO> shipmentAttachments;
    /** 差异都没处理、实拍任务都没开始时可以冲销 */
    private Boolean reversible;

    @Data
    public static class Item {
        private Long id;
        private String model;
        private String brand;
        private String category;
        private Integer shippedQty;
        private Integer receivedQty;
        private Integer qualifiedQty;
        private Integer defectiveQty;
        private String note;
    }
}
