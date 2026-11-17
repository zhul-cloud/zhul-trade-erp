package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 保存真实型号后的询盘型号：真实型号、最后修改人与时间、建档状态，以及单据上用的型号与客户型号 */
@Data
public class ActualModelVO {
    private Long inquiryItemId;
    private String actualModel;
    private String actualModelBy;
    private LocalDateTime actualModelAt;
    /** 建档状态：0-未处理、1-已建档、2-候选中、3-待回填真实型号 */
    private Integer archiveStatus;
    private String archiveStatusName;
    private Long productId;
    /** 单据型号：有真实型号取真实型号，否则取确认型号 */
    private String docModel;
    /** 客户型号：与单据型号相同时为空 */
    private String customerModel;
}
