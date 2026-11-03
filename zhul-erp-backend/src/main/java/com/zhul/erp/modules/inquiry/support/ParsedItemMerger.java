package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseGroupDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseItemDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseOutputDTO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 解析结果里同一型号出现多次（客户在不同位置重复列出）时合并成一行：
 * 品牌相同（忽略大小写）、型号相同（忽略大小写与分隔符）、订货号相同才算同一型号。
 * 合并到第一次出现的那一行：数量相加（没写或小于 1 按 1 算），备注与说明去重拼接，铭牌合并，
 * 识别置信度取更需要核对的那个。没有型号的行不合并。
 */
public final class ParsedItemMerger {

    private static final String SEP = "；";

    private ParsedItemMerger() {
    }

    public static AiParseOutputDTO merge(AiParseOutputDTO output) {
        if (output == null || output.getGroups() == null) {
            return output;
        }
        Map<String, AiParseItemDTO> first = new HashMap<>();
        List<AiParseGroupDTO> groups = new ArrayList<>(output.getGroups().size());
        for (AiParseGroupDTO g : output.getGroups()) {
            List<AiParseItemDTO> kept = new ArrayList<>();
            for (AiParseItemDTO it : g.getItems() == null ? List.<AiParseItemDTO>of() : g.getItems()) {
                String key = key(g.getBrand(), it);
                AiParseItemDTO target = key == null ? null : first.get(key);
                if (target == null) {
                    if (key != null) {
                        first.put(key, it);
                    }
                    kept.add(it);
                } else {
                    absorb(target, it);
                }
            }
            if (!kept.isEmpty()) {
                g.setItems(kept);
                groups.add(g);
            }
        }
        output.setGroups(groups);
        return output;
    }

    private static String key(String brand, AiParseItemDTO it) {
        String model = PriceKeys.model(StringUtils.hasText(it.getConfirmedModel()) ? it.getConfirmedModel() : it.getOriginalModel());
        if (!StringUtils.hasText(model)) {
            return null;
        }
        String b = brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
        String code = it.getOrderCode() == null ? "" : PriceKeys.model(it.getOrderCode());
        return b + "|" + model + "|" + (code == null ? "" : code);
    }

    private static void absorb(AiParseItemDTO target, AiParseItemDTO dup) {
        target.setQuantity(qty(target) + qty(dup));
        target.setRemark(join(target.getRemark(), dup.getRemark()));
        target.setCorrectionNote(join(target.getCorrectionNote(), dup.getCorrectionNote()));
        if (dup.getConfidence() != null && (target.getConfidence() == null || dup.getConfidence() > target.getConfidence())) {
            target.setConfidence(dup.getConfidence());
        }
        if (dup.getNameplates() != null && !dup.getNameplates().isEmpty()) {
            Set<Integer> plates = new LinkedHashSet<>(target.getNameplates() == null ? List.of() : target.getNameplates());
            plates.addAll(dup.getNameplates());
            target.setNameplates(new ArrayList<>(plates));
        }
    }

    private static int qty(AiParseItemDTO it) {
        return it.getQuantity() == null || it.getQuantity() < 1 ? 1 : it.getQuantity();
    }

    private static String join(String a, String b) {
        if (!StringUtils.hasText(b) || (a != null && a.contains(b.trim()))) {
            return a;
        }
        return StringUtils.hasText(a) ? a + SEP + b.trim() : b.trim();
    }
}
