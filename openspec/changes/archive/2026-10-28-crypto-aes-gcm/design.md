# Design

## Context

- `common/utils/AesUtils` 是唯一的加解密入口，调用方只有两处：`ConfigServiceImpl`（加密的系统参数，只写不读，目前没有解密使用方）和 `SupplierBankAccountSync`（收款人身份证号，读写都有）。下一个变更 `ai-config-per-tenant` 会成为加密系统参数的第一个解密使用方。
- 现有实现：Hutool `new AES(md5(AES_KEY))`，即 AES-128/ECB/PKCS5Padding，hex 编码，没有版本标识。
- 数据库迁移用 Flyway SQL，拿不到 `AES_KEY`，无法在 SQL 里重新加密；项目已有先例 `system/storage/StorageMigrationRunner`（`ApplicationRunner` + `JdbcTemplate`，绕开 MyBatis 租户拦截，跨租户处理数据）。
- 目标行为见 `specs/system/data-encryption/spec.md`。

## Goals / Non-Goals

**Goals:**
- `AesUtils` 对外接口（`encrypt` / `decrypt`）不变，调用方不改。
- 密文自带格式版本，以后再换算法时可以按前缀区分，不必再猜格式。
- 迁移可重复执行、单条失败不影响其他记录，不需要人工介入。

**Non-Goals:**
- 不更换 `AES_KEY`、不做密钥轮换（轮换需要「旧密钥解、新密钥加」的双密钥支持，另行设计）。
- 不删除旧 ECB 解密代码（下一个版本确认生产迁移完成后删除）。
- 不改系统参数与收款账户的接口、脱敏规则和页面。

## Decisions

### 1. AES-256-GCM，使用 JDK `javax.crypto`

`Cipher.getInstance("AES/GCM/NoPadding")`，12 字节随机初始向量（`SecureRandom`），128 位标签。

- 不用 Hutool 的 `AES` 封装：它的默认模式就是这次要去掉的 ECB，GCM 用法也不比 JDK 简单，直接用 JDK 更清楚，且不新增依赖。
- 不选 AES-CBC + HMAC：需要自己组合加密和认证，容易出错；GCM 一次完成加密和认证。
- 初始向量用随机数而不是计数器：单实例、写入量很小，随机 96 位初始向量的碰撞概率可以忽略，不需要维护计数器状态。

### 2. 密钥派生：HKDF-SHA256

`AES_KEY` 是任意长度的字符串，用 HKDF（RFC 5869，HmacSHA256 实现，固定 salt `zhul-erp`、info `data-encryption/v2`）派生出 32 字节密钥。

- 替代 MD5：MD5 只能产生 16 字节且不是为密钥派生设计的；HKDF 是标准做法，JDK 的 `Mac` 即可实现（约 20 行），不引入 BouncyCastle。
- 不用 PBKDF2：`AES_KEY` 是部署时生成的随机串，不是用户口令，不需要慢哈希；HKDF 足够且启动无开销。
- info 带版本号，以后格式升级时可派生出不同的密钥，避免新旧格式共用同一把密钥。

### 3. 存储格式 `v2:` + Base64(初始向量 ‖ 密文 ‖ 标签)

- 前缀判断格式，没有前缀即旧 ECB（旧密文是 hex，只含 `0-9a-f`，不可能以 `v2:` 开头，不会误判）。
- Base64 比 hex 短：身份证号 18 字节加密后为 12 + 18 + 16 = 46 字节，Base64 约 64 个字符，加前缀约 67，`payee_id_no varchar(128)` 足够。

### 4. 启动时自动迁移：`EncryptionMigrationRunner`

`ApplicationRunner`，每次启动都执行，用 `JdbcTemplate` 直接查询需要迁移的记录，天然跨租户：

- `sys_config`：`is_encrypted = 1 AND config_value <> '' AND config_value NOT LIKE 'v2:%'`（含已软删除的行，保证库里不留旧密文）。
- `supplier_bank_account`：`payee_id_no <> '' AND payee_id_no NOT LIKE 'v2:%'`。
- 逐条：旧格式解密 → GCM 加密 → `UPDATE ... SET col = ?, update_time = NOW() WHERE id = ? AND col = ?`（带上旧值作为条件，防止迁移期间被并发修改的记录被覆盖）。
- 单条失败（解密异常）：`log.warn` 记录表名和 id，不记录任何密文或明文，继续下一条。
- 结束时 `log.info` 汇总：每张表迁移数、失败数。没有需要迁移的记录时只打一行日志。
- 不更新 `update_by`：这是系统行为，不是用户修改；`update_time` 按规范更新。

不做成 Flyway Java 迁移：Flyway 在 Spring 容器完全就绪前执行，拿 `AES_KEY` 需要额外接线；`ApplicationRunner` 与已有先例一致。

不做成手动开关（像 `StorageMigrationRunner` 那样需要 `--zhul.storage.migrate`）：迁移幂等且无副作用，自动执行避免生产漏跑。

### 5. 兼容解密保留在 `AesUtils` 内部

`decrypt` 先看前缀：`v2:` 走 GCM，否则走旧 ECB（复用现有 Hutool 代码）。迁移完成前用户读取旧数据也不出错；迁移后旧分支不再被命中，下一版本删除。

## Risks / Trade-offs

- [回滚到旧版本后读不出 `v2:` 密文] → 旧代码把 `v2:` 当 hex 解码会失败，供应商详情的身份证号显示会报错。项目未正式使用、数据不重要，可接受；回滚时需重新填写受影响的身份证号与加密配置。PR 说明中写明「本版本迁移后不可回退加密数据」。
- [生产 `AES_KEY` 在迁移前被更换] → 旧密文全部解不开，迁移逐条失败但不阻断启动。部署说明中提醒迁移完成前不要更换 `AES_KEY`。
- [迁移日志泄露敏感信息] → 日志只写表名、id 和数量；单元测试断言异常信息不含明文。
- [随机初始向量重复] → 96 位随机数，在本项目的写入量下概率可以忽略。
- [GCM 解密失败抛异常改变调用方行为] → 旧实现对损坏密文也会抛异常（Hutool 解密失败），调用方行为不变；`SupplierBankAccountSyncTest` 回归验证。

## Migration Plan

1. 部署新版本：启动时自动迁移，查看日志中的汇总行，失败数应为 0。
2. 验证：抽查一个对私收款账户的身份证号脱敏显示正常；数据库中 `payee_id_no`、加密配置值均以 `v2:` 开头。
3. 下一个版本：确认生产日志显示迁移完成后，删除旧 ECB 解密分支与对应测试（另开变更）。
4. 回滚：代码可回退，但已迁移为 `v2:` 的数据旧版本读不出，需重新录入（见 Risks）。
