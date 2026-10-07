package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.entity.CustomerPartyDO;
import com.zhul.erp.modules.masterdata.repository.CustomerPartyMapper;
import com.zhul.erp.modules.product.entity.ProductCustomsDO;
import com.zhul.erp.modules.product.repository.ProductCustomsMapper;
import com.zhul.erp.modules.sales.dto.BankSnapshotDTO;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.dto.PartyOptionVO;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 新建 PI 时的默认值：买方 / 收货人（客户单证主体）、付款条件（客户付款方式）、HS 编码与原产国（商品海关信息） */
@Component
@RequiredArgsConstructor
public class PiDefaults {

    private final CustomerPartyMapper partyMapper;
    private final InquiryItemMapper inquiryItemMapper;
    private final ProductCustomsMapper customsMapper;

    /** 默认发票抬头；没有时用客户注册信息 */
    public PartyDTO buyer(CustomerDO c) {
        CustomerPartyDO p = defaultParty(c.getId(), CustomerConstants.PARTY_BILL_TO);
        if (p != null) {
            return toDto(p);
        }
        PartyDTO d = new PartyDTO();
        d.setName(c.getName());
        d.setCountry(c.getCountry());
        d.setState(c.getState());
        d.setCity(c.getCity());
        d.setPostcode(c.getPostcode());
        d.setAddress(c.getAddress());
        d.setTaxId(c.getTaxId());
        d.setContact(c.getContactName());
        d.setPhone(c.getContactPhone());
        d.setEmail(c.getContactEmail());
        return d;
    }

    /** 默认收货人；没有时为空 */
    public PartyDTO consignee(Long customerId) {
        CustomerPartyDO p = defaultParty(customerId, CustomerConstants.PARTY_CONSIGNEE);
        return p == null ? null : toDto(p);
    }

    /** 客户的发票抬头与收货人（默认在前） */
    public List<PartyOptionVO> options(Long customerId) {
        return partyMapper.selectList(new LambdaQueryWrapper<CustomerPartyDO>()
                        .eq(CustomerPartyDO::getCustomerId, customerId)
                        .in(CustomerPartyDO::getPartyType, CustomerConstants.PARTY_BILL_TO, CustomerConstants.PARTY_CONSIGNEE)
                        .isNull(CustomerPartyDO::getDeletedAt)
                        .orderByDesc(CustomerPartyDO::getIsDefault)
                        .orderByAsc(CustomerPartyDO::getId))
                .stream().map(p -> {
                    PartyOptionVO o = new PartyOptionVO();
                    PartyDTO d = toDto(p);
                    o.setPartyId(d.getPartyId());
                    o.setName(d.getName());
                    o.setCountry(d.getCountry());
                    o.setState(d.getState());
                    o.setCity(d.getCity());
                    o.setPostcode(d.getPostcode());
                    o.setAddress(d.getAddress());
                    o.setTaxId(d.getTaxId());
                    o.setContact(d.getContact());
                    o.setPhone(d.getPhone());
                    o.setEmail(d.getEmail());
                    o.setPartyType(p.getPartyType());
                    o.setIsDefault(Objects.equals(p.getIsDefault(), 1));
                    return o;
                }).toList();
    }

    /** 新填的买方 / 收货人存为客户单证主体；该类型还没有默认时成为默认 */
    public Long saveParty(Long customerId, int type, PartyDTO d) {
        List<CustomerPartyDO> existing = partyMapper.selectList(new LambdaQueryWrapper<CustomerPartyDO>()
                .eq(CustomerPartyDO::getCustomerId, customerId)
                .isNull(CustomerPartyDO::getDeletedAt));
        if (existing.size() >= CustomerConstants.MAX_PARTIES) {
            throw new BizException("客户的单证主体已达 " + CustomerConstants.MAX_PARTIES + " 条上限，请先在客户档案中整理");
        }
        boolean hasDefault = existing.stream().anyMatch(p -> p.getPartyType() == type && Objects.equals(p.getIsDefault(), 1));
        CustomerPartyDO p = new CustomerPartyDO();
        p.setTenantId(PiStore.tenantId());
        p.setCustomerId(customerId);
        p.setPartyType(type);
        p.setCompanyName(d.getName());
        p.setCountry(nz(d.getCountry()));
        p.setState(nz(d.getState()));
        p.setCity(nz(d.getCity()));
        p.setPostcode(nz(d.getPostcode()));
        p.setAddress(nz(d.getAddress()));
        p.setContactName(nz(d.getContact()));
        p.setPhone(nz(d.getPhone()));
        p.setEmail(nz(d.getEmail()));
        p.setTaxId(nz(d.getTaxId()));
        p.setDestinationPort("");
        p.setRemark("");
        p.setIsDefault(hasDefault ? 0 : 1);
        partyMapper.insert(p);
        return p.getId();
    }

