## ADDED Requirements

### Requirement: 共享本地代码图谱配置

项目 SHALL 通过根目录 `codegraph.json` 共享相对路径索引边界，保留前端 TypeScript/TSX、后端 Java、Python 编排源码，排除依赖、构建物、缓存、运行上传数据与凭据；索引数据库和状态 SHALL 被 Git 忽略。

#### Scenario: 三语言源码可查询且数据不入库
- **WHEN** 在仓库根目录初始化 CodeGraph
- **THEN** 能查询三类第一方源码的符号与关系，根 `storage/` 和后端上传目录被排除，后端 `framework/storage` 源码仍可查询，生成的 `.codegraph/` 内容不进入 Git

### Requirement: 按索引状态查询与维护

编程助手 SHALL 优先用 CodeGraph 查询代码结构、调用与影响，核对当前项目和 worktree 的索引状态；缺索引才初始化，正常改动增量同步，发现落后才手动同步，异常才全量重建。不可用时 SHALL 使用源码搜索继续，图谱 SHALL 不替代测试。

#### Scenario: 切换分支或工具不可用
- **WHEN** 切换分支、worktree 或 CodeGraph 不可用
- **THEN** 核对项目路径与索引状态后按需维护；无法使用图谱时继续源码搜索，不启动额外后台 watcher，不复用其他 worktree 的索引
