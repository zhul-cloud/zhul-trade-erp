package com.zhul.erp.modules.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.document.support.PlaceNames;
import com.zhul.erp.modules.product.support.CountryCatalog;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.service.DictItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 发给客户的单据（报价单、PI）：术语地点与业务员输出英文 */
class CustomerDocTextTest {

    private PlaceNames placeNames;

    @BeforeEach
    void setUp() throws Exception {
        CountryCatalog countries = new CountryCatalog(new ObjectMapper());
        countries.load();
        DictItemService dicts = mock(DictItemService.class);
        DictItemVO hk = new DictItemVO();
        hk.setItemName("香港");
        hk.setItemNameEn("Hong Kong");
        when(dicts.listByDictType("trade_term_place")).thenReturn(List.of(hk));
        placeNames = new PlaceNames(countries, dicts);
    }

    @Test
    void 中文国家名换成英文() {
        assertThat(placeNames.english("危地马拉")).isEqualTo("Guatemala");
        assertThat(placeNames.english(" 德国 ")).isEqualTo("Germany");
    }

    @Test
    void 术语地点字典的中文名换成字典英文名() {
        assertThat(placeNames.english("香港")).isEqualTo("Hong Kong");
    }

    @Test
    void 英文和找不到的原样输出() {
        assertThat(placeNames.english("Guatemala City")).isEqualTo("Guatemala City");
        assertThat(placeNames.english("某某仓库")).isEqualTo("某某仓库");
        assertThat(placeNames.english("")).isEmpty();
        assertThat(placeNames.english(null)).isNull();
    }

    @Test
    void 业务员用昵称_没填昵称用姓名() {
        UserBasicDO u = new UserBasicDO();
        u.setName("张三");
        u.setNickname("Kevin");
        assertThat(QuotationRenderModels.sellerName(u)).isEqualTo("Kevin");
        u.setNickname(" ");
        assertThat(QuotationRenderModels.sellerName(u)).isEqualTo("张三");
        assertThat(QuotationRenderModels.sellerName(null)).isEmpty();
    }

    @Test
    void 术语和地点拼接() {
        assertThat(QuotationRenderModels.incoterm("DAP", "Guatemala")).isEqualTo("DAP Guatemala");
        assertThat(QuotationRenderModels.incoterm("EXW", "")).isEqualTo("EXW");
    }
}
