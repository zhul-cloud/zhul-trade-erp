package com.zhul.erp.modules.quotation.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListSortTest {

    private static final Map<String, String> COLUMNS = Map.of("totalAmount", "total_amount_cny");

    @Test
    void defaultsToUpdateTimeWhenFieldMissing() {
        assertThat(ListSort.orderBy(null, null, COLUMNS)).isEqualTo("ORDER BY update_time DESC, id DESC");
    }

    @Test
    void unknownFieldFallsBackToUpdateTime() {
        assertThat(ListSort.orderBy("create_time; DROP TABLE x", "ascend", COLUMNS))
                .isEqualTo("ORDER BY update_time DESC, id DESC");
    }

    @Test
    void whitelistedFieldUsesGivenDirection() {
        assertThat(ListSort.orderBy("totalAmount", "ascend", COLUMNS))
                .isEqualTo("ORDER BY total_amount_cny ASC, id DESC");
        assertThat(ListSort.orderBy("totalAmount", "descend", COLUMNS))
                .isEqualTo("ORDER BY total_amount_cny DESC, id DESC");
    }
}
