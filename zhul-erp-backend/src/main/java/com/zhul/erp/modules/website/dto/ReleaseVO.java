package com.zhul.erp.modules.website.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 发布单：列表只填头，详情带商品和步骤 */
@Data
public class ReleaseVO {
    private Long id;
    private Long siteId;
    private String releaseNo;
    /** 1-排队中、2-执行中、3-已完成、4-失败、5-部署失败 */
    private Integer status;
    private String statusName;
    private Integer itemCount;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String commitSha;
    private String failedStep;
    private String error;
    private JsonNode warnings;
    private List<Item> items = new ArrayList<>();
    private List<Step> steps = new ArrayList<>();

    public record Item(Long websiteProductId, Long listingTaskId, String brand, String model, JsonNode liveUrls,
                       JsonNode checkResult) {
    }

    public record Step(String step, Integer attempt, Integer status, LocalDateTime startedAt, LocalDateTime endedAt,
                       String message, String output) {
    }
}
