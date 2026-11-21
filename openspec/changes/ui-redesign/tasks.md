# Tasks

每个任务做完后，先由独立审核者按 `design.md`「审核角色」复审，结论记入 `review-log.md`。复审通过才勾选完成；结论为「需修改」的，改完重新复审。

## 1. 依据整理

- [ ] 1.1 从代码现有的 `Pill`、`Tag` 着色整理出「状态 → 语义色」对照表 `status-colors.md`；有分歧的逐条列出请产品负责人定，定完即完成
- [x] 1.2 一次性读完所有带关键字搜索的列表的后端查询条件，建立 `search-scope.md`，每行写明页面、可搜字段、匹配方式、后端代码位置。用搜索核对前端所有关键字输入框都已收录，查不到的标「待核实」并问用户
- [ ] 1.3 建立 `stat-cards.md`：列出每个列表的统计卡片、口径、与页签的关系、属于状态类（并入状态下拉数量）、条件类（转快捷筛选）还是指标类（转汇总行），以及涉及的主规范条款。用搜索核对前端所有统计卡片都已收录，分类经产品负责人确认

## 2. 原型与确认

- [x] 2.1 用 OpenPencil 画 4 张样板页的浅色版：工作台、销售订单列表、销售订单详情、登记表单弹窗。交付物是 `docs/03-产品原型/界面改造/` 下的 `.op` 文件和导出的 PNG，快捷筛选、关键字帮助和状态颜色取自第 1 组的依据表，并按 `specs/system/interface-style` 逐条自查
- [x] 2.2 画深色版（4 张样板页各一版），再画主题色对比图（6 种预设在浅色和深色下并排），导出 PNG；无界面服务用完后执行 `op stop`
- [x] 2.3 独立审核者复审原型：对照 `interface-style`、配色取值与对比度，并用 Impeccable critique 打分。问题改完、`review-log.md` 记为通过后，再交给用户
- [x] 2.4 用户确认原型后，同步修订 specs、`design.md`、`tasks.md`（含 Open Questions 的结论），用 `openspec validate ui-redesign --strict` 验证通过。未确认前不开始 3.3 和第 4 组及以后的任务

## 3. 文档清理与新规范

- [x] 3.1 列出要清理的文档和句子，排除 `openspec/changes/archive/`、其他人的变更目录和 PRD 修订历史行；经用户确认后执行
- [x] 3.2 把旧版 `ui-design-patterns.md` 全文和 `docs/03-产品原型/02-商品域/_备选方案存档/` 移入 `docs/_backup/ui-v3/`，写首页说明「已作废，仅供追溯」；用 `git status` 核对原位置已没有这些文件
- [x] 3.3 按确认的原型，整份重写 `.claude/context/ui-design-patterns.md`。内容包括令牌、页面骨架、说明文字放置、文案禁用写法，以及 `design.md` 第 6 节列出的仍然有效的各节。验证：用搜索确认文中不再出现 V1、V3、方案 D、深色默认等旧规则，「渐变」只作为禁止项出现；`AGENTS.md`、`coding.md` 引用的节名都还在，或者引用已同步更新
- [x] 3.4 修改 3.1 清单里其他文档的旧视觉句子，在 `docs/03-产品原型/` 首页写明「视觉以 `ui-design-patterns.md` 为准，旧原型只作业务确认」；用搜索核对清单内的文档不再有旧规则

## 4. 主题令牌与外壳

