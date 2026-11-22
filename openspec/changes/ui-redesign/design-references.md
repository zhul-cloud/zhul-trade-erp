# 设计调研、设计原则与对标检查表

2026-11-23 任务 9.1。用户要求：所有设计优先考虑用户体验和观感；参考网站很多，不要只盯着一两个，要多吸取其他设计师的优秀设计、总结成自己的；做完后要对比优秀设计（如 Vuetify 商店 https://store.vuetifyjs.com/ 的商业后台模板），确认达到同样标准。由 UI 设计调研代理只读调研 22 个参考后整理。

调研限制：只看公开页面、不登录。Vuetify 商店里 Materio 完整版、Modernize、MatDash、Spike 的 Vue 演示要登录，看的是 Materio 免费版（Vuetify）以及同一作者、同一设计语言的 Bootstrap 公开演示；Minimal UI 要登录未看；Material 3、Salesforce 新版文档抓不到正文，改用官方博客与开发者文档。

## 一、参考清单

| # | 参考 | 网址 | 最值得学的一点 |
|---|---|---|---|
| 1 | Materio（Vuetify 商店，免费演示） | demos.themeselection.com/materio-vuetify-vuejs-admin-template-free/demo/ | 同一行卡片严格等高；深色按「越高越亮」分层 |
| 2 | Modernize（Vuetify 商店，Bootstrap 同款） | bootstrapdemos.adminmart.com/modernize/dist/main/ | 反例：深色下彩色浅底大块发脏；页头大横幅浪费空间；价格左对齐 |
| 3 | MatDash（同上） | bootstrapdemos.adminmart.com/matdash/dist/main/ | 阴影很轻；彩色实底数字卡只适合营销型看板 |
| 4 | Spike（同上） | bootstrapdemos.wrappixel.com/spike/dist/main/ | 三列「组合后等高」 |
| 5 | MaterialPro（同上） | bootstrapdemos.wrappixel.com/materialpro/dist/main/ | 同一行等高；彩色顶栏加照片的旧式风格是反例 |
| 6 | Vuexy | demos.pixinvent.com/vuexy-html-admin-template/ | 详情页操作竖排在右侧卡，长页面主操作始终可见 |
| 7 | shadcn/ui 看板示例 | ui.shadcn.com/examples/dashboard | 数字卡：标签 14 次要色、数值 24/600 等宽、趋势胶囊；卡片细边框加极淡阴影 |
| 8 | PrimeDash | demos.icreatorstudio.com/demo-1/ | 本变更的基线（见 `reference-primedash.md`） |
| 9 | IBM Carbon 数据表格 | carbondesignsystem.com/components/data-table/usage/ | 行高五档，两行内容用 64；表头等于行高；行内操作少于 3 个直接放图标 |
| 10 | IBM Carbon 颜色分层 | carbondesignsystem.com/elements/color/overview/ | 深色每高一层亮一档 |
| 11 | Shopify Polaris | shopify.dev/docs/api/app-home | 勾选后工具栏切换成批量模式；详情页主栏放单据内容、侧栏放状态与摘要 |
| 12 | Atlassian 状态标签 | atlassian.design/components/lozenge/usage | 状态标签只用于影响下一步的状态；分类用中性标签 |
| 13 | Atlassian 空状态 | atlassian.design/components/empty-state/usage | 首次使用、无结果、完成、错误四种语气 |
| 14 | Atlassian 间距 | atlassian.design/foundations/spacing | 以 8 为基数的刻度 |
| 15 | Atlassian 层级 | atlassian.design/foundations/elevation | 四级层级；横向滚动用溢出阴影提示 |
| 16 | GitHub Primer | primer.style | 不到 1 秒不显示加载态；数字列右对齐；行操作最多拿出 1 个常用项 |
| 17 | Material 3 | material.io/blog/tone-based-surface-color-m3 | 用表面色调表示层级 |
| 18 | Salesforce Lightning | lightningdesignsystem.com | 记录页页头带关键字段区；舒适 / 紧凑两档密度 |
| 19 | Ant Design 规范 | ant.design/docs/spec/data-format-cn | 单位放表头；金额用币种代码；时间、状态、操作列不折行 |
| 20 | Linear | linear.app/now/how-we-redesigned-the-linear-ui | 主题变量收敛；界面框架少用品牌色；对齐「感觉得到但看不出来」 |
| 21 | Odoo 18 | odoo.com/documentation/18.0/applications/essentials/search.html | 生效条件显示为可删除标签，可存为收藏 |
| 22 | Refero Styles（Ramp、Brex）、Vercel Geist、Stripe Apps | styles.refero.design/ 等 | 高亮黄按钮配深色字；只一个强调色且只给主操作；间距与圆角令牌分级 |

