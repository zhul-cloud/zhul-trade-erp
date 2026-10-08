package com.zhul.erp.modules.inquiry.customerinquiry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.quotation.support.QuotationLocks;
import com.zhul.erp.common.constants.DictTypes;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.crm.dto.OpportunityVO;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseGroupDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseItemDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseOutputDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentRef;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmRowRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryDetailVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerTypeVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.DraftRowVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.DraftVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryItemVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.TaskBriefVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.UploadedFileVO;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryAttachmentDO;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryAttachmentMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.service.CustomerInquiryService;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskAssigneeMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.PriceHistoryService;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingTaskService;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.SourcingProgress;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.GeneratedInquiryCode;
import com.zhul.erp.modules.inquiry.support.InquiryCodeGenerator;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.inquiry.support.SourcingSettings;
import com.zhul.erp.modules.inquiry.support.SpreadsheetText;
import com.zhul.erp.modules.inquiry.support.TaskTimeout;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerInquiryServiceImpl implements CustomerInquiryService {

    static final String PARSE_SKILL_ID = "inquiry-parse-and-split";
    private static final int MAX_CODE_INSERT_ATTEMPTS = 3;
    private static final int KEYWORD_LIMIT = 500;
    private static final Set<Integer> CANCELLABLE = Set.of(
            InquiryConstants.STATUS_PENDING_PARSE, InquiryConstants.STATUS_PARSING, InquiryConstants.STATUS_PENDING_CONFIRM,
            InquiryConstants.STATUS_PARSE_FAILED, InquiryConstants.STATUS_SOURCING, InquiryConstants.STATUS_READY_TO_QUOTE);

    private final CustomerInquiryMapper inquiryMapper;
    private final CustomerInquiryAttachmentMapper attachmentMapper;
    private final InquiryItemMapper itemMapper;
    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final CustomerMapper customerMapper;
    private final InquiryCodeGenerator codeGenerator;
    private final AiTaskService aiTaskService;
    private final OpportunityService opportunityService;
    private final ProductLookupService productLookupService;
    private final PriceHistoryService priceHistoryService;
    private final SourcingTaskService sourcingTaskService;
    private final SourcingProgress progress;
    private final SourcingSettings settings;
    private final PrivateFileStorage fileStorage;
    private final PriceKeys priceKeys;
    private final CurrentUserResolver currentUser;
    private final DataScopeResolver dataScopeResolver;
    private final InquiryLookups lookups;
    private final ObjectMapper objectMapper;
    private final DictItemService dictItemService;
    private final QuoteDicts quoteDicts;
    private final LogService logService;
    private final QuotationLocks quotationLocks;

    // ---------------------------------------------------------------- 附件

    @Override
    public UploadedFileVO uploadAttachment(MultipartFile file) {
        PrivateFileStorage.StoredFile stored = fileStorage.store(InquiryConstants.ATTACHMENT_MODULE, tenantId(), file,
                InquiryConstants.ATTACHMENT_EXTS, InquiryConstants.ATTACHMENT_MAX_BYTES, InquiryConstants.ATTACHMENT_TYPE_MESSAGE);
        return toUploaded(stored);
    }

    @Override
    public UploadedFileVO copyOpportunityAttachment(Long opportunityId, Long attachmentId) {
        OpportunityService.AttachmentFile file = opportunityService.attachmentFile(opportunityId, attachmentId);
        return toUploaded(fileStorage.copy(InquiryConstants.ATTACHMENT_MODULE, tenantId(), file.path(), file.fileName()));
    }

    private static UploadedFileVO toUploaded(PrivateFileStorage.StoredFile stored) {
        UploadedFileVO vo = new UploadedFileVO();
        vo.setFileKey(stored.fileKey());
        vo.setFileName(stored.fileName());
        vo.setContentType(stored.contentType());
        vo.setFileSize(stored.fileSize());
        return vo;
    }

    @Override
    public AttachmentFile attachmentFile(Long id, Long attachmentId) {
        CustomerInquiryDO inquiry = getVisible(id);
        CustomerInquiryAttachmentDO a = attachmentMapper.selectById(attachmentId);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getCustomerInquiryId(), inquiry.getId())) {
            throw new BizException("附件不存在");
        }
        Path path = fileStorage.resolveOwned(InquiryConstants.ATTACHMENT_MODULE, a.getFileKey(), inquiry.getTenantId());
        return new AttachmentFile(path, a.getFileName(), a.getContentType());
    }

    // ---------------------------------------------------------------- 录入

    @Override
    public CustomerTypeVO customerType(Long customerId) {
        List<CustomerInquiryDO> won = inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                .select(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryDate, CustomerInquiryDO::getUpdateTime)
                .eq(CustomerInquiryDO::getTenantId, tenantId())
                .eq(CustomerInquiryDO::getCustomerId, customerId)
                .eq(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_WON)
                .isNull(CustomerInquiryDO::getDeletedAt));
        CustomerTypeVO vo = new CustomerTypeVO();
        vo.setCustomerType(won.isEmpty() ? InquiryConstants.CUSTOMER_NEW : InquiryConstants.CUSTOMER_RETURNING);
        vo.setWonCount(won.size());
        vo.setLastWonDate(won.stream().map(i -> i.getUpdateTime().toLocalDate()).max(LocalDate::compareTo).orElse(null));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerInquiryVO submit(SubmitCustomerInquiryRequest req) {
        int tenantId = tenantId();
        CustomerDO customer = customerMapper.selectById(req.getCustomerId());
        if (customer == null || customer.getDeletedAt() != null || !Objects.equals(customer.getTenantId(), tenantId)) {
            throw new BizException("客户不存在");
        }
        if (req.getOpportunityId() != null
                && !Objects.equals(opportunityService.customerIdOf(req.getOpportunityId()), req.getCustomerId())) {
            throw new BizException("来源商机与客户不一致");
        }
        List<AttachmentRef> refs = req.getAttachments() == null ? List.of() : req.getAttachments();
        if (!StringUtils.hasText(req.getRawContent()) && refs.isEmpty()) {
            throw new BizException("请粘贴询盘内容或上传附件");
        }
        LocalDate inquiryDate = req.getInquiryDate() != null ? req.getInquiryDate() : LocalDate.now();
        LocalDate deadline = req.getQuoteDeadline() != null ? req.getQuoteDeadline() : LocalDate.now();
        if (deadline.isBefore(inquiryDate)) {
            throw new BizException("报价截止不能早于询盘日期");
        }
        dictItemService.requireEnabledValue(DictTypes.SOURCE_CHANNEL, req.getSource(), "来源渠道不正确");
        List<Path> files = new ArrayList<>(refs.size());
        for (AttachmentRef ref : refs) {
            files.add(fileStorage.resolveOwned(InquiryConstants.ATTACHMENT_MODULE, ref.getFileKey(), tenantId));
        }

        CustomerInquiryDO inquiry = new CustomerInquiryDO();
        inquiry.setTenantId(tenantId);
        inquiry.setCustomerId(req.getCustomerId());
        inquiry.setOpportunityId(req.getOpportunityId());
        inquiry.setSource(req.getSource());
        inquiry.setRawContent(StringUtils.hasText(req.getRawContent()) ? req.getRawContent().trim() : "");
        inquiry.setInquiryDate(inquiryDate);
        inquiry.setQuoteDeadline(deadline);
        inquiry.setUrgent(Boolean.TRUE.equals(req.getUrgent()) ? 1 : 0);
        Integer level = req.getLevel() == null ? InquiryConstants.LEVEL_DEFAULT : req.getLevel();
        dictItemService.requireEnabledValue(DictTypes.INQUIRY_LEVEL, level, "询盘等级不正确");
        inquiry.setLevel(level);
        inquiry.setCustomerType(customerType(req.getCustomerId()).getCustomerType());
        inquiry.setParseMode(InquiryConstants.PARSE_MODE_NONE);
        inquiry.setStatus(InquiryConstants.STATUS_PENDING_PARSE);
        inquiry.setTotalOrderCount(0);
        inquiry.setTotalItemCount(0);
        inquiry.setTotalQuantity(0);
        inquiry.setPricedItemCount(0);
        inquiry.setPendingVerifyCount(0);
        inquiry.setNeedsReview(0);
        inquiry.setOwnerId(currentUser.resolve());
        inquiry.setParseError("");
        inquiry.setRemark(req.getRemark() == null ? "" : req.getRemark());
        insertWithRetry(inquiry);

        for (int i = 0; i < refs.size(); i++) {
            AttachmentRef ref = refs.get(i);
            CustomerInquiryAttachmentDO a = new CustomerInquiryAttachmentDO();
            a.setTenantId(tenantId);
            a.setCustomerInquiryId(inquiry.getId());
            a.setFileKey(ref.getFileKey());
            a.setFileName(PrivateFileStorage.cleanFileName(ref.getFileName(), PrivateFileStorage.extOf(ref.getFileKey())));
            a.setContentType(PrivateFileStorage.contentTypeOf(ref.getFileKey()));
            a.setFileSize(files.get(i).toFile().length());
            a.setSort(i);
            attachmentMapper.insert(a);
        }
        if (req.getOpportunityId() != null) {
            opportunityService.onInquiryCreated(req.getOpportunityId(), inquiry.getInquiryCode());
        }
        return toVos(List.of(inquiry)).get(0);
    }

    private void insertWithRetry(CustomerInquiryDO inquiry) {
        for (int attempt = 1; attempt <= MAX_CODE_INSERT_ATTEMPTS; attempt++) {
            GeneratedInquiryCode generated = codeGenerator.nextCustomerInquiryCode(inquiry.getTenantId());
            inquiry.setId(null);
            inquiry.setInquiryCode(generated.getCode());
            try {
                inquiryMapper.insert(inquiry);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_CODE_INSERT_ATTEMPTS) {
                    throw new BizException("客户询盘编号生成冲突，请重试");
                }
            }
        }
    }

    // ---------------------------------------------------------------- 列表与详情

    @Override
    public PageResult<CustomerInquiryVO> page(CustomerInquiryPageQuery q) {
        int tenantId = tenantId();
        LambdaQueryWrapper<CustomerInquiryDO> w = new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getTenantId, tenantId)
                .isNull(CustomerInquiryDO::getDeletedAt);
        dataScopeResolver.current().apply(w, CustomerInquiryDO::getOwnerId);
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenantId)
                            .isNull(CustomerDO::getDeletedAt)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getContactName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(CustomerDO::getId).toList();
            String modelKey = PriceKeys.model(kw);
            List<Long> byModel = !StringUtils.hasText(modelKey) ? List.of() : itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                            .select(InquiryItemDO::getCustomerInquiryId)
                            .eq(InquiryItemDO::getTenantId, tenantId)
                            .like(InquiryItemDO::getModelKey, modelKey)
                            .isNull(InquiryItemDO::getDeletedAt)
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(InquiryItemDO::getCustomerInquiryId).distinct().toList();
            w.and(x -> {
                x.like(CustomerInquiryDO::getInquiryCode, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(CustomerInquiryDO::getCustomerId, customerIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(CustomerInquiryDO::getId, byModel);
                }
            });
        }
        if (q.getStatusList() != null && !q.getStatusList().isEmpty()) {
            w.in(CustomerInquiryDO::getStatus, q.getStatusList());
        }
        w.eq(q.getSource() != null, CustomerInquiryDO::getSource, q.getSource())
                .eq(q.getOwnerId() != null, CustomerInquiryDO::getOwnerId, q.getOwnerId())
                .eq(q.getCustomerType() != null, CustomerInquiryDO::getCustomerType, q.getCustomerType())
                .ge(q.getInquiryDateFrom() != null, CustomerInquiryDO::getInquiryDate, q.getInquiryDateFrom())
                .le(q.getInquiryDateTo() != null, CustomerInquiryDO::getInquiryDate, q.getInquiryDateTo())
                .eq(Boolean.TRUE.equals(q.getUrgentOnly()), CustomerInquiryDO::getUrgent, 1)
                .eq(q.getLevel() != null, CustomerInquiryDO::getLevel, q.getLevel())
                .ge(q.getMinItemCount() != null, CustomerInquiryDO::getTotalItemCount, q.getMinItemCount())
                .le(q.getMaxItemCount() != null, CustomerInquiryDO::getTotalItemCount, q.getMaxItemCount())
                .ge(q.getMinTotalQuantity() != null, CustomerInquiryDO::getTotalQuantity, q.getMinTotalQuantity())
                .le(q.getMaxTotalQuantity() != null, CustomerInquiryDO::getTotalQuantity, q.getMaxTotalQuantity());
        if (Boolean.TRUE.equals(q.getTimeoutOnly())) {
            List<Long> ids = timeoutInquiryIds(tenantId);
            if (ids.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            w.in(CustomerInquiryDO::getId, ids);
        }
        // 排序字段只认白名单，避免拼接任意列；同值再按创建时间倒序
        boolean asc = "asc".equalsIgnoreCase(q.getSortOrder());
        if ("level".equals(q.getSortField())) {
            // 码值越小等级越高：「从高到低」对应码值升序
            w.orderBy(true, !asc, CustomerInquiryDO::getLevel);
        } else if ("totalItemCount".equals(q.getSortField())) {
            w.orderBy(true, asc, CustomerInquiryDO::getTotalItemCount);
        } else if ("totalQuantity".equals(q.getSortField())) {
            w.orderBy(true, asc, CustomerInquiryDO::getTotalQuantity);
        }
        w.orderByDesc(CustomerInquiryDO::getCreateTime).orderByDesc(CustomerInquiryDO::getId);
        int size = Math.min(Math.max(q.getPageSize() == null ? 10 : q.getPageSize(), 1), 100);
        Page<CustomerInquiryDO> page = inquiryMapper.selectPage(new Page<>(q.getPage() == null ? 1 : q.getPage(), size), w);
        return PageResult.of(page.getTotal(), toVos(page.getRecords()));
    }

    private List<Long> timeoutInquiryIds(int tenantId) {
        TaskTimeout limits = TaskTimeout.of(settings, tenantId);
        LocalDateTime now = LocalDateTime.now();
        return taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                        .eq(SourcingTaskDO::getTenantId, tenantId)
                        .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SOURCING)
                        .isNull(SourcingTaskDO::getDeletedAt)
                        .and(x -> x.nested(y -> y.eq(SourcingTaskDO::getUrgent, 0)
                                        .lt(SourcingTaskDO::getFirstAssignedAt, now.minusHours(limits.normalHours())))
                                .or().nested(y -> y.eq(SourcingTaskDO::getUrgent, 1)
                                        .lt(SourcingTaskDO::getFirstAssignedAt, now.minusHours(limits.urgentHours())))))
                .stream().map(SourcingTaskDO::getCustomerInquiryId).distinct().toList();
    }

    @Override
    public CustomerInquiryDetailVO detail(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        CustomerInquiryDetailVO vo = new CustomerInquiryDetailVO();
        vo.setInquiry(toVos(List.of(inquiry)).get(0));
        vo.setRawContent(inquiry.getRawContent());
        vo.setParseError(inquiry.getParseError());
        vo.setRemark(inquiry.getRemark());
        vo.setAttachments(attachments(inquiry.getId()));

        List<SourcingTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getCustomerInquiryId, id)
                .isNull(SourcingTaskDO::getDeletedAt)
                .orderByAsc(SourcingTaskDO::getTaskCode));
        Map<Long, String> taskCodes = tasks.stream().collect(Collectors.toMap(SourcingTaskDO::getId, SourcingTaskDO::getTaskCode));
        List<InquiryItemDO> items = items(id);
        vo.setItems(toItemVos(items, taskCodes));
        vo.setTasks(toTaskBriefs(tasks, items, inquiry.getTenantId()));

        OpportunityVO opp = inquiry.getOpportunityId() == null ? null : opportunityService.findVisible(inquiry.getOpportunityId());
        if (opp != null) {
            vo.setOpportunityCode(opp.getOpportunityCode());
            vo.setOpportunityStageName(opp.getStageName());
        }
        return vo;
    }

    private List<InquiryItemVO> toItemVos(List<InquiryItemDO> items, Map<Long, String> taskCodes) {
        Set<Long> quoteIds = items.stream().map(InquiryItemDO::getSelectedQuoteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PriceRecordVO> selected = new HashMap<>(quoteIds.isEmpty() ? Map.of() : priceHistoryService.toVoMap(quoteMapper.selectBatchIds(quoteIds)));
        List<Long> noStockItems = items.stream().filter(i -> i.getQuoteStatus() == InquiryConstants.ITEM_NO_STOCK).map(InquiryItemDO::getId).toList();
        Map<Long, String> noStockNotes = new HashMap<>();
        if (!noStockItems.isEmpty()) {
            quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                            .in(SourcingQuoteDO::getInquiryItemId, noStockItems)
                            .eq(SourcingQuoteDO::getNoStock, 1)
                            .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                            .isNull(SourcingQuoteDO::getDeletedAt)
                            .orderByDesc(SourcingQuoteDO::getQuotedAt))
                    .forEach(q -> noStockNotes.putIfAbsent(q.getInquiryItemId(), q.getNote()));
        }
        Set<Long> quoted = quotationLocks.lockedItemIds(items.stream().map(InquiryItemDO::getId).toList());
        List<InquiryItemVO> list = new ArrayList<>(items.size());
        for (InquiryItemDO i : items) {
            InquiryItemVO vo = new InquiryItemVO();
            vo.setId(i.getId());
            vo.setQuoted(quoted.contains(i.getId()));
            vo.setLineNo(i.getLineNo());
            vo.setBrand(i.getBrand());
            vo.setBrandKey(i.getBrandKey());
            vo.setCategory(i.getCategory());
            vo.setOriginalModel(i.getOriginalModel());
            vo.setConfirmedModel(i.getConfirmedModel());
            vo.setConfidence(i.getConfidence());
            vo.setCorrectionNote(i.getCorrectionNote());
            vo.setQuantity(i.getQuantity());
            vo.setUnit(i.getUnit());
            vo.setDescription(i.getDescription());
            vo.setDescriptionEn(i.getDescriptionEn());
            vo.setLifecycle(i.getLifecycle());
            vo.setReplacementModel(i.getReplacementModel());
            vo.setDifficulty(i.getDifficulty());
            vo.setPriceSource(i.getPriceSource());
            vo.setQuoteStatus(i.getQuoteStatus());
            vo.setSourcingTaskId(i.getSourcingTaskId());
            vo.setTaskCode(taskCodes.get(i.getSourcingTaskId()));
            vo.setSelectedQuote(selected.get(i.getSelectedQuoteId()));
            vo.setNoStockNote(noStockNotes.get(i.getId()));
            list.add(vo);
        }
        // 同品牌、同品类的型号排在一起；组之间按首次出现的顺序
        Map<String, List<InquiryItemVO>> grouped = new LinkedHashMap<>();
        list.forEach(v -> grouped.computeIfAbsent(v.getBrandKey() + "|" + v.getCategory(), k -> new ArrayList<>()).add(v));
        return grouped.values().stream().flatMap(List::stream).toList();
    }

    private List<TaskBriefVO> toTaskBriefs(List<SourcingTaskDO> tasks, List<InquiryItemDO> items, int tenantId) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SourcingTaskAssigneeDO>> active = assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                        .in(SourcingTaskAssigneeDO::getTaskId, tasks.stream().map(SourcingTaskDO::getId).toList())
                        .eq(SourcingTaskAssigneeDO::getActive, 1)
                        .isNull(SourcingTaskAssigneeDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(SourcingTaskAssigneeDO::getTaskId));
        Map<Long, String> names = lookups.userNames(active.values().stream().flatMap(List::stream).map(SourcingTaskAssigneeDO::getAssigneeId).toList());
        TaskTimeout limits = TaskTimeout.of(settings, tenantId);
        LocalDateTime now = LocalDateTime.now();
        List<TaskBriefVO> list = new ArrayList<>(tasks.size());
        for (SourcingTaskDO t : tasks) {
            TaskBriefVO vo = new TaskBriefVO();
            vo.setId(t.getId());
            vo.setTaskCode(t.getTaskCode());
            vo.setBrand(t.getBrand());
            vo.setCategory(t.getCategory());
            vo.setItemCount(t.getItemCount());
            vo.setPricedCount((int) items.stream().filter(i -> Objects.equals(i.getSourcingTaskId(), t.getId())
                    && i.getQuoteStatus() != InquiryConstants.ITEM_PENDING).count());
            vo.setStatus(t.getStatus());
            vo.setTimeout(limits.isTimeout(t, now));
            vo.setFirstAssignedAt(t.getFirstAssignedAt());
            vo.setAssigneeNames(active.getOrDefault(t.getId(), List.of()).stream().map(a -> names.get(a.getAssigneeId())).toList());
            vo.setReturnReason(t.getReturnReason());
            vo.setReturnNote(t.getReturnNote());
            list.add(vo);
        }
        return list;
    }

    private List<CustomerInquiryVO> toVos(List<CustomerInquiryDO> inquiries) {
        if (inquiries.isEmpty()) {
            return List.of();
        }
        Map<Long, CustomerDO> customers = lookups.customers(inquiries.stream().map(CustomerInquiryDO::getCustomerId).toList());
        Map<Long, String> owners = lookups.userNames(inquiries.stream().map(CustomerInquiryDO::getOwnerId).toList());
        List<Long> ids = inquiries.stream().map(CustomerInquiryDO::getId).toList();
        List<SourcingTaskDO> sourcing = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .in(SourcingTaskDO::getCustomerInquiryId, ids)
                .eq(SourcingTaskDO::getStatus, InquiryConstants.TASK_SOURCING)
                .isNull(SourcingTaskDO::getDeletedAt));
        TaskTimeout limits = TaskTimeout.of(settings, inquiries.get(0).getTenantId());
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> timeouts = sourcing.stream().filter(t -> limits.isTimeout(t, now))
                .collect(Collectors.groupingBy(SourcingTaskDO::getCustomerInquiryId, Collectors.counting()));
        List<CustomerInquiryVO> list = new ArrayList<>(inquiries.size());
        for (CustomerInquiryDO i : inquiries) {
            CustomerInquiryVO vo = new CustomerInquiryVO();
            CustomerDO c = customers.get(i.getCustomerId());
            vo.setId(i.getId());
            vo.setInquiryCode(i.getInquiryCode());
            vo.setCustomerId(i.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(c));
            vo.setCustomerCountry(c == null ? "" : c.getCountry());
            vo.setCustomerType(i.getCustomerType());
            vo.setSource(i.getSource());
            vo.setInquiryDate(i.getInquiryDate());
            vo.setQuoteDeadline(i.getQuoteDeadline());
            vo.setUrgent(Objects.equals(i.getUrgent(), 1));
            vo.setLevel(i.getLevel());
            vo.setStatus(i.getStatus());
            vo.setParseMode(i.getParseMode());
            vo.setTotalItemCount(i.getTotalItemCount());
            vo.setTotalQuantity(i.getTotalQuantity());
            vo.setPricedItemCount(i.getPricedItemCount());
            vo.setTaskCount(i.getTotalOrderCount());
            vo.setTimeoutTaskCount(timeouts.getOrDefault(i.getId(), 0L).intValue());
            vo.setPendingVerifyCount(i.getPendingVerifyCount());
            vo.setNeedsReview(Objects.equals(i.getNeedsReview(), 1));
            vo.setOwnerId(i.getOwnerId());
            vo.setOwnerName(owners.get(i.getOwnerId()));
            vo.setOpportunityId(i.getOpportunityId());
            vo.setCreateTime(i.getCreateTime());
            vo.setUpdateTime(i.getUpdateTime());
            list.add(vo);
        }
        return list;
    }

    private List<AttachmentVO> attachments(Long inquiryId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                        .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, inquiryId)
                        .isNull(CustomerInquiryAttachmentDO::getDeletedAt)
                        .orderByAsc(CustomerInquiryAttachmentDO::getSort))
                .stream().map(a -> {
                    AttachmentVO vo = new AttachmentVO();
                    vo.setId(a.getId());
                    vo.setFileName(a.getFileName());
                    vo.setContentType(a.getContentType());
                    vo.setFileSize(a.getFileSize());
                    return vo;
                }).toList();
    }

    // ---------------------------------------------------------------- 解析

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerInquiryVO startParse(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        if (inquiry.getStatus() != InquiryConstants.STATUS_PENDING_PARSE) {
            throw new BizException("只有待解析的询盘可以发起 AI 解析");
        }
        return submitParseTask(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerInquiryVO retryParse(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        // 待确认也可以重新解析：解析结果不对或补了附件时重来，新结果覆盖原草稿
        if (inquiry.getStatus() != InquiryConstants.STATUS_PARSE_FAILED
                && inquiry.getStatus() != InquiryConstants.STATUS_PENDING_CONFIRM) {
            throw new BizException("只有解析失败或待确认的询盘可以重新解析");
        }
        inquiry.setParseError("");
        return submitParseTask(inquiry);
    }

    /**
     * 拼 AI 解析输入：正文 + Excel / CSV 抽出的文本；图片和 PDF 以本地绝对路径交给编排服务自己读。
     * {@code rawAttachmentPath} 保留第一个图片 / PDF，兼容只认单个附件的旧版编排服务。
     */
    private CustomerInquiryVO submitParseTask(CustomerInquiryDO inquiry) {
        StringBuilder text = new StringBuilder(inquiry.getRawContent() == null ? "" : inquiry.getRawContent());
        List<String> paths = new ArrayList<>();
        for (CustomerInquiryAttachmentDO a : attachmentMapper.selectList(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, inquiry.getId())
                .isNull(CustomerInquiryAttachmentDO::getDeletedAt)
                .orderByAsc(CustomerInquiryAttachmentDO::getSort))) {
            Path path = fileStorage.resolveOwned(InquiryConstants.ATTACHMENT_MODULE, a.getFileKey(), inquiry.getTenantId());
            String ext = PrivateFileStorage.extOf(a.getFileKey());
            if (Set.of("xls", "xlsx", "csv").contains(ext)) {
                text.append(text.isEmpty() ? "" : "\n\n").append("附件《").append(a.getFileName()).append("》：\n")
                        .append(SpreadsheetText.extract(path));
            } else {
                paths.add(path.toString());
            }
        }
        Map<String, Object> input = new HashMap<>();
        input.put("customerInquiryId", inquiry.getId());
        input.put("rawContent", text.toString());
        input.put("attachmentPaths", paths);
        if (!paths.isEmpty()) {
            input.put("rawAttachmentPath", paths.get(0));
        }
        AiTaskVO task = aiTaskService.createAndSubmit(PARSE_SKILL_ID, writeJson(input), currentUser.resolve());
        inquiry.setAiTaskId(task.getId());
        inquiry.setStatus(InquiryConstants.STATUS_PARSING);
        inquiryMapper.updateById(inquiry);
        return toVos(List.of(inquiry)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyParseSuccess(Long aiTaskId, String outputJson) {
        CustomerInquiryDO inquiry = findByAiTask(aiTaskId);
        if (inquiry == null || inquiry.getStatus() != InquiryConstants.STATUS_PARSING) {
            log.warn("AI 解析成功回调已无对应的解析中询盘，忽略，aiTaskId={}", aiTaskId);
            return;
        }
        AiParseOutputDTO output = parseOutput(outputJson);
        int total = 0;
        long quantity = 0;
        int verify = 0;
        for (AiParseGroupDTO g : output.getGroups()) {
            for (AiParseItemDTO item : g.getItems()) {
                total++;
                quantity += item.getQuantity() == null || item.getQuantity() < 0 ? 0 : item.getQuantity();
                if (item.getConfidence() != null && item.getConfidence() >= InquiryConstants.CONFIDENCE_PENDING_VERIFY) {
                    verify++;
                }
            }
        }
        inquiry.setTotalItemCount(total);
        inquiry.setTotalQuantity((int) Math.min(quantity, Integer.MAX_VALUE));
        inquiry.setPendingVerifyCount(verify);
        inquiry.setParseMode(InquiryConstants.PARSE_MODE_AI);
        inquiry.setStatus(InquiryConstants.STATUS_PENDING_CONFIRM);
        inquiryMapper.updateById(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyParseFailure(Long aiTaskId, String errorMessage) {
        CustomerInquiryDO inquiry = findByAiTask(aiTaskId);
        if (inquiry == null || inquiry.getStatus() != InquiryConstants.STATUS_PARSING) {
            log.warn("AI 解析失败回调已无对应的解析中询盘，忽略，aiTaskId={}", aiTaskId);
            return;
        }
        String msg = StringUtils.hasText(errorMessage) ? errorMessage : "AI 解析失败";
        inquiry.setParseError(msg.length() > 300 ? msg.substring(0, 300) : msg);
        inquiry.setStatus(InquiryConstants.STATUS_PARSE_FAILED);
        inquiryMapper.updateById(inquiry);
    }

    // ---------------------------------------------------------------- 确认

    @Override
    public DraftVO draft(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        if (!confirmable(inquiry.getStatus())) {
            throw new BizException("只有待解析、解析失败或待确认的询盘可以录入型号");
        }
        List<DraftRowVO> rows = new ArrayList<>();
        // 只有待确认的 AI 解析结果才预填；待解析 / 解析失败时手动录入，从空表开始
        if (inquiry.getStatus() == InquiryConstants.STATUS_PENDING_CONFIRM
                && inquiry.getParseMode() == InquiryConstants.PARSE_MODE_AI && inquiry.getAiTaskId() != null) {
            AiParseOutputDTO output = parseOutput(aiTaskService.getById(inquiry.getAiTaskId()).getOutput());
            for (AiParseGroupDTO g : output.getGroups()) {
                for (AiParseItemDTO it : g.getItems()) {
                    rows.add(toDraftRow(g, it));
                }
            }
        }
        DraftVO vo = new DraftVO();
        vo.setInquiry(toVos(List.of(inquiry)).get(0));
        vo.setRawContent(inquiry.getRawContent());
        vo.setAttachments(attachments(inquiry.getId()));
        vo.setRows(rows);
        return vo;
    }

    private DraftRowVO toDraftRow(AiParseGroupDTO g, AiParseItemDTO it) {
        DraftRowVO row = new DraftRowVO();
        row.setBrand(g.getBrand());
        row.setCategory(g.getCategory());
        row.setOriginalModel(it.getOriginalModel());
        row.setConfirmedModel(StringUtils.hasText(it.getConfirmedModel()) ? it.getConfirmedModel() : it.getOriginalModel());
        row.setConfidence(it.getConfidence() == null ? InquiryConstants.CONFIDENCE_CONFIRMED : it.getConfidence());
        row.setCorrectionNote(it.getCorrectionNote());
        row.setQuantity(it.getQuantity() == null || it.getQuantity() < 1 ? 1 : it.getQuantity());
        row.setUnit(it.getUnit());
        row.setDescription(it.getDescription());
        row.setDescriptionEn(it.getDescriptionEn());
        row.setLifecycle(it.getLifecycle() == null ? InquiryConstants.LIFECYCLE_UNKNOWN : it.getLifecycle());
        row.setReplacementModel(it.getReplacementModel());
        row.setDifficulty(it.getDifficulty() == null ? 0 : it.getDifficulty());
        row.setInquiryScript(StringUtils.hasText(it.getInquiryScript()) ? it.getInquiryScript() : g.getInquiryTemplate());
        row.setSearchKeywords(it.getSearchKeywords() == null ? List.of() : it.getSearchKeywords());
        row.setMatch(priceHistoryService.match(row.getBrand(), row.getConfirmedModel()));
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void confirm(Long id, ConfirmRequest req) {
        CustomerInquiryDO inquiry = getVisible(id);
        progress.lock(inquiry.getId());
        inquiry = inquiryMapper.selectById(id);
        if (!confirmable(inquiry.getStatus())) {
            throw new BizException("只有待解析、解析失败或待确认的询盘可以确认型号，请刷新后再试");
        }
        // 手动录入不单独改状态：待解析 / 解析失败时直接在确认页逐行填写，确认这一刻才离开原状态
        if (inquiry.getStatus() != InquiryConstants.STATUS_PENDING_CONFIRM) {
            inquiry.setParseMode(InquiryConstants.PARSE_MODE_MANUAL);
            inquiry.setParseError("");
        }
        List<InquiryItemDO> pending = new ArrayList<>();
        QuoteDictSnapshot.Dict lifecycles = quoteDicts.snapshot().lifecycles();
        int line = 0;
        int priced = 0;
        int verify = 0;
        for (ConfirmRowRequest row : req.getRows()) {
            line++;
            Integer lifecycle = row.getLifecycle() == null || row.getLifecycle() == 0 ? InquiryConstants.LIFECYCLE_UNKNOWN : row.getLifecycle();
            if (!lifecycles.enabled().contains(lifecycle)) {
                throw new BizException("第 " + line + " 行生命周期不在可选值内，请从下拉中选择");
            }
            PriceKeys.BrandRef brand = priceKeys.brand(row.getBrand());
            String modelKey = PriceKeys.model(row.getConfirmedModel());
            if (!StringUtils.hasText(modelKey)) {
                throw new BizException("第 " + line + " 行型号无效，请填写字母或数字");
            }
            InquiryItemDO item = new InquiryItemDO();
            item.setTenantId(inquiry.getTenantId());
            item.setCustomerInquiryId(inquiry.getId());
            item.setLineNo(line);
            item.setBrand(row.getBrand().trim());
            item.setBrandId(brand.brandId());
            item.setBrandKey(brand.brandKey());
            item.setCategory(text(row.getCategory()));
            item.setOriginalModel(StringUtils.hasText(row.getOriginalModel()) ? row.getOriginalModel().trim() : row.getConfirmedModel().trim());
            item.setConfirmedModel(row.getConfirmedModel().trim());
            item.setModelKey(modelKey);
            item.setProductId(matchProduct(item.getBrand(), item.getConfirmedModel()));
            item.setConfidence(range(row.getConfidence(), 1, 4, InquiryConstants.CONFIDENCE_CONFIRMED));
            item.setCorrectionNote(text(row.getCorrectionNote()));
            item.setQuantity(row.getQuantity());
            item.setUnit(text(row.getUnit()));
            item.setDescription(text(row.getDescription()));
            item.setDescriptionEn(text(row.getDescriptionEn()));
            item.setLifecycle(lifecycle);
            item.setReplacementModel(text(row.getReplacementModel()));
            item.setDifficulty(range(row.getDifficulty(), 0, 3, 0));
            item.setInquiryScript(text(row.getInquiryScript()));
            item.setSearchKeywords(writeJson(row.getSearchKeywords() == null ? List.of()
                    : row.getSearchKeywords().stream().filter(StringUtils::hasText).map(String::trim).limit(8).toList()));
            item.setRemark("");
            if (item.getConfidence() >= InquiryConstants.CONFIDENCE_PENDING_VERIFY) {
                verify++;
            }
            if (row.getReuseQuoteId() != null) {
                SourcingQuoteDO quote = quoteMapper.selectById(row.getReuseQuoteId());
                if (!reusable(quote, inquiry.getTenantId(), brand.brandKey(), modelKey)) {
                    throw new BizException("第 " + line + " 行选择的历史价格与品牌、型号不一致，请重新选择");
                }
                item.setPriceSource(InquiryConstants.PRICE_SOURCE_HISTORY);
                item.setQuoteStatus(InquiryConstants.ITEM_PRICED);
                item.setSelectedQuoteId(quote.getId());
                priced++;
            } else {
                item.setPriceSource(InquiryConstants.PRICE_SOURCE_SOURCING);
                item.setQuoteStatus(InquiryConstants.ITEM_PENDING);
            }
            itemMapper.insert(item);
            if (item.getSelectedQuoteId() == null) {
                pending.add(item);
            }
        }
        int taskCount = sourcingTaskService.createTasks(inquiry, pending).size();
        inquiry.setTotalItemCount(line);
        inquiry.setTotalQuantity((int) Math.min(req.getRows().stream().mapToLong(ConfirmRowRequest::getQuantity).sum(), Integer.MAX_VALUE));
        inquiry.setPricedItemCount(priced);
        inquiry.setPendingVerifyCount(verify);
        inquiry.setTotalOrderCount(taskCount);
        inquiry.setStatus(pending.isEmpty() ? InquiryConstants.STATUS_READY_TO_QUOTE : InquiryConstants.STATUS_SOURCING);
        inquiryMapper.updateById(inquiry);
    }

    private Long matchProduct(String brand, String model) {
        try {
            ProductMatchVO match = productLookupService.match(brand, model);
            return match == null || match.getExact() == null ? null : match.getExact().getId();
        } catch (BizException e) {
            return null;
        }
    }

    private static boolean reusable(SourcingQuoteDO q, int tenantId, String brandKey, String modelKey) {
        return q != null && q.getDeletedAt() == null && Objects.equals(q.getTenantId(), tenantId)
                && q.getStatus() == InquiryConstants.QUOTE_SUBMITTED && !Objects.equals(q.getNoStock(), 1) && q.getUnitPriceCny() != null
                && brandKey.equals(q.getBrandKey()) && modelKey.equals(q.getModelKey());
    }

    // ---------------------------------------------------------------- 取消、核实、改选价格、调整等级

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLevel(Long id, Integer level) {
        CustomerInquiryDO inquiry = getVisible(id);
        if (InquiryConstants.STATUS_CANCELLED == inquiry.getStatus()) {
            throw new BizException("已取消的询盘不能调整等级");
        }
        dictItemService.requireEnabledValue(DictTypes.INQUIRY_LEVEL, level, "询盘等级不正确");
        if (Objects.equals(inquiry.getLevel(), level)) {
            return;
        }
        Map<Integer, String> labels = dictItemService.intLabels(DictTypes.INQUIRY_LEVEL);
        Map<String, Object> before = Map.of("inquiryCode", inquiry.getInquiryCode(), "level", labels.getOrDefault(inquiry.getLevel(), ""));
        CustomerInquiryDO patch = new CustomerInquiryDO();
        patch.setId(id);
        patch.setLevel(level);
        inquiryMapper.updateById(patch);
        logService.recordOperateLog("客户询盘", "调整询盘等级", before,
                Map.of("inquiryCode", inquiry.getInquiryCode(), "level", labels.getOrDefault(level, "")));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void cancel(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        progress.lock(id);
        inquiry = inquiryMapper.selectById(id);
        if (!CANCELLABLE.contains(inquiry.getStatus())) {
            throw new BizException("已报价的询盘不能取消");
        }
        inquiry.setStatus(InquiryConstants.STATUS_CANCELLED);
        inquiryMapper.updateById(inquiry);
        sourcingTaskService.cancelByInquiry(id);
    }

    @Override
    public void markReviewed(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        inquiry.setNeedsReview(0);
        inquiryMapper.updateById(inquiry);
    }

    // ---------------------------------------------------------------- 工具

    private List<InquiryItemDO> items(Long inquiryId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getCustomerInquiryId, inquiryId)
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getLineNo));
    }

    private CustomerInquiryDO getVisible(Long id) {
        CustomerInquiryDO inquiry = id == null ? null : inquiryMapper.selectById(id);
        DataScope scope = dataScopeResolver.current();
        if (inquiry == null || inquiry.getDeletedAt() != null || !Objects.equals(inquiry.getTenantId(), tenantId())
                || !scope.canSee(inquiry.getOwnerId())) {
            throw new BizException("客户询盘不存在");
        }
        return inquiry;
    }

    private CustomerInquiryDO findByAiTask(Long aiTaskId) {
        return inquiryMapper.selectOne(new LambdaQueryWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getAiTaskId, aiTaskId)
                .isNull(CustomerInquiryDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private AiParseOutputDTO parseOutput(String json) {
        if (!StringUtils.hasText(json)) {
            return new AiParseOutputDTO();
        }
        try {
            return objectMapper.readValue(json, AiParseOutputDTO.class);
        } catch (JsonProcessingException e) {
            throw new BizException("AI 解析结果格式异常，请改为手动录入或重新解析", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 可以录入 / 确认型号的状态：待确认（AI 已解析）或待解析、解析失败（手动录入） */
    private static boolean confirmable(Integer status) {
        return status == InquiryConstants.STATUS_PENDING_CONFIRM || status == InquiryConstants.STATUS_PENDING_PARSE
                || status == InquiryConstants.STATUS_PARSE_FAILED;
    }

    private static int range(Integer v, int min, int max, int fallback) {
        return v == null || v < min || v > max ? fallback : v;
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
