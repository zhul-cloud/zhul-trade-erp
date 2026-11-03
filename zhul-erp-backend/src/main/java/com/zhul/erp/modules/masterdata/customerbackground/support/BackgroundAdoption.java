package com.zhul.erp.modules.masterdata.customerbackground.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundSuggestion;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ContactRequest;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CustomerProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.ProfileUpdateRequest;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.support.JsonArrays;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.support.ContactKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 字段建议的采纳规则（spec crm/customer-background「字段建议与采纳」、design 决策 5）：
 * 建议值换成档案里的值（选项按中文名匹配）、档案里的当前值、联系人按邮箱（其次姓名）匹配。
 * 写入时仍走客户画像与联系人原有的校验。
 */
@Component
@RequiredArgsConstructor
public class BackgroundAdoption {

    public static final String NO_OPTION = "无法采纳：选项不存在";
    public static final String NO_CONTACT_NAME = "无法采纳：联系人没有姓名";
    public static final String NEED_PAYMENT_DETAIL = "无法采纳：这种付款方式还要填定金比例或账期，请在档案里修改";
    public static final String BAD_VALUE = "无法采纳：建议值格式不正确";

    private static final String SPLIT = "[、,，;；/]";

    private final BackgroundFields fields;

    /** 换算结果：value 为写入档案的值；reason 不为空表示不能采纳 */
    public record Resolved(Object value, String reason) {
        static Resolved of(Object value) {
            return new Resolved(value, null);
        }

        static Resolved no(String reason) {
            return new Resolved(null, reason);
        }

        public boolean ok() {
            return reason == null;
        }
    }

    public Resolved resolve(BackgroundSuggestion s, List<CustomerContactDO> contacts) {
        String key = s.getField();
        String v = s.getValue() == null ? "" : s.getValue().trim();
        if (BackgroundFields.isContact(key) && matchContact(contacts, s.getContact()) == null
                && (s.getContact() == null || !StringUtils.hasText(s.getContact().getName()))) {
            return Resolved.no(NO_CONTACT_NAME);
        }
        if (BackgroundFields.isMultiple(key)) {
            Map<Integer, String> options = fields.options(key);
            List<Integer> codes = new ArrayList<>();
            for (String part : v.split(SPLIT)) {
                if (part.isBlank()) {
                    continue;
                }
                Integer code = BackgroundFields.codeOf(options, part);
                if (code == null) {
                    return Resolved.no(NO_OPTION);
                }
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            }
            return codes.isEmpty() ? Resolved.no(NO_OPTION) : Resolved.of(codes);
        }
        Map<Integer, String> options = fields.options(key);
        if (options != null) {
            Integer code = BackgroundFields.codeOf(options, v);
            if (code == null) {
                return Resolved.no(NO_OPTION);
            }
            if ("paymentMethod".equals(key) && (CustomerConstants.PAYMENT_WITH_DEPOSIT.contains(code)
                    || CustomerConstants.PAYMENT_WITH_DAYS.contains(code))) {
                return Resolved.no(NEED_PAYMENT_DETAIL);
            }
            return Resolved.of(code);
        }
        return switch (key) {
            case "foundedYear" -> v.matches("\\d{4}") && Integer.parseInt(v) <= LocalDate.now().getYear()
                    ? Resolved.of(Integer.valueOf(v)) : Resolved.no(BAD_VALUE);
            case "potentialRating" -> v.matches("[1-5]") ? Resolved.of(Integer.valueOf(v)) : Resolved.no(BAD_VALUE);
            case "estAnnualPurchase" -> {
                String n = v.replaceAll("(?i)usd|us\\$|\\$|,|\\s", "");
                yield n.matches("\\d{1,13}(\\.\\d{1,2})?") ? Resolved.of(new BigDecimal(n)) : Resolved.no(BAD_VALUE);
            }
            case "currentBrands" -> {
                List<CustomerProfileVO.BrandRef> brands = new ArrayList<>();
                for (String part : v.split(SPLIT)) {
                    if (!part.isBlank()) {
                        CustomerProfileVO.BrandRef b = new CustomerProfileVO.BrandRef();
                        b.setName(part.trim());
                        brands.add(b);
                    }
                }
                yield brands.isEmpty() ? Resolved.no(BAD_VALUE) : Resolved.of(brands);
            }
            default -> v.isEmpty() ? Resolved.no(BAD_VALUE) : Resolved.of(v);
        };
    }