## 二、本项目设计原则（总纲）

1. **先给答案，再给操作。** 每页第一眼是用户来这一页要找的东西（列表看数据、详情看状态和金额、工作台看要处理的事），筛选和设置往后放。
2. **一屏一个视觉重点。** 每个页头、弹窗、分区最多一个实底主按钮、一个最大的数字。
3. **颜色只表达「能点」和「要注意」。** 主题色给主操作和选中状态，语义色给状态和告警，其余用中性灰。
4. **层级不超过三层。** 页面底、浮起卡、卡内凹格；卡里不套带阴影或描边的卡。
5. **对齐出秩序。** 文字左对齐，数字右对齐，同一列小数点对齐，卡片内边距与表格首列对齐，同一行卡片等高。
6. **数字要精确、可比、带币种。** 等宽数字、千分位、两位小数、币种代码；同一列同一币种时币种写在表头。
7. **紧凑的表格，宽松的卡片。** 兼顾一屏信息量和消费级观感。
8. **已生效的条件永远看得见。** 筛选、排序、视图状态都在界面上显示，能一键清除。
9. **主操作始终触手可及。** 长详情页页头吸顶，长表格横向滚动条吸底，批量操作跟着勾选出现。
10. **每个数据视图都有六种状态。** 加载、刷新中、空、无结果、错误、无权限，都单独设计。
11. **反馈及时，并且能挽回。** 成功用轻提示，失败就地说明原因和改法；能撤销的给撤销，不能撤销的提前写明。
12. **说人话，少说话。** 不写常识性说明，不把字段名或枚举值直接展示给用户；口径放帮助图标。
13. **深色不是浅色反过来。** 层级越高表面越亮，用边框代替阴影，大块彩色浅底在深色下单独核对。
14. **以真实环境验收。** 以 Windows 浏览器 1366×768 和 1440×900、浅深两色、默认琥珀加一种冷色主题为准，不以原型渲染为准。

## 三、对标检查表（每个页面验收用）

做完一个页面后，和标杆模板并排截图，逐项检查，未达标的改到达标再交用户。

**观感**

