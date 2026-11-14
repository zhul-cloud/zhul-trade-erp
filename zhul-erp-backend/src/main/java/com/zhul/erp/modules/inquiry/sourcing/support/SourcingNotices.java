package com.zhul.erp.modules.inquiry.sourcing.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.Notice;
import com.zhul.erp.modules.system.notification.support.Notifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/** 询价相关的通知（spec system/notification「询价通知」） */
@Component
@RequiredArgsConstructor
public class SourcingNotices {

    private static final String BOARD = "/inquiry/sourcing-board";
    private static final String MY_TASKS = "/inquiry/my-tasks";
    private static final String INQUIRY = "/inquiry/customer-inquiries/";

    private final Notifier notifier;
    private final SourcingTaskMapper taskMapper;
    private final CustomerInquiryMapper inquiryMapper;

    /** 询盘有任务进入待分配池：通知采购负责人（自动分配成功的已不在池里，不通知） */
    public void pool(Long inquiryId) {
        List<SourcingTaskDO> waiting = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getCustomerInquiryId, inquiryId)
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_UNASSIGNED)
                .and(w -> w.isNull(SourcingTaskDO::getDeferUntil).or().le(SourcingTaskDO::getDeferUntil, java.time.LocalDate.now()))
                .isNull(SourcingTaskDO::getDeletedAt));
        CustomerInquiryDO inquiry = inquiryMapper.selectById(inquiryId);
        if (waiting.isEmpty() || inquiry == null) {
            return;
        }
        notifier.send(Notice.of(NotificationNode.SOURCING_POOL, inquiry.getInquiryCode(), BOARD)
                .merge(inquiryId)
                .ids(waiting.stream().map(SourcingTaskDO::getId).toList())
                .content(waiting.stream().map(SourcingNotices::taskName).collect(Collectors.joining("、")))
                .urgent(isUrgent(inquiry.getUrgent())));
    }

    /** 任务分配、改派、加人给某人：同一次操作分给同一人的合并成一条 */
    public void assigned(SourcingTaskDO task, Long assigneeId) {
        notifier.send(Notice.of(NotificationNode.SOURCING_ASSIGNED, task.getTaskCode(), MY_TASKS)
                .ids(List.of(task.getId()))
                .content(taskName(task) + " · " + task.getItemCount() + " 个型号")
                .urgent(isUrgent(task.getUrgent()))
                .to(assigneeId));
    }

    /** 处理人以「型号存疑」退回：通知询盘负责人核实 */
    public void modelDoubt(SourcingTaskDO task, CustomerInquiryDO inquiry, List<InquiryItemDO> items, String note) {
        String models = items.stream().map(InquiryItemDO::getConfirmedModel).collect(Collectors.joining("、"));
        notifier.send(Notice.of(NotificationNode.MODEL_DOUBT, task.getTaskCode(), INQUIRY + inquiry.getId())
                .count(items.size())
                .content(note == null || note.isBlank() ? models : models + "：" + note.trim())
                .to(inquiry.getOwnerId()));
    }

    /** 询盘从询价中变为可报价：通知询盘负责人 */
    public void quotable(CustomerInquiryDO inquiry) {
        notifier.send(Notice.of(NotificationNode.INQUIRY_QUOTABLE, inquiry.getInquiryCode(), INQUIRY + inquiry.getId())
                .merge(inquiry.getId())
                .content("共 " + inquiry.getTotalItemCount() + " 个型号")
                .to(inquiry.getOwnerId()));
    }

    private static String taskName(SourcingTaskDO t) {
        return (t.getBrand() == null ? "" : t.getBrand()) + "·" + (t.getCategory() == null ? "" : t.getCategory());
    }

    private static boolean isUrgent(Integer urgent) {
        return urgent != null && urgent == 1;
    }
}
