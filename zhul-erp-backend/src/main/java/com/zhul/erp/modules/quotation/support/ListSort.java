package com.zhul.erp.modules.quotation.support;

import java.util.Map;

/**
 * 列表排序（报价单、PI 共用）：只接受白名单里的字段，对应的 SQL 表达式由调用方给出，前端传什么都不会拼进 SQL。
 * 不传或不认识的字段按创建时间倒序；同值时按 ID 倒序保证分页稳定。
 */
public final class ListSort {

    private ListSort() {
    }

    public static String orderBy(String field, String order, Map<String, String> columns) {
        String expr = field == null ? null : columns.get(field);
        if (expr == null) {
            return "ORDER BY create_time DESC, id DESC";
        }
        return "ORDER BY " + expr + ("ascend".equals(order) ? " ASC" : " DESC") + ", id DESC";
    }
}
