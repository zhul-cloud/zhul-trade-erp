package com.zhul.erp.modules.quotation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.dto.AddQuotationItemsRequest;
import com.zhul.erp.modules.quotation.dto.CreateQuotationRequest;
import com.zhul.erp.modules.quotation.dto.MarkLostRequest;
import com.zhul.erp.modules.quotation.dto.QuotationFeeVO;
import com.zhul.erp.modules.quotation.dto.QuotationItemVO;
import com.zhul.erp.modules.quotation.dto.QuotationListVO;
import com.zhul.erp.modules.quotation.dto.QuotationPageQuery;
import com.zhul.erp.modules.quotation.dto.QuotationStatsVO;
import com.zhul.erp.modules.quotation.dto.QuotationVO;
import com.zhul.erp.modules.quotation.dto.SaveQuotationRequest;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationFeeDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.entity.QuotationSendLogDO;
import com.zhul.erp.modules.quotation.entity.QuotationVersionDO;
import com.zhul.erp.modules.quotation.repository.QuotationFeeMapper;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.quotation.repository.QuotationSendLogMapper;
import com.zhul.erp.modules.quotation.repository.QuotationVersionMapper;
import com.zhul.erp.modules.quotation.service.QuotationService;
import com.zhul.erp.modules.quotation.support.InquiryStatusSync;
import com.zhul.erp.modules.quotation.support.PricingSettings;
import com.zhul.erp.modules.quotation.support.PricingStrategy;
import com.zhul.erp.modules.quotation.support.QuotationCalculator;
import com.zhul.erp.modules.quotation.support.QuotationPis;
import com.zhul.erp.modules.quotation.support.QuotationPricing;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.quotation.support.QuoteCandidates;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.constants.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationServiceImpl implements QuotationService {

    private static final int RETURNING_SAMPLE = 3;
    private static final int KEYWORD_LIMIT = 500;
    private static final Set<Integer> CREATABLE_INQUIRY_STATUSES = Set.of(InquiryConstants.STATUS_SOURCING,
            InquiryConstants.STATUS_READY_TO_QUOTE, InquiryConstants.STATUS_QUOTED);
    private static final Map<String, String> HINT_TEXTS = Map.of(
            QuotationPricing.HINT_DISCONTINUED_URGENT, "停产急件 · 建议主管核价",
            QuotationPricing.HINT_PREMIUM_BRAND, "现货优势品牌 · 建议主管核价");

    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper itemMapper;
    private final QuotationFeeMapper feeMapper;
    private final QuotationSendLogMapper sendLogMapper;
    private final QuotationVersionMapper versionMapper;
    private final QuotationPis quotationPis;
    private final CustomerInquiryMapper inquiryMapper;
    private final InquiryItemMapper inquiryItemMapper;
    private final CustomerMapper customerMapper;
    private final QuotationStore store;
    private final QuoteCandidates candidates;
    private final InquiryStatusSync statusSync;
    private final PricingSettings pricingSettings;
    private final PriceKeys priceKeys;
    private final ExchangeRateService exchangeRateService;
    private final DictItemService dictItemService;
    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;
    private final DocumentNumberService documentNumberService;
    private final com.zhul.erp.modules.product.support.BrandResolver brandResolver;

    // ---------------------------------------------------------------- 新建

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationVO create(CreateQuotationRequest req) {
        boolean byInquiry = req.getInquiryIds() != null && !req.getInquiryIds().isEmpty();
        boolean byItems = req.getItemIds() != null && !req.getItemIds().isEmpty();
        if (byInquiry == byItems) {
            throw new BizException(byInquiry ? "请只选择询盘或型号中的一种" : "请选择要报价的询盘或型号");
        }
        List<QuoteCandidates.Candidate> picked;
        Map<Long, CustomerInquiryDO> inquiries;
        int pending = 0;
        if (byInquiry) {
            inquiries = visibleInquiries(req.getInquiryIds());
            Long customerId = sameCustomer(inquiries.values());
            Map<Long, QuoteCandidates.Candidate> evaluated = candidates.evaluate(candidates.itemsOf(inquiries.keySet()), customerId, null);
            picked = new ArrayList<>();
            // 无货型号也带入（作为无货行），让客户知道是没货还是停产有替代
            for (QuoteCandidates.Candidate c : evaluated.values()) {
                if (c.pickable()) {
                    picked.add(c);
                } else {
                    pending++;
                }
            }
            if (picked.isEmpty()) {
                throw new BizException("所选询盘还没有已回价的型号，回价后才能报价");
            }
        } else {
            List<InquiryItemDO> items = activeItems(req.getItemIds());
            inquiries = visibleInquiries(items.stream().map(InquiryItemDO::getCustomerInquiryId).distinct().toList());
            Long customerId = sameCustomer(inquiries.values());
            Map<Long, QuoteCandidates.Candidate> evaluated = candidates.evaluate(items, customerId, null);
            picked = new ArrayList<>();
            for (InquiryItemDO item : items) {
                QuoteCandidates.Candidate c = evaluated.get(item.getId());
                if (!c.pickable()) {
                    throw new BizException("型号 " + modelOf(item) + " " + QuoteCandidates.PENDING_REASON);
                }
                picked.add(c);
            }
        }
        picked.sort((a, b) -> {
            int byInq = a.item().getCustomerInquiryId().compareTo(b.item().getCustomerInquiryId());
            return byInq != 0 ? byInq : Integer.compare(nz(a.item().getLineNo()), nz(b.item().getLineNo()));
        });
        if (picked.size() > QuotationConstants.MAX_ITEMS) {
            throw new BizException("一张报价单最多 " + QuotationConstants.MAX_ITEMS + " 个型号，请分开报价");
        }
        CustomerDO customer = customerMapper.selectById(inquiries.values().iterator().next().getCustomerId());
        if (customer == null || customer.getDeletedAt() != null) {
            throw new BizException("客户不存在");
        }
        String currency = StringUtils.hasText(customer.getCurrency()) ? customer.getCurrency().trim().toUpperCase(Locale.ROOT)
                : QuotationConstants.DEFAULT_CURRENCY;
        ExchangeRateService.Snapshot rate = exchangeRateService.require(currency);

        QuotationDO q = new QuotationDO();
        q.setTenantId(tenantId());
        q.setCustomerId(customer.getId());
        q.setOwnerId(currentUserId());
        q.setCurrencyCode(currency);
        q.setExchangeRate(rate.rate());
        q.setRateTime(rate.rateTime());
        // 客户档案有默认交易条件时取客户的，没有时为 DAP + 客户国家
        boolean customerTerms = StringUtils.hasText(customer.getIncoterm());
        q.setIncoterm(customerTerms ? customer.getIncoterm() : QuotationConstants.DEFAULT_INCOTERM);
        q.setIncotermPlace(customerTerms ? nz(customer.getIncotermPlace()) : nz(customer.getCountry()));
        q.setValidUntil(LocalDate.now().plusDays(QuotationConstants.VALID_DAYS));
        q.setRemark("");
        q.setStatus(QuotationConstants.STATUS_DRAFT);
        q.setLostReason("");
        q.setLostReasonName("");
        q.setLostNote("");
        q.setItemAmount(BigDecimal.ZERO);
        q.setFeeAmount(BigDecimal.ZERO);
        q.setTotalAmount(BigDecimal.ZERO);
        q.setTotalAmountCny(BigDecimal.ZERO);
        insertWithNo(q);

        List<QuotationItemDO> lines = newLines(q, picked, inquiries, 1, 1, 1);
        lines.forEach(itemMapper::insert);
        QuotationCalculator.applyTotals(q, lines, List.of());
        quotationMapper.updateById(q);
        logService.recordOperateLog(QuotationConstants.MENU, "新建报价单", null,
                Map.of("quotationNo", q.getQuotationNo(), "items", lines.size()));
        log.info("新建报价单，quotationNo={}, items={}", q.getQuotationNo(), lines.size());
        QuotationVO vo = detail(q.getId());
        vo.setPendingItemCount(pending);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationVO addItems(Long id, AddQuotationItemsRequest req) {
        QuotationStore.Edit e = store.editable(id);
        QuotationDO q = e.header();
        List<QuotationItemDO> existing = store.items(id, e.versionNo());
        Set<Long> already = existing.stream().map(QuotationItemDO::getInquiryItemId).collect(Collectors.toSet());
        List<InquiryItemDO> items = activeItems(req.getItemIds()).stream().filter(i -> !already.contains(i.getId())).toList();
        if (items.isEmpty()) {
            return detail(id);
        }
        if (existing.size() + items.size() > QuotationConstants.MAX_ITEMS) {
            throw new BizException("一张报价单最多 " + QuotationConstants.MAX_ITEMS + " 个型号，请分开报价");
        }
        Map<Long, CustomerInquiryDO> inquiries = visibleInquiries(items.stream().map(InquiryItemDO::getCustomerInquiryId).distinct().toList());
        for (CustomerInquiryDO i : inquiries.values()) {
            if (!Objects.equals(i.getCustomerId(), q.getCustomerId())) {
                throw new BizException("不同客户需要分开报价");
            }
        }
        Map<Long, QuoteCandidates.Candidate> evaluated = candidates.evaluate(items, q.getCustomerId(), id);
        List<QuoteCandidates.Candidate> picked = new ArrayList<>();
        for (InquiryItemDO item : items) {
            QuoteCandidates.Candidate c = evaluated.get(item.getId());
            if (!c.pickable()) {
                throw new BizException("型号 " + modelOf(item) + " " + QuoteCandidates.PENDING_REASON);
            }
            picked.add(c);
        }
        int next = existing.stream().mapToInt(i -> nz(i.getLineNo())).max().orElse(0) + 1;
        List<QuotationItemDO> lines = newLines(q, picked, inquiries, next, e.versionNo(), e.current());
        lines.forEach(itemMapper::insert);
        List<QuotationItemDO> all = new ArrayList<>(existing);
        all.addAll(lines);
        QuotationCalculator.applyTotals(q, all, store.fees(id, e.versionNo()));
        store.saveHeader(e);
        return detail(id);
    }

    /** 由候选型号生成报价行：带出采购成本价、货况货期、建议毛利率与售价、转人工提示 */
    private List<QuotationItemDO> newLines(QuotationDO q, List<QuoteCandidates.Candidate> picked,
                                           Map<Long, CustomerInquiryDO> inquiries, int firstLineNo, int versionNo, int current) {
        PricingStrategy strategy = pricingSettings.load(q.getTenantId());
        Map<Integer, String> conditionNames = dictItemService.intLabels(QuotationConstants.DICT_CONDITION);
        Set<String> premiumKeys = strategy.premiumBrands().stream().map(b -> priceKeys.brand(b).brandKey()).collect(Collectors.toSet());
        List<QuotationItemDO> lines = new ArrayList<>(picked.size());
        int lineNo = firstLineNo;
        for (QuoteCandidates.Candidate c : picked) {
            InquiryItemDO src = c.item();
            CustomerInquiryDO inquiry = inquiries.get(src.getCustomerInquiryId());
            QuotationItemDO line = new QuotationItemDO();
            line.setTenantId(q.getTenantId());
            line.setQuotationId(q.getId());
            line.setVersionNo(versionNo);
            line.setIsCurrent(current);
            line.setLineNo(lineNo++);
            line.setCustomerInquiryId(src.getCustomerInquiryId());
            line.setInquiryItemId(src.getId());
            line.setCostQuoteId(c.costQuote() == null ? null : c.costQuote().getId());
            line.setModel(modelOf(src));
            line.setBrand(nz(src.getBrand()));
            line.setBrandKey(nz(src.getBrandKey()));
            line.setCategory(nz(src.getCategory()));
            line.setDescription(nz(src.getDescription()));
            line.setItemCondition(c.costQuote() == null ? 0 : nz(c.costQuote().getItemCondition()));
            line.setLeadTime(c.costQuote() == null ? 0 : nz(c.costQuote().getLeadTime()));
            line.setWarranty(QuotationConstants.DEFAULT_WARRANTY);
            line.setQuantity(src.getQuantity() == null || src.getQuantity() < 1 ? 1 : src.getQuantity());
            line.setNoStock(c.noStock() ? 1 : 0);
            line.setReplacementModel(c.noStock() && Objects.equals(src.getLifecycle(), InquiryConstants.LIFECYCLE_DISCONTINUED)
                    ? nz(src.getReplacementModel()).trim() : "");
            line.setCostPrice(c.costPrice());
            QuotationPricing.Suggestion s = QuotationPricing.suggest(line.getItemCondition(), line.getCostPrice(), strategy, conditionNames);
            if (line.getCostPrice() == null) {
                s = new QuotationPricing.Suggestion(null, null, c.noStock() ? "无货" : s.basis());
            }
            line.setSuggestedMargin(s.marginRate());
            line.setSuggestBasis(s.basis());
            line.setFloorMargin(s.floorRate());
            boolean premium = StringUtils.hasText(line.getBrandKey()) && premiumKeys.contains(line.getBrandKey());
            List<String> hints = line.getCostPrice() == null ? List.of()
                    : QuotationPricing.hints(strategy, s, src.getLifecycle(), inquiry != null && Objects.equals(inquiry.getUrgent(), 1), premium);
            line.setHints(String.join(",", hints));
            if (line.getCostPrice() != null && s.marginRate() != null) {
                line.setPricingMode(QuotationPricing.MODE_MARGIN);
                line.setMarginRate(s.marginRate());
            } else {
                line.setPricingMode(QuotationPricing.MODE_PRICE);
                line.setUnitPrice(BigDecimal.ZERO);
            }
            QuotationCalculator.applyLine(line, q.getExchangeRate());
            lines.add(line);
        }
        return lines;
    }

    /** 编号按全链路规则生成（租户前缀 + QT + 年月日 + 当日流水） */
    private void insertWithNo(QuotationDO q) {
        q.setId(null);
        q.setQuotationNo(documentNumberService.next(DocumentType.QT));
        quotationMapper.insert(q);
    }

    // ---------------------------------------------------------------- 编辑

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationVO save(Long id, SaveQuotationRequest req) {
        store.editable(id);
        quotationMapper.lockById(id);
        QuotationStore.Edit e = store.editable(id);
        QuotationStore.Applied applied = store.apply(e.header(), e.versionNo(), store.items(id, e.versionNo()), req);
        store.saveHeader(e);
        applied.items().forEach(itemMapper::updateById);
        LocalDateTime now = LocalDateTime.now();
        for (QuotationItemDO removed : applied.removed()) {
            removed.setDeletedAt(now);
            itemMapper.updateById(removed);
        }
        replaceFees(id, e.versionNo(), applied.fees());
        return detail(id);
    }

    /** 费用行按位置复用已有行，多出的软删除 */
    private void replaceFees(Long id, int versionNo, List<QuotationFeeDO> fees) {
        List<QuotationFeeDO> old = store.fees(id, versionNo);
        for (int i = 0; i < Math.max(old.size(), fees.size()); i++) {
            if (i < fees.size() && i < old.size()) {
                QuotationFeeDO target = old.get(i);
                QuotationFeeDO src = fees.get(i);
                target.setFeeName(src.getFeeName());
                target.setAmount(src.getAmount());
                target.setAmountCny(src.getAmountCny());
                target.setSortOrder(src.getSortOrder());
                feeMapper.updateById(target);
            } else if (i < fees.size()) {
                feeMapper.insert(fees.get(i));
            } else {
                QuotationFeeDO target = old.get(i);
                target.setDeletedAt(LocalDateTime.now());
                feeMapper.updateById(target);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationVO recalcRate(Long id) {
        QuotationStore.Edit e = store.editable(id);
        QuotationDO q = e.header();
        ExchangeRateService.Snapshot rate = exchangeRateService.require(q.getCurrencyCode());
        BigDecimal before = q.getExchangeRate();
        q.setExchangeRate(rate.rate());
        q.setRateTime(rate.rateTime());
        List<QuotationItemDO> items = store.items(id, e.versionNo());
        for (QuotationItemDO item : items) {
            // 直接改过外币售价的行，按当时的毛利率重算
            if (item.getPricingMode() == QuotationPricing.MODE_PRICE && item.getCostPrice() != null && item.getMarginRate() != null
                    && item.getMarginRate().compareTo(BigDecimal.valueOf(100)) < 0 && item.getMarginRate().signum() >= 0) {
                item.setPricingMode(QuotationPricing.MODE_MARGIN);
            }
            QuotationCalculator.applyLine(item, q.getExchangeRate());
            itemMapper.updateById(item);
        }
        List<QuotationFeeDO> fees = store.fees(id, e.versionNo());
        QuotationCalculator.applyTotals(q, items, fees);
        fees.forEach(feeMapper::updateById);
        store.saveHeader(e);
        logService.recordOperateLog(QuotationConstants.MENU, "按新汇率重算",
                Map.of("quotationNo", q.getQuotationNo(), "rate", before.toPlainString()),
                Map.of("quotationNo", q.getQuotationNo(), "rate", rate.rate().toPlainString()));
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QuotationDO q = store.requireDraft(id);
        LocalDateTime now = LocalDateTime.now();
        q.setDeletedAt(now);
        quotationMapper.updateById(q);
        for (QuotationItemDO i : store.items(id)) {
            i.setDeletedAt(now);
            itemMapper.updateById(i);
        }
        for (QuotationFeeDO f : store.fees(id)) {
            f.setDeletedAt(now);
            feeMapper.updateById(f);
        }
        logService.recordOperateLog(QuotationConstants.MENU, "删除草稿报价单", Map.of("quotationNo", q.getQuotationNo()), null);
    }

    // ---------------------------------------------------------------- 状态流转

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public QuotationVO markSent(Long id, int channel) {
        if (!QuotationConstants.CHANNEL_NAMES.containsKey(channel)) {
            throw new BizException("发送方式不正确");
        }
        QuotationDO q = lockVisible(id);
        if (q.getStatus() == QuotationConstants.STATUS_DRAFT) {
            requirePrices(store.items(id));
            Set<Long> inquiryIds = statusSync.inquiryIdsOf(id);
            statusSync.lock(inquiryIds);
            q.setStatus(QuotationConstants.STATUS_SENT);
            q.setSentAt(LocalDateTime.now());
            quotationMapper.updateById(q);
            insertSentVersion(q);
            statusSync.sync(inquiryIds);
            logService.recordOperateLog(QuotationConstants.MENU, "标为已发送", Map.of("quotationNo", q.getQuotationNo(), "status", "草稿"),
                    Map.of("quotationNo", q.getQuotationNo(), "status", "已发送", "channel", QuotationConstants.CHANNEL_NAMES.get(channel)));
        } else if (q.getStatus() == QuotationConstants.STATUS_SENT && q.getEditingVersionNo() != null) {
            q = sendRevision(q, channel);
        } else if (q.getStatus() != QuotationConstants.STATUS_SENT) {
            throw new BizException("报价单已" + QuotationConstants.STATUS_NAMES.get(q.getStatus()) + "，不能再标为已发送");
        }
        QuotationSendLogDO sendLog = new QuotationSendLogDO();
        sendLog.setTenantId(q.getTenantId());
        sendLog.setQuotationId(id);
        sendLog.setVersionNo(q.getCurrentVersionNo());
        sendLog.setChannel(channel);
        sendLog.setSentBy(currentUserId());
        sendLog.setSentAt(LocalDateTime.now());
        sendLogMapper.insert(sendLog);
        return detail(id);
    }

    private static void requirePrices(List<QuotationItemDO> items) {
        if (items.isEmpty()) {
            throw new BizException("报价单没有型号，不能发送");
        }
        if (items.stream().allMatch(QuotationRenderModels::isNoStockLine)) {
            throw new BizException("报价单的型号都是无货，至少要有一个报价的型号才能发送");
        }
        for (QuotationItemDO i : items) {
            // 无货行不报价，发送时不要求售价
            if (QuotationRenderModels.isNoStockLine(i)) {
                continue;
            }
            if (i.getUnitPrice() == null || i.getUnitPrice().signum() <= 0) {
                throw new BizException("第 " + i.getLineNo() + " 行（" + i.getModel() + "）还没有售价");
            }
        }
    }

    /** 草稿第一次发送时保存 Rev.1 的表头快照 */
    private void insertSentVersion(QuotationDO q) {
        QuotationVersionDO v = new QuotationVersionDO();
        v.setTenantId(q.getTenantId());
        v.setQuotationId(q.getId());
        v.setVersionNo(q.getCurrentVersionNo());
        v.setStatus(QuotationConstants.VERSION_SENT);
        QuotationStore.copyHeader(q, v);
        v.setSentAt(q.getSentAt());
        versionMapper.insert(v);
    }

    /** 发送修改中的新版本：新版本成为当前版本，表头写回报价单，客户询盘按新版本重新判断 */
    private QuotationDO sendRevision(QuotationDO q, int channel) {
        int from = q.getCurrentVersionNo();
        int to = q.getEditingVersionNo();
        requirePrices(store.items(q.getId(), to));
        Set<Long> inquiryIds = new TreeSet<>(statusSync.inquiryIdsOf(q.getId()));
        store.items(q.getId(), to).forEach(i -> inquiryIds.add(i.getCustomerInquiryId()));
        statusSync.lock(inquiryIds);
        LocalDateTime now = LocalDateTime.now();
        flipCurrent(q.getId(), from, 0);
        flipCurrent(q.getId(), to, 1);
        QuotationVersionDO v = store.version(q.getId(), to);
        v.setStatus(QuotationConstants.VERSION_SENT);
        v.setSentAt(now);
        versionMapper.updateById(v);
        QuotationDO next = QuotationStore.overlay(q, v);
        next.setCurrentVersionNo(to);
        next.setEditingVersionNo(null);
        quotationMapper.updateById(next);
        statusSync.sync(inquiryIds);
        logService.recordOperateLog(QuotationConstants.MENU, "发送新版本", Map.of("quotationNo", q.getQuotationNo(), "version", "Rev." + from),
                Map.of("quotationNo", q.getQuotationNo(), "version", "Rev." + to, "channel", QuotationConstants.CHANNEL_NAMES.get(channel)));
        log.info("报价单发送新版本，quotationNo={}, Rev.{} -> Rev.{}", q.getQuotationNo(), from, to);
        return next;
    }

    private void flipCurrent(Long id, int versionNo, int current) {
        LocalDateTime now = LocalDateTime.now();
        itemMapper.update(null, new LambdaUpdateWrapper<QuotationItemDO>()
                .set(QuotationItemDO::getIsCurrent, current).set(QuotationItemDO::getUpdateTime, now)
                .eq(QuotationItemDO::getQuotationId, id).eq(QuotationItemDO::getVersionNo, versionNo));
        feeMapper.update(null, new LambdaUpdateWrapper<QuotationFeeDO>()
                .set(QuotationFeeDO::getIsCurrent, current).set(QuotationFeeDO::getUpdateTime, now)
                .eq(QuotationFeeDO::getQuotationId, id).eq(QuotationFeeDO::getVersionNo, versionNo));
    }

    // ---------------------------------------------------------------- 版本

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationVO revise(Long id) {
        QuotationDO q = lockVisible(id);
        if (q.getStatus() != QuotationConstants.STATUS_SENT) {
            throw new BizException(q.getStatus() == QuotationConstants.STATUS_DRAFT ? "草稿报价单可以直接修改"
                    : "报价单已" + QuotationConstants.STATUS_NAMES.get(q.getStatus()) + "，不能出新版本");
        }
        if (q.getEditingVersionNo() != null) {
            throw new BizException("已有修改中的 Rev." + q.getEditingVersionNo() + "，请先发送或放弃");
        }
        QuotationPis.Brief pi = quotationPis.activeOf(id);
        if (pi != null) {
            throw new BizException("已开 PI " + pi.piNo() + "，请在 PI 上修改");
        }
        int next = Math.max(maxVersionNo(id), q.getCurrentVersionNo()) + 1;
        QuotationVersionDO v = new QuotationVersionDO();
        v.setTenantId(q.getTenantId());
        v.setQuotationId(id);
        v.setVersionNo(next);
        v.setStatus(QuotationConstants.VERSION_EDITING);
        QuotationStore.copyHeader(q, v);
        versionMapper.insert(v);
        for (QuotationItemDO s : store.items(id)) {
            QuotationItemDO line = copyLine(s);
            line.setQuotationId(id);
            line.setVersionNo(next);
            line.setIsCurrent(0);
            // 原样复制已算好的金额（不重算），新版本保存时再按编辑内容重算
            line.setUnitPriceCny(s.getUnitPriceCny());
            line.setAmount(s.getAmount());
            line.setAmountCny(s.getAmountCny());
            line.setNetProfit(s.getNetProfit());
            line.setNetProfitCny(s.getNetProfitCny());
            itemMapper.insert(line);
        }
        for (QuotationFeeDO s : store.fees(id)) {
            QuotationFeeDO f = new QuotationFeeDO();
            f.setTenantId(s.getTenantId());
            f.setQuotationId(id);
            f.setVersionNo(next);
            f.setIsCurrent(0);
            f.setFeeName(s.getFeeName());
            f.setAmount(s.getAmount());
            f.setAmountCny(s.getAmountCny());
            f.setSortOrder(s.getSortOrder());
            feeMapper.insert(f);
        }
        q.setEditingVersionNo(next);
        quotationMapper.updateById(q);
        logService.recordOperateLog(QuotationConstants.MENU, "出新版本", Map.of("quotationNo", q.getQuotationNo(), "version", "Rev." + q.getCurrentVersionNo()),
                Map.of("quotationNo", q.getQuotationNo(), "version", "Rev." + next));
        return detail(id, next);
    }

    /** 版本号含已放弃的版本，避免放弃后再出新版本时重号 */
    private int maxVersionNo(Long id) {
        return versionMapper.selectList(new LambdaQueryWrapper<QuotationVersionDO>()
                        .select(QuotationVersionDO::getVersionNo)
                        .eq(QuotationVersionDO::getQuotationId, id))
                .stream().mapToInt(QuotationVersionDO::getVersionNo).max().orElse(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationVO abandon(Long id) {
        QuotationDO q = lockVisible(id);
        if (q.getEditingVersionNo() == null) {
            throw new BizException("没有修改中的版本");
        }
        int no = q.getEditingVersionNo();
        discardRevision(q);
        logService.recordOperateLog(QuotationConstants.MENU, "放弃新版本", Map.of("quotationNo", q.getQuotationNo(), "version", "Rev." + no),
                Map.of("quotationNo", q.getQuotationNo(), "version", "Rev." + q.getCurrentVersionNo()));
        return detail(id);
    }

    /** 放弃修改中的版本：软删除其型号行与费用行，版本标为已放弃 */
    private void discardRevision(QuotationDO q) {
        int no = q.getEditingVersionNo();
        LocalDateTime now = LocalDateTime.now();
        itemMapper.update(null, new LambdaUpdateWrapper<QuotationItemDO>()
                .set(QuotationItemDO::getDeletedAt, now).set(QuotationItemDO::getUpdateTime, now)
                .eq(QuotationItemDO::getQuotationId, q.getId()).eq(QuotationItemDO::getVersionNo, no)
                .isNull(QuotationItemDO::getDeletedAt));
        feeMapper.update(null, new LambdaUpdateWrapper<QuotationFeeDO>()
                .set(QuotationFeeDO::getDeletedAt, now).set(QuotationFeeDO::getUpdateTime, now)
                .eq(QuotationFeeDO::getQuotationId, q.getId()).eq(QuotationFeeDO::getVersionNo, no)
                .isNull(QuotationFeeDO::getDeletedAt));
        QuotationVersionDO v = store.version(q.getId(), no);
        v.setStatus(QuotationConstants.VERSION_ABANDONED);
        versionMapper.updateById(v);
        q.setEditingVersionNo(null);
        quotationMapper.updateById(q);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationVO copy(Long id) {
        QuotationDO src = store.visible(id);
        ExchangeRateService.Snapshot rate = exchangeRateService.require(src.getCurrencyCode());
        QuotationDO q = new QuotationDO();
        q.setTenantId(src.getTenantId());
        q.setCustomerId(src.getCustomerId());
        q.setOwnerId(currentUserId());
        q.setCurrencyCode(src.getCurrencyCode());
        q.setExchangeRate(rate.rate());
        q.setRateTime(rate.rateTime());
        q.setIncoterm(src.getIncoterm());
        q.setIncotermPlace(src.getIncotermPlace());
        q.setValidUntil(LocalDate.now().plusDays(QuotationConstants.VALID_DAYS));
        q.setRemark(src.getRemark());
        q.setStatus(QuotationConstants.STATUS_DRAFT);
        q.setLostReason("");
        q.setLostReasonName("");
        q.setLostNote("");
        q.setCopiedFromId(src.getId());
        q.setItemAmount(BigDecimal.ZERO);
        q.setFeeAmount(BigDecimal.ZERO);
        q.setTotalAmount(BigDecimal.ZERO);
        q.setTotalAmountCny(BigDecimal.ZERO);
        insertWithNo(q);
        List<QuotationItemDO> lines = new ArrayList<>();
        for (QuotationItemDO s : store.items(id)) {
            QuotationItemDO line = copyLine(s);
            line.setQuotationId(q.getId());
            line.setVersionNo(1);
            line.setIsCurrent(1);
            QuotationCalculator.applyLine(line, q.getExchangeRate());
            itemMapper.insert(line);
            lines.add(line);
        }
        List<QuotationFeeDO> fees = new ArrayList<>();
        for (QuotationFeeDO s : store.fees(id)) {
            QuotationFeeDO f = new QuotationFeeDO();
            f.setTenantId(q.getTenantId());
            f.setQuotationId(q.getId());
            f.setVersionNo(1);
            f.setIsCurrent(1);
            f.setFeeName(s.getFeeName());
            f.setAmount(s.getAmount());
            f.setSortOrder(s.getSortOrder());
            fees.add(f);
        }
        QuotationCalculator.applyTotals(q, lines, fees);
        fees.forEach(feeMapper::insert);
        quotationMapper.updateById(q);
        logService.recordOperateLog(QuotationConstants.MENU, "复制为新报价单", Map.of("quotationNo", src.getQuotationNo()),
                Map.of("quotationNo", q.getQuotationNo()));
        return detail(q.getId());
    }

    private static QuotationItemDO copyLine(QuotationItemDO s) {
        QuotationItemDO l = new QuotationItemDO();
        l.setTenantId(s.getTenantId());
        l.setLineNo(s.getLineNo());
        l.setCustomerInquiryId(s.getCustomerInquiryId());
        l.setInquiryItemId(s.getInquiryItemId());
        l.setCostQuoteId(s.getCostQuoteId());
        l.setModel(s.getModel());
        l.setBrand(s.getBrand());
        l.setBrandKey(s.getBrandKey());
        l.setCategory(s.getCategory());
        l.setDescription(s.getDescription());
        l.setItemCondition(s.getItemCondition());
        l.setLeadTime(s.getLeadTime());
        l.setWarranty(s.getWarranty());
        l.setQuantity(s.getQuantity());
        l.setNoStock(s.getNoStock());
        l.setReplacementModel(s.getReplacementModel());
        l.setCostPrice(s.getCostPrice());
        l.setPricingMode(s.getPricingMode());
        l.setMarginRate(s.getMarginRate());
        l.setMarkupAmount(s.getMarkupAmount());
        l.setSuggestedMargin(s.getSuggestedMargin());
        l.setSuggestBasis(s.getSuggestBasis());
        l.setFloorMargin(s.getFloorMargin());
        l.setHints(s.getHints());
        l.setUnitPrice(s.getUnitPrice());
        return l;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public QuotationVO markLost(Long id, MarkLostRequest req) {
        if (!StringUtils.hasText(req.getReason())) {
            throw new BizException("请选择未成交原因");
        }
        DictItemVO reason = dictItemService.listByDictType(QuotationConstants.DICT_LOST_REASON).stream()
                .filter(i -> Objects.equals(i.getStatus(), 1) && req.getReason().equals(i.getItemCode()))
                .findFirst().orElseThrow(() -> new BizException("未成交原因不存在或已停用"));
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (QuotationConstants.LOST_REASON_OTHER.equals(reason.getItemCode()) && note.isEmpty()) {
            throw new BizException("选择「其他」时请填写说明");
        }
        return close(id, QuotationConstants.STATUS_LOST, "标为未成交", reason.getItemCode(), reason.getItemName(), note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public QuotationVO voidQuotation(Long id) {
        return close(id, QuotationConstants.STATUS_VOID, "作废报价单", null, null, null);
    }

    private QuotationVO close(Long id, int target, String operation, String reason, String reasonName, String note) {
        QuotationDO q = lockVisible(id);
        if (q.getStatus() != QuotationConstants.STATUS_SENT) {
            throw new BizException(q.getStatus() == QuotationConstants.STATUS_DRAFT ? "草稿报价单请先标为已发送"
                    : "报价单已" + QuotationConstants.STATUS_NAMES.get(q.getStatus()) + "，不能再修改状态");
        }
        if (q.getEditingVersionNo() != null) {
            discardRevision(q);
        }
        Set<Long> inquiryIds = statusSync.inquiryIdsOf(id);
        statusSync.lock(inquiryIds);
        q.setStatus(target);
        q.setClosedAt(LocalDateTime.now());
        if (reason != null) {
            q.setLostReason(reason);
            q.setLostReasonName(reasonName);
            q.setLostNote(note);
        }
        quotationMapper.updateById(q);
        statusSync.sync(inquiryIds);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("quotationNo", q.getQuotationNo());
        after.put("status", QuotationConstants.STATUS_NAMES.get(target));
        if (reasonName != null) {
            after.put("reason", reasonName);
            after.put("note", note);
        }
        logService.recordOperateLog(QuotationConstants.MENU, operation,
                Map.of("quotationNo", q.getQuotationNo(), "status", "已发送"), after);
        return detail(id);
    }

    /** 锁住报价单行后再读（状态变更串行） */
    private QuotationDO lockVisible(Long id) {
        store.visible(id);
        quotationMapper.lockById(id);
        return store.visible(id);
    }

    // ---------------------------------------------------------------- 查看

    @Override
    public QuotationVO detail(Long id) {
        return detail(id, null);
    }

    @Override
    public QuotationVO detail(Long id, Integer versionNo) {
        QuotationStore.View view = store.view(id, versionNo);
        QuotationDO q = view.header();
        List<QuotationItemDO> items = view.items();
        List<QuotationFeeDO> fees = view.fees();
        boolean viewingEditing = q.getEditingVersionNo() != null && view.versionNo() == q.getEditingVersionNo();
        QuotationRenderModels.Labels labels = store.labels();
        Map<Long, String> inquiryCodes = lookups.inquiryCodes(items.stream().map(QuotationItemDO::getCustomerInquiryId).toList());
        CustomerDO customer = customerMapper.selectById(q.getCustomerId());
        Map<Long, String> users = lookups.userNames(List.of(q.getOwnerId()));

        QuotationVO vo = new QuotationVO();
        vo.setId(q.getId());
        vo.setQuotationNo(q.getQuotationNo());
        vo.setCustomerId(q.getCustomerId());
        vo.setCustomerName(InquiryLookups.customerName(customer));
        vo.setCustomerCountry(customer == null ? null : customer.getCountry());
        vo.setOwnerId(q.getOwnerId());
        vo.setOwnerName(users.get(q.getOwnerId()));
        vo.setCurrencyCode(q.getCurrencyCode());
        vo.setExchangeRate(q.getExchangeRate());
        vo.setRateTime(q.getRateTime());
        vo.setIncoterm(q.getIncoterm());
        vo.setIncotermPlace(q.getIncotermPlace());
        vo.setValidUntil(q.getValidUntil());
        vo.setRemark(q.getRemark());
        vo.setStatus(q.getStatus());
        vo.setStatusName(QuotationConstants.STATUS_NAMES.get(q.getStatus()));
        vo.setLostReason(q.getLostReason());
        vo.setLostReasonName(q.getLostReasonName());
        vo.setLostNote(q.getLostNote());
        vo.setCopiedFromId(q.getCopiedFromId());
        if (q.getCopiedFromId() != null) {
            QuotationDO from = quotationMapper.selectById(q.getCopiedFromId());
            vo.setCopiedFromNo(from == null ? null : from.getQuotationNo());
        }
        vo.setItemAmount(q.getItemAmount());
        vo.setFeeAmount(q.getFeeAmount());
        vo.setTotalAmount(q.getTotalAmount());
        vo.setTotalAmountCny(q.getTotalAmountCny());
        vo.setNetProfit(q.getNetProfit());
        vo.setNetProfitCny(q.getNetProfitCny());
        vo.setMarginRate(q.getMarginRate());
        vo.setSentAt(q.getSentAt());
        vo.setClosedAt(q.getClosedAt());
        vo.setCreateTime(q.getCreateTime());
        vo.setEditable(q.getStatus() == QuotationConstants.STATUS_DRAFT || viewingEditing);
        vo.setVersionNo(view.versionNo());
        vo.setCurrentVersionNo(q.getCurrentVersionNo());
        vo.setEditingVersionNo(q.getEditingVersionNo());
        vo.setVersions(store.versions(id).stream().map(x -> {
            QuotationVO.VersionBrief b = new QuotationVO.VersionBrief();
            b.setVersionNo(x.getVersionNo());
            b.setStatus(x.getStatus());
            b.setTotalAmount(x.getTotalAmount());
            b.setSentAt(x.getSentAt());
            b.setCreateTime(x.getCreateTime());
            return b;
        }).toList());
        if (q.getStatus() == QuotationConstants.STATUS_SENT && q.getEditingVersionNo() == null) {
            QuotationPis.Brief pi = quotationPis.activeOf(id);
            if (pi != null) {
                vo.setActivePiId(pi.id());
                vo.setActivePiNo(pi.piNo());
            }
        }
        Map<String, String> brandsEn = brandResolver.displayNames(items.stream().map(QuotationItemDO::getBrand).toList());
        vo.setItems(items.stream().map(i -> {
            QuotationItemVO x = toItemVo(i, labels, inquiryCodes);
            x.setBrandEn(brandsEn.getOrDefault(i.getBrand(), i.getBrand()));
            return x;
        }).toList());
        vo.setFees(fees.stream().map(f -> {
            QuotationFeeVO fv = new QuotationFeeVO();
            fv.setFeeName(f.getFeeName());
            fv.setAmount(f.getAmount());
            fv.setAmountCny(f.getAmountCny());
            return fv;
        }).toList());
        vo.setSendLogs(sendLogs(id));
        vo.setInquiryCodes(items.stream().map(i -> inquiryCodes.get(i.getCustomerInquiryId())).filter(Objects::nonNull)
                .distinct().toList());
        applyReturningCustomer(vo, q, items);
        if ((q.getStatus() == QuotationConstants.STATUS_DRAFT || viewingEditing) && !"CNY".equals(q.getCurrencyCode())) {
            try {
                BigDecimal system = exchangeRateService.require(q.getCurrencyCode()).rate();
                if (system.compareTo(q.getExchangeRate()) != 0) {
                    vo.setSystemRate(system);
                }
            } catch (BizException e) {
                log.debug("系统汇率未设置，currency={}", q.getCurrencyCode());
            }
        }
        return vo;
    }

    private void applyReturningCustomer(QuotationVO vo, QuotationDO q, List<QuotationItemDO> items) {
        Set<Long> inquiryIds = items.stream().map(QuotationItemDO::getCustomerInquiryId).collect(Collectors.toSet());
        if (inquiryIds.isEmpty()) {
            return;
        }
        List<CustomerInquiryDO> inquiries = inquiryMapper.selectBatchIds(inquiryIds);
        boolean returning = inquiries.stream().anyMatch(i -> Objects.equals(i.getCustomerType(), InquiryConstants.CUSTOMER_RETURNING));
        vo.setCustomerType(returning ? InquiryConstants.CUSTOMER_RETURNING : InquiryConstants.CUSTOMER_NEW);
        if (!returning || !pricingSettings.load(q.getTenantId()).hintReturningCustomer()) {
            return;
        }
        vo.setReturningCustomer(true);
        vo.setReturningCustomerMargins(quotationMapper.selectList(new LambdaQueryWrapper<QuotationDO>()
                        .select(QuotationDO::getMarginRate)
                        .eq(QuotationDO::getTenantId, q.getTenantId())
                        .eq(QuotationDO::getCustomerId, q.getCustomerId())
                        .in(QuotationDO::getStatus, QuotationConstants.STATUS_WON, QuotationConstants.STATUS_PARTIAL)
                        .isNotNull(QuotationDO::getMarginRate)
                        .isNull(QuotationDO::getDeletedAt)
                        .orderByDesc(QuotationDO::getClosedAt)
                        .last("LIMIT " + RETURNING_SAMPLE))
                .stream().map(QuotationDO::getMarginRate).toList());
    }

    private List<QuotationVO.SendLog> sendLogs(Long id) {
        List<QuotationSendLogDO> rows = sendLogMapper.selectList(new LambdaQueryWrapper<QuotationSendLogDO>()
                .eq(QuotationSendLogDO::getQuotationId, id)
                .isNull(QuotationSendLogDO::getDeletedAt)
                .orderByAsc(QuotationSendLogDO::getId));
        Map<Long, String> names = lookups.userNames(rows.stream().map(QuotationSendLogDO::getSentBy).toList());
        return rows.stream().map(r -> {
            QuotationVO.SendLog s = new QuotationVO.SendLog();
            s.setVersionNo(r.getVersionNo());
            s.setChannel(r.getChannel());
            s.setChannelName(QuotationConstants.CHANNEL_NAMES.get(r.getChannel()));
            s.setSentByName(names.get(r.getSentBy()));
            s.setSentAt(r.getSentAt());
            return s;
        }).toList();
    }

    private static QuotationItemVO toItemVo(QuotationItemDO i, QuotationRenderModels.Labels labels, Map<Long, String> inquiryCodes) {
        QuotationItemVO vo = new QuotationItemVO();
        vo.setId(i.getId());
        vo.setLineNo(i.getLineNo());
        vo.setCustomerInquiryId(i.getCustomerInquiryId());
        vo.setInquiryCode(inquiryCodes.get(i.getCustomerInquiryId()));
        vo.setInquiryItemId(i.getInquiryItemId());
        vo.setModel(i.getModel());
        vo.setBrand(i.getBrand());
        vo.setCategory(i.getCategory());
        vo.setDescription(i.getDescription());
        vo.setItemCondition(i.getItemCondition());
        vo.setConditionName(labels.conditions().get(i.getItemCondition()));
        vo.setLeadTime(i.getLeadTime());
        vo.setLeadTimeName(labels.leadTimes().get(i.getLeadTime()));
        vo.setWarranty(i.getWarranty());
        vo.setQuantity(i.getQuantity());
        vo.setNoStock(Objects.equals(i.getNoStock(), 1));
        vo.setNoStockLine(QuotationRenderModels.isNoStockLine(i));
        vo.setReplacementModel(i.getReplacementModel());
        vo.setCostPrice(i.getCostPrice());
        vo.setPricingMode(i.getPricingMode());
        vo.setMarginRate(i.getMarginRate());
        vo.setMarkupAmount(i.getMarkupAmount());
        vo.setSuggestedMargin(i.getSuggestedMargin());
        vo.setSuggestBasis(i.getSuggestBasis());
        vo.setFloorMargin(i.getFloorMargin());
        vo.setBelowFloor(QuotationPricing.belowFloor(i.getMarginRate(), i.getFloorMargin()));
        vo.setHints(hintTexts(i));
        vo.setUnitPrice(i.getUnitPrice());
        vo.setUnitPriceCny(i.getUnitPriceCny());
        vo.setAmount(i.getAmount());
        vo.setAmountCny(i.getAmountCny());
        vo.setNetProfit(i.getNetProfit());
        vo.setNetProfitCny(i.getNetProfitCny());
        vo.setWon(Objects.equals(i.getWon(), 1));
        return vo;
    }

    private static List<String> hintTexts(QuotationItemDO i) {
        if (!StringUtils.hasText(i.getHints())) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (String code : i.getHints().split(",")) {
            if (QuotationPricing.HINT_TO_CONFIRM.equals(code)) {
                list.add(i.getSuggestedMargin() == null ? "品相待查，建议主管核价" : "生命周期待查 · 建议主管核价");
            } else if (HINT_TEXTS.containsKey(code)) {
                list.add(HINT_TEXTS.get(code));
            }
        }
        return list;
    }

    @Override
    public PageResult<QuotationListVO> page(QuotationPageQuery q) {
        int tenant = tenantId();
        LambdaQueryWrapper<QuotationDO> w = new LambdaQueryWrapper<QuotationDO>()
                .eq(QuotationDO::getTenantId, tenant)
                .isNull(QuotationDO::getDeletedAt);
        dataScopeResolver.current().apply(w, QuotationDO::getOwnerId);
        if (q.getStatus() != null) {
            w.eq(QuotationDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getCurrencyCode())) {
            w.eq(QuotationDO::getCurrencyCode, q.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
        }
        if (q.getOwnerId() != null) {
            w.eq(QuotationDO::getOwnerId, q.getOwnerId());
        }
        if (q.getCreatedFrom() != null) {
            w.ge(QuotationDO::getCreateTime, q.getCreatedFrom().atStartOfDay());
        }
        if (q.getCreatedTo() != null) {
            w.lt(QuotationDO::getCreateTime, q.getCreatedTo().plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenant)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw))
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(CustomerDO::getId).toList();
            List<Long> byModel = itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                            .select(QuotationItemDO::getQuotationId)
                            .eq(QuotationItemDO::getTenantId, tenant)
                            .like(QuotationItemDO::getModel, kw)
                            .eq(QuotationItemDO::getIsCurrent, 1)
                            .isNull(QuotationItemDO::getDeletedAt)
                            .last("LIMIT " + KEYWORD_LIMIT))
                    .stream().map(QuotationItemDO::getQuotationId).distinct().toList();
            w.and(x -> {
                x.like(QuotationDO::getQuotationNo, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(QuotationDO::getCustomerId, customerIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(QuotationDO::getId, byModel);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = quotationMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.orderByDesc(QuotationDO::getCreateTime).orderByDesc(QuotationDO::getId)
                .last("LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(quotationMapper.selectList(w)));
    }

    @Override
    public QuotationStatsVO stats() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        QuotationStatsVO vo = new QuotationStatsVO();
        vo.setDraftCount(quotationMapper.selectCount(scoped().eq(QuotationDO::getStatus, QuotationConstants.STATUS_DRAFT)));
        List<QuotationDO> sent = quotationMapper.selectList(scoped().select(QuotationDO::getSentAt)
                .eq(QuotationDO::getStatus, QuotationConstants.STATUS_SENT));
        vo.setSentCount((long) sent.size());
        vo.setOldestSentDays(sent.stream().map(QuotationDO::getSentAt).filter(Objects::nonNull).min(LocalDateTime::compareTo)
                .map(t -> java.time.Duration.between(t, LocalDateTime.now()).toDays()).orElse(0L));
        List<QuotationDO> won = quotationMapper.selectList(scoped()
                .select(QuotationDO::getTotalAmountCny, QuotationDO::getNetProfitCny, QuotationDO::getMarginRate)
                .in(QuotationDO::getStatus, QuotationConstants.STATUS_WON, QuotationConstants.STATUS_PARTIAL)
                .ge(QuotationDO::getClosedAt, monthStart));
        vo.setMonthWonCount((long) won.size());
        vo.setMonthWonAmountCny(won.stream().map(QuotationDO::getTotalAmountCny).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setMonthWonNetProfitCny(won.stream().map(QuotationDO::getNetProfitCny).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<BigDecimal> margins = won.stream().map(QuotationDO::getMarginRate).filter(Objects::nonNull).toList();
        vo.setMonthAvgMargin(margins.isEmpty() ? null : margins.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(margins.size()), 2, java.math.RoundingMode.HALF_UP));
        List<Long> monthIds = quotationMapper.selectList(scoped().select(QuotationDO::getId)
                        .ne(QuotationDO::getStatus, QuotationConstants.STATUS_VOID)
                        .ge(QuotationDO::getCreateTime, monthStart))
                .stream().map(QuotationDO::getId).toList();
        vo.setMonthBelowFloorCount(monthIds.isEmpty() ? 0L : itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .select(QuotationItemDO::getQuotationId)
                        .in(QuotationItemDO::getQuotationId, monthIds)
                        .isNotNull(QuotationItemDO::getFloorMargin)
                        .apply("margin_rate < floor_margin")
                        .eq(QuotationItemDO::getIsCurrent, 1)
                        .isNull(QuotationItemDO::getDeletedAt))
                .stream().map(QuotationItemDO::getQuotationId).distinct().count());
        return vo;
    }

    /** 当前租户、数据范围内、未删除的报价单 */
    private LambdaQueryWrapper<QuotationDO> scoped() {
        LambdaQueryWrapper<QuotationDO> w = new LambdaQueryWrapper<QuotationDO>()
                .eq(QuotationDO::getTenantId, tenantId())
                .isNull(QuotationDO::getDeletedAt);
        return dataScopeResolver.current().apply(w, QuotationDO::getOwnerId);
    }

    @Override
    public List<QuotationListVO> byInquiry(Long inquiryId) {
        CustomerInquiryDO inquiry = inquiryId == null ? null : inquiryMapper.selectById(inquiryId);
        DataScope scope = dataScopeResolver.current();
        if (inquiry == null || inquiry.getDeletedAt() != null || !Objects.equals(inquiry.getTenantId(), tenantId())
                || !scope.canSee(inquiry.getOwnerId())) {
            throw new BizException("客户询盘不存在");
        }
        Set<Long> ids = itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                        .select(QuotationItemDO::getQuotationId)
                        .eq(QuotationItemDO::getCustomerInquiryId, inquiryId)
                        .eq(QuotationItemDO::getIsCurrent, 1)
                        .isNull(QuotationItemDO::getDeletedAt))
                .stream().map(QuotationItemDO::getQuotationId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return toListVos(quotationMapper.selectList(new LambdaQueryWrapper<QuotationDO>()
                .in(QuotationDO::getId, ids)
                .isNull(QuotationDO::getDeletedAt)
                .orderByDesc(QuotationDO::getCreateTime)
                .orderByDesc(QuotationDO::getId)));
    }

    private List<QuotationListVO> toListVos(List<QuotationDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(QuotationDO::getId).toList();
        List<QuotationItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .select(QuotationItemDO::getQuotationId, QuotationItemDO::getCustomerInquiryId, QuotationItemDO::getQuantity)
                .in(QuotationItemDO::getQuotationId, ids)
                .eq(QuotationItemDO::getIsCurrent, 1)
                .isNull(QuotationItemDO::getDeletedAt));
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Integer> quantities = new HashMap<>();
        Map<Long, Set<Long>> inquiriesByQuotation = new HashMap<>();
        for (QuotationItemDO i : items) {
            counts.merge(i.getQuotationId(), 1, Integer::sum);
            quantities.merge(i.getQuotationId(), nz(i.getQuantity()), Integer::sum);
            inquiriesByQuotation.computeIfAbsent(i.getQuotationId(), k -> new TreeSet<>()).add(i.getCustomerInquiryId());
        }
        Map<Long, String> codes = lookups.inquiryCodes(items.stream().map(QuotationItemDO::getCustomerInquiryId).distinct().toList());
        Map<Long, CustomerDO> customers = lookups.customers(rows.stream().map(QuotationDO::getCustomerId).toList());
        Map<Long, String> users = lookups.userNames(rows.stream().map(QuotationDO::getOwnerId).toList());
        Map<Long, Integer> customerTypes = lookups.customerTypes(inquiriesByQuotation);
        List<QuotationListVO> list = new ArrayList<>(rows.size());
        for (QuotationDO q : rows) {
            QuotationListVO vo = new QuotationListVO();
            vo.setId(q.getId());
            vo.setQuotationNo(q.getQuotationNo());
            vo.setCustomerId(q.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(customers.get(q.getCustomerId())));
            vo.setCustomerCountry(customers.get(q.getCustomerId()) == null ? null : customers.get(q.getCustomerId()).getCountry());
            vo.setCustomerType(customerTypes.get(q.getId()));
            vo.setTotalQuantity(quantities.getOrDefault(q.getId(), 0));
            vo.setItemCount(counts.getOrDefault(q.getId(), 0));
            vo.setCurrencyCode(q.getCurrencyCode());
            vo.setTotalAmount(q.getTotalAmount());
            vo.setMarginRate(q.getMarginRate());
            vo.setStatus(q.getStatus());
            vo.setStatusName(QuotationConstants.STATUS_NAMES.get(q.getStatus()));
            vo.setLostReasonName(StringUtils.hasText(q.getLostReasonName()) ? q.getLostReasonName() : null);
            vo.setInquiryCodes(inquiriesByQuotation.getOrDefault(q.getId(), Set.of()).stream().map(codes::get)
                    .filter(Objects::nonNull).toList());
            vo.setOwnerId(q.getOwnerId());
            vo.setOwnerName(users.get(q.getOwnerId()));
            vo.setCreateTime(q.getCreateTime());
            vo.setSentAt(q.getSentAt());
            vo.setCurrentVersionNo(q.getCurrentVersionNo());
            vo.setEditingVersionNo(q.getEditingVersionNo());
            list.add(vo);
        }
        return list;
    }

    // ---------------------------------------------------------------- 内部

    /** 数据范围内、可报价状态的客户询盘 */
    private Map<Long, CustomerInquiryDO> visibleInquiries(Collection<Long> ids) {
        DataScope scope = dataScopeResolver.current();
        Map<Long, CustomerInquiryDO> map = new LinkedHashMap<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            CustomerInquiryDO i = id == null ? null : inquiryMapper.selectById(id);
            if (i == null || i.getDeletedAt() != null || !Objects.equals(i.getTenantId(), tenantId()) || !scope.canSee(i.getOwnerId())) {
                throw new BizException("客户询盘不存在");
            }
            if (!CREATABLE_INQUIRY_STATUSES.contains(i.getStatus())) {
                throw new BizException("客户询盘 " + i.getInquiryCode() + " 当前状态不能报价");
            }
            map.put(id, i);
        }
        return map;
    }

    private static Long sameCustomer(Collection<CustomerInquiryDO> inquiries) {
        Set<Long> customers = new HashSet<>();
        inquiries.forEach(i -> customers.add(i.getCustomerId()));
        if (customers.size() != 1) {
            throw new BizException("不同客户需要分开报价");
        }
        return customers.iterator().next();
    }

    private List<InquiryItemDO> activeItems(List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        List<InquiryItemDO> items = inquiryItemMapper.selectBatchIds(unique).stream()
                .filter(i -> i.getDeletedAt() == null && Objects.equals(i.getTenantId(), tenantId())).toList();
        if (items.size() != unique.size()) {
            throw new BizException("部分型号不存在，请刷新后再试");
        }
        Map<Long, InquiryItemDO> byId = items.stream().collect(Collectors.toMap(InquiryItemDO::getId, i -> i));
        return unique.stream().map(byId::get).toList();
    }

    private static String modelOf(InquiryItemDO item) {
        return StringUtils.hasText(item.getConfirmedModel()) ? item.getConfirmedModel() : nz(item.getOriginalModel());
    }

    private Long currentUserId() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
