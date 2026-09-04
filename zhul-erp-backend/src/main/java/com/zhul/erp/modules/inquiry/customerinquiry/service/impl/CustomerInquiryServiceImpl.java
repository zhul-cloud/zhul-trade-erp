package com.zhul.erp.modules.inquiry.customerinquiry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.inquiry.constants.ConfidenceLevel;
import com.zhul.erp.modules.inquiry.customerinquiry.constants.CustomerInquiryStatus;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AdvanceCustomerInquiryStatusRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseGroupDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseItemDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseOutputDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitGroupRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitItemRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryPreviewVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.service.CustomerInquiryService;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderVO;
import com.zhul.erp.modules.inquiry.inquiryorder.service.InquiryOrderService;
import com.zhul.erp.modules.inquiry.support.ConfirmedGroupDTO;
import com.zhul.erp.modules.inquiry.support.ConfirmedItemDTO;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.GeneratedInquiryCode;
import com.zhul.erp.modules.inquiry.support.InquiryCodeGenerator;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerInquiryServiceImpl implements CustomerInquiryService {

    private static final int MAX_CODE_INSERT_ATTEMPTS = 3;
    private static final String PARSE_SKILL_ID = "inquiry-parse-and-split";

    private final CustomerInquiryMapper customerInquiryMapper;
    private final InquiryCodeGenerator codeGenerator;
    private final AiTaskService aiTaskService;
    private final InquiryOrderService inquiryOrderService;
    private final CustomerService customerService;
    private final CurrentUserResolver currentUserResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerInquiryVO submit(SubmitCustomerInquiryRequest req) {
        if (customerService.getById(req.getCustomerId()) == null) {
            throw new BizException("客户不存在");
        }
        CustomerInquiryDO inquiry = new CustomerInquiryDO();
        inquiry.setTenantId(currentTenantId());
        inquiry.setCustomerId(req.getCustomerId());
        inquiry.setSource(req.getSource());
        inquiry.setRawContent(req.getRawContent());
        inquiry.setRawAttachmentUrl(req.getRawAttachmentUrl());
        inquiry.setInquiryDate(req.getInquiryDate() != null ? req.getInquiryDate() : LocalDate.now());
        inquiry.setExpectedReplyDate(req.getExpectedReplyDate());
        inquiry.setStatus(CustomerInquiryStatus.PENDING_PARSE);
        inquiry.setTotalOrderCount(0);
        inquiry.setTotalItemCount(0);
        inquiry.setPendingVerifyCount(0);
        inquiry.setOwnerId(currentUserResolver.resolve());
        inquiry.setRemark(req.getRemark());
        insertWithRetry(inquiry);
        return toVo(inquiry);
    }

    @Override
    public PageResult<CustomerInquiryVO> page(CustomerInquiryPageQuery query) {
        LambdaQueryWrapper<CustomerInquiryDO> wrapper = new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getTenantId, currentTenantId())
                .isNull(CustomerInquiryDO::getDeletedAt);
        if (StringUtils.hasText(query.getInquiryCode())) {
            wrapper.like(CustomerInquiryDO::getInquiryCode, query.getInquiryCode());
        }
        if (StringUtils.hasText(query.getCustomerName())) {
            List<Long> matchedCustomerIds = new ArrayList<>();
            customerService.search(query.getCustomerName()).forEach(c -> matchedCustomerIds.add(c.getId()));
            if (matchedCustomerIds.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            wrapper.in(CustomerInquiryDO::getCustomerId, matchedCustomerIds);
        }
        if (query.getStatusList() != null && !query.getStatusList().isEmpty()) {
            wrapper.in(CustomerInquiryDO::getStatus, query.getStatusList());
        }
        if (query.getInquiryDateFrom() != null) {
            wrapper.ge(CustomerInquiryDO::getInquiryDate, query.getInquiryDateFrom());
        }
        if (query.getInquiryDateTo() != null) {
            wrapper.le(CustomerInquiryDO::getInquiryDate, query.getInquiryDateTo());
        }
        if (query.getOwnerId() != null) {
            wrapper.eq(CustomerInquiryDO::getOwnerId, query.getOwnerId());
        }
        wrapper.orderByDesc(CustomerInquiryDO::getCreateTime);

        Page<CustomerInquiryDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
        Page<CustomerInquiryDO> pageResult = customerInquiryMapper.selectPage(pageParam, wrapper);
        List<CustomerInquiryVO> records = new ArrayList<>(pageResult.getRecords().size());
        for (CustomerInquiryDO inquiry : pageResult.getRecords()) {
            records.add(toVo(inquiry));
        }
        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public CustomerInquiryVO getById(Long id) {
        return toVo(getOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerInquiryVO startAiParse(Long id) {
        CustomerInquiryDO inquiry = getOrThrow(id);
        if (inquiry.getStatus() == null || inquiry.getStatus() != CustomerInquiryStatus.PENDING_PARSE) {
            throw new BizException("仅'待解析'状态可以发起AI解析");
        }
        String inputJson = writeJsonSafely(Map.of(
                "customerInquiryId", inquiry.getId(),
                "source", inquiry.getSource() != null ? inquiry.getSource() : 0,
                "rawContent", inquiry.getRawContent() != null ? inquiry.getRawContent() : "",
                "rawAttachmentUrl", inquiry.getRawAttachmentUrl() != null ? inquiry.getRawAttachmentUrl() : ""
        ));
        AiTaskVO task = aiTaskService.createAndSubmit(PARSE_SKILL_ID, inputJson, currentUserResolver.resolve());
        inquiry.setAiTaskId(task.getId());
        inquiry.setStatus(CustomerInquiryStatus.PARSING);
        customerInquiryMapper.updateById(inquiry);
        return toVo(inquiry);
    }

    @Override
    public InquiryPreviewVO getPreview(Long id) {
        CustomerInquiryDO inquiry = getOrThrow(id);
        if (inquiry.getAiTaskId() == null) {
            throw new BizException("该客户询盘尚未发起AI解析");
        }
        AiTaskVO task = aiTaskService.getById(inquiry.getAiTaskId());
        AiParseOutputDTO output = parseOutput(task.getOutput());

        InquiryPreviewVO vo = new InquiryPreviewVO();
        vo.setInquiry(toVo(inquiry));
        vo.setGroups(output.getGroups());

        int total = 0, confirmed = 0, corrected = 0, pendingVerify = 0, unrecognized = 0;
        for (AiParseGroupDTO group : output.getGroups()) {
            for (AiParseItemDTO item : group.getItems()) {
                total++;
                if (item.getConfidence() == null) {
                    continue;
                }
                if (item.getConfidence() == ConfidenceLevel.CONFIRMED) {
                    confirmed++;
                } else if (item.getConfidence() == ConfidenceLevel.CORRECTED) {
                    corrected++;
                } else if (item.getConfidence() == ConfidenceLevel.PENDING_VERIFY) {
                    pendingVerify++;
                } else if (item.getConfidence() == ConfidenceLevel.UNRECOGNIZED) {
                    unrecognized++;
                }
            }
        }
        vo.setTotalItemCount(total);
        vo.setConfirmedCount(confirmed);
        vo.setCorrectedCount(corrected);
        vo.setPendingVerifyCount(pendingVerify);
        vo.setUnrecognizedCount(unrecognized);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSplit(Long id, ConfirmSplitRequest req) {
        CustomerInquiryDO inquiry = getOrThrow(id);
        if (inquiry.getStatus() == null || inquiry.getStatus() != CustomerInquiryStatus.PENDING_CONFIRM) {
            throw new BizException("仅'待确认'状态可以确认拆单");
        }
        AiTaskVO task = aiTaskService.getById(inquiry.getAiTaskId());
        AiParseOutputDTO output = parseOutput(task.getOutput());

        List<ConfirmedGroupDTO> confirmedGroups = new ArrayList<>();
        for (ConfirmSplitGroupRequest groupReq : req.getGroups()) {
            if (groupReq.getItems() == null || groupReq.getItems().isEmpty()) {
                continue; // 空分组随确认动作一并丢弃
            }
            int idx = groupReq.getGroupIndex();
            if (idx < 0 || idx >= output.getGroups().size()) {
                throw new BizException("分组序号超出范围: " + idx);
            }
            AiParseGroupDTO original = output.getGroups().get(idx);

            ConfirmedGroupDTO confirmed = new ConfirmedGroupDTO();
            confirmed.setBrand(original.getBrand());
            confirmed.setCategory(original.getCategory());
            confirmed.setInquiryTemplate(original.getInquiryTemplate());
            confirmed.setEmailTemplateCn(original.getEmailTemplateCn());
            confirmed.setEmailTemplateEn(original.getEmailTemplateEn());

            List<ConfirmedItemDTO> items = new ArrayList<>();
            for (ConfirmSplitItemRequest itemReq : groupReq.getItems()) {
                ConfirmedItemDTO item = new ConfirmedItemDTO();
                item.setOriginalModel(itemReq.getOriginalModel());
                item.setConfirmedModel(itemReq.getConfirmedModel());
                item.setConfidence(itemReq.getConfidence());
                item.setCorrectionNote(itemReq.getCorrectionNote());
                item.setDescription(itemReq.getDescription());
                item.setQuantity(itemReq.getQuantity());
                item.setUnit(itemReq.getUnit());
                item.setDeliveryRequirement(itemReq.getDeliveryRequirement());
                item.setRemark(itemReq.getRemark());
                items.add(item);
            }
            confirmed.setItems(items);
            confirmedGroups.add(confirmed);
        }

        List<InquiryOrderVO> created = inquiryOrderService.createFromConfirmedGroups(
                inquiry.getId(), inquiry.getInquiryCode(), inquiry.getAiTaskId(), confirmedGroups);

        inquiry.setStatus(CustomerInquiryStatus.CONFIRMED);
        inquiry.setTotalOrderCount(created.size());
        customerInquiryMapper.updateById(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        CustomerInquiryDO inquiry = getOrThrow(id);
        if (inquiry.getStatus() != null
                && (inquiry.getStatus() == CustomerInquiryStatus.DEAL || inquiry.getStatus() == CustomerInquiryStatus.CANCELLED)) {
            throw new BizException("当前状态不允许取消");
        }
        inquiry.setStatus(CustomerInquiryStatus.CANCELLED);
        customerInquiryMapper.updateById(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceStatus(Long id, AdvanceCustomerInquiryStatusRequest req) {
        CustomerInquiryDO inquiry = getOrThrow(id);
        int current = inquiry.getStatus();
        int target = req.getTargetStatus();

        if (target == CustomerInquiryStatus.CANCELLED) {
            if (current == CustomerInquiryStatus.DEAL || current == CustomerInquiryStatus.CANCELLED) {
                throw new BizException("当前状态不允许取消");
            }
        } else {
            int[] seq = CustomerInquiryStatus.MANUAL_ADVANCE_SEQUENCE;
            int currentIdx = indexOf(seq, current);
            int targetIdx = indexOf(seq, target);
            if (currentIdx < 0 || targetIdx != currentIdx + 1) {
                throw new BizException("非法的状态流转：不能从当前状态直接跳转到目标状态");
            }
        }
        inquiry.setStatus(target);
        customerInquiryMapper.updateById(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyParseSuccess(Long aiTaskId, String outputJson) {
        CustomerInquiryDO inquiry = findByAiTaskId(aiTaskId);
        if (inquiry == null) {
            log.warn("未找到 aiTaskId={} 对应的客户询盘，忽略本次解析成功回调", aiTaskId);
            return;
        }
        AiParseOutputDTO output = parseOutput(outputJson);
        int total = 0;
        int pendingVerify = 0;
        for (AiParseGroupDTO group : output.getGroups()) {
            for (AiParseItemDTO item : group.getItems()) {
                total++;
                if (item.getConfidence() != null
                        && (item.getConfidence() == ConfidenceLevel.PENDING_VERIFY || item.getConfidence() == ConfidenceLevel.UNRECOGNIZED)) {
                    pendingVerify++;
                }
            }
        }
        inquiry.setTotalItemCount(total);
        inquiry.setPendingVerifyCount(pendingVerify);
        inquiry.setStatus(CustomerInquiryStatus.PENDING_CONFIRM);
        customerInquiryMapper.updateById(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyParseFailure(Long aiTaskId, String errorMessage) {
        CustomerInquiryDO inquiry = findByAiTaskId(aiTaskId);
        if (inquiry == null) {
            log.warn("未找到 aiTaskId={} 对应的客户询盘，忽略本次解析失败回调", aiTaskId);
            return;
        }
        inquiry.setStatus(CustomerInquiryStatus.PARSE_FAILED);
        inquiry.setRemark(errorMessage);
        customerInquiryMapper.updateById(inquiry);
    }

    // ------------------------------------------------------------------

    private CustomerInquiryDO findByAiTaskId(Long aiTaskId) {
        return customerInquiryMapper.selectOne(new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getAiTaskId, aiTaskId)
                .isNull(CustomerInquiryDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private AiParseOutputDTO parseOutput(String outputJson) {
        if (!StringUtils.hasText(outputJson)) {
            return new AiParseOutputDTO();
        }
        try {
            return objectMapper.readValue(outputJson, AiParseOutputDTO.class);
        } catch (Exception e) {
            throw new BizException("AI解析结果格式异常，无法渲染预览");
        }
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化AI解析输入失败", e);
            return "{}";
        }
    }

    private void insertWithRetry(CustomerInquiryDO inquiry) {
        int tenantId = inquiry.getTenantId();
        for (int attempt = 1; attempt <= MAX_CODE_INSERT_ATTEMPTS; attempt++) {
            GeneratedInquiryCode generated = codeGenerator.nextCustomerInquiryCode(tenantId);
            inquiry.setId(null);
            inquiry.setInquiryCode(generated.getCode());
            if (generated.isFallbackUsed() && !StringUtils.hasText(inquiry.getRemark())) {
                inquiry.setRemark("编号生成异常，待人工核实");
            }
            try {
                customerInquiryMapper.insert(inquiry);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_CODE_INSERT_ATTEMPTS) {
                    throw new BizException("客户询盘编号生成冲突，请重试");
                }
            }
        }
    }

    private CustomerInquiryDO getOrThrow(Long id) {
        CustomerInquiryDO inquiry = customerInquiryMapper.selectById(id);
        if (inquiry == null || inquiry.getDeletedAt() != null) {
            throw new BizException("客户询盘不存在");
        }
        return inquiry;
    }

    private static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }

    private CustomerInquiryVO toVo(CustomerInquiryDO inquiry) {
        CustomerInquiryVO vo = new CustomerInquiryVO();
        vo.setId(inquiry.getId());
        vo.setInquiryCode(inquiry.getInquiryCode());
        vo.setCustomerId(inquiry.getCustomerId());
        vo.setSource(inquiry.getSource());
        vo.setRawContent(inquiry.getRawContent());
        vo.setRawAttachmentUrl(inquiry.getRawAttachmentUrl());
        vo.setInquiryDate(inquiry.getInquiryDate());
        vo.setExpectedReplyDate(inquiry.getExpectedReplyDate());
        vo.setStatus(inquiry.getStatus());
        vo.setTotalOrderCount(inquiry.getTotalOrderCount());
        vo.setTotalItemCount(inquiry.getTotalItemCount());
        vo.setPendingVerifyCount(inquiry.getPendingVerifyCount());
        vo.setOwnerId(inquiry.getOwnerId());
        vo.setAiTaskId(inquiry.getAiTaskId());
        vo.setRemark(inquiry.getRemark());
        vo.setCreateTime(inquiry.getCreateTime());
        return vo;
    }
}
