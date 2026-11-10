package com.zhul.erp.modules.inquiry.customerinquiry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.quotation.support.QuotationLocks;
import com.zhul.erp.common.constants.DictTypes;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.MigratedGuard;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.crm.dto.OpportunityDetailVO;
import com.zhul.erp.modules.crm.dto.OpportunityVO;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryAppendMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryAppendDO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AppendVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AppendRequest;
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
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryDownstreamVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.DraftRowVO;
import com.zhul.erp.modules.inquiry.support.InquiryModelSummary;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryModelLineVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.DraftVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryItemVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.UpdateCustomerInquiryRequest;
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
import com.zhul.erp.modules.inquiry.support.InquiryCategories;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inquiry.support.ParsedItemMerger;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.inquiry.support.SourcingSettings;
import com.zhul.erp.modules.inquiry.support.TaskTimeout;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.support.ProductOrderCodes;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import com.zhul.erp.modules.upload.service.DirectUploadService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerInquiryServiceImpl implements CustomerInquiryService {

    static final String PARSE_SKILL_ID = "inquiry-parse-and-split";
    private static final int MAX_CODE_INSERT_ATTEMPTS = 3;
    private static final int KEYWORD_LIMIT = 500;
    /** 录入询盘时的附件上限，修改时补传也按它算（追加型号的附件另算） */
    private static final Set<Integer> CANCELLABLE = Set.of(
            InquiryConstants.STATUS_PENDING_PARSE, InquiryConstants.STATUS_PARSING, InquiryConstants.STATUS_PENDING_CONFIRM,
            InquiryConstants.STATUS_PARSE_FAILED, InquiryConstants.STATUS_SOURCING, InquiryConstants.STATUS_READY_TO_QUOTE);

    private final BusinessUnitService businessUnitService;
    private final CustomerInquiryMapper inquiryMapper;
    private final CustomerInquiryAttachmentMapper attachmentMapper;
    private final InquiryItemMapper itemMapper;
    private final MediaIndexer media;
    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final CustomerMapper customerMapper;
    private final InquiryCodeGenerator codeGenerator;
    private final AiTaskService aiTaskService;
    private final OpportunityService opportunityService;
    private final ProductLookupService productLookupService;
    private final ProductOrderCodes orderCodes;
    private final PriceHistoryService priceHistoryService;
    private final SourcingTaskService sourcingTaskService;
    private final SourcingProgress progress;
    private final SourcingSettings settings;
    /** 发给 AI 编排服务的附件链接有效期：解析任务可能排队，比页面预览的 10 分钟长 */
    private static final Duration AI_LINK_TTL = Duration.ofMinutes(60);

    private final FileStorageService fileStorage;
    private final DirectUploadService directUpload;

    /** 后端对外可达地址：本地存储方式下的附件链接是相对地址，发给 AI 编排服务时拼上它 */
    @Value("${zhul.ai-task.callback-base-url}")
    private String backendBaseUrl;
    private final PriceKeys priceKeys;
    private final CurrentUserResolver currentUser;
    private final DataScopeResolver dataScopeResolver;
    private final com.zhul.erp.framework.security.PermissionChecker perm;
    private final InquiryLookups lookups;
    private final ObjectMapper objectMapper;
    private final DictItemService dictItemService;
    private final QuoteDicts quoteDicts;
    private final LogService logService;
    private final QuotationLocks quotationLocks;
    private final com.zhul.erp.modules.product.candidate.service.ProductArchiver productArchiver;
    private final InquiryCategories inquiryCategories;
    private final CustomerInquiryAppendMapper appendMapper;
    private final com.zhul.erp.modules.crm.followup.support.FollowupHooks followupHooks;

    // ---------------------------------------------------------------- 附件

    @Override
    public UploadedFileVO uploadAttachment(MultipartFile file) {
        FileStorageService.StoredFile stored = fileStorage.store(StorageArea.INQUIRY, tenantId(), file,
                InquiryConstants.ATTACHMENT_EXTS, InquiryConstants.ATTACHMENT_MAX_BYTES, InquiryConstants.ATTACHMENT_TYPE_MESSAGE);
        return toUploaded(stored);
    }

    @Override
    public UploadedFileVO copyOpportunityAttachment(Long opportunityId, Long attachmentId) {
        OpportunityService.AttachmentFile file = opportunityService.attachmentFile(opportunityId, attachmentId);
        return toUploaded(fileStorage.copy(file.storage(), file.fileKey(), StorageArea.INQUIRY, tenantId(), file.fileName()));
    }

    private static UploadedFileVO toUploaded(FileStorageService.StoredFile stored) {
        UploadedFileVO vo = new UploadedFileVO();
        vo.setFileKey(stored.fileKey());
        vo.setFileName(stored.fileName());
        vo.setContentType(stored.contentType());
        vo.setFileSize(stored.fileSize());
        return vo;
    }

    @Override
    public FileLink attachmentLink(Long id, Long attachmentId, boolean download) {
        CustomerInquiryDO inquiry = getVisible(id);
        CustomerInquiryAttachmentDO a = attachmentMapper.selectById(attachmentId);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getCustomerInquiryId(), inquiry.getId())) {
            throw new BizException("附件不存在");
        }
        return fileStorage.link(a.getStorage(), a.getFileKey(), a.getFileName(), download);
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
        CustomerDO customer = customerMapper.selectById(customerId);
        if (customer != null && Objects.equals(customer.getTenantId(), tenantId()) && customer.getDeletedAt() == null) {
            vo.setSourceChannel(customer.getSourceChannel());
        }
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
        List<AttachmentRef> plates = nameplateRefs(req.getNameplates());
        if (!StringUtils.hasText(req.getRawContent()) && refs.isEmpty() && plates.isEmpty()) {
            throw new BizException("请粘贴询盘内容或上传附件");
        }
        LocalDate inquiryDate = req.getInquiryDate() != null ? req.getInquiryDate() : LocalDate.now();
        LocalDate deadline = req.getQuoteDeadline() != null ? req.getQuoteDeadline() : LocalDate.now();
        if (deadline.isBefore(inquiryDate)) {
            throw new BizException("报价截止不能早于询盘日期");
        }
        dictItemService.requireEnabledValue(DictTypes.SOURCE_CHANNEL, req.getSource(), "来源渠道不正确");
        refs = claimAttachments(0L, refs, tenantId);
        for (AttachmentRef ref : plates) {
            fileStorage.locate(StorageArea.INQUIRY, ref.getFileKey(), tenantId);
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

        saveAttachments(inquiry, refs, 0L, InquiryConstants.ATTACHMENT_KIND_FILE);
        saveAttachments(inquiry, plates, 0L, InquiryConstants.ATTACHMENT_KIND_NAMEPLATE);
        if (req.getOpportunityId() != null) {
            opportunityService.onInquiryCreated(req.getOpportunityId(), inquiry.getInquiryCode());
        }
        return toVos(List.of(inquiry)).get(0);
    }

    /**
     * 保存询盘附件；appendId 为 0 时是录入询盘单时的附件，否则属于某次追加。排序接着已有附件。
     * kind 为 1-询盘附件、2-铭牌图片（铭牌图片先未对应型号，确认时再对应）
     */
    private void saveAttachments(CustomerInquiryDO inquiry, List<AttachmentRef> refs, long appendId, int kind) {
        int sort = Math.toIntExact(attachmentMapper.selectCount(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, inquiry.getId())));
        for (AttachmentRef ref : refs) {
            String storage = fileStorage.locate(StorageArea.INQUIRY, ref.getFileKey(), inquiry.getTenantId());
            CustomerInquiryAttachmentDO a = new CustomerInquiryAttachmentDO();
            a.setTenantId(inquiry.getTenantId());
            a.setCustomerInquiryId(inquiry.getId());
            a.setAppendId(appendId);
            a.setKind(kind);
            a.setInquiryItemId(0L);
            a.setFileKey(ref.getFileKey());
            a.setStorage(storage);
            a.setFileName(UploadRules.cleanFileName(ref.getFileName(), UploadRules.extOf(ref.getFileKey())));
            a.setContentType(UploadRules.contentTypeOf(ref.getFileKey()));
            a.setFileSize(fileStorage.storages().of(storage).size(false, ref.getFileKey()));
            a.setSort(sort++);
            attachmentMapper.insert(a);
        }
    }

    /**
     * 询盘附件合计不超过 2GB、不限个数（spec inquiry/inquiry-intake「询盘大附件」）：existing 为保留的已有附件大小，
     * 新增附件按存储里的实际大小计算，不信任前端；同时校验文件键属于本租户、询盘环节且文件存在。铭牌图片不计入。
     * 合计通过后认领直传的临时文件（转到正式位置），返回改成正式文件键的附件列表；其他附件原样返回。
     */
    private List<AttachmentRef> claimAttachments(long existing, List<AttachmentRef> added, int tenantId) {
        long total = existing;
        for (AttachmentRef ref : added) {
            if (DirectUploadService.isPending(ref.getFileKey())) {
                total += directUpload.pendingSize(ref.getFileKey(), InquiryConstants.UPLOAD_SCENE_ATTACHMENT);
                continue;
            }
            String storage = fileStorage.locate(StorageArea.INQUIRY, ref.getFileKey(), tenantId);
            total += Math.max(0, fileStorage.storages().of(storage).size(false, ref.getFileKey()));
        }
        if (total > InquiryConstants.ATTACHMENT_TOTAL_BYTES) {
            throw new BizException(InquiryConstants.ATTACHMENT_TOTAL_MESSAGE);
        }
        List<AttachmentRef> claimed = new ArrayList<>(added.size());
        for (AttachmentRef ref : added) {
            if (!DirectUploadService.isPending(ref.getFileKey())) {
                claimed.add(ref);
                continue;
            }
            AttachmentRef c = new AttachmentRef();
            c.setFileKey(directUpload.claim(ref.getFileKey(), InquiryConstants.UPLOAD_SCENE_ATTACHMENT));
            c.setFileName(ref.getFileName());
            claimed.add(c);
        }
        return claimed;
    }

    /** 询盘附件（不含铭牌图片）的大小合计 */
    private static long fileBytes(List<CustomerInquiryAttachmentDO> rows) {
        return rows.stream().filter(a -> !Objects.equals(a.getKind(), InquiryConstants.ATTACHMENT_KIND_NAMEPLATE))
                .mapToLong(a -> a.getFileSize() == null ? 0 : a.getFileSize()).sum();
    }

    /** 铭牌图片只收 JPG、PNG（上传时已按文件头校验，这里按存储键的扩展名再挡一次铭牌区的其他类型） */
    private static List<AttachmentRef> nameplateRefs(List<AttachmentRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        if (refs.size() > InquiryConstants.NAMEPLATE_MAX) {
            throw new BizException("铭牌图片最多 " + InquiryConstants.NAMEPLATE_MAX + " 张");
        }
        for (AttachmentRef ref : refs) {
            if (!InquiryConstants.NAMEPLATE_EXTS.contains(UploadRules.extOf(ref.getFileKey()))) {
                throw new BizException("铭牌图片只支持 JPG、PNG");
            }
        }
        return refs;
    }

    private void insertWithRetry(CustomerInquiryDO inquiry) {
        // 事业部跟随客户（spec system/business-unit「单据归属事业部」）
        inquiry.setBusinessUnitId(businessUnitService.unitOfCustomer(inquiry.getCustomerId()));
        for (int attempt = 1; attempt <= MAX_CODE_INSERT_ATTEMPTS; attempt++) {
            GeneratedInquiryCode generated = codeGenerator.nextCustomerInquiryCode(inquiry.getTenantId());
            inquiry.setId(null);
            inquiry.setInquiryCode(generated.getCode());
            try {
                inquiryMapper.insert(inquiry);
                followupHooks.inquiryCreated(inquiry);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_CODE_INSERT_ATTEMPTS) {
                    throw new BizException("询盘单编号生成冲突，请重试");
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
        CustomerInquiryAppendDO open = openAppend(inquiry.getId());
        vo.setOpenAppend(open == null ? null : toAppendVo(open));
        vo.setRemark(inquiry.getRemark());
        vo.setAttachments(attachments(inquiry.getId()));
        vo.setNameplates(nameplates(inquiry.getId(), null));

        List<SourcingTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getCustomerInquiryId, id)
                .isNull(SourcingTaskDO::getDeletedAt)
                .orderByAsc(SourcingTaskDO::getTaskCode));
        Map<Long, String> taskCodes = tasks.stream().collect(Collectors.toMap(SourcingTaskDO::getId, SourcingTaskDO::getTaskCode));
        List<InquiryItemDO> items = items(id);
        vo.setItems(toItemVos(items, taskCodes));
        vo.setTasks(toTaskBriefs(tasks, items, inquiry));

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
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        List<InquiryItemVO> list = new ArrayList<>(items.size());
        for (InquiryItemDO i : items) {
            InquiryItemVO vo = new InquiryItemVO();
            vo.setId(i.getId());
            vo.setQuoted(quoted.contains(i.getId()));
            vo.setLineNo(i.getLineNo());
            vo.setBrand(i.getBrand());
            vo.setBrandKey(i.getBrandKey());
            vo.setCategory(i.getCategory());
            vo.setCategoryId(i.getCategoryId());
            vo.setGroupKey(InquiryCategories.groupKey(i.getBrandKey(), i.getCategoryId(), i.getCategory(), categories));
            vo.setOriginalModel(i.getOriginalModel());
            vo.setConfirmedModel(i.getConfirmedModel());
            vo.setOrderCode(i.getOrderCode());
            vo.setConfidence(i.getConfidence());
            vo.setCorrectionNote(i.getCorrectionNote());
            vo.setQuantity(i.getQuantity());
            vo.setUnit(i.getUnit());
            vo.setDescription(i.getDescription());
            vo.setDescriptionEn(i.getDescriptionEn());
            vo.setLifecycle(i.getLifecycle());
            vo.setReplacementModel(i.getReplacementModel());
            vo.setActualModel(i.getActualModel());
            vo.setArchiveStatus(i.getArchiveStatus());
            vo.setArchiveStatusName(com.zhul.erp.modules.product.candidate.constants.CandidateConstants.ARCHIVE_NAMES.get(i.getArchiveStatus()));
            vo.setProductId(i.getProductId());
            vo.setDifficulty(i.getDifficulty());
            vo.setPriceSource(i.getPriceSource());
            vo.setQuoteStatus(i.getQuoteStatus());
            vo.setSourcingTaskId(i.getSourcingTaskId());
            vo.setTaskCode(taskCodes.get(i.getSourcingTaskId()));
            vo.setSelectedQuote(selected.get(i.getSelectedQuoteId()));
            vo.setNoStockNote(noStockNotes.get(i.getId()));
            list.add(vo);
        }
        // 同品牌、同一级品类的型号排在一起；组之间按首次出现的顺序
        Map<String, List<InquiryItemVO>> grouped = new LinkedHashMap<>();
        list.forEach(v -> grouped.computeIfAbsent(v.getGroupKey(), k -> new ArrayList<>()).add(v));
        return grouped.values().stream().flatMap(List::stream).toList();
    }

    private List<TaskBriefVO> toTaskBriefs(List<SourcingTaskDO> tasks, List<InquiryItemDO> items, CustomerInquiryDO inquiry) {
        int tenantId = inquiry.getTenantId();
        boolean owner = Objects.equals(currentUser.resolve(), inquiry.getOwnerId());
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
            boolean salesStage = t.getStatus() == InquiryConstants.TASK_SALES;
            if (salesStage && t.getSalesDeadline() != null) {
                vo.setSalesRemainingMinutes(Math.max(0, java.time.Duration.between(now, t.getSalesDeadline()).toMinutes()));
            }
            vo.setCanTransfer(salesStage && owner);
            vo.setCanClaim(salesStage && owner && perm.canAccessMenu(InquiryConstants.MENU_MY_TASKS));
            vo.setSelfSourcing(active.getOrDefault(t.getId(), List.of()).stream()
                    .anyMatch(a -> Objects.equals(a.getAssignMode(), InquiryConstants.ASSIGN_SELF)));
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
        Map<Long, List<InquiryModelLineVO>> models = modelLines(inquiries);
        Map<Long, AiTaskVO> parsing = parseProgress(inquiries);
        List<CustomerInquiryVO> list = new ArrayList<>(inquiries.size());
        for (CustomerInquiryDO i : inquiries) {
            CustomerInquiryVO vo = new CustomerInquiryVO();
            vo.setMigrated(MigratedGuard.isMigrated(i.getMigrated()));
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
            AiTaskVO p = parsing.get(i.getAiTaskId());
            if (i.getStatus() == InquiryConstants.STATUS_PARSING && p != null && p.getProgressTotal() != null
                    && p.getProgressTotal() > 0) {
                vo.setParseProgressDone(p.getProgressDone());
                vo.setParseProgressTotal(p.getProgressTotal());
            }
            vo.setTotalItemCount(i.getTotalItemCount());
            vo.setTotalQuantity(i.getTotalQuantity());
            List<InquiryModelLineVO> lines = models.getOrDefault(i.getId(), List.of());
            vo.setFirstModel(InquiryModelSummary.firstModel(lines));
            vo.setBrands(InquiryModelSummary.brands(lines));
            vo.setCategories(InquiryModelSummary.categories(lines));
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

    /** 解析中询盘的 AI 任务进度，按任务 ID */
    private Map<Long, AiTaskVO> parseProgress(List<CustomerInquiryDO> inquiries) {
        List<Long> taskIds = inquiries.stream()
                .filter(i -> i.getStatus() == InquiryConstants.STATUS_PARSING && i.getAiTaskId() != null)
                .map(CustomerInquiryDO::getAiTaskId).toList();
        return aiTaskService.listProgress(taskIds).stream().collect(Collectors.toMap(AiTaskVO::getId, t -> t));
    }

    private List<AttachmentVO> attachments(Long inquiryId) {
        return attachments(inquiryId, null);
    }

    /** 询盘附件（不含铭牌图片）：appendId 为空时全部，否则只取该次追加（0 为录入时）的 */
    private List<AttachmentVO> attachments(Long inquiryId, Long appendId) {
        return attachmentRows(inquiryId, appendId, InquiryConstants.ATTACHMENT_KIND_FILE).stream().map(CustomerInquiryServiceImpl::toAttachmentVo).toList();
    }

    /** 铭牌图片：appendId 为空时全部，否则只取该次追加（0 为录入时）的 */
    private List<AttachmentVO> nameplates(Long inquiryId, Long appendId) {
        return attachmentRows(inquiryId, appendId, InquiryConstants.ATTACHMENT_KIND_NAMEPLATE).stream().map(CustomerInquiryServiceImpl::toAttachmentVo).toList();
    }

    private List<CustomerInquiryAttachmentDO> attachmentRows(Long inquiryId, Long appendId, int kind) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, inquiryId)
                .eq(appendId != null, CustomerInquiryAttachmentDO::getAppendId, appendId)
                .eq(CustomerInquiryAttachmentDO::getKind, kind)
                .isNull(CustomerInquiryAttachmentDO::getDeletedAt)
                .orderByAsc(CustomerInquiryAttachmentDO::getSort)
                .orderByAsc(CustomerInquiryAttachmentDO::getId));
    }

    private static AttachmentVO toAttachmentVo(CustomerInquiryAttachmentDO a) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setContentType(a.getContentType());
        vo.setFileSize(a.getFileSize());
        vo.setKind(a.getKind());
        vo.setInquiryItemId(a.getInquiryItemId());
        return vo;
    }

    // ---------------------------------------------------------------- 解析

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerInquiryVO startParse(Long id) {
        CustomerInquiryDO inquiry = getEditable(id);
        if (inquiry.getStatus() != InquiryConstants.STATUS_PENDING_PARSE) {
            throw new BizException("只有待解析的询盘可以发起 AI 解析");
        }
        return submitParseTask(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerInquiryVO retryParse(Long id) {
        CustomerInquiryDO inquiry = getEditable(id);
        // 待确认也可以重新解析：解析结果不对或补了附件时重来，新结果覆盖原草稿
        if (inquiry.getStatus() != InquiryConstants.STATUS_PARSE_FAILED
                && inquiry.getStatus() != InquiryConstants.STATUS_PENDING_CONFIRM) {
            throw new BizException("只有解析失败或待确认的询盘可以重新解析");
        }
        inquiry.setParseError("");
        return submitParseTask(inquiry);
    }

    /** 提交 AI 解析任务：输入见 {@link #parseInput}（图片和 PDF 以临时链接交给编排服务下载） */
    private CustomerInquiryVO submitParseTask(CustomerInquiryDO inquiry) {
        AiTaskVO task = aiTaskService.createAndSubmit(PARSE_SKILL_ID, parseInput(inquiry, inquiry.getRawContent(), 0L), currentUser.resolve());
        inquiry.setAiTaskId(task.getId());
        inquiry.setStatus(InquiryConstants.STATUS_PARSING);
        inquiryMapper.updateById(inquiry);
        return toVos(List.of(inquiry)).get(0);
    }

    /**
     * 解析输入：只取这次的内容与附件（appendId 为 0 时是录入询盘单时的）。所有附件（含 Excel、CSV）都给 AI 编排服务
     * 签发 60 分钟的临时链接（任务可能排队），带上文件名与大小，由它流式下载、预处理，放不进一次请求时分批解析
     * （OpenSpec inquiry-large-attachments D8、D9）。
     */
    private String parseInput(CustomerInquiryDO inquiry, String rawContent, long appendId) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (CustomerInquiryAttachmentDO a : attachmentRows(inquiry.getId(), appendId, InquiryConstants.ATTACHMENT_KIND_FILE)) {
            files.add(aiFileRef(a, "attachment", null));
        }
        // 铭牌图片从 1 编号，AI 按编号返回每个型号对应的图（spec inquiry/inquiry-intake「铭牌图片」）
        int no = 0;
        for (CustomerInquiryAttachmentDO a : attachmentRows(inquiry.getId(), appendId, InquiryConstants.ATTACHMENT_KIND_NAMEPLATE)) {
            files.add(aiFileRef(a, "nameplate", String.valueOf(++no)));
        }
        Map<String, Object> input = new HashMap<>();
        input.put("customerInquiryId", inquiry.getId());
        input.put("rawContent", rawContent == null ? "" : rawContent);
        input.put("attachments", files);
        input.put("categories", parseCategories());
        return writeJson(input);
    }

    /** 发给编排服务的附件：临时链接、扩展名、文件名、字节数（用于判断是否需要分批）、用途；铭牌图片带编号 */
    private Map<String, Object> aiFileRef(CustomerInquiryAttachmentDO a, String role, String no) {
        String url = fileStorage.link(a.getStorage(), a.getFileKey(), a.getFileName(), false, AI_LINK_TTL).url();
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("url", absolute(url));
        ref.put("ext", UploadRules.extOf(a.getFileKey()));
        ref.put("name", a.getFileName() == null ? "" : a.getFileName());
        if (a.getFileSize() != null) {
            ref.put("size", a.getFileSize());
        }
        ref.put("role", role);
        if (no != null) {
            ref.put("no", no);
        }
        return ref;
    }

    private String absolute(String url) {
        return url.startsWith("/") ? backendBaseUrl.replaceAll("/+$", "") + url : url;
    }

    /** 发给 AI 的品类清单：启用的一级品类按排序，每个一级后面跟着它的细分品类 */
    private List<Map<String, Object>> parseCategories() {
        Comparator<ProductCategoryDO> order = Comparator.comparing((ProductCategoryDO c) -> c.getSortOrder() == null ? 0 : c.getSortOrder())
                .thenComparing(ProductCategoryDO::getId);
        List<ProductCategoryDO> all = inquiryCategories.categories();
        List<Map<String, Object>> result = new ArrayList<>(all.size());
        for (ProductCategoryDO top : all.stream().filter(c -> c.getParentId() == null).sorted(order).toList()) {
            result.add(categoryEntry(top, null));
            all.stream().filter(c -> top.getId().equals(c.getParentId())).sorted(order)
                    .forEach(c -> result.add(categoryEntry(c, top.getCategoryCode())));
        }
        return result;
    }

    private static Map<String, Object> categoryEntry(ProductCategoryDO c, String parentCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c.getCategoryCode());
        m.put("nameZh", c.getCategoryNameZh() == null ? "" : c.getCategoryNameZh());
        m.put("name", c.getCategoryName());
        m.put("parentCode", parentCode);
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyParseSuccess(Long aiTaskId, String outputJson) {
        CustomerInquiryDO inquiry = findByAiTask(aiTaskId);
        if (inquiry == null) {
            applyAppendParse(aiTaskId, true, null);
            return;
        }
        if (inquiry.getStatus() != InquiryConstants.STATUS_PARSING) {
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
        if (inquiry == null) {
            applyAppendParse(aiTaskId, false, errorMessage);
            return;
        }
        if (inquiry.getStatus() != InquiryConstants.STATUS_PARSING) {
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
        List<AiParseOutputDTO.Unparsed> unparsed = List.of();
        // 只有待确认的 AI 解析结果才预填；待解析 / 解析失败时手动录入，从空表开始
        if (inquiry.getStatus() == InquiryConstants.STATUS_PENDING_CONFIRM
                && inquiry.getParseMode() == InquiryConstants.PARSE_MODE_AI && inquiry.getAiTaskId() != null) {
            AiParseOutputDTO output = parseOutput(aiTaskService.getById(inquiry.getAiTaskId()).getOutput());
            rows.addAll(draftRows(output, nameplates(inquiry.getId(), 0L)));
            unparsed = output.getUnparsed();
        }
        DraftVO vo = new DraftVO();
        vo.setInquiry(toVos(List.of(inquiry)).get(0));
        vo.setRawContent(inquiry.getRawContent());
        vo.setAttachments(attachments(inquiry.getId()));
        vo.setNameplates(nameplates(inquiry.getId(), 0L));
        vo.setRows(rows);
        vo.setUnparsed(unparsed == null ? List.of() : unparsed);
        return vo;
    }

    /** AI 输出转确认页的行；铭牌编号换成附件 ID，越界的忽略，一张图被多行引用时只给第一行 */
    private List<DraftRowVO> draftRows(AiParseOutputDTO output, List<AttachmentVO> plates) {
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        Set<Long> used = new HashSet<>();
        List<DraftRowVO> rows = new ArrayList<>();
        for (AiParseGroupDTO g : output.getGroups()) {
            for (AiParseItemDTO it : g.getItems()) {
                DraftRowVO row = toDraftRow(g, it, categories);
                List<Long> ids = new ArrayList<>();
                for (Integer no : it.getNameplates() == null ? List.<Integer>of() : it.getNameplates()) {
                    if (no != null && no >= 1 && no <= plates.size() && used.add(plates.get(no - 1).getId())) {
                        ids.add(plates.get(no - 1).getId());
                    }
                }
                row.setNameplateIds(ids);
                rows.add(row);
            }
        }
        return rows;
    }

    private DraftRowVO toDraftRow(AiParseGroupDTO g, AiParseItemDTO it, List<ProductCategoryDO> categories) {
        DraftRowVO row = new DraftRowVO();
        row.setBrand(g.getBrand());
        // AI 给的品类编码优先，无效时按分组的品类文字匹配
        InquiryCategories.Linked category = InquiryCategories.link(null, it.getCategoryCode(), g.getCategory(), categories);
        row.setCategory(category.text());
        row.setCategoryId(category.categoryId());
        row.setOriginalModel(it.getOriginalModel());
        row.setConfirmedModel(StringUtils.hasText(it.getConfirmedModel()) ? it.getConfirmedModel() : it.getOriginalModel());
        row.setOrderCode(ProductOrderCodes.join(ProductOrderCodes.split(it.getOrderCode())));
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
        CustomerInquiryDO inquiry = getEditable(id);
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
        Batch b = insertRows(inquiry, req.getRows(), 0);
        linkNameplates(inquiry.getId(), 0L, req.getRows(), b.ids());
        int taskCount = sourcingTaskService.createTasks(inquiry, b.pending()).size();
        inquiry.setTotalItemCount(b.count());
        inquiry.setTotalQuantity((int) Math.min(b.quantity(), Integer.MAX_VALUE));
        inquiry.setPricedItemCount(b.priced());
        inquiry.setPendingVerifyCount(b.verify());
        inquiry.setTotalOrderCount(taskCount);
        inquiry.setStatus(b.pending().isEmpty() ? InquiryConstants.STATUS_READY_TO_QUOTE : InquiryConstants.STATUS_SOURCING);
        inquiryMapper.updateById(inquiry);
        // 询价任务生成后自动建档：商品库已有的关联，没有的进商品候选，不像型号的待回填真实型号
        productArchiver.sync(b.ids());
        indexMedia(id);
    }

    /** 一次确认写入的型号：行数、总数量、复用历史价数、待核实数、待询价的型号、全部型号 ID */
    private record Batch(int count, long quantity, int priced, int verify, List<InquiryItemDO> pending, List<Long> ids) {
    }

    /** 逐行校验并写入型号明细；行号从 lineOffset + 1 开始，报错的「第 N 行」按确认页的行 */
    private Batch insertRows(CustomerInquiryDO inquiry, List<ConfirmRowRequest> rows, int lineOffset) {
        List<InquiryItemDO> pending = new ArrayList<>();
        List<Long> confirmedIds = new ArrayList<>();
        QuoteDictSnapshot.Dict lifecycles = quoteDicts.snapshot().lifecycles();
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        int line = 0;
        int priced = 0;
        int verify = 0;
        for (ConfirmRowRequest row : rows) {
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
            item.setLineNo(lineOffset + line);
            item.setBrand(row.getBrand().trim());
            item.setBrandId(brand.brandId());
            item.setBrandKey(brand.brandKey());
            InquiryCategories.Linked category = InquiryCategories.link(row.getCategoryId(), null, row.getCategory(), categories);
            item.setCategory(text(category.text()));
            item.setCategoryId(category.categoryId());
            item.setOriginalModel(StringUtils.hasText(row.getOriginalModel()) ? row.getOriginalModel().trim() : row.getConfirmedModel().trim());
            item.setConfirmedModel(row.getConfirmedModel().trim());
            item.setOrderCode(ProductOrderCodes.join(ProductOrderCodes.split(row.getOrderCode())));
            item.setModelKey(modelKey);
            item.setProductId(matchProduct(item.getBrand(), item.getConfirmedModel(), item.getBrandId(), item.getOrderCode()));
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
            confirmedIds.add(item.getId());
            if (item.getSelectedQuoteId() == null) {
                pending.add(item);
            }
        }
        long quantity = rows.stream().mapToLong(ConfirmRowRequest::getQuantity).sum();
        return new Batch(line, quantity, priced, verify, pending, confirmedIds);
    }

    /**
     * 确认时保存铭牌图片对应的型号：只认这次录入或追加（appendId）的铭牌图片，一张图只给第一个引用它的行，
     * 没被引用的为未对应。itemIds 与 rows 一一对应。
     */
    private void linkNameplates(Long inquiryId, long appendId, List<ConfirmRowRequest> rows, List<Long> itemIds) {
        List<CustomerInquiryAttachmentDO> plates = attachmentRows(inquiryId, appendId, InquiryConstants.ATTACHMENT_KIND_NAMEPLATE);
        if (plates.isEmpty()) {
            return;
        }
        Map<Long, Long> target = new HashMap<>();
        Set<Long> valid = plates.stream().map(CustomerInquiryAttachmentDO::getId).collect(Collectors.toSet());
        for (int i = 0; i < rows.size(); i++) {
            List<Long> ids = rows.get(i).getNameplateIds();
            for (Long pid : ids == null ? List.<Long>of() : ids) {
                if (pid != null && valid.contains(pid)) {
                    target.putIfAbsent(pid, itemIds.get(i));
                }
            }
        }
        for (CustomerInquiryAttachmentDO a : plates) {
            long itemId = target.getOrDefault(a.getId(), 0L);
            if (!Objects.equals(a.getInquiryItemId(), itemId)) {
                attachmentMapper.update(null, new LambdaUpdateWrapper<CustomerInquiryAttachmentDO>()
                        .eq(CustomerInquiryAttachmentDO::getId, a.getId())
                        .set(CustomerInquiryAttachmentDO::getInquiryItemId, itemId)
                        .set(CustomerInquiryAttachmentDO::getUpdateTime, LocalDateTime.now()));
            }
        }
    }

    /** 型号对上商品库优先；对不上时按订货号认（型号是建档编码，订货号辅助识别） */
    private Long matchProduct(String brand, String model, Long brandId, String orderCode) {
        try {
            ProductMatchVO match = productLookupService.match(brand, model);
            if (match != null && match.getExact() != null) {
                return match.getExact().getId();
            }
        } catch (BizException e) {
            return null;
        }
        return orderCodes.productOf(brandId, ProductOrderCodes.split(orderCode));
    }

    private static boolean reusable(SourcingQuoteDO q, int tenantId, String brandKey, String modelKey) {
        return q != null && q.getDeletedAt() == null && Objects.equals(q.getTenantId(), tenantId)
                && q.getStatus() == InquiryConstants.QUOTE_SUBMITTED && !Objects.equals(q.getNoStock(), 1) && q.getUnitPriceCny() != null
                && brandKey.equals(q.getBrandKey()) && modelKey.equals(q.getModelKey());
    }

    // ---------------------------------------------------------------- 追加型号（spec inquiry/inquiry-intake「追加型号」）

    private static final int APPEND_PARSING = 1;
    private static final int APPEND_PENDING_CONFIRM = 2;
    private static final int APPEND_PARSE_FAILED = 3;
    private static final int APPEND_CONFIRMED = 4;
    private static final int APPEND_DISCARDED = 5;
    private static final Set<Integer> APPEND_OPEN = Set.of(APPEND_PARSING, APPEND_PENDING_CONFIRM, APPEND_PARSE_FAILED);
    private static final Set<Integer> APPENDABLE = Set.of(InquiryConstants.STATUS_SOURCING, InquiryConstants.STATUS_READY_TO_QUOTE,
            InquiryConstants.STATUS_QUOTED);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppendVO startAppend(Long id, AppendRequest req) {
        CustomerInquiryDO inquiry = getEditable(id);
        progress.lock(inquiry.getId());
        inquiry = inquiryMapper.selectById(id);
        if (!APPENDABLE.contains(inquiry.getStatus())) {
            throw new BizException("只有询价中、可报价、已报价的询盘单可以追加型号");
        }
        if (openAppend(inquiry.getId()) != null) {
            throw new BizException("这张询盘单还有一次追加没有完成，请先确认或放弃");
        }
        List<AttachmentRef> refs = req.getAttachments() == null ? List.of() : req.getAttachments();
        List<AttachmentRef> plates = nameplateRefs(req.getNameplates());
        boolean manual = Boolean.TRUE.equals(req.getManual());
        if (!manual && !StringUtils.hasText(req.getRawContent()) && refs.isEmpty() && plates.isEmpty()) {
            throw new BizException("请粘贴追加的内容或上传附件");
        }
        refs = claimAttachments(fileBytes(attachmentMapper.selectList(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, inquiry.getId())
                .isNull(CustomerInquiryAttachmentDO::getDeletedAt))), refs, inquiry.getTenantId());
        CustomerInquiryAppendDO a = new CustomerInquiryAppendDO();
        a.setTenantId(inquiry.getTenantId());
        a.setCustomerInquiryId(inquiry.getId());
        a.setRawContent(StringUtils.hasText(req.getRawContent()) ? req.getRawContent().trim() : "");
        a.setParseMode(manual ? InquiryConstants.PARSE_MODE_MANUAL : InquiryConstants.PARSE_MODE_AI);
        a.setStatus(manual ? APPEND_PENDING_CONFIRM : APPEND_PARSING);
        a.setParseError("");
        a.setItemCount(0);
        appendMapper.insert(a);
        saveAttachments(inquiry, refs, a.getId(), InquiryConstants.ATTACHMENT_KIND_FILE);
        saveAttachments(inquiry, plates, a.getId(), InquiryConstants.ATTACHMENT_KIND_NAMEPLATE);
        if (!manual) {
            submitAppendParse(inquiry, a);
        }
        return toAppendVo(a);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppendVO retryAppend(Long id, Long appendId) {
        CustomerInquiryDO inquiry = getEditable(id);
        CustomerInquiryAppendDO a = requireAppend(inquiry, appendId);
        if (a.getStatus() != APPEND_PARSE_FAILED && a.getStatus() != APPEND_PENDING_CONFIRM) {
            throw new BizException("只有解析失败或待确认的追加可以重新解析");
        }
        if (!StringUtils.hasText(a.getRawContent()) && attachments(inquiry.getId(), a.getId()).isEmpty()
                && nameplates(inquiry.getId(), a.getId()).isEmpty()) {
            throw new BizException("这次追加没有内容可以解析，请手动录入");
        }
        a.setParseError("");
        a.setParseMode(InquiryConstants.PARSE_MODE_AI);
        submitAppendParse(inquiry, a);
        return toAppendVo(a);
    }

    private void submitAppendParse(CustomerInquiryDO inquiry, CustomerInquiryAppendDO a) {
        AiTaskVO task = aiTaskService.createAndSubmit(PARSE_SKILL_ID, parseInput(inquiry, a.getRawContent(), a.getId()),
                currentUser.resolve());
        a.setAiTaskId(task.getId());
        a.setStatus(APPEND_PARSING);
        appendMapper.updateById(a);
    }

    /** 解析回调：AI 任务不属于询盘单时，找追加批次 */
    private void applyAppendParse(Long aiTaskId, boolean success, String errorMessage) {
        CustomerInquiryAppendDO a = appendMapper.selectOne(new LambdaQueryWrapper<CustomerInquiryAppendDO>()
                .eq(CustomerInquiryAppendDO::getAiTaskId, aiTaskId)
                .isNull(CustomerInquiryAppendDO::getDeletedAt)
                .last("LIMIT 1"));
        if (a == null || a.getStatus() != APPEND_PARSING) {
            log.warn("AI 解析回调已无对应的解析中询盘或追加，忽略，aiTaskId={}", aiTaskId);
            return;
        }
        if (success) {
            a.setStatus(APPEND_PENDING_CONFIRM);
        } else {
            String msg = StringUtils.hasText(errorMessage) ? errorMessage : "AI 解析失败";
            a.setParseError(msg.length() > 300 ? msg.substring(0, 300) : msg);
            a.setStatus(APPEND_PARSE_FAILED);
        }
        appendMapper.updateById(a);
    }

    @Override
    public DraftVO appendDraft(Long id, Long appendId) {
        CustomerInquiryDO inquiry = getVisible(id);
        CustomerInquiryAppendDO a = requireAppend(inquiry, appendId);
        if (a.getStatus() != APPEND_PENDING_CONFIRM && a.getStatus() != APPEND_PARSE_FAILED) {
            throw new BizException(a.getStatus() == APPEND_PARSING ? "追加的内容还在解析，请稍后再试" : "这次追加已经结束");
        }
        List<DraftRowVO> rows = new ArrayList<>();
        List<AiParseOutputDTO.Unparsed> unparsed = List.of();
        if (a.getStatus() == APPEND_PENDING_CONFIRM && a.getParseMode() == InquiryConstants.PARSE_MODE_AI && a.getAiTaskId() != null) {
            AiParseOutputDTO output = parseOutput(aiTaskService.getById(a.getAiTaskId()).getOutput());
            rows.addAll(draftRows(output, nameplates(inquiry.getId(), a.getId())));
            unparsed = output.getUnparsed();
        }
        DraftVO vo = new DraftVO();
        vo.setInquiry(toVos(List.of(inquiry)).get(0));
        vo.setAppendId(a.getId());
        vo.setRawContent(a.getRawContent());
        vo.setAttachments(attachments(inquiry.getId(), a.getId()));
        vo.setNameplates(nameplates(inquiry.getId(), a.getId()));
        vo.setRows(rows);
        vo.setUnparsed(unparsed == null ? List.of() : unparsed);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void confirmAppend(Long id, Long appendId, ConfirmRequest req) {
        CustomerInquiryDO inquiry = getEditable(id);
        progress.lock(inquiry.getId());
        inquiry = inquiryMapper.selectById(id);
        CustomerInquiryAppendDO a = requireAppend(inquiry, appendId);
        if (a.getStatus() != APPEND_PENDING_CONFIRM && a.getStatus() != APPEND_PARSE_FAILED) {
            throw new BizException("这次追加不能确认，请刷新后再试");
        }
        if (!APPENDABLE.contains(inquiry.getStatus())) {
            throw new BizException("只有询价中、可报价、已报价的询盘单可以追加型号");
        }
        if (a.getStatus() == APPEND_PARSE_FAILED) {
            a.setParseMode(InquiryConstants.PARSE_MODE_MANUAL);
        }
        InquiryItemDO last = itemMapper.selectOne(new LambdaQueryWrapper<InquiryItemDO>()
                .select(InquiryItemDO::getLineNo)
                .eq(InquiryItemDO::getCustomerInquiryId, inquiry.getId())
                .orderByDesc(InquiryItemDO::getLineNo)
                .last("LIMIT 1"));
        int lineOffset = last == null || last.getLineNo() == null ? 0 : last.getLineNo();
        Batch b = insertRows(inquiry, req.getRows(), lineOffset);
        linkNameplates(inquiry.getId(), a.getId(), req.getRows(), b.ids());
        long taskIndex = taskMapper.selectCount(new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getCustomerInquiryId, inquiry.getId()));
        int taskCount = sourcingTaskService.createTasks(inquiry, b.pending(), (int) taskIndex).size();
        inquiry.setTotalItemCount(nz(inquiry.getTotalItemCount()) + b.count());
        inquiry.setTotalQuantity((int) Math.min(nz(inquiry.getTotalQuantity()) + b.quantity(), Integer.MAX_VALUE));
        inquiry.setPricedItemCount(nz(inquiry.getPricedItemCount()) + b.priced());
        inquiry.setPendingVerifyCount(nz(inquiry.getPendingVerifyCount()) + b.verify());
        inquiry.setTotalOrderCount(nz(inquiry.getTotalOrderCount()) + taskCount);
        if (!b.pending().isEmpty() || inquiry.getStatus() == InquiryConstants.STATUS_SOURCING) {
            inquiry.setStatus(InquiryConstants.STATUS_SOURCING);
        } else {
            inquiry.setStatus(InquiryConstants.STATUS_READY_TO_QUOTE);
        }
        inquiryMapper.updateById(inquiry);
        a.setStatus(APPEND_CONFIRMED);
        a.setItemCount(b.count());
        appendMapper.updateById(a);
        productArchiver.sync(b.ids());
        indexMedia(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discardAppend(Long id, Long appendId) {
        CustomerInquiryDO inquiry = getEditable(id);
        CustomerInquiryAppendDO a = requireAppend(inquiry, appendId);
        if (!APPEND_OPEN.contains(a.getStatus())) {
            throw new BizException("这次追加已经结束");
        }
        a.setStatus(APPEND_DISCARDED);
        appendMapper.updateById(a);
    }

    private CustomerInquiryAppendDO requireAppend(CustomerInquiryDO inquiry, Long appendId) {
        CustomerInquiryAppendDO a = appendId == null ? null : appendMapper.selectById(appendId);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getCustomerInquiryId(), inquiry.getId())) {
            throw new BizException("追加记录不存在");
        }
        return a;
    }

    private CustomerInquiryAppendDO openAppend(Long inquiryId) {
        return appendMapper.selectOne(new LambdaQueryWrapper<CustomerInquiryAppendDO>()
                .eq(CustomerInquiryAppendDO::getCustomerInquiryId, inquiryId)
                .in(CustomerInquiryAppendDO::getStatus, APPEND_OPEN)
                .isNull(CustomerInquiryAppendDO::getDeletedAt)
                .orderByDesc(CustomerInquiryAppendDO::getId)
                .last("LIMIT 1"));
    }

    private static AppendVO toAppendVo(CustomerInquiryAppendDO a) {
        AppendVO vo = new AppendVO();
        vo.setId(a.getId());
        vo.setStatus(a.getStatus());
        vo.setParseMode(a.getParseMode());
        vo.setParseError(a.getParseError());
        vo.setRawContent(a.getRawContent());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    // ---------------------------------------------------------------- 取消、核实、改选价格、调整等级

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLevel(Long id, Integer level) {
        CustomerInquiryDO inquiry = getEditable(id);
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
        logService.recordOperateLog("询盘单", "调整询盘等级", before,
                Map.of("inquiryCode", inquiry.getInquiryCode(), "level", labels.getOrDefault(level, "")));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UpdateCustomerInquiryRequest req) {
        CustomerInquiryDO inquiry = getEditable(id);
        if (InquiryConstants.STATUS_CANCELLED == inquiry.getStatus()) {
            throw new BizException("已取消的询盘不能修改");
        }
        if (req.getQuoteDeadline().isBefore(req.getInquiryDate())) {
            throw new BizException("报价截止不能早于询盘日期");
        }
        dictItemService.requireEnabledValue(DictTypes.SOURCE_CHANNEL, req.getSource(), "来源渠道不正确");
        boolean oppChanged = !Objects.equals(req.getOpportunityId(), inquiry.getOpportunityId());
        if (oppChanged && req.getOpportunityId() != null
                && !Objects.equals(opportunityService.customerIdOf(req.getOpportunityId()), inquiry.getCustomerId())) {
            throw new BizException("关联商机与客户不一致");
        }
        List<CustomerInquiryAttachmentDO> current = attachmentMapper.selectList(new LambdaQueryWrapper<CustomerInquiryAttachmentDO>()
                .eq(CustomerInquiryAttachmentDO::getCustomerInquiryId, id)
                .isNull(CustomerInquiryAttachmentDO::getDeletedAt));
        Set<Long> keep = req.getKeepAttachmentIds() == null ? Set.of() : Set.copyOf(req.getKeepAttachmentIds());
        List<CustomerInquiryAttachmentDO> removed = current.stream().filter(a -> !keep.contains(a.getId())).toList();
        List<AttachmentRef> added = req.getAttachments() == null ? List.of() : req.getAttachments();
        List<AttachmentRef> addedPlates = nameplateRefs(req.getNameplates());
        added = claimAttachments(fileBytes(current.stream().filter(a -> keep.contains(a.getId())).toList()), added,
                inquiry.getTenantId());
        int urgent = Boolean.TRUE.equals(req.getUrgent()) ? 1 : 0;
        String remark = req.getRemark() == null ? "" : req.getRemark().trim();

        Map<Integer, String> sources = dictItemService.intLabels(DictTypes.SOURCE_CHANNEL);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put("inquiryCode", inquiry.getInquiryCode());
        after.put("inquiryCode", inquiry.getInquiryCode());
        diff(before, after, "来源", sources.getOrDefault(inquiry.getSource(), ""), sources.getOrDefault(req.getSource(), ""));
        diff(before, after, "询盘日期", inquiry.getInquiryDate(), req.getInquiryDate());
        diff(before, after, "报价截止", inquiry.getQuoteDeadline(), req.getQuoteDeadline());
        diff(before, after, "紧急", Objects.equals(inquiry.getUrgent(), 1) ? "是" : "否", urgent == 1 ? "是" : "否");
        diff(before, after, "关联商机", opportunityCode(inquiry.getOpportunityId()), opportunityCode(req.getOpportunityId()));
        diff(before, after, "备注", inquiry.getRemark(), remark);
        List<String> removedFiles = removed.stream().filter(a -> !Objects.equals(a.getKind(), InquiryConstants.ATTACHMENT_KIND_NAMEPLATE))
                .map(CustomerInquiryAttachmentDO::getFileName).toList();
        List<String> removedPlates = removed.stream().filter(a -> Objects.equals(a.getKind(), InquiryConstants.ATTACHMENT_KIND_NAMEPLATE))
                .map(CustomerInquiryAttachmentDO::getFileName).toList();
        if (!removedFiles.isEmpty()) {
            before.put("删除附件", removedFiles);
        }
        if (!removedPlates.isEmpty()) {
            before.put("删除铭牌图片", removedPlates);
        }
        if (!added.isEmpty()) {
            after.put("补传附件", added.stream().map(AttachmentRef::getFileName).toList());
        }
        if (!addedPlates.isEmpty()) {
            after.put("补传铭牌图片", addedPlates.stream().map(AttachmentRef::getFileName).toList());
        }
        if (before.size() == 1 && after.size() == 1) {
            return;
        }

        inquiryMapper.update(null, new LambdaUpdateWrapper<CustomerInquiryDO>()
                .eq(CustomerInquiryDO::getId, id)
                .set(CustomerInquiryDO::getSource, req.getSource())
                .set(CustomerInquiryDO::getInquiryDate, req.getInquiryDate())
                .set(CustomerInquiryDO::getQuoteDeadline, req.getQuoteDeadline())
                .set(CustomerInquiryDO::getUrgent, urgent)
                .set(CustomerInquiryDO::getOpportunityId, req.getOpportunityId())
                .set(CustomerInquiryDO::getRemark, remark)
                .set(CustomerInquiryDO::getUpdateTime, LocalDateTime.now()));
        LocalDateTime now = LocalDateTime.now();
        for (CustomerInquiryAttachmentDO a : removed) {
            attachmentMapper.update(null, new LambdaUpdateWrapper<CustomerInquiryAttachmentDO>()
                    .eq(CustomerInquiryAttachmentDO::getId, a.getId())
                    .set(CustomerInquiryAttachmentDO::getDeletedAt, now)
                    .set(CustomerInquiryAttachmentDO::getUpdateTime, now));
        }
        saveAttachments(inquiry, added, 0L, InquiryConstants.ATTACHMENT_KIND_FILE);
        saveAttachments(inquiry, addedPlates, 0L, InquiryConstants.ATTACHMENT_KIND_NAMEPLATE);
        if (!removed.isEmpty() || !added.isEmpty() || !addedPlates.isEmpty()) {
            indexMedia(id);
        }
        if (!Objects.equals(inquiry.getUrgent(), urgent)) {
            sourcingTaskService.syncUrgent(id, urgent);
        }
        if (oppChanged && req.getOpportunityId() != null) {
            opportunityService.onInquiryCreated(req.getOpportunityId(), inquiry.getInquiryCode());
        }
        logService.recordOperateLog("询盘单", "修改询盘", before, after);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkNameplate(Long id, Long attachmentId, Long inquiryItemId) {
        CustomerInquiryDO inquiry = getEditable(id);
        if (InquiryConstants.STATUS_CANCELLED == inquiry.getStatus()) {
            throw new BizException("已取消的询盘不能修改");
        }
        CustomerInquiryAttachmentDO a = attachmentId == null ? null : attachmentMapper.selectById(attachmentId);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getCustomerInquiryId(), id)
                || !Objects.equals(a.getKind(), InquiryConstants.ATTACHMENT_KIND_NAMEPLATE)) {
            throw new BizException("铭牌图片不存在");
        }
        long target = inquiryItemId == null ? 0L : inquiryItemId;
        InquiryItemDO item = null;
        if (target != 0) {
            item = itemMapper.selectById(target);
            if (item == null || item.getDeletedAt() != null || !Objects.equals(item.getCustomerInquiryId(), id)) {
                throw new BizException("型号不属于这张询盘单");
            }
        }
        if (Objects.equals(a.getInquiryItemId(), target)) {
            return;
        }
        InquiryItemDO from = a.getInquiryItemId() == null || a.getInquiryItemId() == 0 ? null : itemMapper.selectById(a.getInquiryItemId());
        attachmentMapper.update(null, new LambdaUpdateWrapper<CustomerInquiryAttachmentDO>()
                .eq(CustomerInquiryAttachmentDO::getId, a.getId())
                .set(CustomerInquiryAttachmentDO::getInquiryItemId, target)
                .set(CustomerInquiryAttachmentDO::getUpdateTime, LocalDateTime.now()));
        logService.recordOperateLog("询盘单", "改铭牌图片对应型号",
                Map.of("inquiryCode", inquiry.getInquiryCode(), "铭牌图片", a.getFileName(), "对应型号", lineLabel(from)),
                Map.of("inquiryCode", inquiry.getInquiryCode(), "铭牌图片", a.getFileName(), "对应型号", lineLabel(item)));
        indexMedia(id);
    }

    private static String lineLabel(InquiryItemDO item) {
        return item == null ? "未对应型号" : item.getLineNo() + " · " + item.getConfirmedModel();
    }

    /** 只把改了的字段记进操作日志 */
    private static void diff(Map<String, Object> before, Map<String, Object> after, String field, Object from, Object to) {
        String a = from == null ? "" : String.valueOf(from);
        String b = to == null ? "" : String.valueOf(to);
        if (!a.equals(b)) {
            before.put(field, a);
            after.put(field, b);
        }
    }

    private String opportunityCode(Long opportunityId) {
        OpportunityVO opp = opportunityId == null ? null : opportunityService.findVisible(opportunityId);
        return opp == null ? "" : opp.getOpportunityCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void cancel(Long id) {
        CustomerInquiryDO inquiry = getEditable(id);
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
    public List<OpportunityDetailVO> opportunityOptions(Long customerId) {
        return opportunityService.activeOfCustomer(customerId);
    }

    @Override
    public List<InquiryDownstreamVO> downstream(Long id) {
        return inquiryMapper.selectDownstream(tenantId(), getVisible(id).getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CustomerInquiryDO inquiry = getEditable(id);
        progress.lock(id);
        if (!inquiryMapper.selectDownstream(tenantId(), id).isEmpty()) {
            throw new BizException("这条询盘已有询价任务、报价单、PI 或销售订单引用，不能删除");
        }
        LocalDateTime now = LocalDateTime.now();
        CustomerInquiryDO patch = new CustomerInquiryDO();
        patch.setId(id);
        patch.setDeletedAt(now);
        inquiryMapper.updateById(patch);
        SourcingTaskDO task = new SourcingTaskDO();
        task.setDeletedAt(now);
        taskMapper.update(task, new LambdaQueryWrapper<SourcingTaskDO>()
                .eq(SourcingTaskDO::getTenantId, tenantId())
                .eq(SourcingTaskDO::getCustomerInquiryId, id)
                .isNull(SourcingTaskDO::getDeletedAt));
        logService.recordOperateLog("询盘单", "删除询盘", Map.of("inquiryCode", inquiry.getInquiryCode(),
                "customerId", String.valueOf(inquiry.getCustomerId())), null);
    }

    @Override
    public void markReviewed(Long id) {
        CustomerInquiryDO inquiry = getEditable(id);
        inquiry.setNeedsReview(0);
        inquiryMapper.updateById(inquiry);
    }

    // ---------------------------------------------------------------- 工具

    @Override
    public List<InquiryModelLineVO> models(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        return modelLines(List.of(inquiry)).getOrDefault(inquiry.getId(), List.of());
    }

    @Override
    public PageResult<InquiryModelLineVO> modelsPage(Long id, int page, int pageSize) {
        CustomerInquiryDO inquiry = getVisible(id);
        int size = Math.min(Math.max(pageSize, 1), 100);
        int current = Math.max(page, 1);
        int st = inquiry.getStatus() == null ? 0 : inquiry.getStatus();
        if (st <= InquiryConstants.STATUS_PARSE_FAILED) {
            List<InquiryModelLineVO> all = modelLines(List.of(inquiry)).getOrDefault(inquiry.getId(), List.of());
            int from = Math.min((current - 1) * size, all.size());
            return PageResult.of((long) all.size(), all.subList(from, Math.min(from + size, all.size())));
        }
        Page<InquiryItemDO> rows = itemMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<InquiryItemDO>()
                .select(InquiryItemDO::getCustomerInquiryId, InquiryItemDO::getBrand, InquiryItemDO::getConfirmedModel,
                        InquiryItemDO::getOriginalModel, InquiryItemDO::getCategoryId, InquiryItemDO::getCategory,
                        InquiryItemDO::getQuantity, InquiryItemDO::getUnit)
                .eq(InquiryItemDO::getCustomerInquiryId, inquiry.getId())
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getLineNo)
                .orderByAsc(InquiryItemDO::getId));
        List<ProductCategoryDO> categories = inquiryCategories.categories();
        return PageResult.of(rows.getTotal(), rows.getRecords().stream().map(r -> modelLine(r.getBrand(), r.getConfirmedModel(),
                r.getOriginalModel(), rootCategory(r.getCategoryId(), r.getCategory(), categories), r.getQuantity(), r.getUnit()))
                .toList());
    }

    /**
     * 各询盘的型号行：待确认的取 AI 解析结果，已确认的取型号明细；未解析、解析中、解析失败的没有型号。
     * 品类统一换成一级品类。
     */
    private Map<Long, List<InquiryModelLineVO>> modelLines(List<CustomerInquiryDO> inquiries) {
        Map<Long, List<InquiryModelLineVO>> out = new HashMap<>();
        List<ProductCategoryDO> categories = null;
        List<Long> confirmed = new ArrayList<>();
        for (CustomerInquiryDO i : inquiries) {
            int st = i.getStatus() == null ? 0 : i.getStatus();
            if (st == InquiryConstants.STATUS_PENDING_CONFIRM) {
                if (i.getParseMode() == null || i.getParseMode() != InquiryConstants.PARSE_MODE_AI || i.getAiTaskId() == null) {
                    continue;
                }
                if (categories == null) {
                    categories = inquiryCategories.categories();
                }
                var task = aiTaskService.getById(i.getAiTaskId());
                AiParseOutputDTO output;
                try {
                    output = parseOutput(task == null ? null : task.getOutput());
                } catch (BizException e) {
                    // AI 结果格式异常时列表照常显示，只是没有型号摘要
                    log.warn("询盘 AI 解析结果无法读取，inquiryId={}", i.getId());
                    continue;
                }
                List<InquiryModelLineVO> lines = new ArrayList<>();
                for (AiParseGroupDTO g : output.getGroups()) {
                    for (AiParseItemDTO it : g.getItems()) {
                        DraftRowVO row = toDraftRow(g, it, categories);
                        lines.add(modelLine(row.getBrand(), row.getConfirmedModel(), row.getOriginalModel(),
                                rootCategory(row.getCategoryId(), row.getCategory(), categories), row.getQuantity(), row.getUnit()));
                    }
                }
                out.put(i.getId(), lines);
            } else if (st > InquiryConstants.STATUS_PARSE_FAILED) {
                confirmed.add(i.getId());
            }
        }
        if (!confirmed.isEmpty()) {
            if (categories == null) {
                categories = inquiryCategories.categories();
            }
            List<InquiryItemDO> rows = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                    .select(InquiryItemDO::getCustomerInquiryId, InquiryItemDO::getBrand, InquiryItemDO::getConfirmedModel,
                            InquiryItemDO::getOriginalModel, InquiryItemDO::getCategoryId, InquiryItemDO::getCategory,
                            InquiryItemDO::getQuantity, InquiryItemDO::getUnit)
                    .in(InquiryItemDO::getCustomerInquiryId, confirmed)
                    .isNull(InquiryItemDO::getDeletedAt)
                    .orderByAsc(InquiryItemDO::getLineNo)
                    .orderByAsc(InquiryItemDO::getId));
            for (InquiryItemDO r : rows) {
                out.computeIfAbsent(r.getCustomerInquiryId(), k -> new ArrayList<>()).add(modelLine(r.getBrand(),
                        r.getConfirmedModel(), r.getOriginalModel(), rootCategory(r.getCategoryId(), r.getCategory(), categories),
                        r.getQuantity(), r.getUnit()));
            }
        }
        return out;
    }

    private static InquiryModelLineVO modelLine(String brand, String confirmedModel, String originalModel, String category,
                                                Integer quantity, String unit) {
        InquiryModelLineVO l = new InquiryModelLineVO();
        l.setBrand(brand == null ? "" : brand.trim());
        l.setModel(StringUtils.hasText(confirmedModel) ? confirmedModel.trim() : originalModel == null ? "" : originalModel.trim());
        l.setCategory(category);
        l.setQuantity(quantity);
        l.setUnit(unit);
        return l;
    }

    /** 一级品类名；关联不上品类库时用原文 */
    private static String rootCategory(Long categoryId, String text, List<ProductCategoryDO> categories) {
        ProductCategoryDO root = InquiryCategories.root(categoryId, categories);
        return root != null ? InquiryCategories.name(root) : text == null ? "" : text.trim();
    }

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
            throw new BizException("询盘单不存在");
        }
        return inquiry;
    }

    /** 修改类入口取询盘单：历史迁移的询盘单只读 */
    private CustomerInquiryDO getEditable(Long id) {
        CustomerInquiryDO inquiry = getVisible(id);
        MigratedGuard.requireEditable(inquiry.getMigrated());
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
            // 同一型号出现多次时合并数量；询盘统计、确认页、追加确认都读这里，结果一致
            return ParsedItemMerger.merge(objectMapper.readValue(json, AiParseOutputDTO.class));
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

    /** 询盘的图片附件进素材库「客户来图」：对应了型号的铭牌图片只标那个型号，其余标注询盘单当前的全部型号 */
    private void indexMedia(Long inquiryId) {
        CustomerInquiryDO inquiry = inquiryMapper.selectById(inquiryId);
        Map<Long, MediaIndexer.ModelRef> byItem = new LinkedHashMap<>();
        for (InquiryItemDO i : itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getCustomerInquiryId, inquiryId)
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getLineNo))) {
            byItem.put(i.getId(), new MediaIndexer.ModelRef(i.getBrand(),
                    StringUtils.hasText(i.getConfirmedModel()) ? i.getConfirmedModel() : i.getOriginalModel()));
        }
        media.indexInquiry(inquiryId, inquiry.getTenantId(), byItem);
    }
}
