package com.zhul.erp.modules.masterdata.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CountryTimezoneCatalogTest {

    private CountryTimezoneCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CountryTimezoneCatalog(new ObjectMapper());
        catalog.load();
    }

    @Test
    void singleZoneCountryHasOnlyItsOwnZone() {
        assertThat(catalog.all().get("DE")).containsExactly("Europe/Berlin");
    }

    @Test
    void multiZoneCountryListsAll() {
        assertThat(catalog.all().get("US")).contains("America/New_York", "America/Los_Angeles").hasSizeGreaterThan(1);
    }

    @Test
    void everyZoneIsValidForJava() {
        catalog.all().values().forEach(zones -> zones.forEach(z -> assertThat(ZoneId.of(z)).isNotNull()));
    }
}
