package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

import java.util.List;

/** 模型下拉列表；source 为 provider（来自服务商/账号）或 defaults（只有默认模型），error 为获取失败原因 */
@Data
public class AiModelListVO {
    private List<Model> models;
    private String source;
    private String error;

    @Data
    public static class Model {
        private String id;
        private boolean defaultModel;
    }
}
