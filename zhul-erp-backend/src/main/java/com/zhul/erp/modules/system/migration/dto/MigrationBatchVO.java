package com.zhul.erp.modules.system.migration.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

/** 迁移批次：运行方式 1-干跑、2-正式执行；状态 1-运行中、2-成功、3-失败、4-已回滚 */
@Data
public class MigrationBatchVO {
    private Long id;
    private Integer tenantId;
    private String batchNo;
    private String bundleHash;
    private Integer runMode;
    private Integer status;
    private String errorText;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** 阶段 → 数据类别 → 新建 / 跳过 / 失败 */
    private JsonNode stageStats;
    /** 与旧报表的核对结果：summary 汇总、rows 明细；没有核对时为 null */
    private JsonNode verifyResult;
    /** 正在后台回滚 */
    private boolean rollingBack;
}
