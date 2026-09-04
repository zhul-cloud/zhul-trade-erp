package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

@Data
public class InquiryOrderItemVO {
    private Long id;
    private String itemCode;
    private Long inquiryOrderId;
    private String brand;
    private String category;
    private String originalModel;
    private String confirmedModel;
    private Integer confidence;
    private String correctionNote;
    private String description;
    private Integer quantity;
    private String unit;
    private String deliveryRequirement;
    private String remark;
}
