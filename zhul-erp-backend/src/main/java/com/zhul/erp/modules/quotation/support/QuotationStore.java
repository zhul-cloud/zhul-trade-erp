package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.dto.SaveQuotationRequest;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationFeeDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.entity.QuotationVersionDO;
import com.zhul.erp.modules.quotation.repository.QuotationFeeMapper;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.quotation.repository.QuotationVersionMapper;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 报价单读取（按数据范围）与「把编辑内容套到报价单上」（保存与预览共用，只改内存对象） */
@Component
@RequiredArgsConstructor
public class QuotationStore {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000000");

    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper itemMapper;
    private final QuotationFeeMapper feeMapper;
    private final QuotationVersionMapper versionMapper;
    private final DataScopeResolver dataScopeResolver;
    private final DictItemService dictItemService;
    private final ExchangeRateService exchangeRateService;

    /** 当前用户数据范围内的报价单（按报价单创建人） */
    public QuotationDO visible(Long id) {
        QuotationDO q = id == null ? null : quotationMapper.selectById(id);
        if (q == null || q.getDeletedAt() != null || !Objects.equals(q.getTenantId(), tenantId())
                || !dataScopeResolver.current().canSee(q.getOwnerId())) {
            throw new BizException("报价单不存在");
        }
        return q;
    }

    public QuotationDO requireDraft(Long id) {
        QuotationDO q = visible(id);
        if (q.getStatus() != QuotationConstants.STATUS_DRAFT) {
            throw new BizException(notEditable(q));
        }
        return q;
    }

    private static String notEditable(QuotationDO q) {
        return q.getStatus() == QuotationConstants.STATUS_SENT
                ? "报价单已发送，需要改价请点「修改（出新版本）」"
                : "报价单已" + QuotationConstants.STATUS_NAMES.get(q.getStatus()) + "，不能再修改";
    }

    /**
     * 正在编辑的内容：header 为表头（修改中的新版本时是叠加了版本表头的副本），version 为空表示草稿报价单本身。
     * 新增的型号行、费用行用 versionNo 与 current 作为版本号与当前版本标记。
     */
    public record Edit(QuotationDO header, int versionNo, QuotationVersionDO version) {
        public boolean revision() {
            return version != null;
        }

        public int current() {
            return version == null ? 1 : 0;
        }
    }

    /** 可编辑的目标：草稿报价单，或已发送报价单修改中的新版本 */
    public Edit editable(Long id) {
        QuotationDO q = visible(id);
        if (q.getStatus() == QuotationConstants.STATUS_DRAFT) {
            return new Edit(q, q.getCurrentVersionNo(), null);
        }
        if (q.getStatus() == QuotationConstants.STATUS_SENT && q.getEditingVersionNo() != null) {
            QuotationVersionDO v = version(id, q.getEditingVersionNo());
            return new Edit(overlay(q, v), v.getVersionNo(), v);
        }
        throw new BizException(notEditable(q));
    }

    /** 把编辑后的表头写回：草稿写报价单，新版本写版本表 */
    public void saveHeader(Edit e) {
        if (!e.revision()) {
            quotationMapper.updateById(e.header());
            return;
        }
        QuotationVersionDO v = e.version();
        copyHeader(e.header(), v);
        versionMapper.updateById(v);
    }

    /** 要查看的版本：不指定时为修改中的版本，没有时为当前版本；已放弃的版本不能查看 */
    public record View(QuotationDO header, int versionNo, List<QuotationItemDO> items, List<QuotationFeeDO> fees) {
    }

    public View view(Long id, Integer versionNo) {
        QuotationDO q = visible(id);
        int no = versionNo != null ? versionNo : q.getEditingVersionNo() != null ? q.getEditingVersionNo() : q.getCurrentVersionNo();
        if (no == q.getCurrentVersionNo()) {
            return new View(q, no, items(id), fees(id));
        }
        QuotationVersionDO v = versionMapper.selectOne(new LambdaQueryWrapper<QuotationVersionDO>()
                .eq(QuotationVersionDO::getQuotationId, id)
                .eq(QuotationVersionDO::getVersionNo, no)
                .ne(QuotationVersionDO::getStatus, QuotationConstants.VERSION_ABANDONED)
                .isNull(QuotationVersionDO::getDeletedAt));
        if (v == null) {
            throw new BizException("版本不存在");
        }
        return new View(overlay(q, v), no, items(id, no), fees(id, no));
    }

    public QuotationVersionDO version(Long quotationId, int versionNo) {
        QuotationVersionDO v = versionMapper.selectOne(new LambdaQueryWrapper<QuotationVersionDO>()
                .eq(QuotationVersionDO::getQuotationId, quotationId)
                .eq(QuotationVersionDO::getVersionNo, versionNo)
                .isNull(QuotationVersionDO::getDeletedAt));
        if (v == null) {
            throw new BizException("版本不存在");
        }
        return v;
    }

