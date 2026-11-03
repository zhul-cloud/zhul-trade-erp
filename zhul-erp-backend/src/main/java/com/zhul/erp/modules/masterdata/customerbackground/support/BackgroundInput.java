package com.zhul.erp.modules.masterdata.customerbackground.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.masterdata.customerprofile.dto.CustomerProfileVO;
import com.zhul.erp.modules.masterdata.customerprofile.dto.RfmDetailVO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerGradeService;
import com.zhul.erp.modules.masterdata.customerprofile.support.JsonArrays;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 背调的输入组装（design 决策 6）：客户名称、国家、官网、已有画像字段、联系人姓名与邮箱域名、
 * 系统推导的成交情况与常用品牌、最近 3 张询盘原文（每张前 3000 字）。联系人的电话与 WhatsApp 不发送。
 */
@Component
@RequiredArgsConstructor
public class BackgroundInput {

    static final int INQUIRY_COUNT = 3;
    static final int INQUIRY_CHARS = 3000;

    private final BackgroundFields fields;
    private final ProfileFacts facts;
    private final CustomerGradeService gradeService;
    private final CustomerInquiryMapper inquiryMapper;

    /** research 模式的输入 */
    public Map<String, Object> research(CustomerDO c) {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("mode", "research");
        in.put("fields", fields.catalog());
        in.put("customer", customer(c));
        in.put("inquiries", inquiries(c.getId()));
        return in;
    }

    private Map<String, Object> customer(CustomerDO c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getName());
        m.put("country", c.getCountry());
        m.put("website", nz(c.getWebsite()));
        m.put("linkedin", nz(c.getLinkedin()));
        m.put("profile", profile(c));
        List<Map<String, Object>> contacts = new ArrayList<>();
        for (CustomerContactDO x : facts.contacts(c.getId())) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", nz(x.getName()));
            p.put("title", nz(x.getTitle()));
            p.put("emailDomain", domain(x.getEmail()));
            p.put("primary", Integer.valueOf(1).equals(x.getIsPrimary()));
            contacts.add(p);
        }
        m.put("contacts", contacts);
        m.put("deals", deals(c));
        m.put("topBrands", facts.topBrands(c.getId(), 5).stream().map(CustomerProfileVO.BrandCount::getName).toList());
        return m;
    }

    /** 已填的画像字段，按中文名写成文字；选项型换成选项名 */
    private Map<String, Object> profile(CustomerDO c) {
        Map<String, Object> p = new LinkedHashMap<>();
        option(p, "customerRole", "公司类型", c.getCustomerRole());
        option(p, "industry", "行业", c.getIndustry());
        text(p, "其他社媒", c.getSocialUrl());
        if (c.getFoundedYear() != null && c.getFoundedYear() > 0) {
            p.put("成立年份", c.getFoundedYear());
        }
        option(p, "companySize", "公司规模", c.getCompanySize());
        text(p, "主营产品", c.getMainProducts());
        text(p, "现有中国供应商", c.getCnSuppliers());
        if (c.getEstAnnualPurchase() != null && c.getEstAnnualPurchase().signum() > 0) {
            p.put("年度采购量估算", c.getEstAnnualPurchase().stripTrailingZeros().toPlainString() + " " + nz(c.getEstPurchaseCurrency()));
        }
        option(p, "qualityReq", "质量要求", c.getQualityReq());
        option(p, "priceSensitivity", "价格敏感度", c.getPriceSensitivity());
        option(p, "customsAbility", "清关能力", c.getCustomsAbility());
        option(p, "paymentMethod", "付款习惯", c.getPaymentMethod());
        List<String> brands = JsonArrays.read(c.getCurrentBrands(), new TypeReference<List<CustomerProfileVO.BrandRef>>() { })
                .stream().map(CustomerProfileVO.BrandRef::getName).toList();
        if (!brands.isEmpty()) {
            p.put("现有使用品牌", String.join("、", brands));
        }
        option(p, "designAbility", "设计能力", c.getDesignAbility());
        options(p, "protocols", "常用通讯协议", c.getProtocols());
        options(p, "voltages", "电源电压", c.getVoltages());
        options(p, "certifications", "认证要求", c.getCertifications());
        option(p, "repairAbility", "售后维修能力", c.getRepairAbility());
        text(p, "城市", c.getCity());
        if (c.getPotentialRating() != null && c.getPotentialRating() > 0) {
            p.put("潜力评级", c.getPotentialRating() + " 星");
        }
        return p;
    }

    /** 成交情况只给区间，不给具体金额 */
    private Map<String, Object> deals(CustomerDO c) {
        RfmDetailVO rfm = gradeService.detail(c);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("ordersInYear", rfm.getOrdersInYear() == null ? 0 : rfm.getOrdersInYear());
        d.put("totalRangeUsd", range(rfm.getUsdTotal()));
        d.put("lastOrderDate", rfm.getLastOrderDate() == null ? "" : rfm.getLastOrderDate().toString());
        return d;
    }

    static String range(BigDecimal usd) {
        if (usd == null || usd.signum() <= 0) {
            return "没有成交";
        }
        if (usd.compareTo(BigDecimal.valueOf(10_000)) < 0) {
            return "1 万美元以下";
        }
        if (usd.compareTo(BigDecimal.valueOf(50_000)) < 0) {
            return "1–5 万美元";
        }
        if (usd.compareTo(BigDecimal.valueOf(200_000)) < 0) {
            return "5–20 万美元";
        }
        return "20 万美元以上";
    }

    private List<Map<String, Object>> inquiries(Long customerId) {
        List<CustomerInquiryDO> rows = inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                .select(CustomerInquiryDO::getInquiryCode, CustomerInquiryDO::getInquiryDate, CustomerInquiryDO::getRawContent)
                .eq(CustomerInquiryDO::getCustomerId, customerId)
                .isNull(CustomerInquiryDO::getDeletedAt)
                .ne(CustomerInquiryDO::getRawContent, "")
                .isNotNull(CustomerInquiryDO::getRawContent)
                .orderByDesc(CustomerInquiryDO::getInquiryDate)
                .orderByDesc(CustomerInquiryDO::getId)
                .last("LIMIT " + INQUIRY_COUNT));
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (CustomerInquiryDO r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.getInquiryDate() == null ? "" : r.getInquiryDate().toString());
            String raw = r.getRawContent();
            m.put("content", raw.length() > INQUIRY_CHARS ? raw.substring(0, INQUIRY_CHARS) : raw);
            out.add(m);
        }
        return out;
    }

    private void option(Map<String, Object> p, String key, String label, Integer code) {
        if (code == null || code == 0) {
            return;
        }
        Map<Integer, String> names = fields.options(key);
        String name = names == null ? null : names.get(code);
        if (name != null) {
            p.put(label, name);
        }
    }

    private void options(Map<String, Object> p, String key, String label, String json) {
        Map<Integer, String> names = fields.options(key);
        List<String> out = new ArrayList<>();
        for (Integer code : JsonArrays.ints(json)) {
            String name = names == null ? null : names.get(code);
            if (name != null) {
                out.add(name);
            }
        }
        if (!out.isEmpty()) {
            p.put(label, String.join("、", out));
        }
    }

    private static void text(Map<String, Object> p, String label, String v) {
        if (StringUtils.hasText(v)) {
            p.put(label, v.trim());
        }
    }

    static String domain(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "";
        }
        return email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
