-- 品牌初始化（openspec seed-product-brands）：83 个品牌，英文名为正式名称，中文名与常见写法为别名，等级与中英文简介
-- 已有品牌按名称或别名识别后保留 ID 改为正式写法（旧名称留作别名）；Vacon 并入 Danfoss；名单外品牌无引用软删除、有引用停用。可重复执行

-- 欧洲 · Siemens
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('siemens', '西门子')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('siemens', '西门子')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'siemens' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'siemens' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Siemens', `country` = 'Germany', `brand_level` = 2, `description` = 'Automation leader from Munich: SIMATIC PLCs and HMIs, SINAMICS drives, SIRIUS low-voltage controls and SITOP power supplies.',
    `description_zh` = '工控龙头；SIMATIC PLC/HMI、SINAMICS 驱动、SIRIUS 低压电器、SITOP 电源', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Siemens' AS n, 'Germany' AS c, 2 AS lv, 'Automation leader from Munich: SIMATIC PLCs and HMIs, SINAMICS drives, SIRIUS low-voltage controls and SITOP power supplies.' AS den, '工控龙头；SIMATIC PLC/HMI、SINAMICS 驱动、SIRIUS 低压电器、SITOP 电源' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Siemens' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '西门子', '西门子');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · ABB
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('abb', '阿西布朗勃法瑞', 'abb group')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('abb', '阿西布朗勃法瑞', 'abb group')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'abb' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'abb' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'ABB', `country` = 'Switzerland', `brand_level` = 2, `description` = 'Zurich-based group known for ACS drives, AC500 PLCs, motors and robots; B&R is part of ABB.',
    `description_zh` = 'ACS 变频器、AC500 PLC、电机、机器人；B&R 是旗下品牌', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'ABB' AS n, 'Switzerland' AS c, 2 AS lv, 'Zurich-based group known for ACS drives, AC500 PLCs, motors and robots; B&R is part of ABB.' AS den, 'ACS 变频器、AC500 PLC、电机、机器人；B&R 是旗下品牌' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'ABB' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '阿西布朗勃法瑞', '阿西布朗勃法瑞'),
    (0, @bid, 'ABB Group', 'abb group');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Schneider Electric
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('schneider electric', '施耐德电气', '施耐德', 'schneider')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('schneider electric', '施耐德电气', '施耐德', 'schneider')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'schneider electric' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'schneider electric' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Schneider Electric', `country` = 'France', `brand_level` = 2, `description` = 'Modicon PLCs, Altivar drives, Lexium servos and TeSys low-voltage controls; Pro-face belongs to Schneider.',
    `description_zh` = 'Modicon PLC、Altivar 变频器、Lexium 伺服、TeSys 低压电器；旗下有 Pro-face', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Schneider Electric' AS n, 'France' AS c, 2 AS lv, 'Modicon PLCs, Altivar drives, Lexium servos and TeSys low-voltage controls; Pro-face belongs to Schneider.' AS den, 'Modicon PLC、Altivar 变频器、Lexium 伺服、TeSys 低压电器；旗下有 Pro-face' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Schneider Electric' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '施耐德电气', '施耐德电气'),
    (0, @bid, '施耐德', '施耐德'),
    (0, @bid, 'Schneider', 'schneider');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Telemecanique
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('telemecanique', '德美康')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('telemecanique', '德美康')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'telemecanique' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'telemecanique' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Telemecanique', `country` = 'France', `brand_level` = 1, `description` = 'Proximity and limit switches; the Telemecanique Sensors business has belonged to YAGEO since 2023.',
    `description_zh` = '接近开关、限位开关；传感器业务 2023 年起归国巨', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Telemecanique' AS n, 'France' AS c, 1 AS lv, 'Proximity and limit switches; the Telemecanique Sensors business has belonged to YAGEO since 2023.' AS den, '接近开关、限位开关；传感器业务 2023 年起归国巨' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Telemecanique' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '德美康', '德美康');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Bosch Rexroth
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('bosch rexroth', '博世力士乐', '力士乐', 'rexroth')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('bosch rexroth', '博世力士乐', '力士乐', 'rexroth')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'bosch rexroth' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'bosch rexroth' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Bosch Rexroth', `country` = 'Germany', `brand_level` = 1, `description` = 'Hydraulics, IndraDrive servo systems and linear motion technology.',
    `description_zh` = '液压、IndraDrive 伺服、直线运动', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Bosch Rexroth' AS n, 'Germany' AS c, 1 AS lv, 'Hydraulics, IndraDrive servo systems and linear motion technology.' AS den, '液压、IndraDrive 伺服、直线运动' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Bosch Rexroth' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '博世力士乐', '博世力士乐'),
    (0, @bid, '力士乐', '力士乐'),
    (0, @bid, 'Rexroth', 'rexroth');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Lenze
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('lenze', '伦茨')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('lenze', '伦茨')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'lenze' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'lenze' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Lenze', `country` = 'Germany', `brand_level` = 1, `description` = 'Inverters, servo drives and geared motors.',
    `description_zh` = '变频器、伺服、齿轮电机', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Lenze' AS n, 'Germany' AS c, 1 AS lv, 'Inverters, servo drives and geared motors.' AS den, '变频器、伺服、齿轮电机' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Lenze' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '伦茨', '伦茨');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · SEW-EURODRIVE
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('sew-eurodrive', '赛威', 'sew')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('sew-eurodrive', '赛威', 'sew')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'sew-eurodrive' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'sew-eurodrive' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'SEW-EURODRIVE', `country` = 'Germany', `brand_level` = 0, `description` = 'Leading maker of geared motors and gear reducers.',
    `description_zh` = '齿轮电机、减速机龙头', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'SEW-EURODRIVE' AS n, 'Germany' AS c, 0 AS lv, 'Leading maker of geared motors and gear reducers.' AS den, '齿轮电机、减速机龙头' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SEW-EURODRIVE' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '赛威', '赛威'),
    (0, @bid, 'SEW', 'sew');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Danfoss
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('danfoss', '丹佛斯')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('danfoss', '丹佛斯')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'danfoss' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'danfoss' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Danfoss', `country` = 'Denmark', `brand_level` = 1, `description` = 'VLT drives, valves and hydraulics; acquired Finnish drive maker Vacon in 2014.',
    `description_zh` = 'VLT 变频器、阀件、液压；2014 年收购 Vacon（伟肯，芬兰）', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Danfoss' AS n, 'Denmark' AS c, 1 AS lv, 'VLT drives, valves and hydraulics; acquired Finnish drive maker Vacon in 2014.' AS den, 'VLT 变频器、阀件、液压；2014 年收购 Vacon（伟肯，芬兰）' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Danfoss' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '丹佛斯', '丹佛斯'),
    (0, @bid, 'Vacon', 'vacon'),
    (0, @bid, '伟肯', '伟肯');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Beckhoff
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('beckhoff', '倍福')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('beckhoff', '倍福')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'beckhoff' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'beckhoff' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Beckhoff', `country` = 'Germany', `brand_level` = 1, `description` = 'PC-based control and TwinCAT software; the inventor of EtherCAT.',
    `description_zh` = 'PC 控制、TwinCAT，EtherCAT 发明者', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Beckhoff' AS n, 'Germany' AS c, 1 AS lv, 'PC-based control and TwinCAT software; the inventor of EtherCAT.' AS den, 'PC 控制、TwinCAT，EtherCAT 发明者' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Beckhoff' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '倍福', '倍福');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · B&R
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('b&r', '贝加莱', 'b&r automation')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('b&r', '贝加莱', 'b&r automation')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'b&r' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'b&r' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'B&R', `country` = 'Austria', `brand_level` = 0, `description` = 'X20 control system and ACOPOS servo drives; part of ABB since 2017.',
    `description_zh` = 'X20 系统、ACOPOS 伺服，2017 年起属 ABB', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'B&R' AS n, 'Austria' AS c, 0 AS lv, 'X20 control system and ACOPOS servo drives; part of ABB since 2017.' AS den, 'X20 系统、ACOPOS 伺服，2017 年起属 ABB' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'B&R' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '贝加莱', '贝加莱'),
    (0, @bid, 'B&R Automation', 'b&r automation');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Phoenix Contact
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('phoenix contact', '菲尼克斯')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('phoenix contact', '菲尼克斯')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'phoenix contact' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'phoenix contact' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Phoenix Contact', `country` = 'Germany', `brand_level` = 0, `description` = 'Terminal blocks, interface relays and power supplies.',
    `description_zh` = '接线端子、接口继电器、电源', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Phoenix Contact' AS n, 'Germany' AS c, 0 AS lv, 'Terminal blocks, interface relays and power supplies.' AS den, '接线端子、接口继电器、电源' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Phoenix Contact' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '菲尼克斯', '菲尼克斯');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Weidmüller
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('weidmüller', '魏德米勒', 'weidmuller')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('weidmüller', '魏德米勒', 'weidmuller')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'weidmüller' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'weidmüller' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Weidmüller', `country` = 'Germany', `brand_level` = 1, `description` = 'Terminal blocks, interface relays and power supplies.',
    `description_zh` = '接线端子、接口继电器、电源', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Weidmüller' AS n, 'Germany' AS c, 1 AS lv, 'Terminal blocks, interface relays and power supplies.' AS den, '接线端子、接口继电器、电源' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Weidmüller' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '魏德米勒', '魏德米勒'),
    (0, @bid, 'Weidmuller', 'weidmuller');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · WAGO
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('wago', '万可')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('wago', '万可')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'wago' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'wago' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'WAGO', `country` = 'Germany', `brand_level` = 0, `description` = 'Terminal blocks, interface relays and power supplies.',
    `description_zh` = '接线端子、接口继电器、电源', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'WAGO' AS n, 'Germany' AS c, 0 AS lv, 'Terminal blocks, interface relays and power supplies.' AS den, '接线端子、接口继电器、电源' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'WAGO' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '万可', '万可');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Pilz
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('pilz', '皮尔磁')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('pilz', '皮尔磁')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'pilz' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'pilz' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Pilz', `country` = 'Germany', `brand_level` = 0, `description` = 'Safety relays and safety controllers.',
    `description_zh` = '安全继电器、安全控制器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Pilz' AS n, 'Germany' AS c, 0 AS lv, 'Safety relays and safety controllers.' AS den, '安全继电器、安全控制器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Pilz' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '皮尔磁', '皮尔磁');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · SICK
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('sick', '西克')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('sick', '西克')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'sick' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'sick' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'SICK', `country` = 'Germany', `brand_level` = 0, `description` = 'Top-tier sensor maker, strong in laser and safety sensors.',
    `description_zh` = '传感器第一梯队，以激光和安全传感器见长', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'SICK' AS n, 'Germany' AS c, 0 AS lv, 'Top-tier sensor maker, strong in laser and safety sensors.' AS den, '传感器第一梯队，以激光和安全传感器见长' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SICK' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '西克', '西克');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Pepperl+Fuchs
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('pepperl+fuchs', '倍加福', 'p+f')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('pepperl+fuchs', '倍加福', 'p+f')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'pepperl+fuchs' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'pepperl+fuchs' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Pepperl+Fuchs', `country` = 'Germany', `brand_level` = 0, `description` = 'Top-tier sensor maker, strong in explosion-protection products.',
    `description_zh` = '传感器第一梯队，以防爆产品见长', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Pepperl+Fuchs' AS n, 'Germany' AS c, 0 AS lv, 'Top-tier sensor maker, strong in explosion-protection products.' AS den, '传感器第一梯队，以防爆产品见长' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Pepperl+Fuchs' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '倍加福', '倍加福'),
    (0, @bid, 'P+F', 'p+f');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · ifm
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('ifm', '易福门', 'ifm electronic')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('ifm', '易福门', 'ifm electronic')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'ifm' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'ifm' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'ifm', `country` = 'Germany', `brand_level` = 1, `description` = 'Top-tier industrial sensor maker.',
    `description_zh` = '传感器第一梯队', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'ifm' AS n, 'Germany' AS c, 1 AS lv, 'Top-tier industrial sensor maker.' AS den, '传感器第一梯队' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'ifm' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '易福门', '易福门'),
    (0, @bid, 'ifm electronic', 'ifm electronic');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Balluff
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('balluff', '巴鲁夫')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('balluff', '巴鲁夫')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'balluff' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'balluff' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Balluff', `country` = 'Germany', `brand_level` = 1, `description` = 'Top-tier sensor maker, strong in displacement sensors.',
    `description_zh` = '传感器第一梯队，以位移传感器见长', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Balluff' AS n, 'Germany' AS c, 1 AS lv, 'Top-tier sensor maker, strong in displacement sensors.' AS den, '传感器第一梯队，以位移传感器见长' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Balluff' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '巴鲁夫', '巴鲁夫');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · TURCK
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('turck', '图尔克')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('turck', '图尔克')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'turck' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'turck' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'TURCK', `country` = 'Germany', `brand_level` = 1, `description` = 'Top-tier industrial sensor maker.',
    `description_zh` = '传感器第一梯队', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'TURCK' AS n, 'Germany' AS c, 1 AS lv, 'Top-tier industrial sensor maker.' AS den, '传感器第一梯队' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'TURCK' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '图尔克', '图尔克');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Leuze
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('leuze', '劳易测')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('leuze', '劳易测')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'leuze' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'leuze' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Leuze', `country` = 'Germany', `brand_level` = 0, `description` = 'Top-tier industrial sensor maker.',
    `description_zh` = '传感器第一梯队', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Leuze' AS n, 'Germany' AS c, 0 AS lv, 'Top-tier industrial sensor maker.' AS den, '传感器第一梯队' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Leuze' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '劳易测', '劳易测');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Festo
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('festo', '费斯托')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('festo', '费斯托')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'festo' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'festo' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Festo', `country` = 'Germany', `brand_level` = 0, `description` = 'Pneumatic components.',
    `description_zh` = '气动元件', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Festo' AS n, 'Germany' AS c, 0 AS lv, 'Pneumatic components.' AS den, '气动元件' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Festo' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '费斯托', '费斯托');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Bürkert
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('bürkert', '宝德', 'burkert')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('bürkert', '宝德', 'burkert')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'bürkert' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'bürkert' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Bürkert', `country` = 'Germany', `brand_level` = 1, `description` = 'Fluid control valves.',
    `description_zh` = '流体控制阀', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Bürkert' AS n, 'Germany' AS c, 1 AS lv, 'Fluid control valves.' AS den, '流体控制阀' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Bürkert' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '宝德', '宝德'),
    (0, @bid, 'Burkert', 'burkert');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Endress+Hauser
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('endress+hauser', 'e+h', '恩德斯豪斯')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('endress+hauser', 'e+h', '恩德斯豪斯')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'endress+hauser' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'endress+hauser' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Endress+Hauser', `country` = 'Switzerland', `brand_level` = 0, `description` = 'Process instrumentation.',
    `description_zh` = '过程仪表', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Endress+Hauser' AS n, 'Switzerland' AS c, 0 AS lv, 'Process instrumentation.' AS den, '过程仪表' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Endress+Hauser' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, 'E+H', 'e+h'),
    (0, @bid, '恩德斯豪斯', '恩德斯豪斯');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · VEGA
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('vega', '威格')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('vega', '威格')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'vega' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'vega' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'VEGA', `country` = 'Germany', `brand_level` = 1, `description` = 'Process instrumentation, best known for radar level meters.',
    `description_zh` = '过程仪表，以雷达物位计见长', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'VEGA' AS n, 'Germany' AS c, 1 AS lv, 'Process instrumentation, best known for radar level meters.' AS den, '过程仪表，以雷达物位计见长' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'VEGA' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '威格', '威格');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · KROHNE
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('krohne', '科隆')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('krohne', '科隆')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'krohne' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'krohne' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'KROHNE', `country` = 'Germany', `brand_level` = 0, `description` = 'Process instrumentation, best known for flow meters.',
    `description_zh` = '过程仪表，以流量计见长', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'KROHNE' AS n, 'Germany' AS c, 0 AS lv, 'Process instrumentation, best known for flow meters.' AS den, '过程仪表，以流量计见长' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'KROHNE' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '科隆', '科隆');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Heidenhain
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('heidenhain', '海德汉')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('heidenhain', '海德汉')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'heidenhain' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'heidenhain' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Heidenhain', `country` = 'Germany', `brand_level` = 0, `description` = 'Encoders, linear scales and CNC controls.',
    `description_zh` = '编码器、光栅尺、数控系统', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Heidenhain' AS n, 'Germany' AS c, 0 AS lv, 'Encoders, linear scales and CNC controls.' AS den, '编码器、光栅尺、数控系统' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Heidenhain' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '海德汉', '海德汉');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Kübler
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('kübler', '库伯勒', 'kubler')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('kübler', '库伯勒', 'kubler')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'kübler' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'kübler' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Kübler', `country` = 'Germany', `brand_level` = 0, `description` = 'Encoders.',
    `description_zh` = '编码器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Kübler' AS n, 'Germany' AS c, 0 AS lv, 'Encoders.' AS den, '编码器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Kübler' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '库伯勒', '库伯勒'),
    (0, @bid, 'Kubler', 'kubler');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · POSITAL
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('posital', '博思特')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('posital', '博思特')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'posital' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'posital' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'POSITAL', `country` = 'Germany', `brand_level` = 1, `description` = 'Encoders.',
    `description_zh` = '编码器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'POSITAL' AS n, 'Germany' AS c, 1 AS lv, 'Encoders.' AS den, '编码器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'POSITAL' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '博思特', '博思特');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Moeller
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('moeller', '金钟穆勒')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('moeller', '金钟穆勒')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'moeller' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'moeller' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Moeller', `country` = 'Germany', `brand_level` = 0, `description` = 'Part of Eaton since 2008; legacy contactors and motor protection circuit breakers are common.',
    `description_zh` = '2008 年起属伊顿，老款接触器、电动机保护断路器常见', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Moeller' AS n, 'Germany' AS c, 0 AS lv, 'Part of Eaton since 2008; legacy contactors and motor protection circuit breakers are common.' AS den, '2008 年起属伊顿，老款接触器、电动机保护断路器常见' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Moeller' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '金钟穆勒', '金钟穆勒');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Rittal
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('rittal', '威图')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('rittal', '威图')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'rittal' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'rittal' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Rittal', `country` = 'Germany', `brand_level` = 0, `description` = 'Enclosures and enclosure climate control.',
    `description_zh` = '机柜和柜用温控', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Rittal' AS n, 'Germany' AS c, 0 AS lv, 'Enclosures and enclosure climate control.' AS den, '机柜和柜用温控' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Rittal' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '威图', '威图');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Murrelektronik
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('murrelektronik', '穆尔')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('murrelektronik', '穆尔')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'murrelektronik' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'murrelektronik' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Murrelektronik', `country` = 'Germany', `brand_level` = 0, `description` = 'Power supplies and distributed I/O.',
    `description_zh` = '电源和分布式 I/O', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Murrelektronik' AS n, 'Germany' AS c, 0 AS lv, 'Power supplies and distributed I/O.' AS den, '电源和分布式 I/O' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Murrelektronik' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '穆尔', '穆尔');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · Control Techniques
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('control techniques', 'ct')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('control techniques', 'ct')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'control techniques' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'control techniques' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Control Techniques', `country` = 'United Kingdom', `brand_level` = 0, `description` = 'Unidrive inverters; part of Nidec since 2017.',
    `description_zh` = 'Unidrive 变频器，2017 年起属日本电产', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Control Techniques' AS n, 'United Kingdom' AS c, 0 AS lv, 'Unidrive inverters; part of Nidec since 2017.' AS den, 'Unidrive 变频器，2017 年起属日本电产' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Control Techniques' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, 'CT', 'ct');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · FANOX
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('fanox')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('fanox')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'fanox' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'fanox' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'FANOX', `country` = 'Spain', `brand_level` = 1, `description` = 'Protection relays.',
    `description_zh` = '保护继电器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'FANOX' AS n, 'Spain' AS c, 1 AS lv, 'Protection relays.' AS den, '保护继电器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'FANOX' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 欧洲 · ASA
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('asa')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('asa')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'asa' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'asa' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'ASA', `country` = 'Germany', `brand_level` = 1, `description` = 'Limit switches.',
    `description_zh` = '限位开关', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'ASA' AS n, 'Germany' AS c, 1 AS lv, 'Limit switches.' AS den, '限位开关' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'ASA' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Allen-Bradley
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('allen-bradley', 'ab', '罗克韦尔', 'rockwell', 'allen bradley')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('allen-bradley', 'ab', '罗克韦尔', 'rockwell', 'allen bradley')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'allen-bradley' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'allen-bradley' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Allen-Bradley', `country` = 'United States', `brand_level` = 2, `description` = 'North American leader: Logix PLCs, PanelView HMIs, PowerFlex drives and Kinetix servos.',
    `description_zh` = '北美龙头；Logix PLC、PanelView HMI、PowerFlex 变频器、Kinetix 伺服', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Allen-Bradley' AS n, 'United States' AS c, 2 AS lv, 'North American leader: Logix PLCs, PanelView HMIs, PowerFlex drives and Kinetix servos.' AS den, '北美龙头；Logix PLC、PanelView HMI、PowerFlex 变频器、Kinetix 伺服' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Allen-Bradley' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, 'AB', 'ab'),
    (0, @bid, '罗克韦尔', '罗克韦尔'),
    (0, @bid, 'Rockwell', 'rockwell'),
    (0, @bid, 'Allen Bradley', 'allen bradley');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Honeywell
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('honeywell', '霍尼韦尔')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('honeywell', '霍尼韦尔')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'honeywell' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'honeywell' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Honeywell', `country` = 'United States', `brand_level` = 1, `description` = 'DCS, MICRO SWITCH switches and pressure transmitters.',
    `description_zh` = 'DCS、MICRO SWITCH 开关、压力变送器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Honeywell' AS n, 'United States' AS c, 1 AS lv, 'DCS, MICRO SWITCH switches and pressure transmitters.' AS den, 'DCS、MICRO SWITCH 开关、压力变送器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Honeywell' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '霍尼韦尔', '霍尼韦尔');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Emerson
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('emerson', '艾默生')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('emerson', '艾默生')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'emerson' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'emerson' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Emerson', `country` = 'United States', `brand_level` = 0, `description` = 'Rosemount transmitters, Fisher control valves and ASCO solenoid valves; took over the former GE PLC business.',
    `description_zh` = 'Rosemount 变送器、Fisher 调节阀、ASCO 电磁阀；接手了原 GE 的 PLC 业务', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Emerson' AS n, 'United States' AS c, 0 AS lv, 'Rosemount transmitters, Fisher control valves and ASCO solenoid valves; took over the former GE PLC business.' AS den, 'Rosemount 变送器、Fisher 调节阀、ASCO 电磁阀；接手了原 GE 的 PLC 业务' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Emerson' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '艾默生', '艾默生');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Eaton
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('eaton', '伊顿')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('eaton', '伊顿')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'eaton' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'eaton' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Eaton', `country` = 'Ireland', `brand_level` = 1, `description` = 'Contactors, circuit breakers and UPS; Moeller belongs to Eaton.',
    `description_zh` = '接触器、断路器、UPS；旗下有 Moeller', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Eaton' AS n, 'Ireland' AS c, 1 AS lv, 'Contactors, circuit breakers and UPS; Moeller belongs to Eaton.' AS den, '接触器、断路器、UPS；旗下有 Moeller' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Eaton' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '伊顿', '伊顿');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Parker
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('parker', '派克汉尼汾', '派克', 'parker hannifin')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('parker', '派克汉尼汾', '派克', 'parker hannifin')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'parker' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'parker' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Parker', `country` = 'United States', `brand_level` = 0, `description` = 'Hydraulics, pneumatics and drives.',
    `description_zh` = '液压、气动、驱动', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Parker' AS n, 'United States' AS c, 0 AS lv, 'Hydraulics, pneumatics and drives.' AS den, '液压、气动、驱动' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Parker' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '派克汉尼汾', '派克汉尼汾'),
    (0, @bid, '派克', '派克'),
    (0, @bid, 'Parker Hannifin', 'parker hannifin');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Banner
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('banner', '邦纳')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('banner', '邦纳')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'banner' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'banner' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Banner', `country` = 'United States', `brand_level` = 0, `description` = 'Photoelectric and safety sensors.',
    `description_zh` = '光电和安全传感器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Banner' AS n, 'United States' AS c, 0 AS lv, 'Photoelectric and safety sensors.' AS den, '光电和安全传感器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Banner' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '邦纳', '邦纳');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Cognex
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('cognex', '康耐视')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('cognex', '康耐视')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'cognex' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'cognex' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Cognex', `country` = 'United States', `brand_level` = 0, `description` = 'Machine vision.',
    `description_zh` = '机器视觉', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Cognex' AS n, 'United States' AS c, 0 AS lv, 'Machine vision.' AS den, '机器视觉' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Cognex' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '康耐视', '康耐视');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Red Lion
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('red lion', '红狮')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('red lion', '红狮')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'red lion' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'red lion' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Red Lion', `country` = 'United States', `brand_level` = 0, `description` = 'HMIs, industrial Ethernet switches and panel meters.',
    `description_zh` = 'HMI、工业交换机、面板表', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Red Lion' AS n, 'United States' AS c, 0 AS lv, 'HMIs, industrial Ethernet switches and panel meters.' AS den, 'HMI、工业交换机、面板表' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Red Lion' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '红狮', '红狮');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · MAC
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('mac', 'mac valves')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('mac', 'mac valves')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'mac' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'mac' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'MAC', `country` = 'United States', `brand_level` = 1, `description` = 'Solenoid valves.',
    `description_zh` = '电磁阀', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'MAC' AS n, 'United States' AS c, 1 AS lv, 'Solenoid valves.' AS den, '电磁阀' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'MAC' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, 'MAC Valves', 'mac valves');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 美洲 · Barksdale
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('barksdale')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('barksdale')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'barksdale' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'barksdale' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Barksdale', `country` = 'United States', `brand_level` = 1, `description` = 'Pressure switches.',
    `description_zh` = '压力开关', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Barksdale' AS n, 'United States' AS c, 1 AS lv, 'Pressure switches.' AS den, '压力开关' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Barksdale' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Mitsubishi Electric
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('mitsubishi electric', '三菱', '三菱电机', 'mitsubishi')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('mitsubishi electric', '三菱', '三菱电机', 'mitsubishi')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'mitsubishi electric' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'mitsubishi electric' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Mitsubishi Electric', `country` = 'Japan', `brand_level` = 2, `description` = 'MELSEC PLCs, GOT HMIs, FR inverters and MR servos.',
    `description_zh` = 'MELSEC PLC、GOT HMI、FR 变频器、MR 伺服', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Mitsubishi Electric' AS n, 'Japan' AS c, 2 AS lv, 'MELSEC PLCs, GOT HMIs, FR inverters and MR servos.' AS den, 'MELSEC PLC、GOT HMI、FR 变频器、MR 伺服' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Mitsubishi Electric' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '三菱', '三菱'),
    (0, @bid, '三菱电机', '三菱电机'),
    (0, @bid, 'Mitsubishi', 'mitsubishi');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · OMRON
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('omron', '欧姆龙')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('omron', '欧姆龙')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'omron' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'omron' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'OMRON', `country` = 'Japan', `brand_level` = 2, `description` = 'PLCs, sensors, temperature controllers and relays; a very broad range of control components.',
    `description_zh` = 'PLC、传感器、温控器、继电器，控制元件很全', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'OMRON' AS n, 'Japan' AS c, 2 AS lv, 'PLCs, sensors, temperature controllers and relays; a very broad range of control components.' AS den, 'PLC、传感器、温控器、继电器，控制元件很全' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'OMRON' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '欧姆龙', '欧姆龙');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Yaskawa
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('yaskawa', '安川')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('yaskawa', '安川')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'yaskawa' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'yaskawa' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Yaskawa', `country` = 'Japan', `brand_level` = 2, `description` = 'Sigma-7 servos, A1000/GA700 inverters and MOTOMAN robots.',
    `description_zh` = 'Σ-7 伺服、A1000/GA700 变频器、MOTOMAN 机器人', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Yaskawa' AS n, 'Japan' AS c, 2 AS lv, 'Sigma-7 servos, A1000/GA700 inverters and MOTOMAN robots.' AS den, 'Σ-7 伺服、A1000/GA700 变频器、MOTOMAN 机器人' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Yaskawa' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '安川', '安川');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Fanuc
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('fanuc', '发那科')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('fanuc', '发那科')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'fanuc' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'fanuc' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Fanuc', `country` = 'Japan', `brand_level` = 0, `description` = 'Leader in CNC systems and robots, with strong demand for servo spare parts.',
    `description_zh` = '数控系统和机器人龙头，伺服备件需求大', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Fanuc' AS n, 'Japan' AS c, 0 AS lv, 'Leader in CNC systems and robots, with strong demand for servo spare parts.' AS den, '数控系统和机器人龙头，伺服备件需求大' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Fanuc' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '发那科', '发那科');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Panasonic
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('panasonic', '松下', 'sunx')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('panasonic', '松下', 'sunx')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'panasonic' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'panasonic' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Panasonic', `country` = 'Japan', `brand_level` = 1, `description` = 'MINAS servos, FP PLCs and sensors (formerly SUNX).',
    `description_zh` = 'MINAS 伺服、FP PLC、传感器（原 SUNX）', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Panasonic' AS n, 'Japan' AS c, 1 AS lv, 'MINAS servos, FP PLCs and sensors (formerly SUNX).' AS den, 'MINAS 伺服、FP PLC、传感器（原 SUNX）' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Panasonic' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '松下', '松下'),
    (0, @bid, 'SUNX', 'sunx');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Keyence
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('keyence', '基恩士')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('keyence', '基恩士')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'keyence' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'keyence' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Keyence', `country` = 'Japan', `brand_level` = 0, `description` = 'Sensors and machine vision, sold direct only.',
    `description_zh` = '传感器和视觉，只做直销', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Keyence' AS n, 'Japan' AS c, 0 AS lv, 'Sensors and machine vision, sold direct only.' AS den, '传感器和视觉，只做直销' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Keyence' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '基恩士', '基恩士');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Fuji Electric
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('fuji electric', '富士电机', '富士')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('fuji electric', '富士电机', '富士')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'fuji electric' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'fuji electric' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Fuji Electric', `country` = 'Japan', `brand_level` = 0, `description` = 'Mainly inverters.',
    `description_zh` = '变频器为主', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Fuji Electric' AS n, 'Japan' AS c, 0 AS lv, 'Mainly inverters.' AS den, '变频器为主' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Fuji Electric' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '富士电机', '富士电机'),
    (0, @bid, '富士', '富士');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Hitachi
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('hitachi', '日立')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('hitachi', '日立')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'hitachi' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'hitachi' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Hitachi', `country` = 'Japan', `brand_level` = 0, `description` = 'Mainly inverters.',
    `description_zh` = '变频器为主', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Hitachi' AS n, 'Japan' AS c, 0 AS lv, 'Mainly inverters.' AS den, '变频器为主' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Hitachi' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '日立', '日立');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Toshiba
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('toshiba', '东芝')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('toshiba', '东芝')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'toshiba' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'toshiba' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Toshiba', `country` = 'Japan', `brand_level` = 0, `description` = 'Mainly inverters.',
    `description_zh` = '变频器为主', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Toshiba' AS n, 'Japan' AS c, 0 AS lv, 'Mainly inverters.' AS den, '变频器为主' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Toshiba' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '东芝', '东芝');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Pro-face
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('pro-face', '普洛菲斯')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('pro-face', '普洛菲斯')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'pro-face' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'pro-face' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Pro-face', `country` = 'Japan', `brand_level` = 1, `description` = 'Touch panels; part of Schneider Electric since 2002.',
    `description_zh` = '触摸屏，2002 年起属施耐德', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Pro-face' AS n, 'Japan' AS c, 1 AS lv, 'Touch panels; part of Schneider Electric since 2002.' AS den, '触摸屏，2002 年起属施耐德' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Pro-face' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '普洛菲斯', '普洛菲斯');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Koyo
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('koyo', '光洋')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('koyo', '光洋')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'koyo' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'koyo' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Koyo', `country` = 'Japan', `brand_level` = 0, `description` = 'PLCs and encoders.',
    `description_zh` = 'PLC、编码器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Koyo' AS n, 'Japan' AS c, 0 AS lv, 'PLCs and encoders.' AS den, 'PLC、编码器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Koyo' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '光洋', '光洋');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · SMC
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('smc')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('smc')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'smc' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'smc' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'SMC', `country` = 'Japan', `brand_level` = 0, `description` = 'Pneumatic components; the world''s largest pneumatics maker.',
    `description_zh` = '气动元件，全球第一', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'SMC' AS n, 'Japan' AS c, 0 AS lv, 'Pneumatic components; the world''s largest pneumatics maker.' AS den, '气动元件，全球第一' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SMC' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · CKD
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('ckd', '喜开理')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('ckd', '喜开理')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'ckd' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'ckd' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'CKD', `country` = 'Japan', `brand_level` = 0, `description` = 'Pneumatic components.',
    `description_zh` = '气动元件', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'CKD' AS n, 'Japan' AS c, 0 AS lv, 'Pneumatic components.' AS den, '气动元件' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'CKD' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '喜开理', '喜开理');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Azbil
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('azbil', '阿自倍尔', '山武', 'yamatake')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('azbil', '阿自倍尔', '山武', 'yamatake')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'azbil' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'azbil' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Azbil', `country` = 'Japan', `brand_level` = 0, `description` = 'Temperature controllers and control valves.',
    `description_zh` = '温控器、调节阀', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Azbil' AS n, 'Japan' AS c, 0 AS lv, 'Temperature controllers and control valves.' AS den, '温控器、调节阀' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Azbil' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '阿自倍尔', '阿自倍尔'),
    (0, @bid, '山武', '山武'),
    (0, @bid, 'Yamatake', 'yamatake');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Oriental Motor
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('oriental motor', '东方马达')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('oriental motor', '东方马达')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'oriental motor' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'oriental motor' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Oriental Motor', `country` = 'Japan', `brand_level` = 0, `description` = 'Stepper motors and small motors.',
    `description_zh` = '步进电机和小电机', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Oriental Motor' AS n, 'Japan' AS c, 0 AS lv, 'Stepper motors and small motors.' AS den, '步进电机和小电机' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Oriental Motor' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '东方马达', '东方马达');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Nidec
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('nidec', '日本电产', '尼得科')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('nidec', '日本电产', '尼得科')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'nidec' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'nidec' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Nidec', `country` = 'Japan', `brand_level` = 0, `description` = 'Leading motor manufacturer.',
    `description_zh` = '电机龙头', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Nidec' AS n, 'Japan' AS c, 0 AS lv, 'Leading motor manufacturer.' AS den, '电机龙头' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Nidec' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '日本电产', '日本电产'),
    (0, @bid, '尼得科', '尼得科');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · IDEC
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('idec', '和泉')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('idec', '和泉')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'idec' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'idec' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'IDEC', `country` = 'Japan', `brand_level` = 0, `description` = 'Pushbuttons and relays.',
    `description_zh` = '按钮、继电器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'IDEC' AS n, 'Japan' AS c, 0 AS lv, 'Pushbuttons and relays.' AS den, '按钮、继电器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'IDEC' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '和泉', '和泉');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 日本 · Hokuyo
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('hokuyo', '北阳')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('hokuyo', '北阳')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'hokuyo' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'hokuyo' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Hokuyo', `country` = 'Japan', `brand_level` = 0, `description` = 'Laser scanners.',
    `description_zh` = '激光扫描仪', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Hokuyo' AS n, 'Japan' AS c, 0 AS lv, 'Laser scanners.' AS den, '激光扫描仪' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Hokuyo' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '北阳', '北阳');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · LS Electric
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('ls electric', 'ls 产电', 'ls产电', 'lg 产电', 'ls', 'lsis')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('ls electric', 'ls 产电', 'ls产电', 'lg 产电', 'ls', 'lsis')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'ls electric' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'ls electric' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'LS Electric', `country` = 'South Korea', `brand_level` = 1, `description` = 'XGT PLCs, inverters and low-voltage switchgear.',
    `description_zh` = 'XGT PLC、变频器、低压电器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'LS Electric' AS n, 'South Korea' AS c, 1 AS lv, 'XGT PLCs, inverters and low-voltage switchgear.' AS den, 'XGT PLC、变频器、低压电器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'LS Electric' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, 'LS 产电', 'ls 产电'),
    (0, @bid, 'LS产电', 'ls产电'),
    (0, @bid, 'LG 产电', 'lg 产电'),
    (0, @bid, 'LS', 'ls'),
    (0, @bid, 'LSIS', 'lsis');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · Autonics
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('autonics', '奥托尼克斯')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('autonics', '奥托尼克斯')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'autonics' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'autonics' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Autonics', `country` = 'South Korea', `brand_level` = 0, `description` = 'Cost-effective sensors, temperature controllers and counters.',
    `description_zh` = '传感器、温控器、计数器，性价比高', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Autonics' AS n, 'South Korea' AS c, 0 AS lv, 'Cost-effective sensors, temperature controllers and counters.' AS den, '传感器、温控器、计数器，性价比高' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Autonics' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '奥托尼克斯', '奥托尼克斯');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · Delta
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('delta', '台达', 'delta electronics')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('delta', '台达', 'delta electronics')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'delta' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'delta' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Delta', `country` = 'Taiwan, China', `brand_level` = 2, `description` = 'Full line of inverters, servos, PLCs, HMIs and power supplies.',
    `description_zh` = '变频器、伺服、PLC、HMI、电源，产品线全', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Delta' AS n, 'Taiwan, China' AS c, 2 AS lv, 'Full line of inverters, servos, PLCs, HMIs and power supplies.' AS den, '变频器、伺服、PLC、HMI、电源，产品线全' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Delta' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '台达', '台达'),
    (0, @bid, 'Delta Electronics', 'delta electronics');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · Weintek
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('weintek', '威纶通', '威纶', 'weinview', 'we!ntek')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('weintek', '威纶通', '威纶', 'weinview', 'we!ntek')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'weintek' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'weintek' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Weintek', `country` = 'Taiwan, China', `brand_level` = 1, `description` = 'Leading maker of small and mid-size touch panels.',
    `description_zh` = '中小型触摸屏龙头', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Weintek' AS n, 'Taiwan, China' AS c, 1 AS lv, 'Leading maker of small and mid-size touch panels.' AS den, '中小型触摸屏龙头' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Weintek' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '威纶通', '威纶通'),
    (0, @bid, '威纶', '威纶'),
    (0, @bid, 'Weinview', 'weinview'),
    (0, @bid, 'WE!NTEK', 'we!ntek');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · AirTAC
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('airtac', '亚德客')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('airtac', '亚德客')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'airtac' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'airtac' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'AirTAC', `country` = 'Taiwan, China', `brand_level` = 1, `description` = 'Cost-effective pneumatic components.',
    `description_zh` = '性价比高的气动品牌', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'AirTAC' AS n, 'Taiwan, China' AS c, 1 AS lv, 'Cost-effective pneumatic components.' AS den, '性价比高的气动品牌' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'AirTAC' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '亚德客', '亚德客');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · Advantech
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('advantech', '研华')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('advantech', '研华')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'advantech' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'advantech' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Advantech', `country` = 'Taiwan, China', `brand_level` = 0, `description` = 'Industrial PCs.',
    `description_zh` = '工控机', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Advantech' AS n, 'Taiwan, China' AS c, 0 AS lv, 'Industrial PCs.' AS den, '工控机' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Advantech' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '研华', '研华');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · Moxa
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('moxa', '摩莎')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('moxa', '摩莎')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'moxa' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'moxa' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Moxa', `country` = 'Taiwan, China', `brand_level` = 0, `description` = 'Industrial networking.',
    `description_zh` = '工业网络', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Moxa' AS n, 'Taiwan, China' AS c, 0 AS lv, 'Industrial networking.' AS den, '工业网络' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Moxa' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '摩莎', '摩莎');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · HIWIN
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('hiwin', '上银')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('hiwin', '上银')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'hiwin' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'hiwin' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'HIWIN', `country` = 'Taiwan, China', `brand_level` = 0, `description` = 'Linear guideways.',
    `description_zh` = '线性导轨', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'HIWIN' AS n, 'Taiwan, China' AS c, 0 AS lv, 'Linear guideways.' AS den, '线性导轨' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'HIWIN' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '上银', '上银');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · TECO
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('teco', '东元')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('teco', '东元')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'teco' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'teco' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'TECO', `country` = 'Taiwan, China', `brand_level` = 0, `description` = 'Motors and inverters.',
    `description_zh` = '电机、变频器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'TECO' AS n, 'Taiwan, China' AS c, 0 AS lv, 'Motors and inverters.' AS den, '电机、变频器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'TECO' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '东元', '东元');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 韩国、中国台湾 · MEAN WELL
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('mean well', '明纬', 'meanwell')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('mean well', '明纬', 'meanwell')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'mean well' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'mean well' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'MEAN WELL', `country` = 'Taiwan, China', `brand_level` = 0, `description` = 'Switching power supplies.',
    `description_zh` = '开关电源', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'MEAN WELL' AS n, 'Taiwan, China' AS c, 0 AS lv, 'Switching power supplies.' AS den, '开关电源' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'MEAN WELL' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '明纬', '明纬'),
    (0, @bid, 'Meanwell', 'meanwell');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · Inovance
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('inovance', '汇川')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('inovance', '汇川')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'inovance' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'inovance' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Inovance', `country` = 'China', `brand_level` = 0, `description` = 'Leading Chinese automation brand: inverters, servos and PLCs.',
    `description_zh` = '国产工控龙头，变频器、伺服、PLC', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Inovance' AS n, 'China' AS c, 0 AS lv, 'Leading Chinese automation brand: inverters, servos and PLCs.' AS den, '国产工控龙头，变频器、伺服、PLC' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Inovance' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '汇川', '汇川');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · INVT
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('invt', '英威腾')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('invt', '英威腾')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'invt' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'invt' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'INVT', `country` = 'China', `brand_level` = 1, `description` = 'Inverters.',
    `description_zh` = '变频器', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'INVT' AS n, 'China' AS c, 1 AS lv, 'Inverters.' AS den, '变频器' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'INVT' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '英威腾', '英威腾');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · Kinco
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('kinco', '步科')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('kinco', '步科')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'kinco' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'kinco' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Kinco', `country` = 'China', `brand_level` = 1, `description` = 'HMIs and servo systems.',
    `description_zh` = 'HMI 和伺服', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Kinco' AS n, 'China' AS c, 1 AS lv, 'HMIs and servo systems.' AS den, 'HMI 和伺服' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Kinco' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '步科', '步科');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · Leadshine
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('leadshine', '雷赛')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('leadshine', '雷赛')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'leadshine' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'leadshine' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Leadshine', `country` = 'China', `brand_level` = 0, `description` = 'Stepper and servo systems.',
    `description_zh` = '步进和伺服', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Leadshine' AS n, 'China' AS c, 0 AS lv, 'Stepper and servo systems.' AS den, '步进和伺服' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Leadshine' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '雷赛', '雷赛');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · WECON
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('wecon', '维控')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('wecon', '维控')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'wecon' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'wecon' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'WECON', `country` = 'China', `brand_level` = 1, `description` = 'PLCs compatible with the Mitsubishi FX instruction set, plus HMIs.',
    `description_zh` = 'PLC 兼容三菱 FX 指令，另有 HMI', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'WECON' AS n, 'China' AS c, 1 AS lv, 'PLCs compatible with the Mitsubishi FX instruction set, plus HMIs.' AS den, 'PLC 兼容三菱 FX 指令，另有 HMI' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'WECON' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '维控', '维控'),
    (0, @bid, 'Wecon', 'wecon');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · Haiwell
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('haiwell', '海为')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('haiwell', '海为')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'haiwell' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'haiwell' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Haiwell', `country` = 'China', `brand_level` = 1, `description` = 'Compact PLCs and HMIs.',
    `description_zh` = '小型 PLC 和 HMI', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Haiwell' AS n, 'China' AS c, 1 AS lv, 'Compact PLCs and HMIs.' AS den, '小型 PLC 和 HMI' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Haiwell' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '海为', '海为');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · Xinje
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('xinje', '信捷', 'xinjie')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('xinje', '信捷', 'xinjie')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'xinje' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'xinje' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Xinje', `country` = 'China', `brand_level` = 0, `description` = 'Compact PLCs and HMIs.',
    `description_zh` = '小型 PLC 和 HMI', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Xinje' AS n, 'China' AS c, 0 AS lv, 'Compact PLCs and HMIs.' AS den, '小型 PLC 和 HMI' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Xinje' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '信捷', '信捷'),
    (0, @bid, 'Xinjie', 'xinjie');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · Estun
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('estun', '埃斯顿')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('estun', '埃斯顿')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'estun' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'estun' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Estun', `country` = 'China', `brand_level` = 0, `description` = 'Servo systems and robots.',
    `description_zh` = '伺服、机器人', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Estun' AS n, 'China' AS c, 0 AS lv, 'Servo systems and robots.' AS den, '伺服、机器人' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Estun' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '埃斯顿', '埃斯顿');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · CHINT
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('chint', '正泰')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('chint', '正泰')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'chint' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'chint' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'CHINT', `country` = 'China', `brand_level` = 0, `description` = 'Leading low-voltage switchgear brand.',
    `description_zh` = '低压电器龙头', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'CHINT' AS n, 'China' AS c, 0 AS lv, 'Leading low-voltage switchgear brand.' AS den, '低压电器龙头' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'CHINT' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '正泰', '正泰');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · Hollysys
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('hollysys', '和利时')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('hollysys', '和利时')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'hollysys' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'hollysys' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'Hollysys', `country` = 'China', `brand_level` = 0, `description` = 'DCS systems.',
    `description_zh` = 'DCS 系统', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'Hollysys' AS n, 'China' AS c, 0 AS lv, 'DCS systems.' AS den, 'DCS 系统' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Hollysys' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '和利时', '和利时');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;

