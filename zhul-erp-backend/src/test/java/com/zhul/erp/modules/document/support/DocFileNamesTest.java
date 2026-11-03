package com.zhul.erp.modules.document.support;

import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocFileNamesTest {

    @Test
    void joinsFourParts() {
        assertThat(DocFileNames.join("Fuzhou Fouwell Technology Co., Ltd", DocFileNames.QUOTATION, "ACROBOT", "FWQT20261104001"))
                .isEqualTo("Fuzhou Fouwell Technology Co., Ltd - Quotation - ACROBOT - FWQT20261104001");
        assertThat(DocFileNames.join("Fuzhou Fouwell Technology Co., Ltd", DocFileNames.PL, "ACROBOT", "PL001"))
                .isEqualTo("Fuzhou Fouwell Technology Co., Ltd - PACKING LIST - ACROBOT - PL001");
    }

    @Test
    void emptyPartsOmitted_illegalCharsReplaced_customerTruncated() {
        assertThat(DocFileNames.join("", DocFileNames.PI, "", "PI001")).isEqualTo("Invoice - PI001");
        assertThat(DocFileNames.join(null, DocFileNames.CI, "A/B:C*\"D\"", "CI001")).isEqualTo("CI - A B C D - CI001");
        String longName = "X".repeat(80);
        assertThat(DocFileNames.join("", DocFileNames.CI, longName, "CI001")).isEqualTo("CI - " + "X".repeat(60) + " - CI001");
    }

    @Test
    void customerShortNameFirst() {
        CustomerDO c = new CustomerDO();
        c.setName("Acrobot Automation GmbH");
        assertThat(DocFileNames.customerName(c)).isEqualTo("Acrobot Automation GmbH");
        c.setShortName("ACROBOT");
        assertThat(DocFileNames.customerName(c)).isEqualTo("ACROBOT");
        assertThat(DocFileNames.customerName(null)).isEmpty();
    }
}
