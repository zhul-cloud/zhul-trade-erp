package com.zhul.erp.modules.product.candidate.constants;

import java.util.Map;

/** 商品候选池常量 */
public final class CandidateConstants {

    private CandidateConstants() {
    }

    /** 兜底一级品类「其他」的编码：匹配不到品类时建议它 */
    public static final String CATEGORY_OTHER = "other";

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_ARCHIVED = 2;
    public static final int STATUS_MERGED = 3;
    public static final int STATUS_REJECTED = 4;
    public static final Map<Integer, String> STATUS_NAMES = Map.of(STATUS_PENDING, "待审核", STATUS_ARCHIVED, "已建档",
            STATUS_MERGED, "已并入", STATUS_REJECTED, "已驳回");

    /** 可信度 */
    public static final int LEVEL_INQUIRY = 1;
    public static final int LEVEL_IN_STOCK = 2;
    public static final int LEVEL_DEAL = 3;
    public static final Map<Integer, String> LEVEL_NAMES = Map.of(LEVEL_INQUIRY, "询盘出现", LEVEL_IN_STOCK, "采购问到有货", LEVEL_DEAL, "已成交");

    /** 来源类型 */
    public static final int SOURCE_INQUIRY = 1;
    public static final int SOURCE_ACTUAL_MODEL = 2;
    public static final int SOURCE_IN_STOCK = 3;
    public static final int SOURCE_DEAL = 4;
    public static final int SOURCE_MANUAL = 5;
    public static final int SOURCE_IMPORT = 6;
    public static final Map<Integer, String> SOURCE_NAMES = Map.of(SOURCE_INQUIRY, "询盘", SOURCE_ACTUAL_MODEL, "采购回填真实型号",
            SOURCE_IN_STOCK, "采购回价有货", SOURCE_DEAL, "成交", SOURCE_MANUAL, "手动", SOURCE_IMPORT, "导入");

    /** 驳回原因 */
    public static final int REJECT_NOT_MODEL = 1;
    public static final int REJECT_WRONG_MODEL = 2;
    public static final int REJECT_OTHER = 3;
    public static final Map<Integer, String> REJECT_NAMES = Map.of(REJECT_NOT_MODEL, "不是型号", REJECT_WRONG_MODEL, "型号错误", REJECT_OTHER, "其他");

    /** 询盘型号建档状态 */
    public static final int ARCHIVE_NONE = 0;
    public static final int ARCHIVE_DONE = 1;
    public static final int ARCHIVE_CANDIDATE = 2;
    public static final int ARCHIVE_NEED_MODEL = 3;
    public static final Map<Integer, String> ARCHIVE_NAMES = Map.of(ARCHIVE_DONE, "已建档", ARCHIVE_CANDIDATE, "候选中",
            ARCHIVE_NEED_MODEL, "待回填真实型号");

    public static int levelOf(int sourceType) {
        return switch (sourceType) {
            case SOURCE_DEAL -> LEVEL_DEAL;
            case SOURCE_IN_STOCK -> LEVEL_IN_STOCK;
            default -> LEVEL_INQUIRY;
        };
    }

    public static final String MENU = "商品候选";
    public static final String PERM_REVIEW = "product:candidate:review";
}
