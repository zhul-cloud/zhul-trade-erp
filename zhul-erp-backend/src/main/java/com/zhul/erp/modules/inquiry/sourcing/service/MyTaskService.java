package com.zhul.erp.modules.inquiry.sourcing.service;

import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PartTimeBoardVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PastePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReturnTaskRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.SaveQuotesRequest;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;

import java.util.List;

/** 采购（含兼职采购）处理自己的询价任务；返回内容一律不含客户信息 */
public interface MyTaskService {

    /** done=false 为待处理（询价中），true 为已回价 */
    List<MyTaskVO> myTasks(boolean done);

    MyTaskDetailVO detail(Long taskId);

    /** 兼职工作台：本人待办与回价统计 */
    PartTimeBoardVO partTimeBoard();

    void saveQuotes(Long taskId, SaveQuotesRequest req);

    /** 粘贴报价：按本任务的型号识别店家回复的文字，只返回预览，不落库 */
    PastePreviewVO pastePreview(Long taskId, String text);

    void returnTask(Long taskId, ReturnTaskRequest req);

    /** 采购回填真实型号：只影响建档，不受「已报给客户」的锁定限制；返回最新建档状态 */
    com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskItemVO saveActualModel(Long taskId, Long itemId, String actualModel);

    /** 本人当前有效分配的任务，否则抛「询价任务不存在」 */
    SourcingTaskDO myTask(Long taskId);

    /** 把一个型号本次的结果写入询价记录（在线录入与导入共用）；submit 时由调用方负责重算进度 */
    void writeQuotes(SourcingTaskDO task, Long quotedBy, List<QuoteDraft> drafts, boolean submit, int entryMode, Long importId);

    /** 提交后重算明细、任务与客户询盘进度，并记录提交时间 */
    void afterSubmit(SourcingTaskDO task, Long quotedBy, List<Long> itemIds);

    /** 一条待写入的询价结果；noStock 为真时只记无货说明 */
    record QuoteDraft(Long itemId, boolean noStock, Integer channel, String shopName, Long supplierId, java.math.BigDecimal unitPrice,
                      boolean taxIncluded, Integer taxRate, Integer itemCondition, Integer leadTime, String note, boolean recommended) {
    }
}
