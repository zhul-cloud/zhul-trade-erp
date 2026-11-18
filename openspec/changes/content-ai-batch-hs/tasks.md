## 1. 数据库

- [x] 1.1 迁移 V1.5.47：`product_category.hs_code`；新表 `hs_tariff_ref`（平台级）
- [x] 1.2 `TenantTables` 把 `hs_tariff_ref` 列入排除清单并写明理由

## 2. 后端

- [x] 2.1 `CategoryHsCodes`：编码校验、前 6 位、品类默认编码（细分品类沿用上级）
- [x] 2.2 品类保存默认编码；列表（分页、树形）返回实际编码与关税参考概况
- [x] 2.3 `HsTariffService`：关税参考查询、保存（平台账号）、概况；AI 补全提交与结果（只给建议）
- [x] 2.4 商品海关信息返回品类默认编码与关税参考
- [x] 2.5 资料包写入成功后技术问答审核通过（所有入口与 AI 结果确认），写入结果返回条数
- [x] 2.6 批量资料已核、批量确认 AI 结果（问题章节自动跳过）；内容任务列表按 AI 状态筛选
- [x] 2.7 资料包商品信息带上品类默认海关编码

## 3. AI 编排服务

- [x] 3.1 `product-content` 技能运行约定加海关编码兜底规则
- [x] 3.2 新技能 `hs-tariff-ref`（联网查目的国进口关税，只返回输入的国家）

## 4. 前端

- [x] 4.1 资料任务列表：AI 生成筛选、批量「资料已核」「确认 AI 结果」与结果弹窗
- [x] 4.2 品类库：默认海关编码列与表单、目的国关税参考弹窗（含 AI 补全）
- [x] 4.3 商品详情「海关信息」：品类默认编码提示与关税参考
- [x] 4.4 上传结果与 AI 结果说明改为技术问答随资料包审核通过

## 5. 测试

- [x] 5.1 契约测试：`ContentAiContractTest`（AI 筛选、批量确认、批量核对、技术问答审核通过）、`HsTariffContractTest`
- [x] 5.2 单元测试构造更新：`CategoryServiceImplTest`、`ProductCustomsServiceImplTest`；`ProductContentPlanContractTest` 按新规则调整
- [x] 5.3 编排服务测试：`test_tariff.py`
- [x] 5.4 前端测试：`BatchActions.test.tsx`、`TariffModal.test.tsx`
- [ ] 5.5 本机页面实测（BrowserOS neo）
