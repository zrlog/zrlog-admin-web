# zrlog-admin-web

`zrlog-admin-web` 是 ZrLog 后台管理工程，提供后台 API、React 管理界面、资源管理、AI 写作辅助和插件面板宿主。

## 模块

- `zrlog-admin-account/content/assets/ai/access`：各功能的接口、服务、资源和测试。
- `zrlog-admin-common`：共享 HTTP 契约、审计、通知和通用设置。
- `zrlog-admin-web-api`：后端组装与公共 API。
- `zrlog-admin-web-ui`：后台页面、PWA 和静态资源。
- `zrlog-admin-web`：保留 `com.hibegin:zrlog-admin-web` 坐标，组装完整后台。

API 和 UI 分别产出 JAR，下游通过原构件的传递依赖获取完整后台。

## 接入文档

- [后台 API](docs/api/README.md)
- [外部应用授权](docs/api/oauth.md)
- [MCP 内容工具](docs/api/mcp.md)
- [安装与部署](docs/install.md)
- [仓库职责与最小验证命令](AGENTS.md)
