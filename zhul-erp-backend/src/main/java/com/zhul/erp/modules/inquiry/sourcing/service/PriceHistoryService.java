package com.zhul.erp.modules.inquiry.sourcing.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceHistoryGroupVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceHistoryQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceMatchVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 历史询价：已提交的询价记录按「品牌 + 归一化型号」沉淀，供复用、选价与查询 */
public interface PriceHistoryService {

    /** 按品牌与型号匹配有价的历史询价：同品牌的可复用，其他品牌的只作参考 */
    PriceMatchVO match(String brand, String model);

    /** 某个品牌键 + 型号键下的全部已提交记录（含无货），按询价时间倒序 */
    List<SourcingQuoteDO> records(String brandKey, String modelKey);

    PageResult<PriceHistoryGroupVO> query(PriceHistoryQuery query);

    List<PriceRecordVO> toVos(Collection<SourcingQuoteDO> quotes);

    Map<Long, PriceRecordVO> toVoMap(Collection<SourcingQuoteDO> quotes);
}
