package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 一个 6 位海关编码的目的国进口关税参考：固定 9 个主要出口国，未填的国家税率为空 */
@Data
public class TariffVO {
    private String hs6;
    private List<Rate> rates = new ArrayList<>();
    /** 自己填了这个编码（前 6 位相同）的品类名称 */
    private List<String> categories = new ArrayList<>();

    @Data
    public static class Rate {
        private String countryCode;
        private String countryName;
        /** 进口关税文字，空表示未填 */
        private String rate;
        private String note;
        private String source;
        private LocalDate checkedAt;
        private String updateBy;
    }
}
