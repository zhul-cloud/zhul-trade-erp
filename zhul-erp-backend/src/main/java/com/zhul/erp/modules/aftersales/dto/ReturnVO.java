package com.zhul.erp.modules.aftersales.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 销售退货单 TH（列表与详情） */
@Data
public class ReturnVO {
    private Long id;
    private String returnNo;
    private Long afterSalesId;
    private String asNo;
    private Long soId;
    private String soNo;
    private String currencyCode;
    private Long customerId;
    private String customerName;
    /** 客户国家/地区、新老客户、主联系人（列表客户列三行显示） */
    private String customerCountry;
    private Integer customerType;
    private String customerContact;
    private Integer handling;
    private String handlingName;
    private Boolean refundFirst;
    /** 负责人：售后单负责人 */
    private Long ownerId;
    private String ownerName;
    private String carrier;
    private String trackingNo;
    private String remark;
    private Integer status;
    private String statusName;
    private LocalDateTime confirmedAt;
    private String confirmedByName;
    private String voidReason;
    private String itemSummary;
    private Integer totalQty;
    /** 入库后：实收、合格合计 */
    private Integer receivedQty;
    private Integer qualifiedQty;
    private LocalDate receivedDate;
    private List<String> brands;
    private List<String> categories;
    private List<Item> items;
    /** 对应的退货入库单（确认后才有） */
    private DocBrief receipt;
    /** 由它生成的退款申请 */
    private List<RefundBrief> refunds;
    private List<AttachmentVO> attachments;
    private List<DocLogVO> logs;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Item {
        private Long id;
        private Long soItemId;
        private String model;
        private String brand;
        private String category;
        private Integer quantity;
        /** 成交单价（订单币种） */
        private BigDecimal unitPrice;
        private Integer receivedQty;
        private Integer qualifiedQty;
    }

    @Data
    public static class DocBrief {
        private Long id;
        private String no;
        private Integer status;
        private String statusName;
    }

    @Data
    public static class RefundBrief {
        private Long id;
        private String requestNo;
        private String currencyCode;
        private BigDecimal amount;
        private Integer status;
        private String statusName;
    }
}
