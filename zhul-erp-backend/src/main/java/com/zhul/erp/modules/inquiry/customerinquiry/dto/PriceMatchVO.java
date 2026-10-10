package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.List;

/** 按品牌 + 归一化型号匹配到的历史询价 */
@Data
public class PriceMatchVO {
    /** 品牌匹配键（按品牌及别名识别），前端拆分预览按它分组，与生成询价任务的口径一致 */
    private String brandKey;
    /** 同品牌、归一化型号相同的有价记录，按询价时间倒序 */
    private List<PriceRecordVO> sameBrand;
    /** 其他品牌下归一化型号相同的有价记录，仅作参考 */
    private List<PriceRecordVO> otherBrands;
    /** 默认选中的记录：全新原装中最低价，没有全新原装时取最低价 */
    private Long defaultQuoteId;
}
