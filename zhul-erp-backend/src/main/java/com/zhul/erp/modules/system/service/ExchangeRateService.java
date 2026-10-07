package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.dto.ExchangeRateLogVO;
import com.zhul.erp.modules.system.dto.ExchangeRateVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 系统汇率：按租户维护外币对人民币汇率，业务单据统一取用 */
public interface ExchangeRateService {

    /** 可维护的外币 */
    List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "JPY", "RUB");

    String BASE_CURRENCY = "CNY";

    List<ExchangeRateVO> list();

    ExchangeRateVO save(String currencyCode, BigDecimal rate);

    List<ExchangeRateLogVO> logs(String currencyCode);

    /** 当前租户某币种的汇率；人民币为 1；没有设置时抛业务异常（提示联系管理员设置） */
    Snapshot require(String currencyCode);

    record Snapshot(String currencyCode, BigDecimal rate, LocalDateTime rateTime) {
    }
}
