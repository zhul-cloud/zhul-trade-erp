package com.zhul.erp.modules.video.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 视频任务详情：素材、脚本、生成结果、历史 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VideoTaskDetailVO extends VideoTaskVO {
    private List<Material> materials = new ArrayList<>();
    private VideoScript script;
    /** 0-无、1-AI、2-手工修改 */
    private Integer scriptSource;
    /** 0-未生成、1-生成中、2-已生成、3-生成失败 */
    private Integer scriptStatus;
    /** 脚本生成失败的原因，或脚本哪里不符合加入队列的要求 */
    private String scriptError;
    /** 每个镜头的估算秒数，与 script.shots 一一对应 */
    private List<Double> shotSeconds = new ArrayList<>();
    private Double totalSeconds;
    /** 资料还没核对（资料待补、资料待核） */
    private Boolean factsUnverified;
    /** 最近一次生成的结果（竖屏、横屏） */
    private List<Output> outputs = new ArrayList<>();
    /** 素材库里正在用的已通过视频（重新生成期间是旧版本） */
    private List<Output> approvedOutputs = new ArrayList<>();
    private List<Log> logs = new ArrayList<>();

    @Data
    public static class Material {
        private Long attachmentId;
        private String fileName;
        private Long fileSize;
        private Integer sort;
        private Boolean used;
    }

    @Data
    public static class Output {
        private Long id;
        private Integer version;
        private String aspect;
        private Long videoAttachmentId;
        private Long coverAttachmentId;
        private Integer durationSec;
        private Integer status;
        private String statusName;
    }

    @Data
    public static class Log {
        private String action;
        private String actionName;
        private Integer version;
        private String note;
        private String operator;
        private LocalDateTime time;
    }
}
