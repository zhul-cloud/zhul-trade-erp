package com.zhul.erp.modules.quotation.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 预览结果：逐页 JPG（data URL）；被更新的请求取代时 superseded=true，不可用时 unavailable=true */
@Data
public class PreviewVO {
    private List<String> pages;
    private LocalDateTime updatedAt;
    private Boolean superseded;
    private Boolean unavailable;
    private String message;
}
