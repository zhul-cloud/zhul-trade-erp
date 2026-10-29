# 飞书历史数据迁移

把旧 ERP（飞书多维表格）的历史数据清洗后导入指定租户。设计依据见 `openspec/changes/migrate-feishu-history/`。

两段式：

1. **离线清洗**（本目录的 Node 脚本，可以反复跑）：读飞书导出的 Excel 与飞书接口，AI 拆分型号与进价，合并客户与商机，配对订单行与询盘行，产出清洗结果与复核报告。
2. **导入**（后端接口，仅平台账号）：读清洗结果写入目标租户，分阶段事务，可干跑、可重复执行、可按批次回滚。

## 准备

- 飞书导出的 Excel 放在 `~/Downloads/历史数据/`（或用 `HM_SOURCE_DIR` 指定），14 个文件，按工作表名称识别。
- ego 浏览器里登录飞书（读取记录与附件用，只读）。
- 本机 Claude Code 已登录订阅账号（AI 拆分用 `claude -p`，不需要接口密钥；会占用订阅额度）。

## 步骤

```bash
# 1. 读飞书：记录（含 Excel 没有的完整供应商库、退款表），再下载附件（可断点续传）
node scripts/history-migration/feishu/pull.js
node scripts/history-migration/feishu/pull.js attachments

# 2. AI 拆分：型号文本、询价进价、附件；结果缓存在 out/ai-cache/，额度用完会停下，重跑同一条命令从断点继续
node scripts/history-migration/ai-split.js all --concurrency 2
#    抽样把关：out/qa/型号拆分抽样100.csv（人工填核对结论）

# 3. 清洗：产出 out/bundle/*.json 与复核报告 out/review/*.csv
node scripts/history-migration/clean.js
#    人工结论写在 review/decisions.json（含客户信息，不进 git）：
#    { "samePerson": [["C00662","SJ20251015145"]], "notSamePerson": [["C1","SJ2"]], "refundOrders": { "TK2025072900001": "SO..." } }
#    改完结论重跑第 3 步

# 4. 本机导入（平台账号令牌放在 ~/.zhul-platform-token）：先干跑，再正式执行；提交后立即返回，后台运行
curl -X POST localhost:8080/api/v1/system/history-migrations \
  -H "Authorization: Bearer $(cat ~/.zhul-platform-token)" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":1007,\"bundleDir\":\"$PWD/scripts/history-migration/out/bundle\",\"attachmentDir\":\"$PWD/scripts/history-migration/out/feishu/attachments\",\"dryRun\":true}"
#    dryRun 改为 false 正式执行；进度与结果：GET /api/v1/system/history-migrations/<批次ID>

# 5. 核对：生成旧报表数字 out/bundle/reports.json，调用后端核对（与迁移页面同一套规则），输出 out/review/7-报表核对.csv
node scripts/history-migration/verify.js 1007

# 回滚某个批次（后台运行；软删除该批次写入的数据，候选来源重算，素材移出素材库），之后可以重新执行
curl -X POST localhost:8080/api/v1/system/history-migrations/<批次ID>/rollback -H "Authorization: Bearer $(cat ~/.zhul-platform-token)"
```

## 生产环境（在页面上执行）

```bash
# 6. 打迁移包：生成旧报表数字，把用到的图片传到开发 OSS 私有桶的 migration/feishu/（可断点续传），
#    给每张图生成 7 天有效的下载链接，打出 out/pack/迁移包-<版本>.zip
node scripts/history-migration/pack.js
```

然后在生产上：

1. 平台账号在「租户管理 → 租户列表」新建租户（记下管理员临时密码），再点「数据初始化」写入福唯模板的部门、岗位、角色。
2. 点该租户的「历史迁移」，上传迁移包 → 干跑 → 核对数量 → 正式执行（约 30 分钟，可以离开页面）→ 看核对结果；出问题按批次回滚后重新执行。
3. 迁移按姓名建好业务员、采购员的员工档案与登录账号（停用、随机密码）。租户管理员在「用户管理」里启用并重置密码后，他们就能登录。

生产不需要开发 OSS 的密钥：导入时按链接下载图片，存进生产自己的 OSS。链接过期就在本机重新运行第 6 步，再上传新的迁移包。

## 性质

- **不静默丢弃**：每条源记录都有且只有一种结果（迁移、合并、跳过、需人工处理），见 `out/review/2-逐条处理结果.csv`。
- **编码不迁移**：所有单据按新系统规则、按历史业务日期重新编号；旧编号只在迁移对照表 `migration_id_map` 里。
- **迁移数据只读**：除客户、供应商外，迁移生成的单据标记为历史迁移，不能修改、作废、取消；商机一经跟进就成为普通商机。
- **订单成本取快照**：迁移订单的收入、成本、毛利取旧《成本核算》，存在 `sales_order_cost_snapshot`。
- **目录**：`out/`（导出缓存、附件、AI 缓存、清洗结果、报告）与 `review/`（人工结论）都不进 git。

## 测试

```bash
node --test scripts/history-migration/test/*.test.js
```
