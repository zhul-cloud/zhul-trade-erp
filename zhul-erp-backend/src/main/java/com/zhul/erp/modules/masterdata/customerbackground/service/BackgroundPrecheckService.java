package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundPrecheckVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundPrecheckVO.Item;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundFields;
import com.zhul.erp.modules.masterdata.customerbackground.support.BackgroundInput;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileFacts;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.FREE_EMAIL_DOMAINS;
import static com.zhul.erp.modules.masterdata.customerbackground.constants.BackgroundConstants.FREE_EMAIL_YAHOO_PREFIX;

/**
 * 背调前检查（spec crm/customer-background「背调前检查」）：背调会用到的关键信息是否齐全。
 * 取数口径与 {@link BackgroundInput} 相同；只读，不涉及事务。
 */
@Service
@RequiredArgsConstructor
public class BackgroundPrecheckService {

    private final CustomerService customerService;
    private final ProfileFacts facts;
    private final BackgroundInput input;
    private final BackgroundFields fields;

    public BackgroundPrecheckVO check(Long customerId) {
        CustomerDO c = customerService.requireInScope(customerId);
        List<Item> items = new ArrayList<>(6);
        items.add(website(c));
        items.add(companyEmail(facts.contacts(c.getId())));
        items.add(inquiryText(input.inquiryTextCount(c.getId())));
        items.add(basic(c));
        items.add(linkedin(c));
        items.add(companyInfo(c));
        int keyCount = (int) items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getKeyInfo()) && Item.OK.equals(i.getState())).count();
        BackgroundPrecheckVO vo = new BackgroundPrecheckVO();
        vo.setKeyCount(keyCount);
        vo.setLevel(level(keyCount));
        vo.setItems(items);
        return vo;
    }

    /** 关键信息一项都没有为很少，一项为一般，两项及以上为充足 */
    static String level(int keyCount) {
        if (keyCount <= 0) {
            return BackgroundPrecheckVO.LOW;
        }
        return keyCount == 1 ? BackgroundPrecheckVO.MEDIUM : BackgroundPrecheckVO.HIGH;
    }

    /** 免费邮箱的后缀（含 yahoo 各国站点），大小写不敏感 */
    static boolean freeEmailDomain(String domain) {
        String d = domain == null ? "" : domain.trim().toLowerCase();
        return FREE_EMAIL_DOMAINS.contains(d) || d.startsWith(FREE_EMAIL_YAHOO_PREFIX);
    }

    private static Item website(CustomerDO c) {
        Item i = item("website", "官网", true);
        return StringUtils.hasText(c.getWebsite()) ? ok(i, c.getWebsite().trim()) : missing(i, "没填", "profile:1");
    }

    /** 至少一个联系人的邮箱后缀不是免费邮箱 */
    static Item companyEmail(List<CustomerContactDO> contacts) {
        Item i = item("companyEmail", "企业邮箱", true);
        List<CustomerContactDO> company = new ArrayList<>(contacts.size());
        String firstFree = null;
        for (CustomerContactDO x : contacts) {
            String d = BackgroundInput.domain(x.getEmail());
            if (d.isEmpty()) {
                continue;
            }
            if (!freeEmailDomain(d)) {
                company.add(x);
            } else if (firstFree == null) {
                firstFree = d;
            }
        }
        if (!company.isEmpty()) {
            String domains = String.join("、", company.stream().map(x -> BackgroundInput.domain(x.getEmail())).distinct().toList());
            String name = company.get(0).getName();
            String who = !StringUtils.hasText(name) ? "" : "（" + name + (company.size() > 1 ? " 等 " + company.size() + " 人" : "") + "）";
            return ok(i, domains + who);
        }
        if (firstFree != null) {
            i.setState(Item.WARN);
            i.setReason("联系人用的是 " + firstFree + "，找不到公司");
            i.setFixTarget("contact");
            return i;
        }
        return missing(i, "没有联系人邮箱", "contact");
    }

    private static Item inquiryText(int count) {
        Item i = item("inquiryText", "询盘原文", true);
        return count > 0 ? ok(i, count + " 张") : missing(i, "没有询盘原文，在询盘管理登记询盘时贴上客户原文", null);
    }

    private static Item basic(CustomerDO c) {
        Item i = item("basic", "名称、国家", false);
        return ok(i, c.getName() + (StringUtils.hasText(c.getCountry()) ? " · " + c.getCountry() : ""));
    }

    private static Item linkedin(CustomerDO c) {
        Item i = item("linkedin", "领英", false);
        return StringUtils.hasText(c.getLinkedin()) ? ok(i, c.getLinkedin().trim()) : missing(i, "没填", "profile:1");
    }

    /** 公司类型、行业、主营产品；只缺主营产品时去「商业与供应链」页签 */
    private Item companyInfo(CustomerDO c) {
        Item i = item("companyInfo", "公司信息", false);
        String role = optionName("customerRole", c.getCustomerRole());
        String industry = optionName("industry", c.getIndustry());
        String products = StringUtils.hasText(c.getMainProducts()) ? c.getMainProducts().trim() : null;
        List<String> lack = new ArrayList<>(3);
        if (role == null) {
            lack.add("公司类型");
        }
        if (industry == null) {
            lack.add("行业");
        }
        if (products == null) {
            lack.add("主营产品");
        }
        if (lack.isEmpty()) {
            return ok(i, role + " · " + industry + " · " + products);
        }
        boolean onlyProducts = lack.size() == 1 && products == null;
        return missing(i, "缺 " + String.join("、", lack), onlyProducts ? "profile:2" : "profile:1");
    }

    private String optionName(String key, Integer code) {
        if (code == null || code == 0) {
            return null;
        }
        Map<Integer, String> names = fields.options(key);
        return names == null ? null : names.get(code);
    }

    private static Item item(String key, String label, boolean keyInfo) {
        Item i = new Item();
        i.setKey(key);
        i.setLabel(label);
        i.setKeyInfo(keyInfo);
        return i;
    }

    private static Item ok(Item i, String value) {
        i.setState(Item.OK);
        i.setValue(value);
        return i;
    }

    private static Item missing(Item i, String reason, String fixTarget) {
        i.setState(Item.MISSING);
        i.setReason(reason);
        i.setFixTarget(fixTarget);
        return i;
    }
}
