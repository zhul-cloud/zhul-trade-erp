package com.zhul.erp.modules.masterdata.customerprofile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.customerbackground.entity.CustomerBackgroundReportDO;
import com.zhul.erp.modules.masterdata.customerbackground.repository.CustomerBackgroundReportMapper;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CustomerProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ProfileUpdateRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.RfmDetailVO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CountryProfileDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerFieldSourceDO;
import com.zhul.erp.modules.masterdata.customerprofile.support.FieldSources;
import com.zhul.erp.modules.masterdata.customerprofile.support.JsonArrays;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileCompleteness;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 客户画像（spec master-data/customer-profile）：读取（含系统推导、完整度、经营等级、来源、联系人）与按字段修改 */
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    /** 画像里可以在这里修改的字段（名称、国家等仍在客户表单里改） */
    static final Set<String> EDITABLE = Set.of("customerRole", "industry", "website", "linkedin", "socialUrl", "foundedYear",
            "companySize", "potentialRating", "mainProducts", "cnSuppliers", "estAnnualPurchase", "qualityReq", "priceSensitivity",
            "customsAbility", "paymentMethod", "currentBrands", "designAbility", "protocols", "voltages", "certifications",
            "repairAbility", "city", "timezone");

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;
    private final ProfileFacts facts;
    private final FieldSources sources;
    private final CustomerGradeService gradeService;
    private final CountryProfileService countryService;
    private final DictItemService dictItemService;
    private final UserBasicMapper userBasicMapper;
    private final CustomerBackgroundReportMapper reportMapper;
    private final CurrentUserResolver currentUser;
    private final LogService logService;

    @Transactional(rollbackFor = Exception.class)
    public CustomerProfileVO get(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        RfmDetailVO rfm = gradeService.detail(c);
        List<CustomerContactDO> contacts = facts.contacts(customerId);
        CountryProfileDO country = facts.country(c.getTenantId(), c.getCountry());
        boolean derivedBrands = facts.hasDerivedBrands(customerId);
        ProfileCompleteness.Result done = ProfileCompleteness.evaluate(c, contacts, country, derivedBrands);
        facts.refresh(c);

        CustomerProfileVO vo = new CustomerProfileVO();
        vo.setCustomerId(c.getId());
        vo.setName(c.getName());
        vo.setCountry(c.getCountry());
        vo.setCity(c.getCity());
        vo.setTimezone(c.getTimezone());
        vo.setCustomerRole(c.getCustomerRole());
        vo.setIndustry(c.getIndustry());
        vo.setWebsite(c.getWebsite());
        vo.setLinkedin(c.getLinkedin());
        vo.setSocialUrl(c.getSocialUrl());
        vo.setFoundedYear(c.getFoundedYear());
        vo.setCompanySize(c.getCompanySize());
        vo.setPotentialRating(c.getPotentialRating());
        vo.setMainProducts(c.getMainProducts());
        vo.setCnSuppliers(c.getCnSuppliers());
        vo.setEstAnnualPurchase(c.getEstAnnualPurchase());
        vo.setEstPurchaseCurrency(c.getEstPurchaseCurrency());
        vo.setQualityReq(c.getQualityReq());
        vo.setPriceSensitivity(c.getPriceSensitivity());
        vo.setCustomsAbility(c.getCustomsAbility());
        vo.setPaymentMethod(c.getPaymentMethod());
        vo.setDepositRatio(c.getDepositRatio());
        vo.setPaymentDays(c.getPaymentDays());
        vo.setCurrentBrands(new ArrayList<>(JsonArrays.read(c.getCurrentBrands(), new TypeReference<List<CustomerProfileVO.BrandRef>>() { })));
        vo.setDesignAbility(c.getDesignAbility());
        vo.setProtocols(new ArrayList<>(JsonArrays.ints(c.getProtocols())));
        vo.setVoltages(new ArrayList<>(JsonArrays.ints(c.getVoltages())));
        vo.setCertifications(new ArrayList<>(JsonArrays.ints(c.getCertifications())));
        vo.setRepairAbility(c.getRepairAbility());

        CustomerProfileVO.Derived d = new CustomerProfileVO.Derived();
        Object[] stats = facts.inquiryStats(customerId);
        d.setInquiryCount((Long) stats[0]);
        d.setFirstInquiryDate((LocalDate) stats[1]);
        d.setLastInquiryDate((LocalDate) stats[2]);
        d.setOrdersInYear(rfm.getOrdersInYear());
        d.setSalesCnyInYear(rfm.getOrders().stream().map(RfmDetailVO.OrderRow::getAmountCny).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        d.setTopBrands(facts.topBrands(customerId, 5));
        vo.setDerived(d);
        vo.setCountryProfile(country == null ? null : countryService.toVo(country, null));
        vo.setCompleteness(completeness(done));
        vo.setGrade(gradeService.gradeOf(c));
        vo.setSources(sources(c, country, derivedBrands));
        vo.setContacts(contacts.stream().map(CustomerContactService::toVo).toList());
        return vo;
    }

    /** 按字段修改画像：只改 fields 里列出的字段，记来源（人工）并重算完整度 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long customerId, ProfileUpdateRequest req) {
        update(customerId, req, FieldSources.MANUAL, 0L, "修改客户画像");
    }

    /** 同上，来源可以是背调报告（采纳字段建议时 source=REPORT、reportId=报告ID） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long customerId, ProfileUpdateRequest req, int source, long reportId, String operation) {
        CustomerDO c = customerService.requireInScope(customerId);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        Set<String> changed = new LinkedHashSet<>();
        for (String key : new LinkedHashSet<>(req.getFields())) {
            if (!EDITABLE.contains(key)) {
                throw new BizException("「" + key + "」不能在这里修改");
            }
            Object old = valueOf(c, key);
            apply(c, key, req);
            Object now = valueOf(c, key);
            if (!Objects.equals(old, now)) {
                changed.add(key);
                String label = ProfileField.of(key).map(ProfileField::label).orElse(labelOf(key));
                before.put(label, old);
                after.put(label, now);
            }
        }
        if (changed.isEmpty()) {
            return;
        }
        customerMapper.updateById(c);
        sources.record(c.getTenantId(), customerId, changed, source, reportId, currentUser.resolve());
        facts.refresh(c);
        before.put("customerCode", c.getCustomerCode());
        after.put("customerCode", c.getCustomerCode());
        logService.recordOperateLog("客户管理", operation, before, after);
    }

    /** 记一次跟进可以顺手补充的画像字段：自由文本字段，AI 建议与业务员勾选都只限这些 */
    public static final Set<String> FOLLOWUP_FIELDS = Set.of("mainProducts", "cnSuppliers", "city", "website", "linkedin",
            "socialUrl");

    /** 记一次跟进可以补充、且这位客户目前为空的字段（AI 整理只建议这些，可见范围跟随客户） */
    public List<String> blankFollowupFields(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        return FOLLOWUP_FIELDS.stream().sorted()
                .filter(k -> !(valueOf(c, k) instanceof String s && !s.isBlank())).toList();
    }

    /**
     * 记一次跟进时写入勾选的档案字段（spec master-data/customer-profile「记跟进时补充的字段」）：
     * 只补空值，已有值不覆盖；来源记为跟进记录（带跟进记录 ID）。与记一次跟进在同一事务内，校验不通过即抛出。
     *
     * @return 实际写入的字段键 → 值
     */
    public Map<String, String> applyFollowup(Long customerId, Map<String, String> values, long followupId) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        CustomerDO c = customerService.requireInScope(customerId);
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        List<String> keys = new ArrayList<>();
        Map<String, String> applied = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            String v = text(e.getValue());
            if (!FOLLOWUP_FIELDS.contains(key)) {
                throw new BizException("「" + key + "」不能在记跟进时写入档案");
            }
            Object old = valueOf(c, key);
            if (v.isEmpty() || (old instanceof String s && !s.isBlank())) {
                continue;
            }
            switch (key) {
                case "mainProducts" -> req.setMainProducts(v);
                case "cnSuppliers" -> req.setCnSuppliers(v);
                case "city" -> req.setCity(v);
                case "website" -> req.setWebsite(v);
                case "linkedin" -> req.setLinkedin(v);
                default -> req.setSocialUrl(v);
            }
            keys.add(key);
            applied.put(key, v);
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        for (String key : keys) {
            apply(c, key, req);
        }
        customerMapper.updateById(c);
        sources.record(c.getTenantId(), customerId, keys, FieldSources.FOLLOWUP, 0L, followupId, currentUser.resolve());
        facts.refresh(c);
        return applied;
    }

    /** 补全向导「已经问了，等回复」：按字段记录或取消 */
    @Transactional(rollbackFor = Exception.class)
    public void markAsked(Long customerId, Collection<String> keys, boolean asked) {
        CustomerDO c = customerService.requireInScope(customerId);
        for (String k : keys) {
            if (ProfileField.of(k.startsWith("contact.") ? k.substring(k.lastIndexOf('.') + 1) : k).isEmpty()) {
                throw new BizException("字段不正确：" + k);
            }
        }
        sources.markAsked(c.getTenantId(), customerId, keys, asked, currentUser.resolve());
    }

    // ---------------------------------------------------------------- 写入与校验

    private void apply(CustomerDO c, String key, ProfileUpdateRequest r) {
        switch (key) {
            case "customerRole" -> c.setCustomerRole(code(r.getCustomerRole(), CustomerConstants.ROLE_LABELS.keySet(), "公司类型不正确"));
            case "industry" -> c.setIndustry(code(r.getIndustry(), CustomerConstants.INDUSTRY_LABELS.keySet(), "行业不正确"));
            case "website" -> c.setWebsite(url(r.getWebsite(), "官网"));
            case "linkedin" -> c.setLinkedin(url(r.getLinkedin(), "LinkedIn"));
            case "socialUrl" -> c.setSocialUrl(url(r.getSocialUrl(), "其他社媒"));
            case "foundedYear" -> {
                if (r.getFoundedYear() != null && r.getFoundedYear() > LocalDate.now().getYear()) {
                    throw new BizException("成立年份不能晚于今年");
                }
                c.setFoundedYear(r.getFoundedYear());
            }
            case "companySize" -> c.setCompanySize(dict(ProfileField.COMPANY_SIZE, c.getCompanySize(), r.getCompanySize()));
            case "potentialRating" -> c.setPotentialRating(r.getPotentialRating() == null ? 0 : r.getPotentialRating());
            case "mainProducts" -> c.setMainProducts(text(r.getMainProducts()));
            case "cnSuppliers" -> c.setCnSuppliers(text(r.getCnSuppliers()));
            case "estAnnualPurchase" -> c.setEstAnnualPurchase(r.getEstAnnualPurchase() == null ? null
                    : r.getEstAnnualPurchase().setScale(2, RoundingMode.HALF_UP));
            case "qualityReq" -> c.setQualityReq(dict(ProfileField.QUALITY_REQ, c.getQualityReq(), r.getQualityReq()));
            case "priceSensitivity" -> c.setPriceSensitivity(dict(ProfileField.PRICE_SENSITIVITY, c.getPriceSensitivity(), r.getPriceSensitivity()));
            case "customsAbility" -> c.setCustomsAbility(dict(ProfileField.CUSTOMS_ABILITY, c.getCustomsAbility(), r.getCustomsAbility()));
            case "paymentMethod" -> applyPayment(c, r);
            case "currentBrands" -> c.setCurrentBrands(JsonArrays.write(brands(r.getCurrentBrands())));
            case "designAbility" -> c.setDesignAbility(dict(ProfileField.DESIGN_ABILITY, c.getDesignAbility(), r.getDesignAbility()));
            case "protocols" -> c.setProtocols(multi(ProfileField.PROTOCOLS, c.getProtocols(), r.getProtocols()));
            case "voltages" -> c.setVoltages(multi(ProfileField.VOLTAGES, c.getVoltages(), r.getVoltages()));
            case "certifications" -> c.setCertifications(multi(ProfileField.CERTIFICATIONS, c.getCertifications(), r.getCertifications()));
            case "repairAbility" -> c.setRepairAbility(dict(ProfileField.REPAIR_ABILITY, c.getRepairAbility(), r.getRepairAbility()));
            case "city" -> c.setCity(text(r.getCity()));
            case "timezone" -> {
                String tz = text(r.getTimezone());
                if (!tz.isEmpty()) {
                    try {
                        ZoneId.of(tz);
                    } catch (DateTimeException e) {
                        throw new BizException("时区不正确", e);
                    }
                }
                c.setTimezone(tz);
            }
            default -> throw new BizException("「" + key + "」不能在这里修改");
        }
    }

    /** 付款习惯即默认付款方式，与定金比例、账期的联动规则同客户表单 */
    private static void applyPayment(CustomerDO c, ProfileUpdateRequest r) {
        Integer payment = code(r.getPaymentMethod(), CustomerConstants.PAYMENT_LABELS.keySet(), "付款习惯不正确");
        c.setPaymentMethod(payment);
        if (CustomerConstants.PAYMENT_WITH_DEPOSIT.contains(payment)) {
            if (r.getDepositRatio() == null) {
                throw new BizException("请填写定金比例");
            }
            c.setDepositRatio(r.getDepositRatio());
        } else {
            c.setDepositRatio(null);
        }
        if (CustomerConstants.PAYMENT_WITH_DAYS.contains(payment)) {
            if (r.getPaymentDays() == null) {
                throw new BizException("请填写账期（天）");
            }
            c.setPaymentDays(r.getPaymentDays());
        } else {
            c.setPaymentDays(null);
        }
    }

    private Integer dict(ProfileField f, Integer old, Integer value) {
        int v = value == null ? 0 : value;
        if (v != 0 && !Objects.equals(v, old)) {
            dictItemService.requireEnabledValue(f.dictType(), v, f.label() + "不正确");
        }
        return v;
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

    private static List<CustomerProfileVO.BrandRef> brands(List<CustomerProfileVO.BrandRef> in) {
        List<CustomerProfileVO.BrandRef> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CustomerProfileVO.BrandRef b : in == null ? List.<CustomerProfileVO.BrandRef>of() : in) {
            String name = text(b.getName());
            if (name.isEmpty() || !seen.add(name.toLowerCase())) {
                continue;
            }
            CustomerProfileVO.BrandRef x = new CustomerProfileVO.BrandRef();
            x.setBrandId(b.getBrandId());
            x.setName(name);
            out.add(x);
        }
        return out;
    }

    private static Integer code(Integer v, Set<Integer> allowed, String message) {
        int x = v == null ? 0 : v;
        if (x != 0 && !allowed.contains(x)) {
            throw new BizException(message);
        }
        return x;
    }

    private static String url(String v, String label) {
        String s = text(v);
        if (!s.isEmpty() && !s.matches("^(https?://)?[^\\s/$.?#].[^\\s]*$")) {
            throw new BizException(label + "网址格式不正确");
        }
        return s;
    }

    private static Object valueOf(CustomerDO c, String key) {
        return switch (key) {
            case "customerRole" -> c.getCustomerRole();
            case "industry" -> c.getIndustry();
            case "website" -> c.getWebsite();
            case "linkedin" -> c.getLinkedin();
            case "socialUrl" -> c.getSocialUrl();
            case "foundedYear" -> c.getFoundedYear();
            case "companySize" -> c.getCompanySize();
            case "potentialRating" -> c.getPotentialRating();
            case "mainProducts" -> c.getMainProducts();
            case "cnSuppliers" -> c.getCnSuppliers();
            case "estAnnualPurchase" -> c.getEstAnnualPurchase();
            case "qualityReq" -> c.getQualityReq();
            case "priceSensitivity" -> c.getPriceSensitivity();
            case "customsAbility" -> c.getCustomsAbility();
            case "paymentMethod" -> c.getPaymentMethod() + "/" + c.getDepositRatio() + "/" + c.getPaymentDays();
            case "currentBrands" -> c.getCurrentBrands();
            case "designAbility" -> c.getDesignAbility();
            case "protocols" -> c.getProtocols();
            case "voltages" -> c.getVoltages();
            case "certifications" -> c.getCertifications();
            case "repairAbility" -> c.getRepairAbility();
            case "city" -> c.getCity();
            case "timezone" -> c.getTimezone();
            default -> null;
        };
    }

    private static String labelOf(String key) {
        return switch (key) {
            case "website" -> "官网";
            case "linkedin" -> "LinkedIn";
            default -> key;
        };
    }

    // ---------------------------------------------------------------- 组装

    private static CustomerProfileVO.Completeness completeness(ProfileCompleteness.Result r) {
        CustomerProfileVO.Completeness c = new CustomerProfileVO.Completeness();
        c.setMask(r.mask());
        c.setDone(r.doneCount());
        c.setComplete(r.complete());
        r.missing().forEach((module, fields) -> {
            CustomerProfileVO.Missing m = new CustomerProfileVO.Missing();
            m.setModule(module);
            m.setModuleName(ProfileField.MODULE_NAMES.get(module - 1));
            fields.forEach(f -> {
                m.getKeys().add(f.key());
                // ④ 里的 NAME 指联系人姓名
                m.getLabels().add(module == 4 && f == ProfileField.NAME ? "联系人姓名" : f.label());
            });
            c.getMissing().add(m);
        });
        return c;
    }

    private Map<String, CustomerProfileVO.Source> sources(CustomerDO c, CountryProfileDO country, boolean derivedBrands) {
        List<CustomerFieldSourceDO> rows = sources.list(c.getId());
        Set<Long> userIds = new HashSet<>();
        rows.forEach(r -> {
            userIds.add(r.getOperatorId());
            userIds.add(r.getAskedBy());
        });
        userIds.remove(0L);
        userIds.remove(null);
        Map<Long, String> names = userIds.isEmpty() ? Map.of() : userBasicMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(u -> Long.valueOf(u.getId()), UserBasicDO::getName, (a, b) -> a));
        Set<Long> reportIds = rows.stream().map(CustomerFieldSourceDO::getReportId)
                .filter(id -> id != null && id != 0).collect(Collectors.toSet());
        Map<Long, CustomerBackgroundReportDO> reports = reportIds.isEmpty() ? Map.of() : reportMapper.selectBatchIds(reportIds).stream()
                .collect(Collectors.toMap(CustomerBackgroundReportDO::getId, x -> x));
        Map<String, CustomerProfileVO.Source> out = new HashMap<>();
        for (CustomerFieldSourceDO r : rows) {
            CustomerProfileVO.Source s = new CustomerProfileVO.Source();
            s.setSource(r.getSource());
            s.setReportId(r.getReportId() == null || r.getReportId() == 0 ? null : r.getReportId());
            s.setFollowupId(r.getFollowupId() == null || r.getFollowupId() == 0 ? null : r.getFollowupId());
            CustomerBackgroundReportDO report = s.getReportId() == null ? null : reports.get(s.getReportId());
            if (report != null) {
                s.setReportNo(report.getReportNo());
                s.setReportDeleted(report.getDeletedAt() != null);
            }
            s.setOperatorName(names.get(r.getOperatorId()));
            s.setUpdateTime(r.getUpdateTime());
            s.setAskedAt(r.getAskedAt());
            s.setAskedByName(names.get(r.getAskedBy()));
            out.put(r.getFieldKey(), s);
        }
        if (!JsonArrays.nonEmpty(c.getCurrentBrands()) && derivedBrands) {
            out.put(ProfileField.CURRENT_BRANDS.key(), system());
        }
        if (country != null && !JsonArrays.nonEmpty(c.getVoltages()) && JsonArrays.nonEmpty(country.getVoltages())) {
            out.put(ProfileField.VOLTAGES.key(), system());
        }
        if (country != null && !JsonArrays.nonEmpty(c.getCertifications()) && JsonArrays.nonEmpty(country.getCertifications())) {
            out.put(ProfileField.CERTIFICATIONS.key(), system());
        }
        return out;
    }

    private static CustomerProfileVO.Source system() {
        CustomerProfileVO.Source s = new CustomerProfileVO.Source();
        s.setSource(FieldSources.SYSTEM);
        return s;
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}
