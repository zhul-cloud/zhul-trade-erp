package com.zhul.erp.modules.system.dict;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V1.5.15：货况「国产替代」对客户的英文名为 made in China（文字报价、报价单、PI 共用字典英文名） */
class ConditionDictEnTest extends IntegrationTestBase {

    @Test
    void domesticAlternativeShowsMadeInChina() {
        assertEquals("made in China", jdbc.queryForObject("select item_name_en from dict_item where tenant_id = 0 "
                + "and dict_type = 'inquiry_item_condition' and item_code = 'DOMESTIC_ALT' and deleted_at is null", String.class));
    }
}
