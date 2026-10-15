-- 商机阿里等级（openspec opportunity-intake-tweaks）：来源渠道为阿里巴巴国际站时选填
ALTER TABLE `opportunity`
    ADD COLUMN `ali_level` varchar(4) NOT NULL DEFAULT '' COMMENT '阿里等级（L0、L1、L1+、L2、L3、L4，空表示未填；仅阿里巴巴国际站渠道）' AFTER `source_channel`;
