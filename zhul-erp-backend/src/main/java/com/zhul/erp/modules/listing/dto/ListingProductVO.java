package com.zhul.erp.modules.listing.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 发品任务按商品：一个商品一行，汇总各平台任务的状态 */
@Data
public class ListingProductVO {
    private Long productId;
    private String brand;
    private String model;
    private String productName;
    private String category;
    /** 商品状态：pending 待发布、updated 内容有更新、replace 待替换素材、published 已发布、cancelled 已取消 */
    private String status;
    private String statusName;
    /** 有待发布的平台没有可用素材 */
    private Boolean needMedia;
    /** 各平台任务，按平台固定顺序 */
    private List<ListingTaskVO> tasks = new ArrayList<>();
    /** 各平台任务中最早创建、最近更新的一条 */
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