| # | 检查项 | 优秀模板的表现 | 达标标准 | 怎么检查 |
|---|---|---|---|---|
| 1 | 第一眼焦点 | shadcn、Materio：每张卡数值最大，一屏一个主按钮 | 页头最多 1 个实底主按钮；每张卡最大的字是关键数值或标题 | 截图缩到 25%，3 秒内能说出页面重点 |
| 2 | 层级数量 | PrimeDash、Polaris | 不超过三层；卡里没有带阴影或描边的子卡 | 查嵌套卡片 |
| 3 | 同行等高 | Materio、Spike | 同一行（或组合后）底边高度差 ≤1px | 浏览器量每张卡底边坐标 |
| 4 | 对齐 | Linear | 卡片标题、表格首列、搜索区第一个字段左边缘对齐；数字列右边缘对齐 | 截图画参考线，偏差 ≤2px |
| 5 | 间距刻度 | Atlassian、Stripe | 间距只用 4、8、12、16、20、24、28、32 | 抽查 10 处计算样式 |
| 6 | 字阶 | shadcn、Primer | 只用 12、13、14、16、20、24（、28）；相邻层级至少差 2px 或一档颜色 | 列出页面字号不超过 6 种 |
| 7 | 中文字重 | — | 中文只用 400/600；Windows 实测标题确实显示为粗体 | Windows Chrome 截图与原型对比 |
| 8 | 数字排版 | Ant、Catalyst | 金额、数量、汇率等宽右对齐，千分位，金额两位小数，带币种 | 查 `font-variant-numeric`、`text-align`，看小数点对齐 |
| 9 | 主题色面积 | Ramp、Brex、Linear | 非悬停时表格里主题色只出现在勾选框和选中行 | 截图看主题色区域；切红色主题看是否刺眼 |
| 10 | 状态与属性 | Atlassian | 彩色标签只表达流程状态或告警；属性用中性；每行彩色标签不超过 2 个 | 对照 `status-colors.md` |
| 11 | 深色分层 | Carbon、Materio、Atlassian | 页面 < 卡片 < 表头或弹出层，亮度逐级上升；卡片有边框；没有发脏的大块彩色底 | 深色截图逐层取色 |

**体验**

| # | 检查项 | 优秀模板的表现 | 达标标准 | 怎么检查 |
|---|---|---|---|---|
| 12 | 首屏信息量 | shadcn、Materio 一屏 8–10 行 | 列表 1440×900 至少 8 行，1366×768 至少 5 行 | 两种尺寸截图数行数 |
| 13 | 阅读顺序 | Polaris 详情页 | 标题和状态 → 关键数字 → 明细 → 附件、备注、记录；Tab 顺序与视觉一致 | 按 Tab 走一遍 |
| 14 | 常用操作步数 | Carbon | 每行最常用的 2 个操作一次点击完成；其余不超过两次 | 数前 3 个高频任务的点击次数 |
| 15 | 已生效条件可见 | Odoo、Polaris | 所有生效条件（含收起的更多筛选）可见、可单独清除、可一键重置 | 设 3 个条件后收起检查 |
| 16 | 主操作可达 | Vuexy、吸顶页头 | 页面超过两屏时主按钮始终可见；长表格横向滚动条在视口内 | 滚到中部和底部截图 |
| 17 | 操作反馈 | Polaris、Primer | 成功用轻提示；失败就地说明原因和改法；危险操作写明后果、默认焦点在取消 | 逐个触发 |
| 18 | 批量操作 | Polaris、Carbon | 勾选后工具栏切换为「已选 N · 取消选择 · 批量操作」；危险批量有确认 | 勾选 1 张和全选各试 |

**完成度**

| # | 检查项 | 优秀模板的表现 | 达标标准 | 怎么检查 |
|---|---|---|---|---|
| 19 | 加载与刷新 | Primer | 首次加载骨架屏（延迟 300ms，形状与真实行一致）；重新查询保留旧数据加顶部进度条；加载中不显示「共 0 条」 | 限速慢速 3G 录屏 |
| 20 | 空、无结果、错误、无权限 | Atlassian、Primer | 四种状态各有文案和下一步；错误有重试；无权限不显示成空 | 模拟空数组、500、403 |
| 21 | 窄屏与截断 | Ant | 1366 宽下金额不折行、表头不换行、时间不被截断；截断文字悬停显示全文；固定列滚动时有阴影 | 1366×768 截图，横向滚到左中右 |
| 22 | 主题与模式矩阵 | PrimeDash 6 种预设 | 浅深两色 × 琥珀、天蓝、红三种主题色，6 张截图没有撞色、发脏、看不清；焦点框清晰 | 6 组截图并排 |

## 四、调研发现、待用户拍板的取舍

见 `design-review.md`「待用户拍板」D18 起。
