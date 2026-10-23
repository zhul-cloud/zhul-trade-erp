# -*- coding: utf-8 -*-
"""由 subcategory-descriptions.tsv 与 series.tsv 生成迁移 V1.3.9 与反向 SQL。

用法（仓库根目录）：python3 openspec/changes/seed-subcategory-desc-and-series/data/gen_migration.py
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
MIGRATION = os.path.join(ROOT, 'zhul-erp-backend/src/main/resources/db/migration/V1.3.9__seed_subcategory_desc_and_series.sql')
ROLLBACK = os.path.join(ROOT, 'sql/build/rollback/V1.3.9__seed_subcategory_desc_and_series_rollback.sql')


def read_tsv(name):
    with open(os.path.join(HERE, name), encoding='utf-8') as f:
        rows = [line.rstrip('\n').split('\t') for line in f if line.strip()]
    return rows[1:]


def q(s):
    return "'" + s.replace('\\', '\\\\').replace("'", "''") + "'"


subcats = read_tsv('subcategory-descriptions.tsv')
series = read_tsv('series.tsv')
for r in subcats:
    assert len(r) == 3 and len(r[1]) <= 500 and len(r[2]) <= 500, r
for r in series:
    assert len(r) == 7 and r[3] in ('保留', '新增', '改名', '删除') and len(r[1]) <= 64 and len(r[5]) <= 500, r

out = []
w = out.append
w("""-- 细分品类简介与系列重整（openspec seed-subcategory-desc-and-series）
-- 1. 140 个细分品类写入中英文简介，只填空的；
-- 2. 系列按定稿清单：品牌按名称识别，同品牌下按系列名识别（忽略大小写与首尾空格）；
--    已有的保留 ID 与启用状态、补空的简介，没有的新建（同名系列曾被删除的不恢复、不新建）；
--    ATV48 → ATS48、ATS01/ATV22 → ATS01/ATS22 在原 ID 上改名；台达下误挂的 S7-1200 无商品使用时软删除、有商品使用时停用；
--    清单之外的系列不改动。
-- 迁移前的简介与系列状态存入 bak_v139_* 两张备份表，供反向 SQL 恢复。可重复执行，结果一致。
-- 本文件由 openspec/changes/seed-subcategory-desc-and-series/data/gen_migration.py 生成，改清单后重新生成，不要手改。

