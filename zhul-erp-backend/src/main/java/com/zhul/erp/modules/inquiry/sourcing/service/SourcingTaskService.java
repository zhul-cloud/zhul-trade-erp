package com.zhul.erp.modules.inquiry.sourcing.service;

import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.sourcing.dto.AssignRulesVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardTaskDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PurchaserVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.RulePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.SaveAssignRuleRequest;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;

import java.util.List;

/** 询价任务：生成、分配工作台、分配、改派、追加比价、按规则分配 */
public interface SourcingTaskService {

    /** 确认解析时调用：把待询价明细按品牌 + 品类分组生成任务；自动分配开启时按规则分配 */
    List<SourcingTaskDO> createTasks(CustomerInquiryDO inquiry, List<InquiryItemDO> pendingItems);

    /** 追加型号时：任务编号的字母后缀从 startIndex 开始，接着原有任务 */
    List<SourcingTaskDO> createTasks(CustomerInquiryDO inquiry, List<InquiryItemDO> pendingItems, int startIndex);

    /** 取消询盘单时调用：未回价的任务一并取消 */
    void cancelByInquiry(Long inquiryId);

    /** 询盘改了紧急后，同步到还没完成的询价任务（采购待办的排序与超时按它算） */
    void syncUrgent(Long inquiryId, int urgent);

    /** 当前账号负责的询盘里待业务处理的任务 */
    /** 询盘负责人待认领的任务，按页返回，品牌汇总覆盖全部页 */
    com.zhul.erp.modules.inquiry.sourcing.dto.SalesPendingVO salesPending(int page, int pageSize);

    /** 询盘负责人认领：任务变为询价中，标记为业务员自询 */
    void claim(List<Long> taskIds);

    /** 询盘负责人转采购：任务立即进入待分配 */
    void transferToPurchasing(List<Long> taskIds);

    /** 超时流转：待业务处理且已到期时进入待分配；由定时任务逐个调用，每个任务独立事务 */
    void moveToPoolIfDue(Long taskId);

    /** status 为空时返回待分配池，否则返回该状态的任务 */
    /** 分配工作台：统计按全部任务计算，任务列表只返回当前页 */
    BoardVO board(Integer status, int page, int pageSize);

    /** 分配工作台打开的任务详情（本租户任务，不受业务员数据权限限制） */
    BoardTaskDetailVO taskDetail(Long taskId);

    /** 任务里能看到的铭牌图片（spec inquiry/sourcing-task「询价分配查看铭牌图片」） */
    com.zhul.erp.modules.inquiry.sourcing.dto.TaskNameplatesVO nameplates(Long taskId);

    /** 铭牌图片的临时访问链接，只能读任务内型号对应的或同一询盘单未对应型号的 */
    com.zhul.erp.framework.storage.FileStorageService.FileLink nameplateLink(Long taskId, Long attachmentId, boolean download);

    /** 采购负责人指定某型号的采购成本价（quoteId 为空时恢复自动），记操作日志 */
    void setCostQuote(Long itemId, Long quoteId);

    /** 型号的修改记录：各采购每次回价的版本与采购负责人调整成本价，按时间倒序 */
    List<com.zhul.erp.modules.inquiry.sourcing.dto.ItemHistoryEntryVO> itemHistory(Long itemId);

    List<PurchaserVO> purchasers();

    void assign(List<Long> taskIds, Long assigneeId);

    void assignByRecommend(List<Long> taskIds);

    void reassign(Long taskId, Long assigneeId);

    void addAssignee(Long taskId, Long assigneeId);

    List<RulePreviewVO> previewRules(List<Long> taskIds);

    void applyRules(List<Long> taskIds);

    AssignRulesVO rules();

    void createRule(SaveAssignRuleRequest req);

    void updateRule(Long id, SaveAssignRuleRequest req);

    void deleteRule(Long id);

    /** 按传入顺序重排优先级 */
    void reorderRules(List<Long> ruleIds);

    void setAutoAssign(boolean enabled);
}
