package com.zhul.erp.modules.logistics.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** audit BUS-082、BUS-083：CI / PL 导出前的必填检查与收货人取值 */
class DocGroupExportCheckTest {

    private static Map<String, Object> complete() {
        Map<String, Object> h = new HashMap<>();
        h.put("seller.name", "张伟");
        h.put("seller.email", "rep@example.com");
        h.put("seller.phone", "13800000000");
        h.put("consignee.name", "ACROBOT");
        h.put("_check.consigneeStreet", "8 Harbour Road");
        h.put("consignee.contact", "Ana");
        h.put("consignee.phone", "+55 11 0000");
        h.put("consignee.email", "ana@example.com");
        h.put("_check.customer", "ACROBOT");
        return h;
    }

    private static RenderModel model(Map<String, Object> h, String hsCode) {
        Map<String, Object> item = new HashMap<>();
        item.put("item.model", "6ES7321-1FH00-0AA0");
        item.put("item.hsCode", hsCode);
        return new RenderModel(h, List.of(item), List.of());
    }

    @Test
    void completeInfo_exportsWithoutWhatsapp() {
        assertDoesNotThrow(() -> DocGroupService.requireComplete(true, model(complete(), "85371090")));
        assertDoesNotThrow(() -> DocGroupService.requireComplete(false, model(complete(), "")), "PL 不要求 HS 编码，也不要求 WhatsApp");
    }

    @Test
    void plRequiresConsigneeEmail_ciDoesNot() {
        Map<String, Object> h = complete();
        h.put("consignee.email", "");
        assertDoesNotThrow(() -> DocGroupService.requireComplete(true, model(h, "85371090")));
        BizException e = assertThrows(BizException.class, () -> DocGroupService.requireComplete(false, model(h, "")));
        assertEquals("导出前请先补全：收货人的邮箱：在客户「ACROBOT」档案的「收货与单证信息」里补全", e.getMessage());
    }

    @Test
    void noOwner_andMissingHsCode_listedTogether() {
        Map<String, Object> h = complete();
        h.put("seller.name", "");
        BizException e = assertThrows(BizException.class, () -> DocGroupService.requireComplete(true, model(h, "")));
        assertTrue(e.getMessage().contains("订单没有业务员"), e.getMessage());
        assertTrue(e.getMessage().contains("型号 6ES7321-1FH00-0AA0 的 H.S Code"), e.getMessage());
    }

    @Test
    void consignee_fallsBackToPartyWithAddress_andFillsContactFromCustomer() {
        PartyDTO snapshot = new PartyDTO();
        snapshot.setName("ACROBOT");
        snapshot.setCountry("Brazil");
        PartyDTO current = new PartyDTO();
        current.setName("ACROBOT Warehouse");
        current.setAddress("8 Harbour Road");
        current.setPhone("+55 11 1111");
        assertSame(current, DocGroupService.withAddress(snapshot, null, current), "订单快照只有国家时改用有详细地址的");
        assertSame(snapshot, DocGroupService.withAddress(snapshot, null), "都没有详细地址时保留订单快照");

        CustomerDO c = new CustomerDO();
        c.setContactName("Ana");
        c.setContactPhone("+55 11 0000");
        c.setContactEmail("ana@example.com");
        PartyDTO filled = DocGroupService.withContact(current, c);
        assertEquals("Ana", filled.getContact());
        assertEquals("+55 11 1111", filled.getPhone(), "已有的电话不改");
        assertEquals("ana@example.com", filled.getEmail());
    }
}
