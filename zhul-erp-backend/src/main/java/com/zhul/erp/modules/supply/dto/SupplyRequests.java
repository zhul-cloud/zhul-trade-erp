package com.zhul.erp.modules.supply.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 供应链接口的请求体 */
public final class SupplyRequests {

    private SupplyRequests() {
    }

    /** 人工录入信号：搜索关键词（type=5）或同行调研（type=6） */
    @Data
    public static class ManualSignal {
        @NotNull @Min(5) @Max(6)
        private Integer type;
        /** 搜索关键词的平台；同行调研的同行名称 */
        @NotBlank @Size(max = 32)
        private String platform;
        /** 关键词或调研内容 */
        @NotBlank @Size(max = 500)
        private String content;
        @Min(1) @Max(1000000)
        private Integer count;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        @Size(max = 500)
        private String link;
        private Long brandId;
        private Long categoryId;
        @Size(max = 128)
        private String model;
    }

    /** 归类：归到已有需求对象（demandId），或按品牌 + 型号 / 品牌 + 品类新建 */
    @Data
    public static class Classify {
        @NotEmpty @Size(max = 500)
        private List<Long> signalIds;
        private Long demandId;
        private Long brandId;
        private Long categoryId;
        @Size(max = 128)
        private String model;
    }

    @Data
    public static class Ignore {
        @NotEmpty @Size(max = 500)
        private List<Long> signalIds;
        @Size(max = 200)
        private String reason;
    }

    @Data
    public static class Merge {
        @NotNull
        private Long keepId;
        @NotNull
        private Long removeId;
    }

    @Data
    public static class ChangePriority {
        @NotNull @Min(1) @Max(3)
        private Integer priority;
        @NotBlank(message = "请填写理由") @Size(max = 200)
        private String reason;
    }

    @Data
    public static class Reason {
        @NotBlank(message = "请填写原因") @Size(max = 200)
        private String reason;
    }

    @Data
    public static class NextStep {
        private Long ownerId;
        @Size(max = 200)
        private String nextAction;
        private LocalDate nextActionDate;
    }

    @Data
    public static class Defer {
        @NotEmpty @Size(max = 500)
        private List<Long> demandIds;
        @NotNull
        private LocalDate date;
    }

    /** 确认或修改覆盖状态 */
    @Data
    public static class Coverage {
        @NotNull @Min(1) @Max(4)
        private Integer status;
        @Size(max = 200)
        private String note;
    }

    @Data
    public static class Channels {
        private Long primarySupplierId;
        @Size(max = 5)
        private List<Long> backupSupplierIds;
    }

    /** 记一次处理 */
    @Data
    public static class Record {
        @NotEmpty(message = "至少选一个型号") @Size(max = 500)
        private List<Long> demandIds;
        @NotBlank(message = "请填写做了什么") @Size(max = 500)
        private String content;
        @Min(1) @Max(4)
        private Integer coverage;
        @Size(max = 200)
        private String nextAction;
        private LocalDate nextActionDate;
        private Long gapProjectId;
        @Size(max = 64)
        private String idempotencyKey;
    }

    /** 建专项 */
    @Data
    public static class Project {
        @NotNull
        private Long brandId;
        private Long categoryId;
        @NotBlank @Size(max = 50)
        private String name;
        @NotBlank(message = "请填写目标") @Size(max = 200)
        private String goal;
        @NotNull(message = "请选择负责人")
        private Long ownerId;
        @NotNull(message = "请选择截止日期")
        private LocalDate dueDate;
        /** 型号；带开发供应商目标时可以为空 */
        @Size(max = 500)
        private List<Long> demandIds;
        /** 开发供应商目标家数，为空表示只管型号 */
        @Min(value = 1, message = "目标最少 1 家") @Max(value = 10, message = "目标最多 10 家")
        private Integer supplierTarget;
    }

    /** 加上、修改或去掉开发供应商目标（target 为空表示去掉） */
    @Data
    public static class Target {
        @Min(value = 1, message = "目标最少 1 家") @Max(value = 10, message = "目标最多 10 家")
        private Integer target;
    }

    @Data
    public static class ProjectItems {
        @Size(max = 500)
        private List<Long> add;
        @Size(max = 500)
        private List<Long> remove;
    }

    @Data
    public static class CloseProject {
        /** true 为已完成，false 为取消 */
        @NotNull
        private Boolean done;
        @NotBlank(message = "请填写说明") @Size(max = 300)
        private String note;
    }

    // ---------------------------------------------------------------- 第二期：供应商开发

    /** 加入主动拓展 */
    @Data
    public static class Expansion {
        @NotNull(message = "请选择品牌")
        private Long brandId;
        @NotNull(message = "请选择细分品类")
        private Long categoryId;
        @NotNull(message = "请选择热度") @Min(1) @Max(3)
        private Integer heat;
        @NotBlank(message = "请填写理由") @Size(max = 200)
        private String reason;
        private Long ownerId;
    }

    /** 修改主动拓展 */
    @Data
    public static class ExpansionUpdate {
        @NotNull(message = "请选择热度") @Min(1) @Max(3)
        private Integer heat;
        @NotBlank(message = "请填写理由") @Size(max = 200)
        private String reason;
        private Long ownerId;
    }

    /** 加候选：选供应商库里的供应商（supplierId），或只填名称 */
    @Data
    public static class Candidate {
        private Long supplierId;
        @Size(max = 100)
        private String name;
        @Size(max = 50)
        private String contactName;
        @Size(max = 100)
        private String contactInfo;
        @Min(1) @Max(6)
        private Integer source;
        @Size(max = 300)
        private String note;
        private Long ownerId;
        @Size(max = 200)
        private String nextAction;
        private LocalDate nextActionDate;
    }

    @Data
    public static class Link {
        @NotNull(message = "请选择供应商")
        private Long supplierId;
    }

    /** 候选记一次处理：同一开发任务下的一个或多个候选 */
    @Data
    public static class CandidateRecord {
        @NotEmpty(message = "至少选一个候选") @Size(max = 100)
        private List<Long> candidateIds;
        @NotBlank(message = "请填写做了什么") @Size(max = 500)
        private String content;
        /** 新阶段：1 待联系、2 已联系、3 已报价、5 已放弃；为空表示不改 */
        @Min(1) @Max(5)
        private Integer stage;
        @Size(max = 200)
        private String abandonReason;
        @Size(max = 200)
        private String nextAction;
        private LocalDate nextActionDate;
        private Long ownerId;
        @Size(max = 64)
        private String idempotencyKey;
    }
}
