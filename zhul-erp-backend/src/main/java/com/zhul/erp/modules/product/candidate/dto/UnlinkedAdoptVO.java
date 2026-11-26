package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量采纳 AI 建议的结果：成功的组数、关联的明细条数，失败的组与原因 */
@Data
public class UnlinkedAdoptVO {
    private int adopted;
    private int lines;
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private String key;
        private String model;
        private String message;
    }
}
