package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 余额账户（客户 + 币种 + 事业部）：可用余额、PI 占用中与最近变动 */
@Data
public class CreditAccountVO {
    private Long customerId;
    private String customerName;
    private String customerCountry;
    private String currencyCode;
    private Integer businessUnitId;
    private String businessUnitName;
    /** 可用余额（原币）与人民币价值 */
    private BigDecimal available;
    private BigDecimal availableCny;
    /** 还没转订单的 PI 上的抵扣（已从可用余额扣除，关闭 PI 后退回） */
    private BigDecimal occupied;
    private List<Occupier> occupiers;
    /** 有过代收转入 */
    private Boolean collection;
    private LocalDateTime lastTime;
    private String lastTypeName;
    private String lastSourceNo;

    @Data
    public static class Occupier {
        private String sourceType;
        private Long sourceId;
        private String sourceNo;
        private BigDecimal amount;
    }
}
