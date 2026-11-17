-- 供应商主营品类改为只选一级品类：已录的细分品类换成它的一级品类，换完后同一供应商、同一品牌下重复的品类只留一条（其余软删除）

UPDATE `supplier_product_scope` sps
    JOIN `product_category` c ON c.`id` = sps.`category_id`
SET sps.`category_id` = c.`parent_id`,
    sps.`update_time` = NOW(),
    sps.`update_by`   = 'migration'
WHERE sps.`deleted_at` IS NULL
  AND c.`parent_id` IS NOT NULL
  AND c.`parent_id` <> 0;

UPDATE `supplier_product_scope` sps
    JOIN (SELECT MIN(`id`) AS keep_id, `tenant_id`, `supplier_id`, COALESCE(`brand_id`, 0) AS brand_key, `pending_key`, `category_id`
          FROM `supplier_product_scope`
          WHERE `deleted_at` IS NULL AND `category_id` IS NOT NULL
          GROUP BY `tenant_id`, `supplier_id`, COALESCE(`brand_id`, 0), `pending_key`, `category_id`
          HAVING COUNT(*) > 1) k
        ON k.`tenant_id` = sps.`tenant_id` AND k.`supplier_id` = sps.`supplier_id`
            AND k.brand_key = COALESCE(sps.`brand_id`, 0) AND k.`pending_key` = sps.`pending_key`
            AND k.`category_id` = sps.`category_id`
SET sps.`deleted_at`  = NOW(),
    sps.`update_time` = NOW(),
    sps.`update_by`   = 'migration'
WHERE sps.`deleted_at` IS NULL
  AND sps.`id` <> k.keep_id;
