package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.common.constants.DictTypes;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 货况、货期、生命周期字典：一次请求内取一次快照，供校验、Excel 解析与展示 */
@Component
@RequiredArgsConstructor
public class QuoteDicts {

    private final DictItemService dictItemService;

    public QuoteDictSnapshot snapshot() {
        return new QuoteDictSnapshot(load(DictTypes.ITEM_CONDITION), load(DictTypes.LEAD_TIME), load(DictTypes.LIFECYCLE),
                load(DictTypes.TAX_RATE));
    }

    private QuoteDictSnapshot.Dict load(String dictType) {
        List<DictItemVO> items = dictItemService.listByDictType(dictType);
        Map<Integer, String> labels = new LinkedHashMap<>(items.size() * 2);
        Set<Integer> enabled = new LinkedHashSet<>(items.size() * 2);
        for (DictItemVO item : items) {
            String v = item.getItemValue() == null ? "" : item.getItemValue().trim();
            if (!v.matches("\\d{1,3}")) {
                continue;
            }
            int code = Integer.parseInt(v);
            labels.putIfAbsent(code, item.getItemName());
            if (Integer.valueOf(1).equals(item.getStatus())) {
                enabled.add(code);
            }
        }
        return new QuoteDictSnapshot.Dict(labels, enabled);
    }
}
