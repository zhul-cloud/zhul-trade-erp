package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 批量查询展示用的名称，避免在列表里逐行查库 */
@Component
@RequiredArgsConstructor
public class InquiryLookups {

    private final UserBasicMapper userBasicMapper;
    private final CustomerMapper customerMapper;
    private final CustomerInquiryMapper customerInquiryMapper;

    public Map<Long, String> userNames(Collection<Long> ids) {
        List<Integer> keys = ids.stream().filter(Objects::nonNull).map(Long::intValue).distinct().toList();
        Map<Long, String> map = new HashMap<>(keys.size() * 2);
        if (keys.isEmpty()) {
            return map;
        }
        for (UserBasicDO u : userBasicMapper.selectBatchIds(keys)) {
            map.put(u.getId().longValue(), u.getName());
        }
        return map;
    }

    public Map<Long, CustomerDO> customers(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, CustomerDO> map = new HashMap<>(keys.size() * 2);
        if (!keys.isEmpty()) {
            customerMapper.selectBatchIds(keys).forEach(c -> map.put(c.getId(), c));
        }
        return map;
    }

    public static String customerName(CustomerDO c) {
        if (c == null) {
            return "";
        }
        return StringUtils.hasText(c.getName()) ? c.getName() : c.getContactName();
    }

    public Map<Long, String> inquiryCodes(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> map = new HashMap<>(keys.size() * 2);
        if (!keys.isEmpty()) {
            customerInquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                            .select(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryCode)
                            .in(CustomerInquiryDO::getId, keys))
                    .forEach(i -> map.put(i.getId(), i.getInquiryCode()));
        }
        return map;
    }

    /**
     * 单据的新 / 老客户：任一来源客户询盘为老客户即为老客户（报价单、PI、销售订单列表共用）。
     * inquiryIdsByDoc：单据 ID → 来源客户询盘 ID；返回单据 ID → 1-新客户、2-老客户（没有来源询盘的单据不在结果里）
     */
    public Map<Long, Integer> customerTypes(Map<Long, ? extends Collection<Long>> inquiryIdsByDoc) {
        List<Long> keys = inquiryIdsByDoc.values().stream().flatMap(Collection::stream).filter(Objects::nonNull).distinct().toList();
        Map<Long, Integer> typeOf = new HashMap<>(keys.size() * 2);
        if (!keys.isEmpty()) {
            customerInquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                            .select(CustomerInquiryDO::getId, CustomerInquiryDO::getCustomerType)
                            .in(CustomerInquiryDO::getId, keys))
                    .forEach(i -> typeOf.put(i.getId(), i.getCustomerType()));
        }
        Map<Long, Integer> result = new HashMap<>(inquiryIdsByDoc.size() * 2);
        inquiryIdsByDoc.forEach((doc, ids) -> {
            if (ids.stream().anyMatch(id -> Objects.equals(typeOf.get(id), InquiryConstants.CUSTOMER_RETURNING))) {
                result.put(doc, InquiryConstants.CUSTOMER_RETURNING);
            } else if (ids.stream().anyMatch(typeOf::containsKey)) {
                result.put(doc, InquiryConstants.CUSTOMER_NEW);
            }
        });
        return result;
    }

    /** 客户询盘 ID → 负责业务员 ID */
    public Map<Long, Long> inquiryOwners(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> map = new HashMap<>(keys.size() * 2);
        if (!keys.isEmpty()) {
            customerInquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                            .select(CustomerInquiryDO::getId, CustomerInquiryDO::getOwnerId)
                            .in(CustomerInquiryDO::getId, keys))
                    .forEach(i -> map.put(i.getId(), i.getOwnerId()));
        }
        return map;
    }

    /** 采购看到的询盘摘要：所属业务员、询盘等级、新老客户；客户名称对兼职采购不返回（为 null） */
    public record InquiryBrief(Long salesId, String salesName, Integer level, Integer customerType, String customerName,
                               java.time.LocalDate quoteDeadline, Integer inquiryStatus) {
    }

    /** 客户询盘 ID → 采购看到的摘要；hideCustomerName 为真时不带客户名称 */
    public Map<Long, InquiryBrief> briefs(Collection<Long> inquiryIds, boolean hideCustomerName) {
        List<Long> keys = inquiryIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, InquiryBrief> map = new HashMap<>(keys.size() * 2);
        if (keys.isEmpty()) {
            return map;
        }
        List<CustomerInquiryDO> inquiries = customerInquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                .select(CustomerInquiryDO::getId, CustomerInquiryDO::getOwnerId, CustomerInquiryDO::getLevel,
                        CustomerInquiryDO::getCustomerType, CustomerInquiryDO::getCustomerId, CustomerInquiryDO::getQuoteDeadline,
                        CustomerInquiryDO::getStatus)
                .in(CustomerInquiryDO::getId, keys));
        Map<Long, String> sales = userNames(inquiries.stream().map(CustomerInquiryDO::getOwnerId).toList());
        Map<Long, CustomerDO> customers = hideCustomerName ? Map.of()
                : customers(inquiries.stream().map(CustomerInquiryDO::getCustomerId).toList());
        for (CustomerInquiryDO i : inquiries) {
            map.put(i.getId(), new InquiryBrief(i.getOwnerId(), sales.get(i.getOwnerId()), i.getLevel(), i.getCustomerType(),
                    hideCustomerName ? null : customerName(customers.get(i.getCustomerId())), i.getQuoteDeadline(), i.getStatus()));
        }
        return map;
    }

    /** 兼职采购（内置角色）看不到客户名称 */
    public boolean isPartTime(Long userId) {
        if (userId == null) {
            return false;
        }
        UserBasicDO u = userBasicMapper.selectById(userId.intValue());
        return u != null && InquiryConstants.PART_TIME_ROLE.equals(u.getRoleCode());
    }
}