    /** 未放弃的版本，按版本号升序 */
    public List<QuotationVersionDO> versions(Long quotationId) {
        return versionMapper.selectList(new LambdaQueryWrapper<QuotationVersionDO>()
                .eq(QuotationVersionDO::getQuotationId, quotationId)
                .ne(QuotationVersionDO::getStatus, QuotationConstants.VERSION_ABANDONED)
                .isNull(QuotationVersionDO::getDeletedAt)
                .orderByAsc(QuotationVersionDO::getVersionNo));
    }

    /** 报价单表头叠加版本表头后的副本（不落库） */
    public static QuotationDO overlay(QuotationDO q, QuotationVersionDO v) {
        QuotationDO h = new QuotationDO();
        BeanUtils.copyProperties(q, h);
        h.setExchangeRate(v.getExchangeRate());
        h.setRateTime(v.getRateTime());
        h.setIncoterm(v.getIncoterm());
        h.setIncotermPlace(v.getIncotermPlace());
        h.setValidUntil(v.getValidUntil());
        h.setRemark(v.getRemark());
        h.setItemAmount(v.getItemAmount());
        h.setFeeAmount(v.getFeeAmount());
        h.setTotalAmount(v.getTotalAmount());
        h.setTotalAmountCny(v.getTotalAmountCny());
        h.setNetProfit(v.getNetProfit());
        h.setNetProfitCny(v.getNetProfitCny());
        h.setMarginRate(v.getMarginRate());
        return h;
    }

    /** 表头的版本相关字段：报价单 → 版本 */
    public static void copyHeader(QuotationDO from, QuotationVersionDO to) {
        to.setExchangeRate(from.getExchangeRate());
        to.setRateTime(from.getRateTime());
        to.setIncoterm(from.getIncoterm());
        to.setIncotermPlace(from.getIncotermPlace());
        to.setValidUntil(from.getValidUntil());
        to.setRemark(from.getRemark());
        to.setItemAmount(from.getItemAmount());
        to.setFeeAmount(from.getFeeAmount());
        to.setTotalAmount(from.getTotalAmount());
        to.setTotalAmountCny(from.getTotalAmountCny());
        to.setNetProfit(from.getNetProfit());
        to.setNetProfitCny(from.getNetProfitCny());
        to.setMarginRate(from.getMarginRate());
    }