- [x] 4.1 开始改代码前，先把 develop 同步进来一次，记入 `sync-log.md`，并跑本机全量前端检查（2026-11-21：合入至 `fab4cb75`；基线 `npm run lint` 通过，Jest 142 个套件 552 个用例通过）
- [x] 4.2 按 `design.md` 第 2 节重写 `src/theme/palette.ts`：保留、改名、删除各键，删除三个渐变常量，修好所有编译报错的引用处；验证 `npm run lint` 通过（2026-11-21：115 个文件 282 处改名；`accentSoft` 改为 `primarySoft`，理由见 design；默认 Logo 改纯色、浏览器图标固定琥珀一并完成；lint、Jest 通过）
- [ ] 4.3 改掉编译抓不到的渐变和旧字体：`LOGO_GRADIENT_STOPS` 和 `BrandLogo`、`public/logo.svg`、`public/favicon.ico`、`AuthLayout` 的光晕、4 处 `IBM Plex Mono`。默认 Logo 按 `site-appearance` 增量实现；用 BrowserOS neo 截图核对登录页、侧边栏和浏览器标签图标
- [ ] 4.4 更新 `src/theme/antd.ts`、`shell.ts`、`AppTheme.tsx`：主按钮、危险按钮、圆角、阴影、焦点色、状态标签（按 `status-colors.md`），加入 Public Sans 依赖。验证：切换主题后弹窗、消息、登录页都跟着变
- [x] 4.5 补 Jest 测试：主题切换后的令牌、默认 Logo 随主题变化、浏览器标签图标固定为琥珀底白字（以 `site-appearance` 增量为准）；验证 `npm test` 通过（2026-11-21：`src/theme/theme.test.tsx`；全量 Jest 通过）
- [ ] 4.6 按定稿的外壳原型（F1–F1d）改外壳：`TopBar`、`AppLogo`、`SidebarUser`、`AvatarDropdown`、`PageTabs`、`ThemeToggle`（改为主题设置弹出层）、`LangDropdown`、`NotificationBell`，补「跳到主要内容」；删除 `VersionDropdown` 和 `AvatarDropdown` 的模板遗留分支。主题设置的模式、主题色、涨跌颜色保存在本机浏览器，读写失败时用默认值。验证：Jest 覆盖主题设置三项的保存与恢复、收起侧栏状态、全部标签下拉的数量；`npm run lint`、`npm test` 通过；用 BrowserOS neo 截图与 F 图逐张对照（浅色、深色、收起侧栏、1366 宽开满 10 个标签），键盘能用 Tab、Enter、Esc 打开和关闭两个弹出层，关闭后焦点回到触发按钮
- [x] 4.7 涨跌颜色落地：提供按方向取色的统一方法（默认涨红跌绿），替换 `design.md` 第 8 节涨跌颜色一条里「纳入的地方」按正负着色的代码，去掉 `higherIsBetter` 对颜色的影响；告警色不动。验证：Jest 覆盖两种设置下的上涨、下跌、盈利、亏损、零值；用搜索核对列出的文件不再直接按正负写死绿色或红色（2026-11-21：`src/theme/delta.tsx` 的 `useDeltaColor`、`DeltaText`，替换 13 个文件；零值与空值用正文色；`delta.test.tsx` 覆盖两种设置；搜索确认所列文件不再按正负写死红绿）

## 5. 骨架组件与检查规则

- [ ] 5.1 在 `src/components/ListLayout/` 下扩展：`FilterCard` 增加「更多筛选」，新增 `PageHeader`、`SummaryBar`、`QuickFilters`、`Section`、`HelpTip`。写 Jest 测试，覆盖汇总行的金额按币种显示、更多筛选的生效数量、快捷筛选的选中与取消、帮助图标悬停弹出；已用 `ListLayout` 的采购入库、销售出库测试仍然通过。验证 `npm test` 通过
- [ ] 5.2 新增 `npm run lint:ui`，按 `design.md` 第 4 节检查，可按目录运行，确认弹窗和警告条不检查，支持写明原因的例外，并为脚本写自测。在全仓库跑一次，报出的问题按业务域整理成 `migration-list.md`
- [ ] 5.3 在 `ui-design-patterns.md` 写明骨架组件的用法和 `lint:ui` 的例外写法；验证文中示例与组件的实际 API 一致

## 6. 样板页落地

- [ ] 6.1 按确认的原型改造销售订单列表和详情，关键字帮助按 `search-scope.md` 写，统计卡片按 `stat-cards.md` 并入状态下拉、转成快捷筛选或汇总行。补 Jest 测试，验证状态数量、快捷筛选和汇总行的数字与改造前的统计一致；验证 lint、`lint:ui`（销售订单目录）、Jest 通过，截图与原型一致
- [ ] 6.2 按原型改造工作台，各岗位看板的数据和口径不变；验证在同一时刻与改造前各看板的数字一致，没有数据的组件按「空状态」显示
- [ ] 6.3 按原型改造登记表单弹窗样板，说明文字按放置规则处理；验证 lint、`lint:ui`（该弹窗所在目录）通过，表单提交行为不变

