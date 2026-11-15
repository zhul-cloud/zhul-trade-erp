## 1. 项目接入

- [x] 1.1 核对 CodeGraph 1.6.2 的帮助与配置加载实现，添加共享排除项、Git 忽略与唯一规则正文。
- [x] 1.2 在当前仓库初始化索引，验证 TypeScript/TSX、Java、Python 查询及调用、影响关系；检查排除项和 Git 忽略生效。
- [x] 1.3 检查可移植配置与 Git 差异，通过 OpenSpec 严格验证，同步主规格并归档。

## 验证记录

- CodeGraph 1.6.2 首次索引：2,583 个文件，94,296 个节点，214,836 条边；`status --json` 显示 `state=complete`、无待同步文件、无待解析引用、无需重建、无 worktree 路径冲突。
- `query` 命中 `usePageTitle`（TypeScript）、`QuotationDetail`（TSX）、`QuotationServiceImpl`（Java）、`preprocess`（Python）。`callers preprocess --file scripts/ai-orchestrator/large_parse.py` 命中编排入口 `_parse_large`；限定 PageTabs/hooks.ts 的 `usePageTitle` 有 62 个调用方，`impact --depth 1` 返回 63 个节点、62 条边。
- 按只读数据库文件表核查：被排除的依赖、构建物、根存储、上传数据与环境配置均无命中；保留 12 个 `framework/storage` 源码文件、9 个 Python 编排服务文件，解析错误为 0。使用安装包实际加载器与匹配器验证 24 项配置、15 个排除样例及 3 个第一方源码保留样例。
- `git check-ignore --no-index -v` 验证数据库、WAL/SHM、daemon.pid、生成的内部 `.gitignore` 与 WSL 索引目录均被根规则忽略；未生成 Git hooks 或额外后台 watcher。