    /** 把换算后的值放进画像修改请求（客户字段） */
    @SuppressWarnings("unchecked")
    public static void put(ProfileUpdateRequest req, String key, Object value) {
        req.getFields().add(key);
        switch (key) {
            case "customerRole" -> req.setCustomerRole((Integer) value);
            case "industry" -> req.setIndustry((Integer) value);
            case "website" -> req.setWebsite((String) value);
            case "linkedin" -> req.setLinkedin((String) value);
            case "socialUrl" -> req.setSocialUrl((String) value);
            case "foundedYear" -> req.setFoundedYear((Integer) value);
            case "companySize" -> req.setCompanySize((Integer) value);
            case "potentialRating" -> req.setPotentialRating((Integer) value);
            case "mainProducts" -> req.setMainProducts((String) value);
            case "cnSuppliers" -> req.setCnSuppliers((String) value);
            case "estAnnualPurchase" -> req.setEstAnnualPurchase((BigDecimal) value);
            case "qualityReq" -> req.setQualityReq((Integer) value);
            case "priceSensitivity" -> req.setPriceSensitivity((Integer) value);
            case "customsAbility" -> req.setCustomsAbility((Integer) value);
            case "paymentMethod" -> req.setPaymentMethod((Integer) value);
            case "currentBrands" -> req.setCurrentBrands((List<CustomerProfileVO.BrandRef>) value);
            case "designAbility" -> req.setDesignAbility((Integer) value);
            case "protocols" -> req.setProtocols((List<Integer>) value);
            case "voltages" -> req.setVoltages((List<Integer>) value);
            case "certifications" -> req.setCertifications((List<Integer>) value);
            case "repairAbility" -> req.setRepairAbility((Integer) value);
            case "city" -> req.setCity((String) value);
            default -> throw new IllegalArgumentException("不是客户字段：" + key);
        }
    }

    /** 把换算后的值放进联系人请求 */
    public static void put(ContactRequest req, String key, Object value) {
        switch (key) {
            case BackgroundFields.CONTACT_TITLE -> req.setTitle((String) value);
            case "jobRole" -> req.setJobRole((Integer) value);
            case "decisionRole" -> req.setDecisionRole((Integer) value);
            default -> throw new IllegalArgumentException("不是联系人字段：" + key);
        }
    }

    /** 已有联系人整体写回请求（联系人修改是整体替换） */
    public static ContactRequest requestOf(CustomerContactDO x) {
        ContactRequest r = new ContactRequest();
        r.setName(x.getName());
        r.setTitle(x.getTitle());
        r.setEmail(x.getEmail());
        r.setPhone(x.getPhone());
        r.setWhatsapp(x.getWhatsapp());
        r.setOtherIm(x.getOtherIm());
        r.setJobRole(x.getJobRole());
        r.setDecisionRole(x.getDecisionRole());
        r.setCommStyle(x.getCommStyle());
        r.setDisc(x.getDisc());
        r.setChinaAttitude(x.getChinaAttitude());
        r.setActiveHours(x.getActiveHours());
        r.setLikes(x.getLikes());
        return r;
    }

