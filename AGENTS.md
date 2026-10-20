# AGENTS.md

`zrlog-admin-web` 是 ZrLog 后台管理工程。先读本文件，再按任务读取源码和测试。若本地存在 `.codex/AGENTS.md`
，同时读取其中的工作区约定；该目录不提交到仓库。

## 工程职责

- `zrlog-admin-account/content/assets/ai/access`：所属功能的 Controller、服务、DTO、资源和测试。
- `zrlog-admin-common`：共享 HTTP 契约、审计、通知和通用设置。
- `zrlog-admin-web-api`：后端组装与公共 API，不依赖 UI。
- `zrlog-admin-web-ui`：React 界面、页面渲染、PWA 和静态资源。
- `zrlog-admin-web`：兼容构件的组装、开发入口和集成测试。

公开接入见 [API 文档](docs/api/README.md)。后台负责插件面板的宿主能力，插件业务由插件维护。修改接口结构时检查调用方和
Native/Gson 注册。

## 构建与验证

```bash
scripts/check-admin-guardrails.sh
./mvnw -q -DskipTests compile
cd zrlog-admin-web-ui/src/main/frontend && yarn type-check
```

本地启动入口为 `scripts/dev-start.sh`，内存预览入口为 `bash shell/memory-run.sh`。按改动范围运行相关测试；保留已有工作区改动，不提交构建输出、运行目录和本地配置。