    /** 当前版本的型号行 */
    public List<QuotationItemDO> items(Long quotationId) {
        return itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .eq(QuotationItemDO::getQuotationId, quotationId)
                .eq(QuotationItemDO::getIsCurrent, 1)
                .isNull(QuotationItemDO::getDeletedAt)
                .orderByAsc(QuotationItemDO::getLineNo)
                .orderByAsc(QuotationItemDO::getId));
    }

    public List<QuotationItemDO> items(Long quotationId, int versionNo) {
        return itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .eq(QuotationItemDO::getQuotationId, quotationId)
                .eq(QuotationItemDO::getVersionNo, versionNo)
                .isNull(QuotationItemDO::getDeletedAt)
                .orderByAsc(QuotationItemDO::getLineNo)
                .orderByAsc(QuotationItemDO::getId));
    }

    /** 当前版本的费用行 */
    public List<QuotationFeeDO> fees(Long quotationId) {
        return feeMapper.selectList(new LambdaQueryWrapper<QuotationFeeDO>()
                .eq(QuotationFeeDO::getQuotationId, quotationId)
                .eq(QuotationFeeDO::getIsCurrent, 1)
                .isNull(QuotationFeeDO::getDeletedAt)
                .orderByAsc(QuotationFeeDO::getSortOrder)
                .orderByAsc(QuotationFeeDO::getId));
    }

    public List<QuotationFeeDO> fees(Long quotationId, int versionNo) {
        return feeMapper.selectList(new LambdaQueryWrapper<QuotationFeeDO>()
                .eq(QuotationFeeDO::getQuotationId, quotationId)
                .eq(QuotationFeeDO::getVersionNo, versionNo)
                .isNull(QuotationFeeDO::getDeletedAt)
                .orderByAsc(QuotationFeeDO::getSortOrder)
                .orderByAsc(QuotationFeeDO::getId));
    }

    public QuotationRenderModels.Labels labels() {
        return new QuotationRenderModels.Labels(
                dictItemService.intLabels(QuotationConstants.DICT_CONDITION),
                dictItemService.intEnLabels(QuotationConstants.DICT_CONDITION),
                dictItemService.intLabels(QuotationConstants.DICT_LEAD_TIME),
                dictItemService.intEnLabels(QuotationConstants.DICT_LEAD_TIME));
    }

    /** 编辑结果：调整后的型号行（按请求顺序）、被删掉的行、新的费用行 */
    public record Applied(List<QuotationItemDO> items, List<QuotationItemDO> removed, List<QuotationFeeDO> fees) {
    }

    /**
     * 把编辑内容套到报价单及其型号行上（修改传入的对象），并重算每行与合计。
     * 币种变化时按新币种取当前系统汇率；型号行只能修改已有行（新增型号走「追加型号」）。
     */
    public Applied apply(QuotationDO q, int versionNo, List<QuotationItemDO> existing, SaveQuotationRequest req) {
        String currency = req.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (!CustomerConstants.CURRENCIES.contains(currency)) {
            throw new BizException("不支持的币种：" + req.getCurrencyCode());
        }
        if (!currency.equals(q.getCurrencyCode())) {
            if (q.getStatus() != QuotationConstants.STATUS_DRAFT) {
                throw new BizException("新版本不能改币种，要换币种请「复制为新报价单」");
            }
            ExchangeRateService.Snapshot rate = exchangeRateService.require(currency);
            q.setCurrencyCode(currency);
            q.setExchangeRate(rate.rate());
            q.setRateTime(rate.rateTime());
        }
        q.setIncoterm(trim(req.getIncoterm()));
        q.setIncotermPlace(trim(req.getIncotermPlace()));
        q.setValidUntil(req.getValidUntil());
        q.setRemark(trim(req.getRemark()));

        Map<Long, QuotationItemDO> byId = existing.stream().collect(Collectors.toMap(QuotationItemDO::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        Set<Integer> leadTimes = dictItemService.intLabels(QuotationConstants.DICT_LEAD_TIME).keySet();
        List<QuotationItemDO> items = new ArrayList<>(req.getItems().size());
        int lineNo = 1;
        for (SaveQuotationRequest.Item r : req.getItems()) {
            QuotationItemDO item = byId.get(r.getId());
            if (item == null || !seen.add(r.getId())) {
                throw new BizException("型号行不存在或重复，请刷新后再试");
            }
            String label = "第 " + lineNo + " 行（" + item.getModel() + "）";
            item.setLineNo(lineNo++);
            item.setDescription(trim(r.getDescription()));
            int lead = r.getLeadTime() == null ? 0 : r.getLeadTime();
            if (lead != 0 && !leadTimes.contains(lead)) {
                throw new BizException(label + "：货期不存在");
            }
            item.setLeadTime(lead);
            item.setWarranty(r.getWarranty() == null || r.getWarranty().isBlank() ? QuotationConstants.DEFAULT_WARRANTY : r.getWarranty().trim());
            item.setQuantity(r.getQuantity());
            int mode = item.getCostPrice() == null ? QuotationPricing.MODE_PRICE : r.getPricingMode();
            item.setPricingMode(mode);
            item.setMarginRate(r.getMarginRate());
            item.setMarkupAmount(r.getMarkupAmount());
            BigDecimal price = r.getUnitPrice();
            if (mode == QuotationPricing.MODE_PRICE && price == null) {
                price = BigDecimal.ZERO;
            }
            if (price != null && price.compareTo(MAX_AMOUNT) >= 0) {
                throw new BizException(label + "：售价过大，请检查");
            }
            item.setUnitPrice(price);
            try {
                QuotationCalculator.applyLine(item, q.getExchangeRate());
            } catch (BizException e) {
                throw new BizException(label + "：" + e.getMessage(), e);
            }
            items.add(item);
        }
        List<QuotationItemDO> removed = existing.stream().filter(i -> !seen.contains(i.getId())).toList();
        List<QuotationFeeDO> fees = new ArrayList<>();
        int sort = 1;
        for (SaveQuotationRequest.Fee f : req.getFees() == null ? List.<SaveQuotationRequest.Fee>of() : req.getFees()) {
            if (f.getAmount().signum() < 0) {
                throw new BizException("费用「" + f.getFeeName() + "」金额不能为负");
            }
            if (f.getAmount().compareTo(MAX_AMOUNT) >= 0) {
                throw new BizException("费用「" + f.getFeeName() + "」金额过大，请检查");
            }
            QuotationFeeDO fee = new QuotationFeeDO();
            fee.setTenantId(q.getTenantId());
            fee.setQuotationId(q.getId());
            fee.setVersionNo(versionNo);
            fee.setIsCurrent(versionNo == q.getCurrentVersionNo() ? 1 : 0);
            fee.setFeeName(f.getFeeName().trim());
            fee.setAmount(f.getAmount());
            fee.setSortOrder(sort++);
            fees.add(fee);
        }
        QuotationCalculator.applyTotals(q, items, fees);
        return new Applied(items, removed, fees);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
