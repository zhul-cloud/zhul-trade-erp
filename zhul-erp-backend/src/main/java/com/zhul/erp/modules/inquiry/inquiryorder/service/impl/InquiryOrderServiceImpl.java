package com.zhul.erp.modules.inquiry.inquiryorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.ChannelPlatform;
import com.zhul.erp.modules.inquiry.constants.ConfidenceLevel;
import com.zhul.erp.modules.inquiry.constants.InquirySourceType;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.constants.InquiryOrderStatus;
import com.zhul.erp.modules.inquiry.inquiryorder.constants.SupplierAssociationStatus;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AddInquiryOrderSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AdvanceInquiryOrderStatusRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AssignPurchaserRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.ConvertToSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.CreateInquiryOrderManualRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderItemVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderPageQuery;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderSupplierVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryTemplateVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.ManualItemRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteComparisonCellVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteComparisonRowVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteComparisonVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.RecordQuoteRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderDO;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderItemDO;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderItemQuoteDO;
import com.zhul.erp.modules.inquiry.inquiryorder.entity.InquiryOrderSupplierDO;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderItemMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderItemQuoteMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.repository.InquiryOrderSupplierMapper;
import com.zhul.erp.modules.inquiry.inquiryorder.service.InquiryOrderService;
import com.zhul.erp.modules.inquiry.support.ConfirmedGroupDTO;
import com.zhul.erp.modules.inquiry.support.ConfirmedItemDTO;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.GeneratedInquiryCode;
import com.zhul.erp.modules.inquiry.support.InquiryCodeGenerator;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class InquiryOrderServiceImpl implements InquiryOrderService {

    private static final int MAX_CODE_INSERT_ATTEMPTS = 3;

    private final InquiryOrderMapper inquiryOrderMapper;
    private final InquiryOrderItemMapper inquiryOrderItemMapper;
    private final InquiryOrderSupplierMapper inquiryOrderSupplierMapper;
    private final InquiryOrderItemQuoteMapper inquiryOrderItemQuoteMapper;
    private final InquiryCodeGenerator codeGenerator;
    private final CurrentUserResolver currentUserResolver;
    private final SupplierService supplierService;
    private final CustomerInquiryMapper customerInquiryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InquiryOrderVO> createFromConfirmedGroups(Long customerInquiryId, String customerInquiryCode,
                                                            Long aiTaskId, List<ConfirmedGroupDTO> groups) {
        int tenantId = currentTenantId();
        List<InquiryOrderVO> result = new ArrayList<>();
        for (ConfirmedGroupDTO group : groups) {
            if (group.getItems() == null || group.getItems().isEmpty()) {
                continue; // 空分组随确认动作一并丢弃，不生成询盘单
            }
            InquiryOrderDO order = new InquiryOrderDO();
            order.setTenantId(tenantId);
            order.setCustomerInquiryId(customerInquiryId);
            order.setBrand(group.getBrand());
            order.setCategory(group.getCategory());
            order.setItemCount(group.getItems().size());
            order.setStatus(InquiryOrderStatus.PENDING_ASSIGN);
            order.setInquiryTemplate(group.getInquiryTemplate());
            order.setEmailTemplateCn(group.getEmailTemplateCn());
            order.setEmailTemplateEn(group.getEmailTemplateEn());
            order.setAiTaskId(aiTaskId);
            insertOrderWithRetry(order, () -> codeGenerator.nextInquiryOrderCodeForParent(tenantId, customerInquiryId, customerInquiryCode));

            for (ConfirmedItemDTO item : group.getItems()) {
                InquiryOrderItemDO itemDo = new InquiryOrderItemDO();
                itemDo.setTenantId(tenantId);
                itemDo.setItemCode(codeGenerator.nextItemCode(tenantId, order.getId(), order.getInquiryCode()));
                itemDo.setInquiryOrderId(order.getId());
                itemDo.setBrand(group.getBrand());
                itemDo.setCategory(group.getCategory());
                itemDo.setOriginalModel(item.getOriginalModel());
                itemDo.setConfirmedModel(item.getConfirmedModel());
                itemDo.setConfidence(item.getConfidence() != null ? item.getConfidence() : ConfidenceLevel.CONFIRMED);
                itemDo.setCorrectionNote(item.getCorrectionNote());
                itemDo.setDescription(item.getDescription());
                itemDo.setQuantity(item.getQuantity());
                itemDo.setUnit(item.getUnit());
                itemDo.setDeliveryRequirement(item.getDeliveryRequirement());
                itemDo.setRemark(item.getRemark());
                inquiryOrderItemMapper.insert(itemDo);
            }
            result.add(toVo(order));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InquiryOrderVO createManual(CreateInquiryOrderManualRequest req) {
        int tenantId = currentTenantId();
        InquiryOrderDO order = new InquiryOrderDO();
        order.setTenantId(tenantId);
        order.setBrand(req.getBrand());
        order.setCategory(req.getCategory());
        order.setItemCount(req.getItems().size());
        order.setStatus(InquiryOrderStatus.PENDING_ASSIGN);
        order.setAiTaskId(null);

        Supplier<GeneratedInquiryCode> codeSupplier;
        if (req.getCustomerInquiryId() != null) {
            // 入口B：关联一条已经真实存在、只是AI解析失败了的 customer_inquiry
            // （不是新建空壳记录，与 design.md 决策12否决的方案不是一回事），
            // customerId 与 customerInquiryId 互斥，此时忽略 customerId。
            CustomerInquiryDO parent = customerInquiryMapper.selectById(req.getCustomerInquiryId());
            if (parent == null || parent.getDeletedAt() != null) {
                throw new BizException("关联的客户询盘不存在");
            }
            order.setCustomerInquiryId(parent.getId());
            order.setCustomerId(null);
            codeSupplier = () -> codeGenerator.nextInquiryOrderCodeForParent(tenantId, parent.getId(), parent.getInquiryCode());
        } else {
            // 入口A：客户可选，直接写 customer_id，不关联/不创建任何 customer_inquiry。
            order.setCustomerInquiryId(null);
            order.setCustomerId(req.getCustomerId());
            codeSupplier = () -> codeGenerator.nextStandaloneInquiryOrderCode(tenantId);
        }
        insertOrderWithRetry(order, codeSupplier);

        for (ManualItemRequest item : req.getItems()) {
            InquiryOrderItemDO itemDo = new InquiryOrderItemDO();
            itemDo.setTenantId(tenantId);
            itemDo.setItemCode(codeGenerator.nextItemCode(tenantId, order.getId(), order.getInquiryCode()));
            itemDo.setInquiryOrderId(order.getId());
            itemDo.setBrand(req.getBrand());
            itemDo.setCategory(req.getCategory());
            itemDo.setOriginalModel(item.getModel());
            itemDo.setConfirmedModel(item.getModel());
            itemDo.setConfidence(ConfidenceLevel.CONFIRMED);
            itemDo.setQuantity(item.getQuantity());
            itemDo.setUnit(item.getUnit());
            itemDo.setRemark(item.getRemark());
            inquiryOrderItemMapper.insert(itemDo);
        }
        return toVo(order);
    }

    @Override
    public PageResult<InquiryOrderVO> page(InquiryOrderPageQuery query) {
        LambdaQueryWrapper<InquiryOrderDO> wrapper = new LambdaQueryWrapper<InquiryOrderDO>()
                .eq(InquiryOrderDO::getTenantId, currentTenantId())
                .isNull(InquiryOrderDO::getDeletedAt);
        if (StringUtils.hasText(query.getInquiryCode())) {
            wrapper.like(InquiryOrderDO::getInquiryCode, query.getInquiryCode());
        }
        if (StringUtils.hasText(query.getBrand())) {
            wrapper.like(InquiryOrderDO::getBrand, query.getBrand());
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(InquiryOrderDO::getCategory, query.getCategory());
        }
        if (query.getStatusList() != null && !query.getStatusList().isEmpty()) {
            wrapper.in(InquiryOrderDO::getStatus, query.getStatusList());
        }
        if (query.getManualOnly() != null) {
            if (query.getManualOnly()) {
                wrapper.isNull(InquiryOrderDO::getCustomerInquiryId);
            } else {
                wrapper.isNotNull(InquiryOrderDO::getCustomerInquiryId);
            }
        }
        if (Boolean.TRUE.equals(query.getMine())) {
            Long currentUserId = currentUserResolver.resolve();
            wrapper.eq(InquiryOrderDO::getAssigneeId, currentUserId)
                    .notIn(InquiryOrderDO::getStatus, List.of(InquiryOrderStatus.DEAL, InquiryOrderStatus.CANCELLED));
        } else if (query.getAssigneeId() != null) {
            wrapper.eq(InquiryOrderDO::getAssigneeId, query.getAssigneeId());
        }
        wrapper.orderByDesc(InquiryOrderDO::getUpdateTime);

        Page<InquiryOrderDO> pageParam = new Page<>(query.getPage(), query.getPageSize());
        Page<InquiryOrderDO> pageResult = inquiryOrderMapper.selectPage(pageParam, wrapper);
        List<InquiryOrderVO> records = new ArrayList<>(pageResult.getRecords().size());
        for (InquiryOrderDO order : pageResult.getRecords()) {
            records.add(toVo(order));
        }
        return PageResult.of(pageResult.getTotal(), records);
    }

    @Override
    public InquiryOrderVO getById(Long id) {
        return toVo(getOrThrow(id));
    }

    @Override
    public List<InquiryOrderVO> listByCustomerInquiryId(Long customerInquiryId) {
        List<InquiryOrderDO> list = inquiryOrderMapper.selectList(new LambdaQueryWrapper<InquiryOrderDO>()
                .eq(InquiryOrderDO::getTenantId, currentTenantId())
                .eq(InquiryOrderDO::getCustomerInquiryId, customerInquiryId)
                .isNull(InquiryOrderDO::getDeletedAt)
                .orderByAsc(InquiryOrderDO::getInquiryCode));
        List<InquiryOrderVO> result = new ArrayList<>(list.size());
        for (InquiryOrderDO order : list) {
            result.add(toVo(order));
        }
        return result;
    }

    @Override
    public List<InquiryOrderItemVO> listItems(Long inquiryOrderId) {
        List<InquiryOrderItemDO> list = inquiryOrderItemMapper.selectList(new LambdaQueryWrapper<InquiryOrderItemDO>()
                .eq(InquiryOrderItemDO::getTenantId, currentTenantId())
                .eq(InquiryOrderItemDO::getInquiryOrderId, inquiryOrderId)
                .isNull(InquiryOrderItemDO::getDeletedAt)
                .orderByAsc(InquiryOrderItemDO::getItemCode));
        List<InquiryOrderItemVO> result = new ArrayList<>(list.size());
        for (InquiryOrderItemDO item : list) {
            result.add(toItemVo(item));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, AssignPurchaserRequest req) {
        InquiryOrderDO order = getOrThrow(id);
        order.setAssigneeId(req.getAssigneeId());
        if (order.getStatus() != null && order.getStatus() == InquiryOrderStatus.PENDING_ASSIGN) {
            order.setStatus(InquiryOrderStatus.ASSIGNED);
        }
        inquiryOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InquiryOrderSupplierVO addSupplier(Long inquiryOrderId, AddInquiryOrderSupplierRequest req) {
        validateSourceTypeFields(req);
        InquiryOrderSupplierDO supplier = new InquiryOrderSupplierDO();
        supplier.setTenantId(currentTenantId());
        supplier.setInquiryOrderId(inquiryOrderId);
        supplier.setSourceType(req.getSourceType());
        supplier.setSupplierId(req.getSupplierId());
        supplier.setChannelPlatform(req.getChannelPlatform());
        supplier.setChannelName(req.getChannelName());
        supplier.setChannelLink(req.getChannelLink());
        supplier.setSentDate(req.getSentDate());
        supplier.setReplyDeadline(req.getReplyDeadline());
        supplier.setStatus(SupplierAssociationStatus.PENDING_SEND);
        inquiryOrderSupplierMapper.insert(supplier);
        return toSupplierVo(supplier);
    }

    @Override
    public List<InquiryOrderSupplierVO> listSuppliers(Long inquiryOrderId) {
        List<InquiryOrderSupplierDO> list = inquiryOrderSupplierMapper.selectList(new LambdaQueryWrapper<InquiryOrderSupplierDO>()
                .eq(InquiryOrderSupplierDO::getTenantId, currentTenantId())
                .eq(InquiryOrderSupplierDO::getInquiryOrderId, inquiryOrderId)
                .isNull(InquiryOrderSupplierDO::getDeletedAt)
                .orderByAsc(InquiryOrderSupplierDO::getCreateTime));
        List<InquiryOrderSupplierVO> result = new ArrayList<>(list.size());
        for (InquiryOrderSupplierDO d : list) {
            result.add(toSupplierVo(d));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InquiryOrderSupplierVO convertToFormalSupplier(Long inquiryOrderSupplierId, ConvertToSupplierRequest req) {
        InquiryOrderSupplierDO assoc = inquiryOrderSupplierMapper.selectById(inquiryOrderSupplierId);
        if (assoc == null || assoc.getDeletedAt() != null) {
            throw new BizException("询盘单供应商关联不存在");
        }
        if (assoc.getSourceType() == null || assoc.getSourceType() != InquirySourceType.ECOMMERCE_CHANNEL) {
            throw new BizException("只有电商询价渠道来源才能转为正式供应商");
        }

        Long newSupplierId;
        if (req.getLinkExistingSupplierId() != null) {
            newSupplierId = req.getLinkExistingSupplierId();
        } else {
            CreateSupplierFromChannelRequest channelReq = new CreateSupplierFromChannelRequest();
            channelReq.setChannelName(assoc.getChannelName());
            channelReq.setForce(req.isForce());
            SupplierCreateResultVO result = supplierService.createFromChannel(channelReq);
            if (result.isDuplicate()) {
                throw new BizException("该名称已存在（供应商ID=" + result.getExistingSupplier().getId()
                        + "），如需使用该供应商请重新提交并设置 linkExistingSupplierId，或设置 force=true 强制新建同名供应商");
            }
            newSupplierId = result.getCreatedSupplier().getId();
        }

        assoc.setSourceType(InquirySourceType.FORMAL_SUPPLIER);
        assoc.setSupplierId(newSupplierId);
        inquiryOrderSupplierMapper.updateById(assoc);
        return toSupplierVo(assoc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuoteVO recordQuote(Long inquiryOrderId, RecordQuoteRequest req) {
        InquiryOrderItemQuoteDO existing = inquiryOrderItemQuoteMapper.selectOne(new LambdaQueryWrapper<InquiryOrderItemQuoteDO>()
                .eq(InquiryOrderItemQuoteDO::getTenantId, currentTenantId())
                .eq(InquiryOrderItemQuoteDO::getInquiryOrderItemId, req.getInquiryOrderItemId())
                .eq(InquiryOrderItemQuoteDO::getInquiryOrderSupplierId, req.getInquiryOrderSupplierId()));

        InquiryOrderItemQuoteDO quote = existing != null ? existing : new InquiryOrderItemQuoteDO();
        quote.setTenantId(currentTenantId());
        quote.setInquiryOrderItemId(req.getInquiryOrderItemId());
        quote.setInquiryOrderSupplierId(req.getInquiryOrderSupplierId());
        quote.setQuotePriceOriginal(req.getQuotePriceOriginal());
        quote.setCurrencyCode(req.getCurrencyCode());
        quote.setExchangeRate(req.getExchangeRate());
        quote.setQuotePriceCny(calculateCnyAmount(req.getQuotePriceOriginal(), req.getExchangeRate()));
        quote.setSupplierDelivery(req.getSupplierDelivery());
        quote.setQuoteStatus(req.getQuoteStatus());

        if (existing != null) {
            inquiryOrderItemQuoteMapper.updateById(quote);
        } else {
            inquiryOrderItemQuoteMapper.insert(quote);
        }
        return toQuoteVo(quote);
    }

    @Override
    public QuoteComparisonVO getQuoteComparison(Long inquiryOrderId) {
        List<InquiryOrderSupplierVO> suppliers = listSuppliers(inquiryOrderId);
        List<InquiryOrderItemVO> items = listItems(inquiryOrderId);
        if (items.isEmpty()) {
            QuoteComparisonVO empty = new QuoteComparisonVO();
            empty.setColumns(suppliers);
            return empty;
        }

        List<Long> itemIds = new ArrayList<>(items.size());
        for (InquiryOrderItemVO item : items) {
            itemIds.add(item.getId());
        }
        List<InquiryOrderItemQuoteDO> quotes = inquiryOrderItemQuoteMapper.selectList(
                new LambdaQueryWrapper<InquiryOrderItemQuoteDO>()
                        .eq(InquiryOrderItemQuoteDO::getTenantId, currentTenantId())
                        .in(InquiryOrderItemQuoteDO::getInquiryOrderItemId, itemIds));

        Map<Long, Map<Long, InquiryOrderItemQuoteDO>> byItemThenSupplier = new HashMap<>();
        for (InquiryOrderItemQuoteDO q : quotes) {
            byItemThenSupplier.computeIfAbsent(q.getInquiryOrderItemId(), k -> new HashMap<>())
                    .put(q.getInquiryOrderSupplierId(), q);
        }

        QuoteComparisonVO result = new QuoteComparisonVO();
        result.setColumns(suppliers);
        for (InquiryOrderItemVO item : items) {
            QuoteComparisonRowVO row = new QuoteComparisonRowVO();
            row.setInquiryOrderItemId(item.getId());
            row.setConfirmedModel(item.getConfirmedModel());

            Map<Long, InquiryOrderItemQuoteDO> quotesForItem = byItemThenSupplier.getOrDefault(item.getId(), Map.of());
            BigDecimal lowest = null;
            Long lowestSupplierId = null;
            for (InquiryOrderSupplierVO supplier : suppliers) {
                InquiryOrderItemQuoteDO q = quotesForItem.get(supplier.getId());
                QuoteComparisonCellVO cell = new QuoteComparisonCellVO();
                cell.setInquiryOrderSupplierId(supplier.getId());
                if (q != null && q.getQuotePriceCny() != null) {
                    cell.setQuoted(true);
                    cell.setQuotePriceCny(q.getQuotePriceCny());
                    cell.setQuotePriceOriginal(q.getQuotePriceOriginal());
                    cell.setCurrencyCode(q.getCurrencyCode());
                    cell.setSupplierDelivery(q.getSupplierDelivery());
                    if (lowest == null || q.getQuotePriceCny().compareTo(lowest) < 0) {
                        lowest = q.getQuotePriceCny();
                        lowestSupplierId = supplier.getId();
                    }
                } else {
                    cell.setQuoted(false);
                }
                row.getCellsBySupplierId().put(supplier.getId(), cell);
            }
            if (lowestSupplierId != null) {
                row.getCellsBySupplierId().get(lowestSupplierId).setLowestPrice(true);
            }
            result.getRows().add(row);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceStatus(Long id, AdvanceInquiryOrderStatusRequest req) {
        InquiryOrderDO order = getOrThrow(id);
        int current = order.getStatus();
        int target = req.getTargetStatus();

        if (target == InquiryOrderStatus.CANCELLED) {
            if (current == InquiryOrderStatus.DEAL || current == InquiryOrderStatus.CANCELLED
                    || current == InquiryOrderStatus.PENDING_ASSIGN) {
                throw new BizException("当前状态不允许取消");
            }
        } else {
            int[] seq = InquiryOrderStatus.MANUAL_ADVANCE_SEQUENCE;
            int currentIdx = indexOf(seq, current);
            int targetIdx = indexOf(seq, target);
            if (currentIdx < 0 || targetIdx != currentIdx + 1) {
                throw new BizException("非法的状态流转：不能从当前状态直接跳转到目标状态");
            }
        }
        order.setStatus(target);
        inquiryOrderMapper.updateById(order);
    }

    @Override
    public InquiryTemplateVO getTemplates(Long inquiryOrderId) {
        InquiryOrderDO order = getOrThrow(inquiryOrderId);
        InquiryTemplateVO vo = new InquiryTemplateVO();
        vo.setInquiryTemplate(order.getInquiryTemplate());
        vo.setEmailTemplateCn(order.getEmailTemplateCn());
        vo.setEmailTemplateEn(order.getEmailTemplateEn());
        return vo;
    }

    // ------------------------------------------------------------------

    private void insertOrderWithRetry(InquiryOrderDO order, Supplier<GeneratedInquiryCode> codeSupplier) {
        for (int attempt = 1; attempt <= MAX_CODE_INSERT_ATTEMPTS; attempt++) {
            GeneratedInquiryCode generated = codeSupplier.get();
            order.setId(null);
            order.setInquiryCode(generated.getCode());
            if (generated.isFallbackUsed() && !StringUtils.hasText(order.getRemark())) {
                order.setRemark("编号生成异常，待人工核实");
            }
            try {
                inquiryOrderMapper.insert(order);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == MAX_CODE_INSERT_ATTEMPTS) {
                    throw new BizException("询盘单编号生成冲突，请重试");
                }
            }
        }
    }

    private void validateSourceTypeFields(AddInquiryOrderSupplierRequest req) {
        if (req.getSourceType() == null) {
            throw new BizException("报价来源类型不能为空");
        }
        if (req.getSourceType() == InquirySourceType.FORMAL_SUPPLIER) {
            if (req.getSupplierId() == null) {
                throw new BizException("正式供应商必须选择供应商");
            }
        } else if (req.getSourceType() == InquirySourceType.ECOMMERCE_CHANNEL) {
            if (req.getChannelPlatform() == null) {
                throw new BizException("电商询价渠道必须选择平台");
            }
            if (!StringUtils.hasText(req.getChannelName())) {
                throw new BizException("电商询价渠道必须填写店铺/卖家名称");
            }
        } else {
            throw new BizException("未知的报价来源类型");
        }
    }

    private InquiryOrderDO getOrThrow(Long id) {
        InquiryOrderDO order = inquiryOrderMapper.selectById(id);
        if (order == null || order.getDeletedAt() != null) {
            throw new BizException("询盘单不存在");
        }
        return order;
    }

    private static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static BigDecimal calculateCnyAmount(BigDecimal original, BigDecimal rate) {
        if (original == null || rate == null) {
            return null;
        }
        return original.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private int currentTenantId() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : 0;
    }

    private InquiryOrderVO toVo(InquiryOrderDO order) {
        InquiryOrderVO vo = new InquiryOrderVO();
        vo.setId(order.getId());
        vo.setInquiryCode(order.getInquiryCode());
        vo.setCustomerInquiryId(order.getCustomerInquiryId());
        vo.setCustomerId(order.getCustomerId());
        vo.setBrand(order.getBrand());
        vo.setCategory(order.getCategory());
        vo.setItemCount(order.getItemCount());
        vo.setAssigneeId(order.getAssigneeId());
        vo.setStatus(order.getStatus());
        vo.setInquiryTemplate(order.getInquiryTemplate());
        vo.setEmailTemplateCn(order.getEmailTemplateCn());
        vo.setEmailTemplateEn(order.getEmailTemplateEn());
        vo.setAiTaskId(order.getAiTaskId());
        vo.setRemark(order.getRemark());
        vo.setCreateBy(order.getCreateBy());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateBy(order.getUpdateBy());
        vo.setUpdateTime(order.getUpdateTime());
        return vo;
    }

    private InquiryOrderItemVO toItemVo(InquiryOrderItemDO item) {
        InquiryOrderItemVO vo = new InquiryOrderItemVO();
        vo.setId(item.getId());
        vo.setItemCode(item.getItemCode());
        vo.setInquiryOrderId(item.getInquiryOrderId());
        vo.setBrand(item.getBrand());
        vo.setCategory(item.getCategory());
        vo.setOriginalModel(item.getOriginalModel());
        vo.setConfirmedModel(item.getConfirmedModel());
        vo.setConfidence(item.getConfidence());
        vo.setCorrectionNote(item.getCorrectionNote());
        vo.setDescription(item.getDescription());
        vo.setQuantity(item.getQuantity());
        vo.setUnit(item.getUnit());
        vo.setDeliveryRequirement(item.getDeliveryRequirement());
        vo.setRemark(item.getRemark());
        return vo;
    }

    private InquiryOrderSupplierVO toSupplierVo(InquiryOrderSupplierDO d) {
        InquiryOrderSupplierVO vo = new InquiryOrderSupplierVO();
        vo.setId(d.getId());
        vo.setInquiryOrderId(d.getInquiryOrderId());
        vo.setSourceType(d.getSourceType());
        vo.setSupplierId(d.getSupplierId());
        vo.setChannelPlatform(d.getChannelPlatform());
        vo.setChannelName(d.getChannelName());
        vo.setChannelLink(d.getChannelLink());
        vo.setSentDate(d.getSentDate());
        vo.setReplyDeadline(d.getReplyDeadline());
        vo.setStatus(d.getStatus());
        vo.setQuoteFileUrl(d.getQuoteFileUrl());
        vo.setRemark(d.getRemark());

        if (d.getSourceType() != null && d.getSourceType() == InquirySourceType.FORMAL_SUPPLIER && d.getSupplierId() != null) {
            SupplierVO supplier = supplierService.getById(d.getSupplierId());
            vo.setSupplierName(supplier != null ? supplier.getName() : null);
            vo.setDisplayName(vo.getSupplierName());
        } else {
            vo.setDisplayName(platformLabel(d.getChannelPlatform()) + "·" + d.getChannelName());
        }
        return vo;
    }

    private QuoteVO toQuoteVo(InquiryOrderItemQuoteDO quote) {
        QuoteVO vo = new QuoteVO();
        vo.setId(quote.getId());
        vo.setInquiryOrderItemId(quote.getInquiryOrderItemId());
        vo.setInquiryOrderSupplierId(quote.getInquiryOrderSupplierId());
        vo.setQuotePriceOriginal(quote.getQuotePriceOriginal());
        vo.setCurrencyCode(quote.getCurrencyCode());
        vo.setExchangeRate(quote.getExchangeRate());
        vo.setQuotePriceCny(quote.getQuotePriceCny());
        vo.setSupplierDelivery(quote.getSupplierDelivery());
        vo.setQuoteStatus(quote.getQuoteStatus());
        return vo;
    }

    private static String platformLabel(Integer platform) {
        if (platform == null) {
            return "电商";
        }
        if (platform == ChannelPlatform.TAOBAO) {
            return "淘宝";
        }
        if (platform == ChannelPlatform.ALIBABA_1688) {
            return "1688";
        }
        if (platform == ChannelPlatform.XIANYU) {
            return "闲鱼";
        }
        return "其他平台";
    }
}
