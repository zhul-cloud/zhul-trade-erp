package com.zhul.erp.modules.masterdata.customerprofile.support;

import com.zhul.erp.modules.masterdata.customerprofile.entity.CountryProfileDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户档案完整度（spec master-data/customer-profile「客户档案完整度」）：每个模块的核心项全部满足才算完成。
 * 纯计算，不查库；调用方准备好客户、联系人、所在国家的国家资料，以及询盘里是否推导出常用品牌。
 */
public final class ProfileCompleteness {

    private ProfileCompleteness() {
    }

    /** mask：已完成模块的位；missing：模块号 → 缺的核心项（已完成的模块不在里面） */
    public record Result(int mask, Map<Integer, List<ProfileField>> missing) {
        public int doneCount() {
            return Integer.bitCount(mask);
        }

        public boolean complete() {
            return mask == ProfileField.ALL_DONE;
        }
    }

    public static Result evaluate(CustomerDO c, List<CustomerContactDO> contacts, CountryProfileDO country, boolean derivedBrands) {
        Map<Integer, List<ProfileField>> missing = new LinkedHashMap<>();
        int mask = 0;
        for (int m = 1; m <= ProfileField.MODULES; m++) {
            List<ProfileField> lack = m == 4 ? missingContact(contacts) : missingOf(m, c, country, derivedBrands);
            if (lack.isEmpty()) {
                mask |= ProfileField.bit(m);
            } else {
                missing.put(m, lack);
            }
        }
        return new Result(mask, missing);
    }

    private static List<ProfileField> missingOf(int module, CustomerDO c, CountryProfileDO country, boolean derivedBrands) {
        List<ProfileField> lack = new ArrayList<>();
        for (ProfileField f : ProfileField.coreOf(module)) {
            if (!filled(f, c, country, derivedBrands)) {
                lack.add(f);
            }
        }
        return lack;
    }

    static boolean filled(ProfileField f, CustomerDO c, CountryProfileDO country, boolean derivedBrands) {
        return switch (f) {
            case NAME -> StringUtils.hasText(c.getName());
            case CUSTOMER_ROLE -> positive(c.getCustomerRole());
            case INDUSTRY -> positive(c.getIndustry());
            case WEB -> StringUtils.hasText(c.getWebsite()) || StringUtils.hasText(c.getLinkedin());
            case COMPANY_SIZE -> positive(c.getCompanySize());
            case POTENTIAL -> positive(c.getPotentialRating());
            case MAIN_PRODUCTS -> StringUtils.hasText(c.getMainProducts());
            case QUALITY_REQ -> positive(c.getQualityReq());
            case PRICE_SENSITIVITY -> positive(c.getPriceSensitivity());
            case CUSTOMS_ABILITY -> positive(c.getCustomsAbility());
            case PAYMENT_METHOD -> positive(c.getPaymentMethod());
            case CURRENT_BRANDS -> JsonArrays.nonEmpty(c.getCurrentBrands()) || derivedBrands;
            case DESIGN_ABILITY -> positive(c.getDesignAbility());
            case PROTOCOLS -> JsonArrays.nonEmpty(c.getProtocols());
            case VOLTAGES -> JsonArrays.nonEmpty(c.getVoltages()) || (country != null && JsonArrays.nonEmpty(country.getVoltages()));
            case REPAIR_ABILITY -> positive(c.getRepairAbility());
            case COUNTRY -> StringUtils.hasText(c.getCountry());
            case CITY -> StringUtils.hasText(c.getCity());
            case TIMEZONE -> StringUtils.hasText(c.getTimezone());
            case COUNTRY_PROFILE -> country != null && JsonArrays.nonEmpty(country.getVoltages()) && positive(country.getFxControl());
            default -> true;
        };
    }

    /** ④ 至少一个联系人同时填了姓名、职位类别、决策权重、沟通风格；没有时按最接近的那个联系人（主联系人优先）列缺项 */
    private static List<ProfileField> missingContact(List<CustomerContactDO> contacts) {
        List<ProfileField> best = null;
        List<CustomerContactDO> ordered = contacts.stream()
                .sorted(Comparator.comparing((CustomerContactDO x) -> x.getIsPrimary() != null && x.getIsPrimary() == 1).reversed())
                .toList();
        for (CustomerContactDO x : ordered) {
            List<ProfileField> lack = new ArrayList<>();
            if (!StringUtils.hasText(x.getName())) {
                lack.add(ProfileField.NAME);
            }
            if (!positive(x.getJobRole())) {
                lack.add(ProfileField.CONTACT_JOB_ROLE);
            }
            if (!positive(x.getDecisionRole())) {
                lack.add(ProfileField.CONTACT_DECISION_ROLE);
            }
            if (!positive(x.getCommStyle())) {
                lack.add(ProfileField.CONTACT_COMM_STYLE);
            }
            if (lack.isEmpty()) {
                return List.of();
            }
            if (best == null || lack.size() < best.size()) {
                best = lack;
            }
        }
        return best != null ? best
                : List.of(ProfileField.NAME, ProfileField.CONTACT_JOB_ROLE, ProfileField.CONTACT_DECISION_ROLE, ProfileField.CONTACT_COMM_STYLE);
    }

    private static boolean positive(Integer v) {
        return v != null && v > 0;
    }
}
