package com.zhul.erp.modules.masterdata.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 客户名称查重用的规范化：转小写 → 非字母数字当作分隔 → 反复去掉结尾的公司后缀词 → 拼接去空格。
 * 例：「ABC Automation GmbH」「abc automation」「ABC-Automation Co., Ltd.」都得到 {@code abcautomation}。
 * 名称全部由后缀词组成时（如「Trading Co Ltd」去完为空）保留原词，避免规范化成空串后误判重复。
 */
public final class CustomerNameNormalizer {

    private static final Set<String> SUFFIXES = Set.of(
            "co", "company", "ltd", "limited", "llc", "inc", "incorporated", "corp", "corporation",
            "gmbh", "ag", "sa", "sas", "srl", "bv", "nv", "pty", "plc", "llp", "kg", "oy", "ab", "as", "spa");

    private CustomerNameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String[] parts = name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        List<String> words = new ArrayList<>(Arrays.stream(parts).filter(w -> !w.isEmpty()).toList());
        List<String> original = List.copyOf(words);
        while (!words.isEmpty() && SUFFIXES.contains(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        return String.join("", words.isEmpty() ? original : words);
    }
}
