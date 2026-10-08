package com.zhul.erp.modules.sales.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.dto.SavePiRequest;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 把编辑请求套用到版本与型号行上并重算（只改传入的对象，不落库）。
 * 保存时 persistParties = true，会把勾选「保存到客户档案」的买方 / 收货人写入客户单证主体；实时预览时为 false。
 */
@Component
@RequiredArgsConstructor
public class PiEditor {

    private static final int MAX_FEES = 20;

    private final PiDefaults defaults;
    private final BankAccountService bankAccountService;
    private final PiStore store;

    /** 套用结果：按请求顺序的型号行、新的费用行（未入库） */
    public record Applied(List<PiItemDO> items, List<PiFeeDO> fees) {
    }

    public Applied apply(ProformaInvoiceDO pi, PiVersionDO v, List<PiItemDO> existingItems, SavePiRequest req, boolean persistParties) {
        Map<Long, PiItemDO> existing = existingItems.stream().collect(Collectors.toMap(PiItemDO::getId, Function.identity()));
        List<PiItemDO> items = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int line = 1;
        for (SavePiRequest.Item r : req.getItems()) {
            PiItemDO i = existing.get(r.getId());
            if (i == null || !seen.add(r.getId())) {
                throw new BizException("型号行不存在，请刷新后再试");
            }
            if (r.getUnitPrice().signum() <= 0) {
                throw new BizException("第 " + line + " 行（" + i.getModel() + "）单价需要大于 0");
            }
            i.setLineNo(line++);
            i.setDescription(trim(r.getDescription()));
            if (r.getLeadTime() != null) {
                i.setLeadTime(r.getLeadTime());
            }
            i.setWarranty(StringUtils.hasText(r.getWarranty()) ? r.getWarranty().trim() : SalesConstants.DEFAULT_WARRANTY);
            i.setQuantity(r.getQuantity());
            i.setUnitPrice(r.getUnitPrice());
            i.setHsCode(trim(r.getHsCode()));
            i.setOriginCountry(trim(r.getOriginCountry()));
            i.setRemark(trim(r.getRemark()));
            items.add(i);
        }
        List<PiFeeDO> fees = new ArrayList<>();
        if (req.getFees() != null) {
            if (req.getFees().size() > MAX_FEES) {
                throw new BizException("费用行最多 " + MAX_FEES + " 行");
            }
            for (SavePiRequest.Fee f : req.getFees()) {
                if (f.getAmount().signum() < 0) {
                    throw new BizException("费用金额不能为负，优惠请用整单折扣");
                }
                PiFeeDO fee = new PiFeeDO();
                fee.setTenantId(pi.getTenantId());
                fee.setPiId(pi.getId());
                fee.setVersionId(v.getId());
                fee.setFeeName(f.getFeeName().trim());
                fee.setAmount(f.getAmount());
                fee.setRemark(trim(f.getRemark()));
                fees.add(fee);
            }
        }

        PartyDTO buyer = PiDefaults.normalize(req.getBuyer());
        if (buyer == null) {
            throw new BizException("请填写买方");
        }
        PartyDTO consignee = PiDefaults.normalize(req.getConsignee());
        if (persistParties && Boolean.TRUE.equals(req.getSaveBuyerToCustomer()) && buyer.getPartyId() == null) {
            buyer.setPartyId(defaults.saveParty(pi.getCustomerId(), CustomerConstants.PARTY_BILL_TO, buyer));
        }
        if (persistParties && consignee != null && Boolean.TRUE.equals(req.getSaveConsigneeToCustomer()) && consignee.getPartyId() == null) {
            consignee.setPartyId(defaults.saveParty(pi.getCustomerId(), CustomerConstants.PARTY_CONSIGNEE, consignee));
        }
        v.setBuyerJson(store.toJson(buyer));
        v.setBuyerPartyId(buyer.getPartyId());
        v.setConsigneeJson(store.toJson(consignee));
        v.setConsigneePartyId(consignee == null ? null : consignee.getPartyId());
        if (req.getValidUntil() != null && !req.getValidUntil().equals(v.getValidUntil())) {
            if (pi.getCreateTime() != null && req.getValidUntil().isBefore(pi.getCreateTime().toLocalDate())) {
                throw new BizException("有效期不能早于 PI 日期");
            }
            v.setValidUntil(req.getValidUntil());
        }
        v.setDeliveryTime(trim(req.getDeliveryTime()));
        v.setPaymentTerm(trim(req.getPaymentTerm()));
        String incoterm = trim(req.getIncoterm()).toUpperCase(Locale.ROOT);
        if (!incoterm.isEmpty() && !CustomerConstants.INCOTERMS.contains(incoterm)) {
            throw new BizException("贸易术语不正确：" + incoterm);
        }
        v.setIncoterm(incoterm);
        v.setIncotermPlace(trim(req.getIncotermPlace()));
        v.setPortOfShipment(trim(req.getPortOfShipment()));
        v.setRemark(trim(req.getRemark()));
        if (req.getBankAccountId() == null) {
            v.setBankAccountId(null);
            v.setBankAccountJson(null);
        } else if (!req.getBankAccountId().equals(v.getBankAccountId())) {
            BankAccountDO bank = bankAccountService.requireEnabled(req.getBankAccountId());
            if (!bank.getCurrencyCode().equals(pi.getCurrencyCode())) {
                throw new BizException("收款账户币种（" + bank.getCurrencyCode() + "）与 PI 币种（" + pi.getCurrencyCode() + "）不同");
            }
            v.setBankAccountId(bank.getId());
            v.setBankAccountJson(store.toJson(PiDefaults.bankSnapshot(bank)));
        }
        v.setDiscountType(req.getDiscountType() == null ? SalesConstants.DISCOUNT_NONE : req.getDiscountType());
        v.setDiscountValue(req.getDiscountValue() == null || v.getDiscountType() == SalesConstants.DISCOUNT_NONE
                ? BigDecimal.ZERO : req.getDiscountValue());

        for (PiItemDO i : items) {
            PiCalculator.applyLine(i, pi.getExchangeRate());
        }
        PiCalculator.applyTotals(v, items, fees, pi.getExchangeRate());
        return new Applied(items, fees);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
