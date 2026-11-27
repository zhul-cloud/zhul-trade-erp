package com.zhul.erp.modules.website.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/** 站点的导入、比对任务；列表不带结果，详情带结果和差异确认记录 */
@Data
public class SiteJobVO {
    private Long id;
    private Long siteId;
    /** import-导入现有商品、compare-一致性比对 */
    private String jobType;
    private String jobNo;
    /** 1-执行中、2-已完成、3-失败、4-已确认 */
    private Integer status;
    private String statusName;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String createBy;
    private LocalDateTime createTime;
    /** 导入：total、matched、unmatched、siteOnlyFields、missing；比对：same、differences、onlyOnSite、onlyInErp */
    private JsonNode result;
    private JsonNode accepted;
    /** 比对：还没确认接受的差异条数 */
    private Integer pending;
}
