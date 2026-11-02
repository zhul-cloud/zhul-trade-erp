package com.zhul.erp.modules.masterdata.customerprofile;

import com.zhul.erp.modules.masterdata.customerprofile.entity.CountryProfileDO;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileCompleteness;
import com.zhul.erp.modules.masterdata.customerprofile.support.ProfileField;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户档案完整度：五个模块的核心项与边界（spec master-data/customer-profile「客户档案完整度」） */
class ProfileCompletenessTest {

    private static CustomerDO full() {
        CustomerDO c = new CustomerDO();
        c.setName("Pacific Controls Pty Ltd");
        c.setCustomerRole(2);
        c.setIndustry(8);
        c.setLinkedin("https://linkedin.com/company/pc");
        c.setCompanySize(2);
        c.setPotentialRating(4);
        c.setMainProducts("Water automation");
        c.setQualityReq(1);
        c.setPriceSensitivity(2);
        c.setCustomsAbility(1);
        c.setPaymentMethod(3);
        c.setCurrentBrands("[{\"name\":\"Siemens\"}]");
        c.setDesignAbility(1);
        c.setProtocols("[2]");
        c.setRepairAbility(1);
        c.setCountry("Australia");
        c.setCity("Sydney");
        c.setTimezone("Australia/Sydney");
        return c;
    }

    private static CountryProfileDO country() {
        CountryProfileDO d = new CountryProfileDO();
        d.setVoltages("[3,7]");
        d.setFxControl(1);
        return d;
    }

    private static CustomerContactDO contact(boolean primary, String name, int job, int decision, int style) {
        CustomerContactDO x = new CustomerContactDO();
        x.setIsPrimary(primary ? 1 : 0);
        x.setName(name);
        x.setJobRole(job);
        x.setDecisionRole(decision);
        x.setCommStyle(style);
        return x;
    }

    @Test
    void allModulesComplete() {
        ProfileCompleteness.Result r = ProfileCompleteness.evaluate(full(), List.of(contact(true, "James", 1, 1, 1)), country(), false);
        assertEquals(31, r.mask());
        assertTrue(r.complete());
    }

    @Test
    void emptyCustomerMissesEverything() {
        CustomerDO c = new CustomerDO();
        ProfileCompleteness.Result r = ProfileCompleteness.evaluate(c, List.of(), null, false);
        assertEquals(0, r.mask());
        assertEquals(5, r.missing().size());
        assertEquals(4, r.missing().get(4).size(), "没有联系人时决策人缺四项");
    }

    @Test
    void derivedSourcesCompleteTechModule() {
        CustomerDO c = full();
        c.setCurrentBrands("[]");
        assertEquals(0, ProfileCompleteness.evaluate(c, List.of(), country(), false).mask() & 4, "没有品牌");
        assertEquals(4, ProfileCompleteness.evaluate(c, List.of(), country(), true).mask() & 4, "询盘推导出常用品牌即可");
        c.setVoltages(null);
        assertEquals(4, ProfileCompleteness.evaluate(c, List.of(), country(), true).mask() & 4, "国家资料有电压即可");
        assertEquals(0, ProfileCompleteness.evaluate(c, List.of(), null, true).mask() & 4, "都没有电压");
    }

    @Test
    void webAcceptsWebsiteOrLinkedin() {
        CustomerDO c = full();
        c.setLinkedin("");
        assertFalse((ProfileCompleteness.evaluate(c, List.of(), country(), false).mask() & 1) != 0);
        c.setWebsite("https://pc.com.au");
        assertTrue((ProfileCompleteness.evaluate(c, List.of(), country(), false).mask() & 1) != 0);
    }

    @Test
    void decisionModuleNeedsOneCompleteContact() {
        List<CustomerContactDO> partial = List.of(contact(true, "James", 1, 0, 1), contact(false, "Liam", 2, 0, 3));
        ProfileCompleteness.Result r = ProfileCompleteness.evaluate(full(), partial, country(), false);
        assertEquals(0, r.mask() & 8);
        assertEquals(List.of(ProfileField.CONTACT_DECISION_ROLE), r.missing().get(4));
        List<CustomerContactDO> one = List.of(contact(true, "James", 1, 0, 1), contact(false, "Liam", 2, 2, 3));
        assertEquals(8, ProfileCompleteness.evaluate(full(), one, country(), false).mask() & 8, "有一个联系人四项齐全即可");
    }

    @Test
    void macroNeedsCountryProfileWithVoltageAndFx() {
        CountryProfileDO noFx = country();
        noFx.setFxControl(0);
        ProfileCompleteness.Result r = ProfileCompleteness.evaluate(full(), List.of(), noFx, false);
        assertEquals(0, r.mask() & 16);
        assertEquals(List.of(ProfileField.COUNTRY_PROFILE), r.missing().get(5));
    }
}
