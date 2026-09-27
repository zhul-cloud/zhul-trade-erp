package com.zhul.erp.modules.product.repository;

/**
 * 档案完整度各模块"已完成"的判定条件（SQL 片段）。
 * 完整度计算和"按缺项筛选 / 缺项统计"共用这些片段，才能保证统计出的数量与筛选结果的条数一致。
 */
public final class CompletenessSql {

    private CompletenessSql() {
    }

    /** 物流：净重或任一尺寸（单品或包装）已填 */
    public static final String LOGISTICS_FILLED = "(net_weight_kg IS NOT NULL OR length_mm IS NOT NULL "
            + "OR width_mm IS NOT NULL OR height_mm IS NOT NULL OR package_length_mm IS NOT NULL "
            + "OR package_width_mm IS NOT NULL OR package_height_mm IS NOT NULL)";

    /** 图片视频：至少一张图片且已设主图（主图只能是图片，所以有未删除的主图即可） */
    public static final String MEDIA_DONE = "media_type = 1 AND is_main = 1";

    /** 海关：HS 编码已填 */
    public static final String CUSTOMS_DONE = "hs_code != ''";

    /** FAQ：至少一条已确认（来源不是"待审核"） */
    public static final String FAQ_DONE = "source != 3";

    // ---- 缺项筛选：外层是 product 表，用 product.id 关联 ----

    public static final String MISSING_MEDIA = "SELECT 1 FROM product_media m WHERE m.product_id = product.id "
            + "AND m.deleted_at IS NULL AND m.media_type = 1 AND m.is_main = 1";

    public static final String MISSING_LOGISTICS = "SELECT 1 FROM product_logistics l WHERE l.product_id = product.id "
            + "AND l.deleted_at IS NULL AND (l.net_weight_kg IS NOT NULL OR l.length_mm IS NOT NULL "
            + "OR l.width_mm IS NOT NULL OR l.height_mm IS NOT NULL OR l.package_length_mm IS NOT NULL "
            + "OR l.package_width_mm IS NOT NULL OR l.package_height_mm IS NOT NULL)";

    public static final String MISSING_CUSTOMS = "SELECT 1 FROM product_customs c WHERE c.product_id = product.id "
            + "AND c.deleted_at IS NULL AND c.hs_code != ''";

    public static final String MISSING_PRICE = "SELECT 1 FROM product_reference_price r WHERE r.product_id = product.id "
            + "AND r.deleted_at IS NULL";
}
