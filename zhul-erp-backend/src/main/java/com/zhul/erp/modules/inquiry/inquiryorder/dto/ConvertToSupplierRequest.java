package com.zhul.erp.modules.inquiry.inquiryorder.dto;

import lombok.Data;

/**
 * "转为正式供应商"：名称固定预填自 channelName。若该名称已存在同名供应商，
 * 接口会抛出携带已有供应商ID的错误提示（PRD 7节"该名称已存在，是否关联已有供应商？"），
 * 前端据此二选一重新提交：force=true 强制新建同名供应商，或 linkExistingSupplierId
 * 直接关联该已有供应商，不新建。
 */
@Data
public class ConvertToSupplierRequest {
    private boolean force = false;
    private Long linkExistingSupplierId;
}
