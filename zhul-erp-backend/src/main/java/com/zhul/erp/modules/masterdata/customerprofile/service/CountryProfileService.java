package com.zhul.erp.modules.masterdata.customerprofile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CountryProfileRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CountryProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CountryProfileDO;
import com.zhul.erp.modules.masterdata.customerprofile.repository.CountryProfileMapper;
import com.zhul.erp.modules.masterdata.customerprofile.support.JsonArrays;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.product.support.CountryCatalog;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 国家资料（spec master-data/country-profile）：各公司按国家维护一次，所在国家的客户都带出 */
@Service
@RequiredArgsConstructor
public class CountryProfileService {

    public static final int FX_STRICT = 3;

    private final CountryProfileMapper mapper;
    private final CustomerMapper customerMapper;
    private final CountryCatalog countryCatalog;
    private final DictItemService dictItemService;
    private final ProfileFacts facts;
    private final LogService logService;

    /**
     * 列表：已维护的国家，加上本公司客户所在、还没维护的国家（id 为空）。没维护的排在前面，各自按客户数从多到少。
     * filter：unmaintained 只看未维护，strict 只看外汇管制严格。
     */
    public List<CountryProfileVO> list(String keyword, String filter) {
        int tenantId = tenantId();
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> r : customerMapper.selectMaps(new QueryWrapper<CustomerDO>()
                .select("country", "COUNT(*) AS cnt")
                .eq("tenant_id", tenantId)
                .isNull("deleted_at")
                .ne("country", "")
                .groupBy("country"))) {
            counts.put((String) r.get("country"), ((Number) r.get("cnt")).longValue());
        }
        List<CountryProfileVO> out = new ArrayList<>();
        Set<String> maintained = new HashSet<>();
        for (CountryProfileDO d : mapper.selectList(new LambdaQueryWrapper<CountryProfileDO>()
                .eq(CountryProfileDO::getTenantId, tenantId)
                .isNull(CountryProfileDO::getDeletedAt))) {
            maintained.add(d.getCountry());
            out.add(toVo(d, counts.getOrDefault(d.getCountry(), 0L)));
        }
        counts.forEach((country, n) -> {
            if (!maintained.contains(country)) {
                CountryProfileVO vo = new CountryProfileVO();
                vo.setCountry(country);
                vo.setCustomerCount(n);
                out.add(vo);
            }
        });
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        return out.stream()
                .filter(v -> kw == null || v.getCountry().toLowerCase(Locale.ROOT).contains(kw))
                .filter(v -> !"unmaintained".equals(filter) || v.getId() == null)
                .filter(v -> !"strict".equals(filter) || Objects.equals(v.getFxControl(), FX_STRICT))
                .sorted(Comparator.comparing((CountryProfileVO v) -> v.getId() != null)
                        .thenComparing(CountryProfileVO::getCustomerCount, Comparator.reverseOrder())
                        .thenComparing(CountryProfileVO::getCountry))
                .toList();
    }

    /** 新增或修改（按国家唯一）；保存后重算这个国家客户的完整度 */
    @Transactional(rollbackFor = Exception.class)
    public CountryProfileVO save(CountryProfileRequest req) {
        int tenantId = tenantId();
        String country = countryCatalog.canonicalName(req.getCountry());
        if (country == null) {
            throw new BizException("国家不在系统国家清单里");
        }
        CountryProfileDO d = mapper.selectOne(new LambdaQueryWrapper<CountryProfileDO>()
                .eq(CountryProfileDO::getTenantId, tenantId)
                .eq(CountryProfileDO::getCountry, country)
                .last("LIMIT 1"));
        boolean create = d == null;
        if (create) {
            d = new CountryProfileDO();
            d.setTenantId(tenantId);
            d.setCountry(country);
        }
        String before = create ? null : summary(d);
        d.setVoltages(multi(ProfileField.VOLTAGES, d.getVoltages(), req.getVoltages()));
        d.setCertifications(multi(ProfileField.CERTIFICATIONS, d.getCertifications(), req.getCertifications()));
        d.setPlug(text(req.getPlug()));
        d.setHolidays(text(req.getHolidays()));
        d.setFxControl(req.getFxControl() == null ? 0 : req.getFxControl());
        d.setFxNote(text(req.getFxNote()));
        d.setTariffNote(text(req.getTariffNote()));
        d.setDeletedAt(null);
        if (create) {
            mapper.insert(d);
        } else {
            mapper.updateById(d);
        }
        facts.refreshAll(tenantId, customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getTenantId, tenantId)
                .eq(CustomerDO::getCountry, country)
                .isNull(CustomerDO::getDeletedAt)));
        logService.recordOperateLog("国家资料", create ? "新增国家资料" : "修改国家资料",
                before == null ? null : Map.of("country", country, "资料", before), Map.of("country", country, "资料", summary(d)));
        return toVo(d, null);
    }

    public CountryProfileVO toVo(CountryProfileDO d, Long customerCount) {
        CountryProfileVO vo = new CountryProfileVO();
        vo.setId(d.getId());
        vo.setCountry(d.getCountry());
        vo.setCustomerCount(customerCount);
        vo.setVoltages(new ArrayList<>(JsonArrays.ints(d.getVoltages())));
        vo.setPlug(d.getPlug());
        vo.setHolidays(d.getHolidays());
        vo.setFxControl(d.getFxControl());
        vo.setFxNote(d.getFxNote());
        vo.setTariffNote(d.getTariffNote());
        vo.setCertifications(new ArrayList<>(JsonArrays.ints(d.getCertifications())));
        vo.setCreateTime(d.getCreateTime());
        vo.setCreateBy(d.getCreateBy());
        vo.setUpdateTime(d.getUpdateTime());
        vo.setUpdateBy(d.getUpdateBy());
        return vo;
    }

    private String multi(ProfileField f, String oldJson, List<Integer> values) {
        Set<Integer> old = new HashSet<>(JsonArrays.ints(oldJson));
        List<Integer> out = new ArrayList<>();
        for (Integer v : values == null ? List.<Integer>of() : values) {
            if (v == null || out.contains(v)) {
                continue;
            }
            if (!old.contains(v)) {
                dictItemService.requireEnabledValue(f.dictType(), v, f.label() + "不正确");
            }
            out.add(v);
        }
        return JsonArrays.write(out);
    }

    private static String summary(CountryProfileDO d) {
        return "电压 " + d.getVoltages() + "；插头 " + d.getPlug() + "；外汇管制 " + d.getFxControl() + "；节假日 " + d.getHolidays();
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
