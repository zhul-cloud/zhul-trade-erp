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
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private final DictItemService dictItemService;

    /** 默认发票抬头；没有时用客户注册信息 */
    public PartyDTO buyer(CustomerDO c) {
        CustomerPartyDO p = defaultParty(c.getId(), CustomerConstants.PARTY_BILL_TO);
        return p != null ? toDto(p) : registrationOf(c);
    }

    /** 客户注册信息（英文名称、地址、税号、联系人）作为单证主体 */
    private static PartyDTO registrationOf(CustomerDO c) {
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

    /** 客户的发票抬头与收货人（默认在前），最后附一条客户注册信息供选作买方 */
    public List<PartyOptionVO> options(CustomerDO customer) {
        Long customerId = customer.getId();
        List<PartyOptionVO> list = new ArrayList<>(partyMapper.selectList(new LambdaQueryWrapper<CustomerPartyDO>()
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
                    o.setRegistration(false);
                    return o;
                }).toList());
        PartyDTO reg = registrationOf(customer);
        PartyOptionVO o = new PartyOptionVO();
        o.setName(reg.getName());
        o.setCountry(reg.getCountry());
        o.setState(reg.getState());
        o.setCity(reg.getCity());
        o.setPostcode(reg.getPostcode());
        o.setAddress(reg.getAddress());
        o.setTaxId(reg.getTaxId());
        o.setContact(reg.getContact());
        o.setPhone(reg.getPhone());
        o.setEmail(reg.getEmail());
        o.setPartyType(CustomerConstants.PARTY_BILL_TO);
        o.setIsDefault(false);
        o.setRegistration(true);
        list.add(o);
        return list;
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

    public static final String DICT_PAYMENT_TERM = "pi_payment_term";
    public static final String DICT_PORT = "port_of_shipment";
    public static final String DICT_DELIVERY = "pi_delivery_time";
    public static final String DICT_LEAD_TIME = "inquiry_lead_time";
    public static final String FALLBACK_PAYMENT_TERM = "T/T 100% in advance";
    public static final String FALLBACK_PORT = "Hong Kong";

    /** 字典默认项的英文名（没有英文名用中文名）；字典没有默认项时用 fallback */
    public String defaultText(String dictType, String fallback) {
        return dictItemService.listByDictType(dictType).stream()
                .filter(d -> Objects.equals(d.getStatus(), 1) && Objects.equals(d.getIsDefault(), 1))
                .findFirst().map(PiDefaults::text).orElse(fallback);
    }

    /** 交期：取型号中排序最靠后（最长）的货期，找编码相同的交期字典项；找不到时用交期字典的默认项 */
    public String deliveryTime(List<Integer> leadTimes) {
        Map<String, DictItemVO> leadByValue = new HashMap<>();
        for (DictItemVO d : dictItemService.listByDictType(DICT_LEAD_TIME)) {
            leadByValue.put(d.getItemValue(), d);
        }
        DictItemVO longest = leadTimes.stream().filter(Objects::nonNull).map(v -> leadByValue.get(String.valueOf(v)))
                .filter(Objects::nonNull)
                .max(Comparator.comparing(d -> d.getSortOrder() == null ? 0 : d.getSortOrder()))
                .orElse(null);
        if (longest != null) {
            for (DictItemVO d : dictItemService.listByDictType(DICT_DELIVERY)) {
                if (Objects.equals(d.getStatus(), 1) && longest.getItemCode().equals(d.getItemCode())) {
                    return text(d);
                }
            }
        }
        return defaultText(DICT_DELIVERY, "");
    }

    private static String text(DictItemVO d) {
        return StringUtils.hasText(d.getItemNameEn()) ? d.getItemNameEn() : d.getItemName();
    }

    /** 收货人与买方相同时的快照（不关联单证主体） */
    public static PartyDTO sameAs(PartyDTO buyer) {
        PartyDTO c = normalize(buyer);
        if (c != null) {
            c.setPartyId(null);
        }
        return c;
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
