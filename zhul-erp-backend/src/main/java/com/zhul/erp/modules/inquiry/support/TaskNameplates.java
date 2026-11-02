package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentVO;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryAttachmentDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryAttachmentMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.TaskNameplatesVO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 询价任务里的铭牌图片：采购（含兼职）与采购负责人只能看到任务内型号对应的铭牌图片，以及同一询盘单里未对应型号的，
 * 其他询盘附件一律看不到。调用方先按各自的规则确认能看这个任务。
 */
@Component
@RequiredArgsConstructor
public class TaskNameplates {

    private final CustomerInquiryAttachmentMapper attachmentMapper;
    private final InquiryItemMapper itemMapper;
    private final FileStorageService fileStorage;

    public TaskNameplatesVO of(SourcingTaskDO task) {
        List<Long> itemIds = itemIds(task);
        Map<Long, TaskNameplatesVO.ItemPlates> byItem = new LinkedHashMap<>();
        TaskNameplatesVO vo = new TaskNameplatesVO();
        for (CustomerInquiryAttachmentDO a : visible(task, itemIds)) {
            AttachmentVO f = toVo(a);
            if (a.getInquiryItemId() == null || a.getInquiryItemId() == 0) {
                vo.getUnmatched().add(f);
            } else {
                byItem.computeIfAbsent(a.getInquiryItemId(), id -> {
                    TaskNameplatesVO.ItemPlates p = new TaskNameplatesVO.ItemPlates();
                    p.setItemId(id);
                    return p;
                }).getFiles().add(f);
            }
        }
        vo.getItems().addAll(byItem.values());
        return vo;
    }

    public FileLink link(SourcingTaskDO task, Long attachmentId, boolean download) {
        CustomerInquiryAttachmentDO a = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        boolean ok = a != null && a.getDeletedAt() == null
                && Objects.equals(a.getTenantId(), task.getTenantId())
                && Objects.equals(a.getCustomerInquiryId(), task.getCustomerInquiryId())
                && Objects.equals(a.getKind(), InquiryConstants.ATTACHMENT_KIND_NAMEPLATE)
                && (a.getInquiryItemId() == null || a.getInquiryItemId() == 0 || itemIds(task).contains(a.getInquiryItemId()));
        if (!ok) {
            throw new BizException("铭牌图片不存在");
        }
        return fileStorage.link(a.getStorage(), a.getFileKey(), a.getFileName(), download);
    }

    private List<Long> itemIds(SourcingTaskDO task) {
        return itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                        .select(InquiryItemDO::getId)
                        .eq(InquiryItemDO::getSourcingTaskId, task.getId())
                        .isNull(InquiryItemDO::getDeletedAt))
                .stream().map(InquiryItemDO::getId).toList();
    }

    private List<CustomerInquiryAttachmentDO> visible(SourcingTaskDO task, List<Long> itemIds) {
        Set<Long> mine = Set.copyOf(itemIds);
        return attachmentMapper.selectList(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                        .eq(CustomerInquiryAttachmentDO::getTenantId, task.getTenantId())
                        .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, task.getCustomerInquiryId())
                        .eq(CustomerInquiryAttachmentDO::getKind, InquiryConstants.ATTACHMENT_KIND_NAMEPLATE)
                        .isNull(CustomerInquiryAttachmentDO::getDeletedAt)
                        .orderByAsc(CustomerInquiryAttachmentDO::getSort)
                        .orderByAsc(CustomerInquiryAttachmentDO::getId))
                .stream().filter(a -> a.getInquiryItemId() == null || a.getInquiryItemId() == 0 || mine.contains(a.getInquiryItemId()))
                .collect(Collectors.toList());
    }

    private static AttachmentVO toVo(CustomerInquiryAttachmentDO a) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setContentType(a.getContentType());
        vo.setFileSize(a.getFileSize());
        vo.setKind(a.getKind());
        vo.setInquiryItemId(a.getInquiryItemId());
        return vo;
    }
}
