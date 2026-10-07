package com.zhul.erp.modules.quotation.service;

import com.zhul.erp.modules.quotation.dto.PricingStrategyVO;
import com.zhul.erp.modules.quotation.dto.SavePricingStrategyRequest;

/** 定价策略（SOP V6）：品相毛利率与红线、低值耗材分层、转人工提示 */
public interface PricingStrategyService {

    PricingStrategyVO get();

    PricingStrategyVO save(SavePricingStrategyRequest req);
}
