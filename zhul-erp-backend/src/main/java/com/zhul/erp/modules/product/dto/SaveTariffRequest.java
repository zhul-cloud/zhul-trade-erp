package com.zhul.erp.modules.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 保存关税参考：按国家整份覆盖；没传的国家不变，税率为空表示未填 */
@Data
public class SaveTariffRequest {
    @NotNull(message = "请填写关税参考")
    @Valid
    private List<Rate> rates = new ArrayList<>();

    @Data
    public static class Rate {
        private String countryCode;
        private String rate;
        private String note;
        private String source;
    }
}
