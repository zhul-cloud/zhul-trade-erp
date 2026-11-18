package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量操作结果（批量确认 AI 结果、批量资料已核）：逐个独立处理，某个失败不影响其他 */
@Data
public class ContentBatchResultVO {
    private int done;
    private int partial;
    private int failed;
    /** 随资料包审核通过的技术问答条数（批量确认 AI 结果） */
    private int approvedFaqs;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        /** 批量确认时为 AI 生成记录 ID，批量核对时为商品 ID */
        private Long id;
        private Long productId;
        /** 型号 */
        private String label;
        /** done 已完成、partial 部分写入、failed 未处理 */
        private String outcome;
        /** 结果说明：写入了什么、跳过了哪些章节、没处理的原因 */
        private String message;
        /** 随资料包审核通过的技术问答条数 */
        private int approvedFaqs;
    }

    public void add(Item item) {
        items.add(item);
        approvedFaqs += item.getApprovedFaqs();
        switch (item.getOutcome()) {
            case OUTCOME_DONE -> done++;
            case OUTCOME_PARTIAL -> partial++;
            default -> failed++;
        }
    }

    public static final String OUTCOME_DONE = "done";
    public static final String OUTCOME_PARTIAL = "partial";
    public static final String OUTCOME_FAILED = "failed";
}
