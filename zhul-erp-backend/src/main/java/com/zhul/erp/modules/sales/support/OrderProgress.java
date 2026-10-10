package com.zhul.erp.modules.sales.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.service.DictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 字典「销售订单状态」：待采购固定为第一步、已完成为终点、已取消单列（三个内置码不看启停）；
 * 中间步骤按字典排序，停用的步骤不能再推进到，但已处于该步骤的型号照常显示与排序。
 */
@Component
@RequiredArgsConstructor
public class OrderProgress {

    private static final Map<String, String> BUILTIN_NAMES = Map.of(SalesConstants.PROGRESS_PENDING, "待采购",
            SalesConstants.PROGRESS_COMPLETED, "已完成", SalesConstants.PROGRESS_CANCELLED, "已取消");

    private final DictItemService dictItemService;

    public record Step(String code, String name, boolean enabled) {
    }

    /** 进行中的步骤（含停用的），按推进顺序：待采购在最前，不含已完成、已取消 */
    public List<Step> ordered() {
        List<DictItemVO> items = dictItemService.listByDictType(SalesConstants.DICT_SO_STATUS);
        List<Step> list = new ArrayList<>(items.size() + 1);
        list.add(new Step(SalesConstants.PROGRESS_PENDING, nameIn(items, SalesConstants.PROGRESS_PENDING), true));
        items.stream()
                .filter(d -> !BUILTIN_NAMES.containsKey(d.getItemCode()))
                .sorted(Comparator.comparing(DictItemVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(d -> list.add(new Step(d.getItemCode(), d.getItemName(), Objects.equals(d.getStatus(), 1))));
        return list;
    }

    /** 码值 → 名称（含内置码） */
    public Map<String, String> names() {
        List<DictItemVO> items = dictItemService.listByDictType(SalesConstants.DICT_SO_STATUS);
        Map<String, String> map = new LinkedHashMap<>();
        BUILTIN_NAMES.keySet().forEach(k -> map.put(k, nameIn(items, k)));
        items.forEach(d -> map.putIfAbsent(d.getItemCode(), d.getItemName()));
        return map;
    }

    /** 推进顺序中的位置；字典里已删除的码排最后 */
    public static int rank(List<Step> ordered, String code) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).code().equals(code)) {
                return i;
            }
        }
        return ordered.size();
    }

    /** 最后一个启用的进行中步骤：所有型号到这一步后才能确认客户收货 */
    public static Step last(List<Step> ordered) {
        for (int i = ordered.size() - 1; i >= 0; i--) {
            if (ordered.get(i).enabled()) {
                return ordered.get(i);
            }
        }
        return ordered.get(0);
    }

    /** 可推进到的步骤：启用的进行中步骤 */
    public static Step require(List<Step> ordered, String code) {
        return ordered.stream().filter(s -> s.enabled() && s.code().equals(code)).findFirst()
                .orElseThrow(() -> new BizException("订单进度不存在或已停用"));
    }

    private static String nameIn(List<DictItemVO> items, String code) {
        return items.stream().filter(d -> code.equals(d.getItemCode())).map(DictItemVO::getItemName).findFirst()
                .orElse(BUILTIN_NAMES.getOrDefault(code, code));
    }
}
