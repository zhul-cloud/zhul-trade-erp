package com.zhul.erp.modules.inquiry.sourcing.dto;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 待审核的一个型号：某位兼职采购对它提交的一批回价 */
@Data
public class ReviewItemVO {
    private Long itemId;
    private String model;
    private String originalModel;
    private Integer quantity;
    private String unit;
    private String description;
    private Long quotedBy;
    private String quotedByName;
    private LocalDateTime submittedAt;
    /** 本批待审核的记录（渠道、店铺按「查看货源信息」权限返回） */
    private List<PriceRecordVO> quotes;
    /** 同品牌同型号历史询价中的最低价，供参考；没有时为空 */
    private PriceRecordVO historyLowest;
}
