package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.document.constants.DocTypes;
import com.zhul.erp.modules.document.support.Placeholders;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** openspec actual-model-and-description D1：单据型号、客户型号与「型号是否相同」；客户型号占位符只在报价单、PI 可用 */
class DocModelsTest {

    private static InquiryItemDO item(String original, String confirmed, String actual) {
        InquiryItemDO i = new InquiryItemDO();
        i.setOriginalModel(original);
        i.setConfirmedModel(confirmed);
        i.setActualModel(actual);
        return i;
    }

    @Test
    void docModel_prefersActualThenConfirmedThenOriginal() {
        assertThat(DocModels.docModel(item("LC2D18B7", "LC2D18B7", " LC2D18B7C "))).isEqualTo("LC2D18B7C");
        assertThat(DocModels.docModel(item("LC2D18B7", "LC2D18B7", ""))).isEqualTo("LC2D18B7");
        assertThat(DocModels.docModel(item("Contactor, 32A", null, null))).isEqualTo("Contactor, 32A");
        assertThat(DocModels.docModel(item(null, null, null))).isEmpty();
        assertThat(DocModels.customerModel(item("LC2D18B7 ", "", "LC2D18B7C"))).isEqualTo("LC2D18B7");
    }

    @Test
    void shownCustomerModel_onlyWhenModelsDiffer() {
        assertThat(DocModels.shownCustomerModel("LC2D18B7C", "LC2D18B7")).isEqualTo("LC2D18B7");
        assertThat(DocModels.shownCustomerModel("LC2D18B7C", "lc2d18b7c")).isEmpty();
        assertThat(DocModels.shownCustomerModel("6ES7214-1AG40-0XB0", "6es7214 1ag40 0xb0")).isEmpty();
        assertThat(DocModels.shownCustomerModel("LC2D18B7C", "")).isEmpty();
        assertThat(DocModels.shownCustomerModel("LC2D18B7C", null)).isEmpty();
        assertThat(DocModels.sameModel("", "LC2D18B7")).isTrue();
    }

    @Test
    void customerModelPlaceholder_quotationAndPiOnly() {
        assertThat(Placeholders.known(DocTypes.QUOTATION, "item.customerModel")).isTrue();
        assertThat(Placeholders.known(DocTypes.PI, "item.customerModel")).isTrue();
        assertThat(Placeholders.known(DocTypes.CI, "item.customerModel")).isFalse();
        assertThat(Placeholders.known(DocTypes.PL, "item.customerModel")).isFalse();
    }
}
