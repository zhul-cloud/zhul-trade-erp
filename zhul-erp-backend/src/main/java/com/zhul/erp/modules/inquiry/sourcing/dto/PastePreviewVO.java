package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 粘贴报价的识别结果：不落库，前端预览修改后合并进回价草稿 */
@Data
public class PastePreviewVO {
    private List<Row> rows;
    /** 整段通用说明（没有型号也没有价格的行），已套用到各行并写进备注 */
    private String common;
    /** AI 识别才有：本人其他待处理任务里的型号，按任务列出 */
    private List<OtherTask> otherTasks;
    /** AI 识别才有：不在本人任何待处理任务里的型号 */
    private List<String> unknownModels;

    @Data
    public static class Row {
        private String raw;
        /** 未匹配为空 */
        private Long itemId;
        private String model;
        private BigDecimal unitPrice;
        private Boolean taxIncluded;
        private Integer taxRate;
        private Integer itemCondition;
        private Integer leadTime;
        private String note;
        /** MATCHED-已识别、UNMATCHED-未匹配、NO_PRICE-缺单价 */
        private String status;
        /** 型号已报给客户，回价不能再修改 */
        private Boolean locked;
        /** AI 识别才有：SURE-确定、CHECK-请核对 */
        private String confidence;
        /** 含税时的不含税价：含税价 ÷ (1 + 税率%)，两位小数 HALF_UP；不含税时等于单价 */
        private BigDecimal unitPriceExTax;
    }

    @Data
    public static class OtherTask {
        private Long taskId;
        private String taskCode;
        private String brand;
        private String category;
        private Integer modelCount;
    }
}
