package com.zhul.erp.modules.document.support;

import com.zhul.erp.modules.product.support.CountryCatalog;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 发给客户的单据上的地点（贸易术语地点）一律输出英文：
 * 中文国家名按国家清单换成英文，术语地点字典的中文名换成字典的英文名；都找不到的原样输出。
 */
@Component
@RequiredArgsConstructor
public class PlaceNames {

    static final String DICT_TRADE_TERM_PLACE = "trade_term_place";

    private final CountryCatalog countries;
    private final DictItemService dictItemService;

    public String english(String place) {
        if (place == null || place.isBlank() || !hasChinese(place)) {
            return place;
        }
        String text = place.trim();
        String country = countries.englishOf(text);
        if (country != null) {
            return country;
        }
        for (DictItemVO d : dictItemService.listByDictType(DICT_TRADE_TERM_PLACE)) {
            if (text.equals(d.getItemName()) && d.getItemNameEn() != null && !d.getItemNameEn().isBlank()) {
                return d.getItemNameEn();
            }
        }
        return place;
    }

    private static boolean hasChinese(String s) {
        return s.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
    }
}
