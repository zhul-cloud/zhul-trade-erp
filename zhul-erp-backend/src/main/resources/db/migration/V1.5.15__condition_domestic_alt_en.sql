-- 用户反馈（2026-11-05）：货况「国产替代」在文字报价、报价单、PI 上对客户显示 made in China。
-- 只改仍是原默认英文名的记录（平台与各租户），租户自己改过的不动。

UPDATE `dict_item`
SET `item_name_en` = 'made in China',
    `update_time`  = NOW(),
    `update_by`    = 'sys'
WHERE `dict_type` = 'inquiry_item_condition'
  AND `item_code` = 'DOMESTIC_ALT'
  AND `item_name_en` = 'domestic alternative';

-- 回退：
-- UPDATE `dict_item` SET `item_name_en` = 'domestic alternative', `update_time` = NOW(), `update_by` = 'sys'
-- WHERE `dict_type` = 'inquiry_item_condition' AND `item_code` = 'DOMESTIC_ALT' AND `item_name_en` = 'made in China';
