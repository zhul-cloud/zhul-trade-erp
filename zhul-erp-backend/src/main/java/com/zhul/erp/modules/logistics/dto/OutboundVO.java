package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import com.zhul.erp.modules.system.doclog.DocRefVO;

/** 销售出库单 */
@Data
public class OutboundVO {
    private Long id;
    private String obNo;
    private Long soId;
    private String soNo;
    private Long customerId;
    private String customerName;
    private String ownerName;
    private Long forwarderId;
    private String forwarderName;
    /** 货代为上门取件时「上门取件 · 承运商 · 账号」，否则为空 */
    private String pickupLabel;
    private Integer source;
    private String sourceName;
    private Integer status;
    private String statusName;
    private String note;
    private String withdrawReason;
    private Integer totalQuantity;
    private List<Item> items;
    /** 合并打包组（openspec outbound-merge-pack）：单独打包为空；合并时箱子、箱数与计费重都是整组的 */
    private Long packId;
    /** 同组的其他销售出库单 */
    private List<PackMate> packMates;
    private List<Box> boxes;
    private Integer boxCount;
    /** 计费重合计（国内体积系数 5000），两位小数 */
    private BigDecimal chargeableWeight;
    private Courier courier;
    private Long logisticsId;
    private String shNo;
    private String packedByName;
    private LocalDateTime packedAt;
    /** 打包发货的照片（详情返回） */
    private List<AttachmentVO> packingAttachments;
    /** 还没有营销实拍的型号（详情返回，打包页提醒，不拦截） */
    private List<String> missingMarketing;
    /** 详情页（change warehouse-logistics-docs）：单据附件（与打包照片分开）、关联单据、操作日志 */
    private List<AttachmentVO> attachments;
    private List<DocRefVO> related;
    private List<DocLogVO> logs;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    @Data
    public static class Item {
        private Long id;
        private Long soItemId;
        /** 关联的商品（候选或已建档）与是否候选商品（spec product/document-product-link「单据上的商品标记」） */
        private Long productId;
        private Boolean productCandidate;
        private String model;
        private String brand;
        /** 品类取订单型号行 */
        private String category;
        private Integer quantity;
        /** 已装箱数量（各箱合计） */
        private Integer packedQty;
    }

    @Data
    public static class Box {
        private Long id;
        private Integer boxNo;
        private Integer length;
        private Integer width;
        private Integer height;
        private BigDecimal grossWeight;
        private BigDecimal netWeight;
        private BigDecimal chargeable;
        private List<BoxItem> items;
    }

    @Data
    public static class BoxItem {
        private Long outboundItemId;
        /** 箱内这一行所属的销售出库单（合并打包的箱子会有多张） */
        private Long outboundId;
        private String obNo;
        private String model;
        private Integer quantity;
    }

    @Data
    public static class PackMate {
        private Long id;
        private String obNo;
        private String soNo;
        private Integer status;
    }

    @Data
    public static class Courier {
        private Long id;
        private String carrier;
        private String trackingNo;
        private LocalDate sentDate;
        private BigDecimal freight;
        private String payerName;
        /** 本销售出库单分到的国内运费 */
        private BigDecimal share;
        /** 共用这票快递的销售出库单数 */
        private Integer outboundCount;
    }
}
