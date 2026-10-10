package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

/** 型号明细 */
@Data
public class InquiryItemVO {
    private Long id;
    private Integer lineNo;
    private String brand;
    /** 品牌匹配键（按品牌及别名识别），同品牌的不同写法相同 */
    private String brandKey;
    private String category;
    private String originalModel;
    private String confirmedModel;
    private Integer confidence;
    private String correctionNote;
    private Integer quantity;
    private String unit;
    private String description;
    /** 英文描述（给客户看的单据用；系统内显示中文 description） */
    private String descriptionEn;
    private Integer lifecycle;
    private String replacementModel;
    /** 采购回填的真实型号 */
    private String actualModel;
    /** 建档状态：0-未处理、1-已建档、2-候选中、3-待回填真实型号 */
    private Integer archiveStatus;
    private String archiveStatusName;
    /** 已建档时关联的商品 */
    private Long productId;
    private Integer difficulty;
    private Integer priceSource;
    private Integer quoteStatus;
    private Long sourcingTaskId;
    private String taskCode;
    private PriceRecordVO selectedQuote;
    /** 无货时的说明 */
    private String noStockNote;
    /** 已报给客户：出现在已发送（及之后成交 / 未成交）的报价单中 */
    private Boolean quoted;
}
