# zrlog-admin-web

`zrlog-admin-web` 是 ZrLog 后台管理端模块，包含后台接口、后台静态资源构建和本地开发入口。后端使用 Java、SimpleWebServer、Gson、DbUtils；前端使用 React、Ant Design、styled-components 和 css-in-js。

## 目录

- `zrlog-admin-account/content/assets/ai/access`：各功能自己的 Controller、服务、DTO、资源和测试。
- `zrlog-admin-common`：共享 HTTP 契约、审计、通知状态和通用设置；不依赖功能模块。
- `zrlog-admin-web-api`：Java 后端组装与公共 API；不依赖 UI，不打包页面资源。
- `zrlog-admin-web-ui`：React、页面渲染、PWA 和后台页面静态化；依赖 API。
- `zrlog-admin-web`：兼容原构件坐标的组装模块、开发启动入口和跨模块集成测试。
- `zrlog-admin-test-support`：仅测试使用的数据库与日志夹具，不进入运行时依赖。
- `zrlog-admin-web-ui/src/main/frontend/build`：忽略的前端构建输出，仅由 UI 构件打包。
- `zrlog-admin-web-ui/src/main/frontend`：React 后台页面工程。
- `docs`：后台开发规则和协议文档。
- `shell`：本地运行、前端构建和静态资源预览脚本。
- `conf`：本地开发配置示例。

模块依赖、启动开关及验证记录见 [后台模块化](docs/admin-module-plan.md)。API 和 UI 分别产出 JAR，UI 的 `build` 输出仅打包到 UI JAR 的 `admin/` 目录。下游继续依赖 `com.hibegin:zrlog-admin-web`，通过传递依赖获得完整后台；API 和 UI 仍在同一仓库一起构建、发布。

## 本地开发

前后端一起启动：

```shell
scripts/dev-start.sh
```

脚本会复用已运行的 `http://localhost:17080/sub` 后端和 `http://localhost:3000/admin` 前端；未运行时会自动启动。前端默认启用 polling watcher，避免 Linux inotify 数量不足导致 `ENOSPC`。

### 后端

```shell
./mvnw -DskipTests install
./mvnw -pl zrlog-admin-web exec:java -Dexec.mainClass="com.zrlog.admin.Application"
```

开发入口 `com.zrlog.admin.Application` 默认使用 `17080` 端口和 `/sub` context path，并从 `conf/db.properties` 读取本地数据库配置。

需要先打包再启动时，也可以使用：

```shell
sh shell/mvn-run.sh
```

### 前端

```shell
cd zrlog-admin-web-ui/src/main/frontend
yarn install --frozen-lockfile --ignore-scripts
yarn start
```

前端工程的排障笔记保留在 [zrlog-admin-web-ui/src/main/frontend/README.md](zrlog-admin-web-ui/src/main/frontend/README.md)。

## 构建

### 后台 jar

```shell
./mvnw -Pjar clean package
```

`jar` profile 会生成 starter jar，并把运行依赖复制到 `lib`。

### 后台静态资源

```shell
./mvnw -PnodeBuild package
```

`nodeBuild` profile 只在 UI 模块执行：在 `zrlog-admin-web-ui/src/main/frontend` 下运行 `yarn install --frozen-lockfile` 和构建，输出到其 `build` 目录，并打入 UI JAR。Node/Yarn 工具缓存保留在根 `target`。

只需要直接构建前端时：

```shell
cd zrlog-admin-web-ui/src/main/frontend
yarn type-check
yarn build
```

如果需要预览已构建的后台静态资源：

```shell
sh shell/admin-static-page.sh
```

## 开发规则

- 面向 AI Agent 的仓库入口见 [AGENTS.md](AGENTS.md)。
- 大功能、跨前后端改动和 AI 辅助开发必须遵守 [工程协作约定](docs/engineering-conventions.md)。
- 后台 API 的统一入口、接口约定和 OpenAPI 契约见 [后台 API 文档](docs/api/README.md)。
- AI 模型列表的热加载、官方目录同步脚本和 GitHub Actions 每日更新 PR 见 [AI 模型目录](docs/ai-model-catalog.md)。
- 前端可见文案必须维护在 `zrlog-admin-web-ui/src/main/frontend/src/i18n/admin.ts`，详见 [i18n 规则](docs/i18n.md)。
- 后台主题样式、圆角和链接颜色必须复用主题配置，详见 [前端主题规则](docs/frontend-theme.md)。
- 后台 UI 调整必须使用真实页面验收，详见 [后台 UI 真实页面验收](docs/admin-ui-real-page-validation.md)。
- 新增会被 Gson 序列化或反序列化的 DTO 时，要同步维护 native image 注册，详见 [Native Image 规则](docs/native-image.md)。
- 后台插件交互面板协议见 [插件交互面板协议](docs/plugin-interactive-surface.md)。
- 高频接口优化需要记录 before/after 证据，详见 [接口性能基线](docs/api-performance-baseline.md)。
- 后续后台开发事项见 [后台后续开发计划](docs/admin-next-dev-plan.md)。

## 提交前检查

按改动范围选择检查项：

```shell
scripts/check-admin-guardrails.sh
mvn -q -DskipTests compile
cd zrlog-admin-web-ui/src/main/frontend && yarn type-check
cd zrlog-admin-web-ui/src/main/frontend && yarn build
git diff --check
```

如果需要一次性执行护栏扫描、后端编译、前端类型检查和 diff 空白检查：

```shell
scripts/check-admin-guardrails.sh --full
```
