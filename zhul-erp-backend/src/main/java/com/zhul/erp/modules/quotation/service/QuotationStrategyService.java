package com.zhul.erp.modules.quotation.service;

import com.zhul.erp.modules.quotation.dto.PriceHistoryVO;
import com.zhul.erp.modules.quotation.dto.StrategyTierDTO;

import java.util.List;

/** 报价策略：沿用历史价与「按金额分层毛利」档位（其余策略在前端计算） */
public interface QuotationStrategyService {

    /** 报价单正在查看的版本中各有价行的历史价（每个型号最多一条，成交优先） */
    List<PriceHistoryVO> priceHistory(Long quotationId, Integer versionNo);

    List<StrategyTierDTO> tiers();

    List<StrategyTierDTO> saveTiers(List<StrategyTierDTO> tiers);
}
