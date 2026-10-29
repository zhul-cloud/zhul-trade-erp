# Proposal

## Why

敏感数据现在用 `AesUtils` 加密：AES/ECB（Hutool `new AES(key)` 的默认模式），密钥是 `AES_KEY` 的 MD5。ECB 没有随机初始向量，同一明文永远得到同一密文；也没有完整性校验，密文被篡改时会解出错误内容而不报错。下一个变更（AI 配置按租户维护）要把各租户的 API Key 加密存进系统参数，并在提交任务时解密使用；在此之前先把加密方式换成带认证的 AES-GCM。项目尚未正式使用，现有密文不重要，现在换成本最低。

## What Changes

- 加密改为 AES-256-GCM：密钥由 `AES_KEY` 经 HKDF-SHA256 派生为 32 字节；每次加密使用随机 12 字节初始向量；密文带 128 位认证标签。存储格式为 `v2:` + Base64(初始向量 ‖ 密文 ‖ 标签)。
- 解密按前缀区分：`v2:` 开头按 GCM 解密，认证失败时报错、不返回部分明文；没有前缀的按旧 ECB 解密，只用于过渡期读取旧数据。
- 后端启动时一次性重新加密已有旧密文：系统参数中 `is_encrypted = 1` 的配置值、供应商收款账户的收款人身份证号。逐条处理，单条失败只记录日志、不阻断启动，也不影响其他记录；全部完成后再次启动不重复处理。
- 新写入一律使用 GCM，不再产生 ECB 密文。
- 旧 ECB 解密代码保留到下一个版本，确认生产迁移完成后另行删除（不在本变更内）。

## Capabilities

### New Capabilities

- `system/data-encryption`：敏感数据加密存储的方式（算法、密钥派生、密文格式、认证失败的处理）及旧密文的迁移规则。

### Modified Capabilities

（无）系统参数加密项「入库为密文、返回前端为 `****`」、收款人身份证号「加密存储、接口不返回明文」的行为不变，只是加密方式换了。

## Impact

- 后端：`common/utils/AesUtils`（GCM 加解密、HKDF 密钥派生、旧格式兼容解密）；新增启动时迁移（参考 `StorageMigrationRunner`）；调用方 `ConfigServiceImpl`、`SupplierBankAccountSync` 接口不变。
- 数据：`sys_config.config_value`（text）与 `supplier_bank_account.payee_id_no`（varchar(128)，GCM 密文约 70 个字符）长度足够，无表结构变更、无 Flyway 迁移（SQL 无法取得 `AES_KEY`，由后端启动时完成）。
- 配置：`AES_KEY` 不变，不新增 `.env` 配置项。生产 `AES_KEY` 不得在迁移完成前更换，否则旧密文无法解开。
- 依赖：使用 JDK 自带 `javax.crypto`（AES/GCM/NoPadding）；HKDF 用 JDK 的 HmacSHA256 实现，不新增依赖。
- 测试：`AesUtils` 单元测试（往返、随机初始向量、篡改报错、旧格式兼容、空值），迁移的集成测试；`SupplierBankAccountSyncTest` 回归。