    /** 建议里的联系人按邮箱找已有联系人，没有邮箱或找不到时按姓名（忽略大小写）找 */
    public static CustomerContactDO matchContact(List<CustomerContactDO> contacts, BackgroundSuggestion.Contact ct) {
        if (ct == null) {
            return null;
        }
        String email = ContactKeys.email(ct.getEmail());
        if (StringUtils.hasText(email)) {
            for (CustomerContactDO x : contacts) {
                if (email.equals(x.getEmailKey())) {
                    return x;
                }
            }
        }
        if (StringUtils.hasText(ct.getName())) {
            for (CustomerContactDO x : contacts) {
                if (x.getName() != null && x.getName().trim().equalsIgnoreCase(ct.getName().trim())) {
                    return x;
                }
            }
        }
        return null;
    }

    /** 档案里的当前值（显示用）；空为没有值 */
    public String current(CustomerDO c, List<CustomerContactDO> contacts, BackgroundSuggestion s) {
        String key = s.getField();
        if (BackgroundFields.isContact(key)) {
            CustomerContactDO x = matchContact(contacts, s.getContact());
            if (x == null) {
                return null;
            }
            return switch (key) {
                case BackgroundFields.CONTACT_TITLE -> blankToNull(x.getTitle());
                case "jobRole" -> label(key, x.getJobRole());
                case "decisionRole" -> label(key, x.getDecisionRole());
                default -> null;
            };
        }
        return switch (key) {
            case "customerRole" -> label(key, c.getCustomerRole());
            case "industry" -> label(key, c.getIndustry());
            case "website" -> blankToNull(c.getWebsite());
            case "linkedin" -> blankToNull(c.getLinkedin());
            case "socialUrl" -> blankToNull(c.getSocialUrl());
            case "foundedYear" -> c.getFoundedYear() == null || c.getFoundedYear() == 0 ? null : String.valueOf(c.getFoundedYear());
            case "companySize" -> label(key, c.getCompanySize());
            case "potentialRating" -> c.getPotentialRating() == null || c.getPotentialRating() == 0 ? null : c.getPotentialRating() + " 星";
            case "mainProducts" -> blankToNull(c.getMainProducts());
            case "cnSuppliers" -> blankToNull(c.getCnSuppliers());
            case "estAnnualPurchase" -> c.getEstAnnualPurchase() == null || c.getEstAnnualPurchase().signum() == 0 ? null
                    : c.getEstAnnualPurchase().stripTrailingZeros().toPlainString() + " " + c.getEstPurchaseCurrency();
            case "qualityReq" -> label(key, c.getQualityReq());
            case "priceSensitivity" -> label(key, c.getPriceSensitivity());
            case "customsAbility" -> label(key, c.getCustomsAbility());
            case "paymentMethod" -> label(key, c.getPaymentMethod());
            case "currentBrands" -> {
                List<String> names = JsonArrays.read(c.getCurrentBrands(), new TypeReference<List<CustomerProfileVO.BrandRef>>() { })
                        .stream().map(CustomerProfileVO.BrandRef::getName).filter(Objects::nonNull).toList();
                yield names.isEmpty() ? null : String.join("、", names);
            }
            case "designAbility" -> label(key, c.getDesignAbility());
            case "protocols" -> labels(key, c.getProtocols());
            case "voltages" -> labels(key, c.getVoltages());
            case "certifications" -> labels(key, c.getCertifications());
            case "repairAbility" -> label(key, c.getRepairAbility());
            case "city" -> blankToNull(c.getCity());
            default -> null;
        };
    }

    private String label(String key, Integer code) {
        if (code == null || code == 0) {
            return null;
        }
        Map<Integer, String> options = fields.options(key);
        return options == null ? String.valueOf(code) : options.getOrDefault(code, String.valueOf(code));
    }

    private String labels(String key, String json) {
        List<Integer> codes = JsonArrays.ints(json);
        if (codes.isEmpty()) {
            return null;
        }
        Map<Integer, String> options = fields.options(key);
        return String.join("、", codes.stream().map(code -> options.getOrDefault(code, String.valueOf(code))).toList());
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