-- 中国大陆 · SUPCON
SET @bid := NULL, @old := NULL;
SELECT b.`id`, b.`brand_name` INTO @bid, @old FROM `product_brand` b
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL
  AND (LOWER(TRIM(b.`brand_name`)) IN ('supcon', '中控')
       OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` IN ('supcon', '中控')))
ORDER BY LOWER(TRIM(b.`brand_name`)) = 'supcon' DESC, b.`id` LIMIT 1;
UPDATE `product_brand` SET `brand_name` = CONCAT(LEFT(`brand_name`, 40), '#deleted-', `id`), `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `deleted_at` IS NOT NULL AND LOWER(TRIM(`brand_name`)) = 'supcon' AND @bid IS NOT NULL AND `id` <> @bid;
UPDATE `product_brand` SET `brand_name` = 'SUPCON', `country` = 'China', `brand_level` = 0, `description` = 'DCS systems.',
    `description_zh` = 'DCS 系统', `status` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = @bid;
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `brand_level`, `description`, `description_zh`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'SUPCON' AS n, 'China' AS c, 0 AS lv, 'DCS systems.' AS den, 'DCS 系统' AS dzh, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
WHERE @bid IS NULL
ON DUPLICATE KEY UPDATE `country` = v.c, `brand_level` = v.lv, `description` = v.den, `description_zh` = v.dzh,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';
SELECT `id` INTO @bid FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'SUPCON' LIMIT 1;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`) VALUES
    (0, @bid, '中控', '中控');
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @bid, @old, LOWER(TRIM(@old)) FROM DUAL WHERE @old IS NOT NULL;


-- ---------------------------------------------------------------- Vacon 并入 Danfoss
SET @v := NULL, @d := NULL;
SELECT `id` INTO @v FROM `product_brand` WHERE `tenant_id` = 0 AND `deleted_at` IS NULL AND LOWER(TRIM(`brand_name`)) = 'vacon' LIMIT 1;
SELECT `id` INTO @d FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Danfoss' LIMIT 1;
SET @vk := CONCAT('#', @v), @dk := CONCAT('#', @d);
UPDATE `supplier_product_scope` SET `brand_id` = @d, `update_time` = NOW(), `update_by` = 'sys' WHERE @v IS NOT NULL AND `brand_id` = @v;
UPDATE `inquiry_item` SET `brand_id` = @d, `brand_key` = @dk, `update_time` = NOW(), `update_by` = 'sys'
WHERE @v IS NOT NULL AND (`brand_id` = @v OR `brand_key` = @vk);
UPDATE `sourcing_task` SET `brand_key` = @dk, `update_time` = NOW(), `update_by` = 'sys' WHERE @v IS NOT NULL AND `brand_key` = @vk;
UPDATE `sourcing_quote` SET `brand_key` = @dk, `update_time` = NOW(), `update_by` = 'sys' WHERE @v IS NOT NULL AND `brand_key` = @vk;
UPDATE `quotation_item` SET `brand_key` = @dk, `update_time` = NOW(), `update_by` = 'sys' WHERE @v IS NOT NULL AND `brand_key` = @vk;
-- 与 Danfoss 已有行冲突（同名系列、同型号商品或候选）的不移动
UPDATE `product_series` s
LEFT JOIN (SELECT DISTINCT `series_name` FROM `product_series` WHERE `tenant_id` = 0 AND `brand_id` = @d) x ON x.`series_name` = s.`series_name`
SET s.`brand_id` = @d, s.`update_time` = NOW(), s.`update_by` = 'sys'
WHERE @v IS NOT NULL AND s.`tenant_id` = 0 AND s.`brand_id` = @v AND x.`series_name` IS NULL;
UPDATE `product` p
LEFT JOIN (SELECT DISTINCT `mpn_normalized` FROM `product` WHERE `tenant_id` = 0 AND `brand_id` = @d) x ON x.`mpn_normalized` = p.`mpn_normalized`
SET p.`brand_id` = @d, p.`update_time` = NOW(), p.`update_by` = 'sys'
WHERE @v IS NOT NULL AND p.`tenant_id` = 0 AND p.`brand_id` = @v AND x.`mpn_normalized` IS NULL;
UPDATE `product_candidate` c
LEFT JOIN (SELECT DISTINCT `mpn_normalized` FROM `product_candidate` WHERE `brand_key` = @dk) x ON x.`mpn_normalized` = c.`mpn_normalized`
SET c.`brand_id` = @d, c.`brand_key` = @dk, c.`update_time` = NOW(), c.`update_by` = 'sys'
WHERE @v IS NOT NULL AND (c.`brand_id` = @v OR c.`brand_key` = @vk) AND x.`mpn_normalized` IS NULL;
UPDATE `product_brand_alias` SET `brand_id` = @d, `update_time` = NOW(), `update_by` = 'sys' WHERE @v IS NOT NULL AND `brand_id` = @v;
INSERT IGNORE INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, @d, a.alias, a.k FROM (SELECT 'Vacon' AS alias, 'vacon' AS k UNION ALL SELECT '伟肯', '伟肯') a WHERE @d IS NOT NULL;

-- ---------------------------------------------------------------- 名单外的品牌（含合并后的 Vacon）
UPDATE `product_brand` b SET b.`deleted_at` = NOW(), b.`update_time` = NOW(), b.`update_by` = 'sys'
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND LOWER(TRIM(b.`brand_name`)) NOT IN ('siemens', 'abb', 'schneider electric', 'telemecanique', 'bosch rexroth', 'lenze', 'sew-eurodrive', 'danfoss', 'beckhoff', 'b&r', 'phoenix contact', 'weidmüller', 'wago', 'pilz', 'sick', 'pepperl+fuchs', 'ifm', 'balluff', 'turck', 'leuze', 'festo', 'bürkert', 'endress+hauser', 'vega', 'krohne', 'heidenhain', 'kübler', 'posital', 'moeller', 'rittal', 'murrelektronik', 'control techniques', 'fanox', 'asa', 'allen-bradley', 'honeywell', 'emerson', 'eaton', 'parker', 'banner', 'cognex', 'red lion', 'mac', 'barksdale', 'mitsubishi electric', 'omron', 'yaskawa', 'fanuc', 'panasonic', 'keyence', 'fuji electric', 'hitachi', 'toshiba', 'pro-face', 'koyo', 'smc', 'ckd', 'azbil', 'oriental motor', 'nidec', 'idec', 'hokuyo', 'ls electric', 'autonics', 'delta', 'weintek', 'airtac', 'advantech', 'moxa', 'hiwin', 'teco', 'mean well', 'inovance', 'invt', 'kinco', 'leadshine', 'wecon', 'haiwell', 'xinje', 'estun', 'chint', 'hollysys', 'supcon')
  AND NOT EXISTS (SELECT 1 FROM `product` x WHERE x.`brand_id` = b.`id` AND x.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `product_series` x WHERE x.`brand_id` = b.`id` AND x.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `product_candidate` x WHERE (x.`brand_id` = b.`id` OR x.`brand_key` = CONCAT('#', b.`id`)) AND x.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `supplier_product_scope` x WHERE x.`brand_id` = b.`id` AND x.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `inquiry_item` x WHERE (x.`brand_id` = b.`id` OR x.`brand_key` = CONCAT('#', b.`id`)) AND x.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `sourcing_task` x WHERE x.`brand_key` = CONCAT('#', b.`id`))
  AND NOT EXISTS (SELECT 1 FROM `sourcing_quote` x WHERE x.`brand_key` = CONCAT('#', b.`id`))
  AND NOT EXISTS (SELECT 1 FROM `quotation_item` x WHERE x.`brand_key` = CONCAT('#', b.`id`));
UPDATE `product_brand` b SET b.`status` = 0, b.`update_time` = NOW(), b.`update_by` = 'sys'
WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND b.`status` = 1 AND LOWER(TRIM(b.`brand_name`)) NOT IN ('siemens', 'abb', 'schneider electric', 'telemecanique', 'bosch rexroth', 'lenze', 'sew-eurodrive', 'danfoss', 'beckhoff', 'b&r', 'phoenix contact', 'weidmüller', 'wago', 'pilz', 'sick', 'pepperl+fuchs', 'ifm', 'balluff', 'turck', 'leuze', 'festo', 'bürkert', 'endress+hauser', 'vega', 'krohne', 'heidenhain', 'kübler', 'posital', 'moeller', 'rittal', 'murrelektronik', 'control techniques', 'fanox', 'asa', 'allen-bradley', 'honeywell', 'emerson', 'eaton', 'parker', 'banner', 'cognex', 'red lion', 'mac', 'barksdale', 'mitsubishi electric', 'omron', 'yaskawa', 'fanuc', 'panasonic', 'keyence', 'fuji electric', 'hitachi', 'toshiba', 'pro-face', 'koyo', 'smc', 'ckd', 'azbil', 'oriental motor', 'nidec', 'idec', 'hokuyo', 'ls electric', 'autonics', 'delta', 'weintek', 'airtac', 'advantech', 'moxa', 'hiwin', 'teco', 'mean well', 'inovance', 'invt', 'kinco', 'leadshine', 'wecon', 'haiwell', 'xinje', 'estun', 'chint', 'hollysys', 'supcon');

-- 别名与品牌名称交叉唯一：去掉与未删除品牌名称相同的别名（如改名后的 ABB 原有别名「ABB」）
DELETE a FROM `product_brand_alias` a
JOIN `product_brand` b ON b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND LOWER(TRIM(b.`brand_name`)) = a.`alias_key`
WHERE a.`tenant_id` = 0;
