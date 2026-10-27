# 烛龙ERP 前端

外贸业财一体化中台的前端，基于 Umi Max 4、antd 6、ProComponents 3，构建工具为 utoopack。

## 开发

```bash
npm install
npm run dev        # 不用 mock，接口代理到 localhost:8080 的后端
npm start          # 加载页面目录下的 _mock.ts
```

## 检查与构建

```bash
npm run lint       # Biome + tsc
npx antd lint ./src
npm test           # Jest
npm run build      # 生产构建，输出到 dist/
```

生产环境通过 `Dockerfile` 构建镜像（nginx 提供静态文件并代理 `/api/` 到后端），部署流程见仓库 `docs/06-部署运维/`。

## 说明

- 开发约定与架构要点见 [AGENTS.md](./AGENTS.md)（Claude Code 通过 CLAUDE.md 引用）
- `patches/` 下是对第三方依赖的补丁，`npm install` 时自动应用，用途见 AGENTS.md「构建补丁」
- 项目最初来自 [Ant Design Pro](https://github.com/ant-design/ant-design-pro) 模板（MIT 许可，见 [LICENSE](./LICENSE)）
