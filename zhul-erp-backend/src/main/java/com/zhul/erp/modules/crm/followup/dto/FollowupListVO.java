package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 跟进列表：每次 20 条，带总数；PI 后另带按币种的未收合计 */
@Data
public class FollowupListVO {
    private Long total;
    private List<FollowupItemVO> records = new ArrayList<>();
    private List<CurrencyAmount> unpaid = new ArrayList<>();

    @Data
    public static class CurrencyAmount {
        private String currencyCode;
        private BigDecimal amount;
    }
}
