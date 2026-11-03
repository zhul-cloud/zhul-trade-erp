package com.zhul.erp.modules.masterdata.customerprofile.support;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 客户画像字段（spec master-data/customer-profile「客户画像五个模块」「客户档案完整度」）。
 * 每个字段属于一个模块，标明是否核心项，以及补全向导里「谁能给出答案」的分类（spec crm/customer-background「档案补全」）。
 * 联系人字段（{@link #isContact()}）按主联系人或任一联系人判断，字段来源键为 contact.{联系人ID}.{key}。
 */
public enum ProfileField {
    // ① 基础画像与定性
    NAME("name", "客户名称", 1, true, Category.JUDGE, null),
    CUSTOMER_ROLE("customerRole", "公司类型", 1, true, Category.JUDGE, null),
    INDUSTRY("industry", "行业", 1, true, Category.RESEARCH, null),
    WEB("web", "官网或 LinkedIn", 1, true, Category.RESEARCH, null),
    COMPANY_SIZE("companySize", "公司规模", 1, true, Category.RESEARCH, "customer_company_size"),
    POTENTIAL("potentialRating", "潜力评级", 1, true, Category.JUDGE, null),
    FOUNDED_YEAR("foundedYear", "成立年份", 1, false, Category.RESEARCH, null),
    SOCIAL("socialUrl", "其他社媒", 1, false, Category.RESEARCH, null),
    // ② 商业与供应链能力
    MAIN_PRODUCTS("mainProducts", "主营产品", 2, true, Category.RESEARCH, null),
    QUALITY_REQ("qualityReq", "质量要求", 2, true, Category.JUDGE, "customer_quality_req"),
    PRICE_SENSITIVITY("priceSensitivity", "价格敏感度", 2, true, Category.JUDGE, "customer_price_sensitivity"),
    CUSTOMS_ABILITY("customsAbility", "清关能力", 2, true, Category.ASK, "customer_customs_ability"),
    PAYMENT_METHOD("paymentMethod", "付款习惯", 2, true, Category.ASK, null),
    EST_PURCHASE("estAnnualPurchase", "年度采购量估算", 2, false, Category.RESEARCH, null),
    CN_SUPPLIERS("cnSuppliers", "现有中国供应商", 2, false, Category.RESEARCH, null),
    // ③ 工控技术偏好
    CURRENT_BRANDS("currentBrands", "现有使用品牌", 3, true, Category.ASK, null),
    DESIGN_ABILITY("designAbility", "设计能力", 3, true, Category.ASK, "customer_design_ability"),
    PROTOCOLS("protocols", "常用通讯协议", 3, true, Category.ASK, "customer_protocol"),
    VOLTAGES("voltages", "电源电压", 3, true, Category.ASK, "customer_voltage"),
    REPAIR_ABILITY("repairAbility", "售后维修能力", 3, true, Category.ASK, "customer_repair_ability"),
    CERTIFICATIONS("certifications", "认证要求", 3, false, Category.ASK, "customer_certification"),
    // ④ 关键决策人（联系人字段）
    CONTACT_JOB_ROLE("jobRole", "职位类别", 4, true, Category.RESEARCH, "contact_job_role"),
    CONTACT_DECISION_ROLE("decisionRole", "决策权重", 4, true, Category.ASK, "contact_decision_role"),
    CONTACT_COMM_STYLE("commStyle", "沟通风格", 4, true, Category.JUDGE, "contact_comm_style"),
    CONTACT_DISC("disc", "性格（DISC）", 4, false, Category.JUDGE, "contact_disc"),
    CONTACT_CHINA_ATTITUDE("chinaAttitude", "对华态度", 4, false, Category.JUDGE, "contact_china_attitude"),
    CONTACT_ACTIVE_HOURS("activeHours", "活跃时间", 4, false, Category.JUDGE, null),
    CONTACT_LIKES("likes", "禁忌与喜好", 4, false, Category.JUDGE, null),
    // ⑤ 国家与宏观背景
    COUNTRY("country", "国家", 5, true, Category.JUDGE, null),
    CITY("city", "城市", 5, true, Category.RESEARCH, null),
    TIMEZONE("timezone", "时区", 5, true, Category.JUDGE, null),
    COUNTRY_PROFILE("countryProfile", "国家资料（电压、外汇管制）", 5, true, Category.SYSTEM, null);

    /** 补全向导的四步：系统已经知道、AI 背调能查到、你自己就能判断、只能问客户 */
    public enum Category { SYSTEM, RESEARCH, JUDGE, ASK }

    public static final int MODULES = 5;
    public static final int ALL_DONE = 31;
    public static final List<String> MODULE_NAMES = List.of("基础画像", "商业能力", "技术偏好", "决策人", "宏观背景");

    private final String key;
    private final String label;
    private final int module;
    private final boolean core;
    private final Category category;
    private final String dictType;

    ProfileField(String key, String label, int module, boolean core, Category category, String dictType) {
        this.key = key;
        this.label = label;
        this.module = module;
        this.core = core;
        this.category = category;
        this.dictType = dictType;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int module() {
        return module;
    }

    public boolean core() {
        return core;
    }

    public Category category() {
        return category;
    }

    public String dictType() {
        return dictType;
    }

    public boolean isContact() {
        return module == 4;
    }

    /** 模块对应的位：①=1、②=2、③=4、④=8、⑤=16 */
    public static int bit(int module) {
        return 1 << (module - 1);
    }

    public static Optional<ProfileField> of(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }

    public static List<ProfileField> coreOf(int module) {
        return Arrays.stream(values()).filter(f -> f.module == module && f.core).toList();
    }
}