CREATE TABLE IF NOT EXISTS `bak_v139_category_desc` (
  `id`             bigint       NOT NULL COMMENT '品类ID',
  `description`    varchar(500) NOT NULL DEFAULT '' COMMENT '迁移前的英文简介',
  `description_zh` varchar(500) NOT NULL DEFAULT '' COMMENT '迁移前的中文简介',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V1.3.9 细分品类简介迁移前备份（反向 SQL 用）';

CREATE TABLE IF NOT EXISTS `bak_v139_product_series` (
  `id`          bigint       NOT NULL COMMENT '系列ID',
  `series_name` varchar(64)  NOT NULL DEFAULT '' COMMENT '迁移前的系列名称',
  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '迁移前的简介',
  `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '迁移前的状态（0-禁用、1-启用）',
  `deleted_at`  datetime     NULL COMMENT '迁移前的软删除时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V1.3.9 系列迁移前备份（反向 SQL 用；不在表里的系列是本次新建的）';

INSERT IGNORE INTO `bak_v139_category_desc` (`id`, `description`, `description_zh`)
SELECT `id`, `description`, `description_zh` FROM `product_category` WHERE `tenant_id` = 0 AND `parent_id` IS NOT NULL;
INSERT IGNORE INTO `bak_v139_product_series` (`id`, `series_name`, `description`, `status`, `deleted_at`)
SELECT `id`, `series_name`, `description`, `status`, `deleted_at` FROM `product_series` WHERE `tenant_id` = 0;

-- ================================================================ 细分品类简介
""")
for code, zh, en in subcats:
    w(f"UPDATE `product_category` SET `description_zh` = IF(`description_zh` = '', {q(zh)}, `description_zh`), "
      f"`description` = IF(`description` = '', {q(en)}, `description`), `update_time` = NOW(), `update_by` = 'sys'\n"
      f"WHERE `tenant_id` = 0 AND `category_code` = {q(code)} AND `parent_id` IS NOT NULL AND (`description_zh` = '' OR `description` = '');")

w("\n-- ================================================================ 系列")
brand = None
for b, name, _cat, action, old, desc, _note in series:
    if b != brand:
        brand = b
        w(f"\n-- {b}")
        w(f"SET @b := (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = {q(b)} AND `deleted_at` IS NULL ORDER BY `id` LIMIT 1);")
    same = f"`tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = {q(name)}"
    if action == '删除':
        w(f"UPDATE `product_series` s SET s.`deleted_at` = NOW(), s.`update_time` = NOW(), s.`update_by` = 'sys'\n"
          f"WHERE s.`tenant_id` = 0 AND s.`brand_id` = @b AND TRIM(s.`series_name`) = {q(name)} AND s.`deleted_at` IS NULL\n"
          f"  AND NOT EXISTS (SELECT 1 FROM `product` p WHERE p.`series_id` = s.`id` AND p.`deleted_at` IS NULL);")
        w(f"UPDATE `product_series` SET `status` = 0, `update_time` = NOW(), `update_by` = 'sys'\n"
          f"WHERE {same} AND `deleted_at` IS NULL AND `status` = 1;")
        continue
    if action == '改名':
        w(f"SET @taken := (SELECT COUNT(*) FROM `product_series` WHERE {same});")
        w(f"UPDATE `product_series` SET `series_name` = {q(name)}, `update_time` = NOW(), `update_by` = 'sys'\n"
          f"WHERE `tenant_id` = 0 AND `brand_id` = @b AND TRIM(`series_name`) = {q(old)} AND `deleted_at` IS NULL AND @taken = 0;")
    w(f"UPDATE `product_series` SET `description` = {q(desc)}, `update_time` = NOW(), `update_by` = 'sys'\n"
      f"WHERE {same} AND `deleted_at` IS NULL AND `description` = '';")
    w(f"INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `description`, `status`, `create_by`, `update_by`)\n"
      f"SELECT 0, @b, {q(name)}, {q(desc)}, 1, 'sys', 'sys' FROM DUAL\n"
      f"WHERE @b IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `id` FROM `product_series` WHERE {same}) x);")

os.makedirs(os.path.dirname(MIGRATION), exist_ok=True)
with open(MIGRATION, 'w', encoding='utf-8') as f:
    f.write('\n'.join(out) + '\n')

rb = """-- 细分品类简介与系列重整 V1.3.9 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.9 的记录）。
-- 按 bak_v139_* 备份恢复：品类简介、系列名称（改名的改回）、简介、状态与软删除时间回到迁移前；
-- 本次新建的系列（不在备份表里）无商品使用的软删除，有商品使用的停用（不物理删除）。

UPDATE `product_category` c JOIN `bak_v139_category_desc` b ON b.`id` = c.`id`
SET c.`description` = b.`description`, c.`description_zh` = b.`description_zh`, c.`update_time` = NOW(), c.`update_by` = 'sys';

UPDATE `product_series` s JOIN `bak_v139_product_series` b ON b.`id` = s.`id`
SET s.`series_name` = b.`series_name`, s.`description` = b.`description`, s.`status` = b.`status`, s.`deleted_at` = b.`deleted_at`,
    s.`update_time` = NOW(), s.`update_by` = 'sys';

UPDATE `product_series` s SET s.`deleted_at` = NOW(), s.`update_time` = NOW(), s.`update_by` = 'sys'
WHERE s.`tenant_id` = 0 AND s.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `bak_v139_product_series` b WHERE b.`id` = s.`id`)
  AND NOT EXISTS (SELECT 1 FROM `product` p WHERE p.`series_id` = s.`id` AND p.`deleted_at` IS NULL);
UPDATE `product_series` s SET s.`status` = 0, s.`update_time` = NOW(), s.`update_by` = 'sys'
WHERE s.`tenant_id` = 0 AND s.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `bak_v139_product_series` b WHERE b.`id` = s.`id`);

DROP TABLE IF EXISTS `bak_v139_category_desc`;
DROP TABLE IF EXISTS `bak_v139_product_series`;
"""
with open(ROLLBACK, 'w', encoding='utf-8') as f:
    f.write(rb)
print('ok', len(subcats), '个细分品类,', len(series), '行系列')
