package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 确认时提交的一行 */
@Data
public class ConfirmRowRequest {
    @NotBlank(message = "请填写品牌")
    @Size(max = 64, message = "品牌不能超过 64 字")
    private String brand;
    @Size(max = 32, message = "品类不能超过 32 字")
    private String category;
    @Size(max = 128, message = "原始型号不能超过 128 字")
    private String originalModel;
    @NotBlank(message = "请填写型号")
    @Size(max = 128, message = "型号不能超过 128 字")
    private String confirmedModel;
    private Integer confidence;
    @Size(max = 300, message = "纠正说明不能超过 300 字")
    private String correctionNote;
    @NotNull(message = "请填写数量")
    @Min(value = 1, message = "数量至少为 1")
    private Integer quantity;
    @Size(max = 16, message = "单位不能超过 16 字")
    private String unit;
    @Size(max = 300, message = "描述不能超过 300 字")
    private String description;
    @Size(max = 300, message = "英文描述不能超过 300 字")
    /** 英文描述（给客户看的单据用；系统内显示中文 description） */
    private String descriptionEn;
    private Integer lifecycle;
    @Size(max = 128, message = "替代型号不能超过 128 字")
    private String replacementModel;
    private Integer difficulty;
    @Size(max = 500, message = "询价话术不能超过 500 字")
    private String inquiryScript;
    private List<String> searchKeywords;
    /** 复用的历史询价记录；为空表示需要询价 */
    private Long reuseQuoteId;
}
