package com.zhul.erp.modules.supply.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 覆盖矩阵：行为品牌，列为二级品类 */
@Data
public class MatrixVO {
    /** cover / stockout / supplier */
    private String mode;
    private int days;
    private List<Axis> brands;
    private List<Axis> categories;
    private List<Cell> cells;

    @Data
    public static class Axis {
        private Long id;
        private String name;
        private int total;
    }

    @Data
    public static class Cell {
        private Long brandId;
        private Long categoryId;
        private int total;
        /** 覆盖状态 1–4 → 数量 */
        private Map<Integer, Integer> coverage;
        /** 最差的一档，用于颜色 */
        private int worst;
        private int stockoutModels;
        private int stockoutTimes;
        private Long projectId;
        private String projectName;
        /** 按供应商：合格家数、要求家数、缺口、热度、线索供应商数、进行中的开发任务（专项带开发目标时） */
        private int qualified;
        private int required;
        private int gap;
        private int heat;
        private int leads;
        private Long devProjectId;
    }
}
