# 第三轮逐文件覆盖台账

> 审查快照：以下结果与仓库 / 清理状态均记录于 2026-11-01，基线 `09da3dc3c790c8c1b729cad8d195a66944d7546e`。后续当前进度见[问题状态台账](../09-remediation/issues.md)；修复结果另记对应任务，不回写历史通过数量或将修复混入旧证据。

第三轮逐文件静态审读及分类检查已收口。前两轮62主题及测试/浏览器证据继续有效，新主题见总台账；本轮不把旧结论、检索命中或测试通过自动转换为已审，完整阅读也不等于全部状态组合验收。

基线为 `docs/erp-audit` / `09da3dc3c790c8c1b729cad8d195a66944d7546e`。清单按Git跟踪路径枚举，路径以仓库根目录为基准。实际审查只读产品，不读真实.env、密钥或业务数据，不修改产品、依赖、Git或Wiki。

| 所有权 | 清单 | 范围 |
| --- | --- | --- |
| 后端业务域执行者 | [backend-business.csv](backend-business.csv) | inquiry、quotation、sales、finance、purchase、warehouse、logistics、crm、masterdata及对应测试/关联契约 |
| 后端平台/商品执行者 | [backend-platform.csv](backend-platform.csv) | 其余main Java与test Java，含common/framework/入口/support，以及关联access、AI、document、product、system当前spec |
| 前端执行者 | [frontend.csv](frontend.csv) / [pages.csv](pages.csv) | 前端自研源码、页面、共享组件与对应测试；页面操作覆盖与源码文件状态分开 |
| 主审运行/SQL域 | [runtime-data.csv](runtime-data.csv) | scripts、sql、根Compose/示例配置/Git属性、工作流、后端构建入口与main/test resources |
| 补充文档/资产分工 | [additional.csv](additional.csv) | 四个源码/运行清单之外的1238个Git路径，全部从pending开始；当前文档、活动提案、规则、历史代码/参考与资产分kind，不省略archive内脚本/SQL |

初始union预检为3315个Git跟踪路径：前三代码域与runtime并集2077，additional补齐1238；跨表重复按path去重。pages.csv是页面级操作覆盖清单，不因其行数再次增加源码文件分母。初始覆盖清单完整只表示已分配/列出全部路径，**不表示内容已审完**。

additional中当前docs含44份Markdown及1份SQL；活动change13，规则/context/tool34，根README与配置另列。历史archive的535份Markdown按historical-reference核对结构、引用和仍被现行文档引用的冲突，不逐句当现行规范；其中1个JS、2个Python、1个SQL仍按历史代码审查。原型PNG/OP、DOCX/PDF等按用途、结构与引用可达核对，不把旧原型逐图UX当成现行页面浏览器验收。

CSV列为 `path,kind,status,checks,findings,evidence`。初始全部pending；只有完整内容阅读，并核对该文件适用的调用链、输入/输出契约和已有保护后才能标reviewed。DTO/实体/测试同样需记录实际字段、消费者或对应被测契约，不能仅按文件类型批量标记。部分阅读保持pending，在checks/evidence说明进展；阻塞不当成无问题。每个reviewed文件记录简短实际审查点/结论，即使没有新增发现。

静态逐文件审查、真实组件/函数测试、真实后端/数据库验证、浏览器布局/焦点/操作分别记证据，不相互替代。未运行的用例不计通过，发现错误的见证通过也不代表整改完成。

node_modules、target、dist、Umi生成目录及其他可再生构建/生成产物不计自研源码。Git跟踪的assets、文档、规格与脚本/SQL按性质单列；有build字样的Git跟踪迁移SQL不因目录名自动排除。关联SQL/spec可能同时被主审与域执行者交叉核对，**全库覆盖率按唯一Git path取并集去重，不能把多个ledger行数直接相加**。全库manifest与完成结论由主审在各范围收口后统一形成。

## 收口口径

5份逐路径清单并集3315，与Git跟踪3315个唯一路径完全一致（UTF8、-z枚举），missing/extra/各表内部重复均0。1206个主Java文件、156个test Java文件（含测试支撑）完整读审，前端412路径中407完整审读、4二进制资产和1锁文件分类；平台679与业务819均无pending，runtime232和additional1238按下述结构边界收口。语义覆盖包括全部当前自研脚本/样式/配置；历史生成V1.3.9 SQL单列结构，精确生成器离线复算与tracked逐字一致。

按唯一路径、优先保留语义审查状态去重：2151reviewed、1150structural-reviewed、8reviewed-fixture、4classified-asset、1classified-generated（前端依赖锁文件）、1structural-reviewed-generated-data，共3315。1150结构包括additional1139与runtime11；历史535MD、元数据、二进制/原型与许可文本的适用边界均在各行，不称全部历史内容逐句验证。

pages.csv不计文件分母。88次组件注册对应85唯一组件入口，20redirect和16group独立；主审完成所有组件注册正常render和redirect实测，但没有遍历全部状态/角色/写入/响应式/读屏组合。浏览器及审查资源均已收口，清理经owner执行及主审独立复核，见验证总览/前端专题；不把清理等同产品整改。
