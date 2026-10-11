package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 导入确认结果：逐个文件独立写入 */
@Data
public class CandidateImportResultVO {
    private int created;
    private int appended;
    private int skipped;
    private int failed;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String fileName;
        private String model;
        /** CREATED-新建、APPENDED-追加来源、SKIPPED-跳过、FAILED-失败 */
        private String outcome;
        private String message;
    }
}
