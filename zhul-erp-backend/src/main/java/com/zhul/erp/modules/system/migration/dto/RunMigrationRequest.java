package com.zhul.erp.modules.system.migration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 执行历史数据迁移：目标租户、迁移包（上传的 ID，或服务器本地的清洗结果目录）、附件缓存目录、是否干跑 */
@Data
public class RunMigrationRequest {
    @NotNull(message = "请选择目标租户")
    private Integer tenantId;
    /** 上传的迁移包 ID（与 bundleDir 二选一） */
    private String bundleId;
    /** 服务器本地的清洗结果目录（本机演练用） */
    private String bundleDir;
    private String attachmentDir;
    private boolean dryRun = true;
}
