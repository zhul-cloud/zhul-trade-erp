package com.zhul.erp.modules.system.migration.dto;

import lombok.Data;

import java.util.Map;

/** 上传的迁移包：ID（内容哈希）、清洗结果版本、旧系统导出时间、图片链接到期时间、各文件的条数 */
@Data
public class MigrationBundleVO {
    private String id;
    private String version;
    private String sourceExportedAt;
    private String linksExpireAt;
    private Map<String, Integer> counts;
}
