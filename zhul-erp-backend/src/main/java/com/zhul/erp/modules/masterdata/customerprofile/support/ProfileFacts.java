package com.zhul.erp.modules.masterdata.customerprofile.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CustomerProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CountryProfileDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CountryProfileMapper;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerContactMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 准备完整度与系统推导要用的数据：联系人、国家资料、询盘里的常用品牌与询盘统计；并把完整度存回客户表 */
@Component
@RequiredArgsConstructor
public class ProfileFacts {

    private static final String BRANDS_SUBQUERY = "SELECT 1 FROM inquiry_item ii WHERE ii.customer_inquiry_id = customer_inquiry.id "
            + "AND ii.deleted_at IS NULL AND ii.brand <> ''";

    private final CustomerContactMapper contactMapper;
    private final CountryProfileMapper countryMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final CustomerMapper customerMapper;
    private final com.zhul.erp.modules.inquiry.support.CurrentUserResolver currentUser;
    private final com.zhul.erp.modules.masterdata.customerprofile.repository.CustomerStatsMapper statsMapper;

    public List<CustomerContactDO> contacts(Long customerId) {
        return contactMapper.selectList(new LambdaQueryWrapper<CustomerContactDO>()
                .eq(CustomerContactDO::getCustomerId, customerId)
                .isNull(CustomerContactDO::getDeletedAt)
                .orderByDesc(CustomerContactDO::getIsPrimary)
                .orderByAsc(CustomerContactDO::getId));
    }

    public CountryProfileDO country(int tenantId, String country) {
        if (country == null || country.isBlank()) {
            return null;
        }
        return countryMapper.selectOne(new LambdaQueryWrapper<CountryProfileDO>()
                .eq(CountryProfileDO::getTenantId, tenantId)
                .eq(CountryProfileDO::getCountry, country)
                .isNull(CountryProfileDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    public boolean hasDerivedBrands(Long customerId) {
        return !customersWithBrands(List.of(customerId)).isEmpty();
    }

    /** 常用品牌：这个客户全部询盘型号按品牌键计数（同一品牌的不同写法合并），取前 limit 个 */
    public List<CustomerProfileVO.BrandCount> topBrands(Long customerId, int limit) {
        List<CustomerProfileVO.BrandCount> out = new ArrayList<>();
        for (Map<String, Object> r : statsMapper.topBrands(customerId, limit)) {
            CustomerProfileVO.BrandCount b = new CustomerProfileVO.BrandCount();
            b.setName(String.valueOf(r.get("name")));
            b.setCount(((Number) r.get("cnt")).longValue());
            out.add(b);
        }
        return out;
    }

    /** [询盘数, 首次询盘日期, 最近询盘日期] */
    public Object[] inquiryStats(Long customerId) {
        Map<String, Object> r = inquiryMapper.selectMaps(new QueryWrapper<CustomerInquiryDO>()
                .select("COUNT(*) AS cnt", "MIN(inquiry_date) AS first_date", "MAX(inquiry_date) AS last_date")
                .eq("customer_id", customerId)
                .isNull("deleted_at")).get(0);
        return new Object[] {((Number) r.get("cnt")).longValue(), toDate(r.get("first_date")), toDate(r.get("last_date"))};
    }

    public ProfileCompleteness.Result evaluate(CustomerDO c) {
        return ProfileCompleteness.evaluate(c, contacts(c.getId()), country(c.getTenantId(), c.getCountry()), hasDerivedBrands(c.getId()));
    }

    /** 重算一个客户的完整度并存回（只改 profile_mask，不动更新时间） */
    public ProfileCompleteness.Result refresh(CustomerDO c) {
        ProfileCompleteness.Result r = evaluate(c);
        saveMask(c, r.mask());
        return r;
    }

    public void refresh(Long customerId) {
        CustomerDO c = customerMapper.selectById(customerId);
        if (c != null && c.getDeletedAt() == null) {
            refresh(c);
        }
    }

    /** 批量重算（国家资料变更、每日任务）：一次取齐联系人、国家资料与询盘品牌 */
    public int refreshAll(int tenantId, List<CustomerDO> customers) {
        if (customers.isEmpty()) {
            return 0;
        }
        List<Long> ids = customers.stream().map(CustomerDO::getId).toList();
        Map<Long, List<CustomerContactDO>> contacts = new java.util.HashMap<>();
        for (List<Long> part : partition(ids)) {
            contactMapper.selectList(new LambdaQueryWrapper<CustomerContactDO>()
                            .in(CustomerContactDO::getCustomerId, part)
                            .isNull(CustomerContactDO::getDeletedAt))
                    .forEach(x -> contacts.computeIfAbsent(x.getCustomerId(), k -> new ArrayList<>()).add(x));
        }
        Map<String, CountryProfileDO> countries = countryMapper.selectList(new LambdaQueryWrapper<CountryProfileDO>()
                        .eq(CountryProfileDO::getTenantId, tenantId)
                        .isNull(CountryProfileDO::getDeletedAt))
                .stream().collect(Collectors.toMap(CountryProfileDO::getCountry, Function.identity(), (a, b) -> a));
        Set<Long> withBrands = customersWithBrands(ids);
        int changed = 0;
        for (CustomerDO c : customers) {
            ProfileCompleteness.Result r = ProfileCompleteness.evaluate(c, contacts.getOrDefault(c.getId(), List.of()),
                    countries.get(c.getCountry()), withBrands.contains(c.getId()));
            if (saveMask(c, r.mask())) {
                changed++;
            }
        }
        return changed;
    }

    private boolean saveMask(CustomerDO c, int mask) {
        if (Objects.equals(c.getProfileMask(), mask)) {
            return false;
        }
        LambdaUpdateWrapper<CustomerDO> w = new LambdaUpdateWrapper<CustomerDO>()
                .eq(CustomerDO::getId, c.getId())
                .set(CustomerDO::getProfileMask, mask);
        // 档案补全「今天已补完」：变为 5/5 时记下时间和操作人，降到 5 以下时清空
        boolean done = mask == ProfileField.ALL_DONE;
        boolean wasDone = Objects.equals(c.getProfileMask(), ProfileField.ALL_DONE);
        if (done && !wasDone) {
            Long me = currentUser.resolve();
            c.setProfileDoneAt(LocalDateTime.now());
            c.setProfileDoneBy(me == null ? 0L : me);
            w.set(CustomerDO::getProfileDoneAt, c.getProfileDoneAt()).set(CustomerDO::getProfileDoneBy, c.getProfileDoneBy());
        } else if (!done && wasDone) {
            c.setProfileDoneAt(null);
            c.setProfileDoneBy(0L);
            w.set(CustomerDO::getProfileDoneAt, null).set(CustomerDO::getProfileDoneBy, 0L);
        }
        customerMapper.update(null, w);
        c.setProfileMask(mask);
        return true;
    }

    private Set<Long> customersWithBrands(Collection<Long> ids) {
        Set<Long> out = new HashSet<>();
        for (List<Long> part : partition(new ArrayList<>(ids))) {
            inquiryMapper.selectObjs(new QueryWrapper<CustomerInquiryDO>()
                            .select("DISTINCT customer_id")
                            .in("customer_id", part)
                            .isNull("deleted_at")
                            .exists(BRANDS_SUBQUERY))
                    .forEach(o -> out.add(((Number) o).longValue()));
        }
        return out;
    }

    private static List<List<Long>> partition(List<Long> ids) {
        List<List<Long>> parts = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            parts.add(ids.subList(i, Math.min(ids.size(), i + 500)));
        }
        return parts;
    }

    private static LocalDate toDate(Object v) {
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        return null;
    }
}