## 7. 按业务域迁移

每个批次都包含以下工作：
- 页面迁移到骨架组件，去掉面包屑和写死的颜色、样式；
- 按规则处理说明文字（用 Impeccable clarify、distill，只改措辞）；
- 列表关键字帮助按 `search-scope.md` 写，统计卡片按 `stat-cards.md` 处理；
- 页面结构有变化的先补原型，再在本变更补对应主规范的增量；
- 页面文案变化后同步修改 Jest 断言。

每批完成的标准：
- 本机 lint、antd lint、Jest、构建全部通过；
- 该域目录的 `lint:ui` 没有报错；
- 用 BrowserOS neo 截图核对浅色和深色；
- 独立审核者复审通过；
- 交用户看过。

- [ ] 7.1 销售管理：客户跟进、商机、客户、询盘、报价、形式发票、销售订单其余页面、销售退货、售后。客户跟进页的渐变箭头要在 `crm/customer-followup` 补增量
- [ ] 7.2 采购管理：询价分配、我的询价、询价记录、采购需求、采购订单、发货跟踪、采购退货、供应商库。在 `purchase/purchase-order`（逾期未发统计卡）和 `inquiry/sourcing-task`（顶部统计卡片）补增量
- [ ] 7.3 仓库管理与物流管理：采购入库、销售出库、退货入库、闲置库存、实拍任务、出运管理、运费账单
- [ ] 7.4 财务管理：收款、退款、余额、结汇、应付、付款、报销、费用、其他收入
- [ ] 7.5 商品管理与运营管理：商品档案、品牌、品类、系列、商品候选、资料任务、营销任务、发品任务、素材管理
- [ ] 7.6 统计分析：销售、询价、砍价、利润、经营、商机统计。图表改用新的系列色，统计口径取自代码，放进帮助图标
- [ ] 7.7 业务设置、系统管理、租户管理，以及登录、找回密码、异常页
- [ ] 7.8 全仓库跑 `lint:ui`，确认 `src/` 下没有未写明原因的报错，然后把它并入 `npm run lint`。用搜索确认已经没有 `Breadcrumb` 和 `crumbs`；`search-scope.md` 里每个列表都已落地

## 8. 同步 develop

- [ ] 8.1 每完成一个批次，以及 develop 有较多前端合并时，用 merge 把 develop 合进来：业务逻辑以 develop 为准，界面按新规范改。跑 `lint:ui`，改掉报出的问题，记入 `sync-log.md`，再跑本机全量前端检查；同步带进来的改动也要交独立审核者复审

## 9. 合并前回归

- [ ] 9.1 独立审核者全量复审：核对 `review-log.md` 没有未关闭的问题，抽查各业务域页面的视觉、文案和行为
- [ ] 9.2 用户逐页验收，结论记入 `acceptance.md`
- [ ] 9.3 和其他开发约定前端冻结后，最后同步一次 develop，复审这次同步带进来的改动，跑本机全量前端检查，用 BrowserOS neo 按菜单逐页截图回归浅色和深色；请用户补看受影响的页面，结论补进 `acceptance.md`
- [ ] 9.4 更新用户手册里的界面截图和文字，在 `docs/06-部署运维/11-分支与版本规范.md` 补长期分支例外；用搜索核对手册里不再出现面包屑、旧配色的描述（已知一处：`订单部分取消-使用手册-V1.0.md` 的「紫色的『多收』」改为警告色）
- [ ] 9.5 `openspec validate ui-redesign --strict` 通过

## Workflow follow-up

- 发 PR 到 develop，跑完整 CI 后合并；按发版流程拉 `release/X.Y.0` 上线，升次版本号。
- 上线并经用户验收后，归档本变更，同步主规范。
