package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一个文件的解析预览 */
@Data
public class ContentPreviewVO {
    private String fileName;
    /** 第几个上传的文件（从 0 开始）：v2 营销文件拆成多项时，同一文件的项相同 */
    private int fileIndex;
    private String brand;
    private String model;
    private String lang;
    /** 包类型：facts-资料包、marketing-营销包 */
    private String type;
    /** 营销包平台：alibaba、mic、site、social；资料包为空 */
    private String platform;
    /** 规范版本：2 为 v2，空为旧格式 */
    private Integer schema;
    /** v2 营销文件拆出的部分：keywords（关键词库）或平台编码；资料包与旧格式为空 */
    private String part;
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
        /** 块键：旧格式与资料包为章节名，v2 营销文件拆出的项为「部分:章节名」，预览修改、跳过都用它 */
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
