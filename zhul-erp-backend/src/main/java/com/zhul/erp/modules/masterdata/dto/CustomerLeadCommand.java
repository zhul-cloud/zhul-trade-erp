package com.zhul.erp.modules.masterdata.dto;

/**
 * 商机登记时创建客户的入参（服务内部调用，不直接暴露为接口）：客户名称可为空，联系人名称与国家必填，
 * 负责人为登记人。
 */
public record CustomerLeadCommand(
        String name,
        String contactName,
        String country,
        int sourceChannel,
        String contactEmail,
        String whatsapp,
        String contactPhone,
        String website) {
}
