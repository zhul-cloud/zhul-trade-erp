package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一个文件的解析预览 */
@Data
public class ContentPreviewVO {
    private String fileName;
    private String brand;
    private String model;
    private String lang;
    private Long productId;
    /** 匹配到的商品：品牌 · 型号 */
    private String productLabel;
    /** 文件级错误：有则不能确认 */
    private List<String> errors = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
    /** 没有文件级错误，且未跳过的块都没有错误 */
    private boolean confirmable;
    private List<BlockVO> blocks = new ArrayList<>();

    @Data
    public static class BlockVO {
        private String section;
        private String label;
        /** shared-共享商品库、company-本公司 */
        private String target;
        private String raw;
        private String text;
        private int count;
        private List<String> columns = new ArrayList<>();
        private List<List<String>> rows = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        /** 写入方式说明，如「替换未核实」「Rated voltage 已核实，保留原值」 */
        private List<String> hints = new ArrayList<>();
        private boolean skipped;
        private boolean edited;
    }
}
