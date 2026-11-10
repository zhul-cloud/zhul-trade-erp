package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量操作结果：成功的客户数、单据数，以及跳过或失败的客户和原因 */
@Data
public class BatchResultVO {
    private Integer success;
    private Integer items;
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private Long customerId;
        private String customerName;
        private String reason;
    }
}
