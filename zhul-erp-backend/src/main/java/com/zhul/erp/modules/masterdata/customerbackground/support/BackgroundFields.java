package com.zhul.erp.modules.masterdata.customerbackground.support;

import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 背调可以给出建议的画像字段（design 决策 1：field 只能取画像字段键，选项型字段必须是选项中文名之一）。
 * 交给 AI 的字段清单与采纳时的选项匹配共用这一份。
 * 不含潜力评级（由综合评分给出）、时区，以及只能凭打交道印象判断的联系人字段。
 */
@Component
@RequiredArgsConstructor
public class BackgroundFields {

    /** 联系人原文职位：不是画像字段，用于新发现的关键联系人 */
    public static final String CONTACT_TITLE = "title";

    /** 字段键 → [中文名, 模块]，按交给 AI 的顺序 */
    private static final Map<String, Object[]> FIELDS = new LinkedHashMap<>();

    static {
        put(ProfileField.CUSTOMER_ROLE);
        put(ProfileField.INDUSTRY);
        FIELDS.put("website", new Object[] {"官网", 1});
        FIELDS.put("linkedin", new Object[] {"LinkedIn", 1});
        put(ProfileField.SOCIAL);
        put(ProfileField.FOUNDED_YEAR);
        put(ProfileField.COMPANY_SIZE);
        put(ProfileField.MAIN_PRODUCTS);
        put(ProfileField.CN_SUPPLIERS);
        put(ProfileField.EST_PURCHASE);
        put(ProfileField.QUALITY_REQ);
        put(ProfileField.PRICE_SENSITIVITY);
        put(ProfileField.CUSTOMS_ABILITY);
        put(ProfileField.PAYMENT_METHOD);
        put(ProfileField.CURRENT_BRANDS);
        put(ProfileField.DESIGN_ABILITY);
        put(ProfileField.PROTOCOLS);
        put(ProfileField.VOLTAGES);
        put(ProfileField.CERTIFICATIONS);
        put(ProfileField.REPAIR_ABILITY);
        put(ProfileField.CITY);
        FIELDS.put(CONTACT_TITLE, new Object[] {"联系人职位", 4});
        put(ProfileField.CONTACT_JOB_ROLE);
        put(ProfileField.CONTACT_DECISION_ROLE);
    }

    private static void put(ProfileField f) {
        FIELDS.put(f.key(), new Object[] {f.label(), f.module()});
    }

    private final DictItemService dictItemService;

    public static boolean allowed(String key) {
        return FIELDS.containsKey(key);
    }

    public static boolean isContact(String key) {
        return allowed(key) && (int) FIELDS.get(key)[1] == 4;
    }

    /** 选项型字段的 码值 → 中文名（字典只取启用项，按排序）；不是选项型返回 null */
    public Map<Integer, String> options(String key) {
        return switch (key) {
            case "customerRole" -> new TreeMap<>(CustomerConstants.ROLE_LABELS);
            case "industry" -> new TreeMap<>(CustomerConstants.INDUSTRY_LABELS);
            case "paymentMethod" -> new TreeMap<>(CustomerConstants.PAYMENT_LABELS);
            default -> ProfileField.of(key).map(ProfileField::dictType).map(this::enabledDict).orElse(null);
        };
    }

    private Map<Integer, String> enabledDict(String dictType) {
        Map<Integer, String> out = new LinkedHashMap<>();
        // 画像字段的字典都是整数码值，不是整数的项不参与
        dictItemService.listByDictType(dictType).stream()
                .filter(i -> Integer.valueOf(1).equals(i.getStatus()) && i.getItemValue() != null && i.getItemValue().matches("\\d{1,9}"))
                .sorted(Comparator.comparing(i -> i.getSortOrder() == null ? 0 : i.getSortOrder()))
                .forEach(i -> out.putIfAbsent(Integer.valueOf(i.getItemValue()), i.getItemName()));
        return out;
    }

    /** 交给 AI 的字段清单：key、中文名、模块、是否联系人字段、选项（选项型字段） */
    public List<Map<String, Object>> catalog() {
        List<Map<String, Object>> out = new ArrayList<>(FIELDS.size());
        FIELDS.forEach((key, v) -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("key", key);
            f.put("label", v[0]);
            f.put("module", v[1]);
            f.put("contact", (int) v[1] == 4);
            Map<Integer, String> options = options(key);
            if (options != null) {
                f.put("options", new ArrayList<>(options.values()));
            }
            if (ProfileField.PROTOCOLS.key().equals(key) || ProfileField.VOLTAGES.key().equals(key)
                    || ProfileField.CERTIFICATIONS.key().equals(key)) {
                f.put("multiple", true);
            }
            out.add(f);
        });
        return out;
    }
}
