# AGENTS.md

这份文档是 AI Agent 在 `zrlog-admin-web` 工程内工作的入口规则。进入本仓库后，先读本文件，再按任务打开 `docs/engineering-conventions.md`、具体 controller/service、前端页面或 `zrlog-ops` 验收规则。

## 工程定位

`zrlog-admin-web` 是 ZrLog 的后台管理工程，主要负责后台 API、React 管理界面、插件交互面板宿主、AI 写作辅助、资源管理和后台运行时状态展示。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| `src/main/java` | 后台 controller、service、DTO、插件宿主协议和后台业务。 |
| `src/main/frontend` | React 后台页面、路由、组件、主题、i18n 和前端构建。 |
| `src/main/resources` | 后台 i18n、AI prompt、静态资源和 native 相关资源。 |
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
- 不做无关重构，不引入无关依赖，不改变构建、发布、目录和运行约定。

## 构建与验证

常用命令：

```bash
scripts/check-admin-guardrails.sh
mvn -q -DskipTests compile
cd src/main/frontend && yarn type-check
cd src/main/frontend && yarn build
```

修改后端 Java 行为时至少运行相关测试或 `mvn -q -DskipTests compile`。修改前端 TypeScript、页面或主题时至少运行 `cd src/main/frontend && yarn type-check`，必要时运行 `yarn build`。修改跨前后端协议、native/Gson DTO、插件交互面板或 AI SSE 时，需要补充对应专项验证。

## i18n

后台 i18n 相关工作必须遵守 `docs/i18n.md`。

关键约束：

- 前端可见 UI 文案统一放在 `src/main/frontend/src/i18n/admin.ts`。
- 使用 `getRes().admin.user.info` 这类带类型检查的属性访问；不要使用 `getRes()["admin.user.info"]` 或 `res["title"]`。
- 不要把前端文案加到后端 `.properties` 文件。
- 后端 i18n 只服务后端自己输出的消息，使用 `admin_backend_*.properties`。
- 前端组件里不要保留硬编码可见文案，也不要保留 `getRes().x || "保存"` 这类 fallback 字面量。

## 前端主题

后台前端主题相关工作必须遵守 `docs/frontend-theme.md`。

关键约束： 

- 普通矩形块、卡片、面板、输入框、列表项、弹层容器等圆角统一使用主题配置，不要硬编码 `8/10/12/14/16/18/24/999` 这类值。
- 链接、链接按钮、可点击文字的颜色应优先复用主题色或组件默认主题行为，不要直接写死蓝色或其他固定颜色值。
- `styled-components`、布局壳组件、跨组件公共样式如果需要圆角或链接颜色，也要通过主题值传入，不要在样式文件里单独维护固定值。
