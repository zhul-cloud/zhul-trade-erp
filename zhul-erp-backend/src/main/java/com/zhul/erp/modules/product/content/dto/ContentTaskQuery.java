package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

/** 内容任务列表查询：按更新时间倒序 */
@Data
public class ContentTaskQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    /** 1-待生成、2-进行中、3-已完成；不传为全部 */
    private Integer status;
    private Long brandId;
    private Long categoryId;
    /** 型号或名称 */
    private String keyword;
}
