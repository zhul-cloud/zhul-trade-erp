package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

/** 采购需求列表查询 */
@Data
public class RequirementPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 订单编号、型号、客户 */
    private String keyword;
    private Long purchaserId;
    /** 建议供应商 */
    private Long supplierId;
    /** open-还没全部下单（默认，含草稿中）、need-需要生成采购单（还有可下单数量）、all-全部 */
    private String view;
    /** 1-现货、2-期货 */
    private Integer stockType;
}
