package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 修改售后单的处理信息：等级（改等级须写原因）、原因与责任、方案、负责人、下一步与下次更新时间 */
@Data
public class AfterSalesPlanRequest {
    private String levelCode;
    @Size(max = 200, message = "等级调整原因不能超过 200 字")
    private String levelNote;
    private String reasonCode;
    private List<String> solutions;
    private Long ownerId;
    @Size(max = 300, message = "下一步行动不能超过 300 字")
    private String nextAction;
    private LocalDateTime nextUpdateAt;
}
