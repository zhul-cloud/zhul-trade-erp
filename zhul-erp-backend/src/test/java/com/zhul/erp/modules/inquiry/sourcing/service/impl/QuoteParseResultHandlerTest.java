package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 报价识别结果的清洗：空结果、非 JSON、重复明细、税率不在字典、零价、超大金额 */
class QuoteParseResultHandlerTest {

    private final ObjectMapper om = new ObjectMapper();
    private final QuoteParseResultHandler handler = new QuoteParseResultHandler(null, null, om);
    private final QuoteDictSnapshot dicts = new QuoteDictSnapshot(dict(1, 2), dict(1, 3), dict(), dict(13, 1));

    private static QuoteDictSnapshot.Dict dict(Integer... codes) {
        Map<Integer, String> labels = new java.util.LinkedHashMap<>();
        for (Integer c : codes) {
            labels.put(c, "v" + c);
        }
        return new QuoteDictSnapshot.Dict(labels, Set.of(codes));
    }

    private JsonNode clean(String json) throws Exception {
        return om.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(handler.clean(json, dicts));
    }

    @Test
    void emptyOrBrokenOutput_becomesEmptyResult() throws Exception {
        for (String in : new String[] {null, "{}", "not json"}) {
            JsonNode out = clean(in);
            assertEquals(0, out.path("rows").size());
            assertEquals(0, out.path("otherModels").size());
            assertEquals("", out.path("common").asText());
        }
    }

    @Test
    void rows_validatedAgainstDicts_taxConvertedHalfUp() throws Exception {
        JsonNode out = clean("""
                {"rows":[
                  {"itemId":1,"raw":"a","unitPrice":0,"taxIncluded":false,"itemCondition":2,"leadTime":3,"confidence":"sure"},
                  {"itemId":1,"raw":"dup","unitPrice":5,"taxIncluded":false,"confidence":"sure"},
                  {"itemId":2,"raw":"b","unitPrice":100,"taxIncluded":true,"taxRate":6,"confidence":"sure"},
                  {"itemId":3,"raw":"c","unitPrice":99999999.995,"taxIncluded":true,"taxRate":1,"confidence":"sure"},
                  {"itemId":"x","raw":"bad id","unitPrice":1}
                ],"otherModels":[{"taskId":8,"model":" "}],"unknownModels":["A","A"],"common":" 含税 "}
                """);
        JsonNode rows = out.path("rows");
        assertEquals(3, rows.size(), "重复明细与非法 id 去掉");
        assertEquals("SURE", rows.get(0).path("confidence").asText(), "零价合法");
        assertEquals(0, new java.math.BigDecimal("0.00").compareTo(rows.get(0).path("unitPriceExTax").decimalValue()));
        assertFalse(rows.get(1).has("taxRate"), "税率不在字典置空");
        assertEquals("CHECK", rows.get(1).path("confidence").asText());
        assertEquals(0, new java.math.BigDecimal("88.50").compareTo(rows.get(1).path("unitPriceExTax").decimalValue()), "置空后按默认 13% 换算");
        assertEquals(0, new java.math.BigDecimal("100000000.00").compareTo(rows.get(2).path("unitPrice").decimalValue()));
        assertEquals(0, new java.math.BigDecimal("99009900.99").compareTo(rows.get(2).path("unitPriceExTax").decimalValue()), "1e8 ÷ 1.01 HALF_UP");
        assertTrue(out.path("otherModels").isEmpty());
        assertEquals(1, out.path("unknownModels").size());
        assertEquals("含税", out.path("common").asText());
    }
}
