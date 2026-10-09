package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

/** 采购需求列表查询（按订单、按型号两个视图共用） */
@Data
public class RequirementPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 订单编号、型号、客户 */
    private String keyword;
    private Long purchaserId;
    /** 只看我负责的（采购员为当前用户） */
    private Boolean mine;
    /** 向谁买：supplier-老供应商、shop-线上店铺、none-还没定 */
    private String sourceType;
    /** 建议供应商 */
    private Long supplierId;
    /** open-还没全部下单（默认，含草稿中）、need-需要生成采购单（还有可下单数量）、all-全部 */
    private String view;
    /** 1-现货、2-期货 */
    private Integer stockType;
}
