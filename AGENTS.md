# AGENTS.md

这份文档是 AI Agent 在 `zrlog-admin-web` 工程内工作的入口规则。进入本仓库后，先读本文件，再按任务打开 `docs/engineering-conventions.md`、具体 controller/service、前端页面或 `zrlog-ops` 验收规则。涉及后台 API 时，先查 `docs/api/README.md` 和 `docs/api/openapi.yaml`。

## 工程定位

`zrlog-admin-web` 是 ZrLog 的后台管理工程，主要负责后台 API、React 管理界面、插件交互面板宿主、AI 写作辅助、资源管理和后台运行时状态展示。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| `zrlog-admin-*/src/main/java` | 按功能维护 controller、service、DTO 和 Native 注册；依赖方向见 `docs/admin-module-plan.md`。 |
| `zrlog-admin-web-api` | 后端组装与公共 API；不依赖 UI，也不打包页面资源。 |
| `zrlog-admin-web-ui` | React、页面渲染、PWA、后台页面静态化及其资源；前端只构建一次。 |
| `zrlog-admin-web` | 保留原构件坐标，仅组装 API/UI、容纳开发入口与跨模块集成测试。 |
| `zrlog-admin-web-ui/src/main/frontend` | React 后台页面、路由、组件、主题、i18n 和前端构建。 |
| `zrlog-admin-*/src/main/resources` | common 的后端 i18n、AI 的 prompt、account 的 WebAuthn metadata、UI 的页面配置资源；`zrlog-admin-web-ui/src/main/frontend/build` 仅为前端生成目录。 |
| `docs/` | 工程协作、i18n、主题、native-image 和安装说明。 |
| `doc/` | 产品设计、审计记录和功能规划材料。 |
| `scripts/` | 本地启动和工程护栏脚本。 |
| `conf/` | 本地运行配置，修改前确认不是用户调试状态。 |

## 工程协作

后台大功能、跨前后端改动、插件交互面板和 AI 辅助开发必须遵守 `docs/engineering-conventions.md`。
跨仓库边界和统一验收入口见 `zrlog-ops/docs/repository-structure-guide.md` 与 `zrlog-ops/acceptance/zrlog-admin-web.yaml`。

关键约束：

- 开始编码前先读真实链路：路由、controller、service、DTO、前端调用点、现有测试和构建脚本。
- 大功能先拆成可验证切片，并先写清接口契约、数据归属、兼容范围和非目标。
- 保持 admin/plugin 边界：admin 只负责容器、协议、通用渲染、通用控制和运行时状态展示；插件保留业务接口、业务数据和动作处理。
- 涉及 URL、资源地址、插件地址或 context path 时，必须检查后端生成、前端拼接、页面显示、表单提交和持久化完整链路，避免重复拼接。
- 新增接口结构优先使用 typed DTO，并同步维护 native image 注册；不要用临时 `Map` 贯穿业务逻辑。
- `docs/api/openapi.yaml` 只维护受支持的关键后台接口，不是 `AdminRouters` 的完整清单。文档中的接口有变更时必须同步维护；新接口只有在明确作为稳定接口支持后才写入。
- 不做无关重构，不引入无关依赖，不改变构建、发布、目录和运行约定。

## 构建与验证

常用命令：

```bash
scripts/check-admin-guardrails.sh
mvn -q -DskipTests compile
cd zrlog-admin-web-ui/src/main/frontend && yarn type-check
cd zrlog-admin-web-ui/src/main/frontend && yarn build
```

修改后端 Java 行为时至少运行相关测试或 `mvn -q -DskipTests compile`。修改前端 TypeScript、页面或主题时至少运行 `cd zrlog-admin-web-ui/src/main/frontend && yarn type-check`，必要时运行 `yarn build`。修改跨前后端协议、native/Gson DTO、插件交互面板或 AI SSE 时，需要补充对应专项验证。

## 共享测试夹具

- 通用数据库、日志和内存目录工具使用 base 的 `zrlog-test-support`，仅以 test scope 引入，遵守 `zrlog-base/docs/test-support.md`。
- admin 专用资源、身份、MFA 和上传 fake 放在 admin-common 的测试源码，通过仅包含 support 包的 tests classifier 共享；不引入第二个测试模块。
- 安装/启动测试调用真实 InstallService；MemoryApplicationTest 在临时项目目录中运行并恢复全局配置，不能重置开发者的 `.zrlog-memory`。

## MemoryApplication

`com.zrlog.admin.MemoryApplication` 位于 `zrlog-admin-web/src/test/java`，是本地开发辅助入口，用于启动干净的内存数据库后台环境，不是发布产物入口。

关键约束：

- 安装配置模板放在工程外部配置目录 `conf/memory-install.json`，不要放进 `src/main/resources`。
- 启动时使用工程目录下的 `.zrlog-memory/` 作为隔离 runtime root；每次启动前清空该目录，再生成 `conf/db.properties` 和 `conf/install.lock`，不要生成无必要的安装配置中间文件。
- `.zrlog-memory/` 必须被 git 忽略；不要写入或覆盖仓库已有的 `conf/db.properties`、`conf/install.lock`。
- 内存模式仍通过 install-web 的安装链路：读取 `conf/memory-install.json` 模板，补入运行时 DB/host，反序列化 `InstallConfigVO`，创建 `InstallService`，调用 `install()`；不要手工拼完整安装流程。
- `configMsg.secretKey` 等需要稳定的安装字段应固定在 `conf/memory-install.json`，避免每次启动随机变化。
- `MemoryApplication*.class` 和 memory 安装配置不得进入最终 jar；修改后用 jar 条目检查确认。
- `MemoryApplicationTest` 必须覆盖两层：install-web 写入和种子数据，以及 `ZrLogConfig` 从生成的 `conf/db.properties` 构建 datasource 的启动链路。
- 本地启动优先使用 `bash shell/memory-run.sh`；端口可用 `--port=18080` 或 `ZRLOG_MEMORY_PORT=18080` 覆盖。该脚本会在后台静态资源缺失时先完成前端打包。

## i18n

后台 i18n 相关工作必须遵守 `docs/i18n.md`。

产品表达读取 [Ops 产品文案规范](../zrlog-ops/docs/content-writing-guide.md)，资源与语言实现读取本地 `docs/i18n.md`。

关键约束：

- 前端可见 UI 文案统一放在 `zrlog-admin-web-ui/src/main/frontend/src/i18n/admin.ts`。
- 使用 `getRes().admin.user.info` 这类带类型检查的属性访问；不要使用 `getRes()["admin.user.info"]` 或 `res["title"]`。
- 不要把前端文案加到后端 `.properties` 文件。
- 后端 i18n 只服务后端自己输出的消息，使用 `admin_backend_*.properties`。
- 前端组件里不要保留硬编码可见文案，也不要保留 `getRes().x || "保存"` 这类 fallback 字面量。

## 前端主题

后台 UI 从 [Ops UI 统一入口](../zrlog-ops/docs/ui-design-guide.md) 读取适用范围与专项规范；[主题隔离规则](../zrlog-ops/docs/material3-agent-guide.md#后台默认主题的隔离规则) 和 [后台验收契约](../zrlog-ops/acceptance/zrlog-admin-web.yaml) 为对应规则来源。本地 [前端主题实现](docs/frontend-theme.md) 维护源码分派、token API 与代码示例，本入口不复制规则正文。
