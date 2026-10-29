package com.zhul.erp.common.utils;

import com.zhul.erp.common.exception.BizException;

/**
 * 历史迁移数据只读（openspec migrate-feishu-history）：迁移生成的单据（migrated = 1）不能修改、作废、取消、删除。
 * 各模块在修改类入口查出单据后调用 {@link #requireEditable(Integer)}。
 */
public final class MigratedGuard {

    public static final String ERROR_CODE = "MIGRATED_READ_ONLY";
    public static final String MESSAGE = "历史迁移数据不能修改";

    private MigratedGuard() {
    }

    public static boolean isMigrated(Integer migrated) {
        return migrated != null && migrated == 1;
    }

    public static void requireEditable(Integer migrated) {
        if (isMigrated(migrated)) {
            throw BizException.of(ERROR_CODE, MESSAGE);
        }
    }
}
