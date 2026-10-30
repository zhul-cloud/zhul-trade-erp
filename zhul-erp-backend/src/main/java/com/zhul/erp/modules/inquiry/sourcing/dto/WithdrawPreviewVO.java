package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 撤回回价前的预览：哪些型号会撤回、哪些已报给客户不能撤回、哪些草稿报价单用了要撤回的价格 */
@Data
public class WithdrawPreviewVO {
    /** 本人提交过回价、可以撤回的型号 */
    private List<String> models;
    /** 已报给客户、不能撤回的型号 */
    private List<Locked> locked;
    /** 用了要撤回的价格的草稿报价单 */
    private List<DraftQuotation> drafts;

    public record Locked(String model, String quotationNo) {
    }

    public record DraftQuotation(String quotationNo, List<String> models) {
    }
}
