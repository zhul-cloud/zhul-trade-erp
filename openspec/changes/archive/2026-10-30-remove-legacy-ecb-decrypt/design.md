## Context

`AesUtils.decrypt` 对没有 `v2:` 前缀的值走 `decryptLegacy`（hutool AES/ECB，密钥为 `AES_KEY` 的 MD5）；`EncryptionMigrationRunner` 每次启动扫描 `sys_config`、`supplier_bank_account`，把旧格式改写为 `v2:`。两张 AI 配置表从一开始就只写 `v2:`，不在迁移范围内。

## Goals / Non-Goals

**Goals:** 删除旧格式解密与启动迁移；没有 `v2:` 前缀的非空值解密时明确报错。

**Non-Goals:** 不改 `v2:` 格式、密钥派生和各业务模块的加解密调用方式。

## Decisions

- **无前缀直接报错，不做任何兜底**：与「密文被改动」同样抛 `BizException`，信息为「数据解密失败：密文格式不正确」，不含密文内容。备选是返回空值或原值，会把读取失败伪装成「没有配置」，排查更难，不采用。
- **整体删除迁移任务**：迁移只处理旧格式，旧格式不再可解，保留它没有意义。
- **删除前核对三处数据库**：生产、本机开发库、测试库的四个加密列都没有旧格式值，核对 SQL 记录在 tasks。

## Risks / Trade-offs

- 某个未核对的环境残留旧格式密文 → 读取时报解密失败。缓解：proposal 写明先用旧版本启动一次完成迁移。
