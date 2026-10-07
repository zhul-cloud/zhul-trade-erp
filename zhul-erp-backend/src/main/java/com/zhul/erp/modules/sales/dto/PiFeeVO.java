package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import java.math.BigDecimal;

/** PI 费用行 */
@Data
public class PiFeeVO {
    private String feeName;
    private BigDecimal amount;
    private BigDecimal amountCny;
    private String remark;
}
