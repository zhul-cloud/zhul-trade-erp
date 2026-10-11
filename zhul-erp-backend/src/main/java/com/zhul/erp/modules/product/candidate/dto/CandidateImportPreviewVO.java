package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一个商品文件的导入预览 */
@Data
public class CandidateImportPreviewVO {
    private String fileName;
    private String brand;
    /** 识别到的品牌，为空表示品牌未识别 */
    private Long brandId;
    private String brandName;
    private String model;
    private Long categoryId;
    private String categoryName;
    private String productName;
    private String description;
    private String descriptionEn;
    /** NEW-新候选、IN_POOL-已在候选池、IN_PRODUCT-已在商品库、REJECTED-曾被驳回、DUPLICATE-同批重复、INVALID-不能导入 */
    private String result;
    private String resultName;
    /** 补充说明：原因、已有来源次数等 */
    private String note;
    private Long candidateId;
    private Long productId;
    private List<String> errors = new ArrayList<>();
}
