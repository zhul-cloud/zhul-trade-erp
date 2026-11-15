## Why

Codex 与 Claude Code 需要共用本项目的代码结构、调用关系和影响分析入口，减少重复探索，并避免索引依赖、构建物、运行数据或凭据。

## What Changes

- 在唯一规则正文 `AGENTS.md` 中约定 CodeGraph 查询、缺索引初始化、增量同步、异常重建及源码搜索回退。
- 共享根目录 `codegraph.json` 的相对路径排除项，覆盖前端 TypeScript/TSX、后端 Java 和 Python 编排服务的第一方源码。
- Git 忽略本地索引、缓存与状态；实际初始化和三语言查询以已安装的 `@colbymchenry/codegraph` 1.6.2 验证。

## Capabilities

### New Capabilities

- `development/codegraph`: 团队共用的本地代码图谱规则与索引边界。

### Modified Capabilities

无。

## Impact

仅开发工具配置与文档；不改业务代码、数据库、生产环境变量、用户级 CLI/MCP 安装或 `.claude/CLAUDE.md` 引用。不新增后台服务。索引数据库留本地，不入库。
