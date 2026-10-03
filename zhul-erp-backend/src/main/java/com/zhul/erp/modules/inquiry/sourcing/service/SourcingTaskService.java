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

    /** 取消客户询盘时调用：未回价的任务一并取消 */
    void cancelByInquiry(Long inquiryId);

    /** status 为空时返回待分配池，否则返回该状态的任务 */
    BoardVO board(Integer status);

    /** 分配工作台打开的任务详情（本租户任务，不受业务员数据权限限制） */
    BoardTaskDetailVO taskDetail(Long taskId);

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
