package com.zhul.erp.modules.product.candidate.dto;

import lombok.Data;

/** 商品候选列表查询 */
@Data
public class CandidatePageQuery {
    private Integer page;
    private Integer pageSize;
    /** 1-待审核、2-已建档、3-已并入、4-已驳回；不传为待审核 */
    private Integer status;
    /** 品牌、型号、名称 */
    private String keyword;
    /** 可信度 1-3 */
    private Integer level;
    /** 品牌：1-已确认（品牌库里有）、2-待确认；不传为全部 */
    private Integer brandState;
    /** 品类：1-已选、2-未选；不传为全部 */
    private Integer categoryState;
}
