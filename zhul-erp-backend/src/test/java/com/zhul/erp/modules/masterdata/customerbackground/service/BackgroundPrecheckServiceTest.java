package com.zhul.erp.modules.masterdata.customerbackground.service;

import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundPrecheckVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundPrecheckVO.Item;
import com.zhul.erp.modules.masterdata.customerprofile.entity.CustomerContactDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec crm/customer-background「背调前检查」：免费邮箱判断、企业邮箱检查项、充足度 */
class BackgroundPrecheckServiceTest {

    private static CustomerContactDO contact(String name, String email) {
        CustomerContactDO c = new CustomerContactDO();
        c.setName(name);
        c.setEmail(email);
        return c;
    }

    @Test
    void freeEmailDomain_commonProvidersAndYahooCountrySites() {
        assertTrue(BackgroundPrecheckService.freeEmailDomain("gmail.com"));
        assertTrue(BackgroundPrecheckService.freeEmailDomain(" Gmail.COM "));
        assertTrue(BackgroundPrecheckService.freeEmailDomain("qq.com"));
        assertTrue(BackgroundPrecheckService.freeEmailDomain("yahoo.co.uk"));
        assertTrue(BackgroundPrecheckService.freeEmailDomain("yahoo.com.au"));
        assertFalse(BackgroundPrecheckService.freeEmailDomain("pacificcontrols.com.au"));
        assertFalse(BackgroundPrecheckService.freeEmailDomain("mygmail.com"));
        assertFalse(BackgroundPrecheckService.freeEmailDomain(null));
    }

    @Test
    void companyEmail_companyDomainWins() {
        Item i = BackgroundPrecheckService.companyEmail(List.of(
                contact("Tom", "tom@gmail.com"),
                contact("James Walker", "James@PacificControls.com.au"),
                contact("Liam Chen", "liam@pacificcontrols.com.au")));
        assertEquals(Item.OK, i.getState());
        assertEquals("pacificcontrols.com.au（James Walker 等 2 人）", i.getValue());
    }

    @Test
    void companyEmail_onlyFreeEmailWarns() {
        Item i = BackgroundPrecheckService.companyEmail(List.of(contact("Tom", "tom@gmail.com"), contact("Ann", "")));
        assertEquals(Item.WARN, i.getState());
        assertEquals("联系人用的是 gmail.com，找不到公司", i.getReason());
        assertEquals("contact", i.getFixTarget());
    }

    @Test
    void companyEmail_noEmailOrNoContactIsMissing() {
        Item none = BackgroundPrecheckService.companyEmail(List.of());
        assertEquals(Item.MISSING, none.getState());
        assertEquals("没有联系人邮箱", none.getReason());
        Item bad = BackgroundPrecheckService.companyEmail(List.of(contact("Tom", "not-an-email")));
        assertEquals(Item.MISSING, bad.getState());
    }

    @Test
    void level_byKeyCount() {
        assertEquals(BackgroundPrecheckVO.LOW, BackgroundPrecheckService.level(0));
        assertEquals(BackgroundPrecheckVO.MEDIUM, BackgroundPrecheckService.level(1));
        assertEquals(BackgroundPrecheckVO.HIGH, BackgroundPrecheckService.level(2));
        assertEquals(BackgroundPrecheckVO.HIGH, BackgroundPrecheckService.level(3));
    }
}
