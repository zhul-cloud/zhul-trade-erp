package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

/** 内容任务列表查询：按更新时间倒序 */
@Data
public class ContentTaskQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    /**
     * 页签，不传为全部。资料任务：1-资料待补、2-资料待核、verified-资料已核；
     * 营销任务：unverified-资料未核对、3-营销待生成、4-已完成
     */
    private String tab;
    private Long brandId;
    private Long categoryId;
    /** 型号或名称 */
    private String keyword;
}
