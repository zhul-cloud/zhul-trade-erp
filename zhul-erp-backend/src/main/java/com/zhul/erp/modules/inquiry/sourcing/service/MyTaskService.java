package com.zhul.erp.modules.inquiry.sourcing.service;

import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.QuoteBoardVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PastePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReturnTaskRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.SaveQuotesRequest;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;

import java.util.List;

/** 采购（含兼职采购）处理自己的询价任务；返回内容一律不含客户信息 */
public interface MyTaskService {

    /** done=false 为待处理（询价中），true 为已回价 */
    List<MyTaskVO> myTasks(boolean done);

    /** 按关键词（任务编号、询盘单编号、品牌、品类、型号）搜索本人的任务 */
    List<MyTaskVO> myTasks(boolean done, String keyword);

    MyTaskDetailVO detail(Long taskId);

    /** 型号的历史询价明细：最近 5 次成交与最近 5 条询价记录（不含本任务） */
    com.zhul.erp.modules.inquiry.sourcing.service.impl.ItemPriceHistory.Detail itemHistory(Long taskId, Long itemId);

    /** 兼职工作台：本人待办与回价统计 */
    QuoteBoardVO quoteBoard();

    void saveQuotes(Long taskId, SaveQuotesRequest req);

    /** 粘贴报价：按本任务的型号识别店家回复的文字，只返回预览，不落库 */
    PastePreviewVO pastePreview(Long taskId, String text);

    void returnTask(Long taskId, ReturnTaskRequest req);

    /** 撤回回价前的预览 */
    com.zhul.erp.modules.inquiry.sourcing.dto.WithdrawPreviewVO withdrawPreview(Long taskId);

    /**
     * 撤回已回价任务里本人提交的回价：未报给客户的型号的回价变回草稿（业务员看不到），
     * 任务回到询价中，询盘从可报价回到询价中；已报给客户的型号保持不变
     */
    void withdraw(Long taskId);

    /** 采购回填真实型号：只影响建档，不受「已报给客户」的锁定限制；返回最新建档状态 */
    com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskItemVO saveActualModel(Long taskId, Long itemId, String actualModel);

    /** 本人当前有效分配的任务，否则抛「询价任务不存在」 */
    SourcingTaskDO myTask(Long taskId);

    /** 把一个型号本次的结果写入询价记录（在线录入与导入共用）；submit 时由调用方负责重算进度 */
    void writeQuotes(SourcingTaskDO task, Long quotedBy, List<QuoteDraft> drafts, boolean submit, int entryMode, Long importId);

    /** 提交后重算明细、任务与询盘单进度，并记录提交时间 */
    void afterSubmit(SourcingTaskDO task, Long quotedBy, List<Long> itemIds);

    /** 一条待写入的询价结果；noStock 为真时只记无货说明 */
    record QuoteDraft(Long itemId, boolean noStock, Integer channel, String shopName, Long supplierId, java.math.BigDecimal unitPrice,
                      boolean taxIncluded, Integer taxRate, Integer itemCondition, Integer leadTime, String note, boolean recommended,
                      boolean freeShipping, List<Long> attachmentIds) {

        /** Excel 导入等没有包邮与附件的来源：按包邮、无附件 */
        public QuoteDraft(Long itemId, boolean noStock, Integer channel, String shopName, Long supplierId, java.math.BigDecimal unitPrice,
                          boolean taxIncluded, Integer taxRate, Integer itemCondition, Integer leadTime, String note, boolean recommended) {
            this(itemId, noStock, channel, shopName, supplierId, unitPrice, taxIncluded, taxRate, itemCondition, leadTime, note, recommended,
                    true, List.of());
        }
    }
}