    /** 按客户付款方式生成英文付款条件 */
    public static String paymentTerm(CustomerDO c) {
        Integer m = c.getPaymentMethod();
        int deposit = c.getDepositRatio() == null ? 30 : c.getDepositRatio();
        int days = c.getPaymentDays() == null ? 30 : c.getPaymentDays();
        if (m == null || m == 0) {
            return "T/T 100% in advance";
        }
        return switch (m) {
            case 1 -> "T/T 100% in advance";
            case 2 -> "T/T " + deposit + "% deposit, balance before shipment";
            case 3 -> "T/T " + deposit + "% deposit, balance against copy of B/L";
            case 4 -> "L/C at sight";
            case 5 -> "L/C " + days + " days";
            case 6 -> "D/P at sight";
            case 7 -> "D/A " + days + " days";
            case 8 -> "O/A " + days + " days";
            default -> "";
        };
    }

    /** 询盘型号 → 商品海关信息（HS 编码、原产国） */
    public Map<Long, ProductCustomsDO> customsByInquiryItem(Collection<Long> inquiryItemIds) {
        if (inquiryItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> productByItem = new HashMap<>();
        for (InquiryItemDO i : inquiryItemMapper.selectBatchIds(inquiryItemIds)) {
            if (i.getProductId() != null) {
                productByItem.put(i.getId(), i.getProductId());
            }
        }
        if (productByItem.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductCustomsDO> byProduct = customsMapper.selectList(new LambdaQueryWrapper<ProductCustomsDO>()
                        .in(ProductCustomsDO::getProductId, productByItem.values())
                        .isNull(ProductCustomsDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductCustomsDO::getProductId, p -> p, (a, b) -> a));
        Map<Long, ProductCustomsDO> result = new HashMap<>();
        productByItem.forEach((item, product) -> {
            ProductCustomsDO c = byProduct.get(product);
            if (c != null) {
                result.put(item, c);
            }
        });
        return result;
    }

    public static BankSnapshotDTO bankSnapshot(BankAccountDO a) {
        BankSnapshotDTO b = new BankSnapshotDTO();
        b.setId(a.getId());
        b.setCurrencyCode(a.getCurrencyCode());
        b.setBankName(a.getBankName());
        b.setAccountName(a.getAccountName());
        b.setAccountNo(a.getAccountNo());
        b.setSwiftCode(a.getSwiftCode());
        b.setCountry(a.getCountry());
        b.setBankAddress(a.getBankAddress());
        b.setBankCode(a.getBankCode());
        b.setBranchCode(a.getBranchCode());
        return b;
    }

    /** 去掉首尾空格、空串转 null */
    public static PartyDTO normalize(PartyDTO d) {
        if (d == null || !StringUtils.hasText(d.getName())) {
            return null;
        }
        PartyDTO n = new PartyDTO();
        n.setPartyId(d.getPartyId());
        n.setName(d.getName().trim());
        n.setCountry(trim(d.getCountry()));
        n.setState(trim(d.getState()));
        n.setCity(trim(d.getCity()));
        n.setPostcode(trim(d.getPostcode()));
        n.setAddress(trim(d.getAddress()));
        n.setTaxId(trim(d.getTaxId()));
        n.setContact(trim(d.getContact()));
        n.setPhone(trim(d.getPhone()));
        n.setEmail(trim(d.getEmail()));
        return n;
    }

    private CustomerPartyDO defaultParty(Long customerId, int type) {
        List<CustomerPartyDO> list = partyMapper.selectList(new LambdaQueryWrapper<CustomerPartyDO>()
                .eq(CustomerPartyDO::getCustomerId, customerId)
                .eq(CustomerPartyDO::getPartyType, type)
                .isNull(CustomerPartyDO::getDeletedAt)
                .orderByDesc(CustomerPartyDO::getIsDefault)
                .orderByAsc(CustomerPartyDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    private static PartyDTO toDto(CustomerPartyDO p) {
        PartyDTO d = new PartyDTO();
        d.setPartyId(p.getId());
        d.setName(p.getCompanyName());
        d.setCountry(p.getCountry());
        d.setState(p.getState());
        d.setCity(p.getCity());
        d.setPostcode(p.getPostcode());
        d.setAddress(p.getAddress());
        d.setTaxId(p.getTaxId());
        d.setContact(p.getContactName());
        d.setPhone(p.getPhone());
        d.setEmail(p.getEmail());
        return d;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
