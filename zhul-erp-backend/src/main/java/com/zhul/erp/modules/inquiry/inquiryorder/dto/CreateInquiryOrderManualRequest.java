package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动新建询盘单（跳过AI）。两种互斥入口：
 * <ul>
 *   <li>入口A（P04"手动新建"）：客户可选——留空则 customerId 为空；选择客户时直接写入
 *   inquiry_order.customer_id，不创建任何 customer_inquiry 记录（design.md 决策12否决的
 *   是"自动新建一条空壳 customer_inquiry"，这里没有新建任何记录，不受影响）。</li>
 *   <li>入口B（P03"解析失败"态的兜底手动创建）：customerInquiryId 关联一条已经真实存在、
 *   只是AI解析失败了的 customer_inquiry，写入 inquiry_order.customer_inquiry_id，编号沿用
 *   "有父级"规则；此时 customerId 必须为空，两者互斥，同时传入以 customerInquiryId 优先。</li>
 * </ul>
 */
@Data
public class CreateInquiryOrderManualRequest {
    private Long customerId;
    /** 入口B专用，见上方类注释；与 customerId 互斥。 */
    private Long customerInquiryId;
    @NotBlank(message = "品牌不能为空")
    private String brand;
    @NotBlank(message = "品类不能为空")
    private String category;
    @Valid
    @NotEmpty(message = "至少需要一条型号明细")
    private List<ManualItemRequest> items = new ArrayList<>();
}
