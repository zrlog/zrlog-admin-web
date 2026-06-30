# 工程协作约定

本约定用于后台大功能、跨前后端改动、插件交互面板和 AI 辅助开发。目标是让改动始终沿着现有工程边界推进，避免因为补全假设导致实现走样。

## 大功能工作流

- 开始编码前先读真实链路：路由、controller、service、DTO、前端调用点、现有测试和构建脚本。不要只凭文件名或接口名推断行为。
- 大功能先拆成可验证切片，并先写清接口契约、数据归属、兼容范围和非目标。计划不清楚时，先补计划，不要直接铺开实现。
- 每个切片只覆盖一个清晰行为面。发现相邻问题时，只有同链路、低风险、能被当前验证覆盖时才一起修；否则记录为后续项。
- 不做无关重构，不引入无关依赖，不改变构建、发布、目录和运行约定。
- 最终说明必须包含：实际改动、验证命令、未处理风险或需要后续确认的点。

## 模块边界

- admin 负责后台容器、协议、通用渲染、通用控制和运行时状态展示。
- 插件负责自己的业务接口、业务数据、动作处理和复杂页面实现。
- 可复用的后台渲染能力应留在 `zrlog-admin-web`，插件仓库只保留协议示例和插件业务实现。
- 跨 admin/plugin 改动时，不要让 admin 解析或持有插件私有业务模型；使用稳定协议字段和 opaque 引用传递动作。

## 接口和 DTO

- 新增或调整接口时优先使用 typed DTO，不要用临时 `Map` 贯穿业务逻辑。
- 保持已有 JSON 字段名和兼容语义。确实需要改字段时，先确认消费方和迁移策略。
- 新增会被 Gson 序列化或反序列化的 request、response、VO、嵌套类时，必须同步维护 native image 注册，详见 [Native Image 规则](native-image.md)。
- controller 负责协议边界，service 负责业务组装和校验；不要把业务拼装长期留在 controller 或前端补丁里。

## URL 和 Context Path

- 涉及 URL、资源地址、插件地址或 context path 时，必须检查完整链路：后端生成、前端拼接、页面显示、表单提交和持久化。
- 明确区分展示地址和保存值。展示层可以拼接 backend server/context path，但提交和持久化应保持接口约定的原始值，避免重复拼接。
- 不要在多个层同时追加 context path。新增 helper 或修复路径逻辑时，要确认已有前端 helper、后端生成逻辑和数据库历史值。
- URL 校验至少覆盖：空 context path、非空 context path、站内根路径、相对路径、外链、协议相对 URL、非法 scheme 和路径穿越。

## 前端约束

- 前端可见文案统一维护在 `src/main/frontend/src/i18n/admin.ts`，详见 [i18n 规则](i18n.md)。
- 后台主题样式、圆角、链接颜色和公共 shell 行为必须复用现有主题和组件约定，详见 [前端主题规则](frontend-theme.md)。
- 前端不应通过“显示能跑”的局部字符串拼接掩盖接口契约问题。跨前后端数据形状变化时，应先修 DTO 和调用类型。
- 修改页面行为后，优先用真实页面或现有脚本验证用户路径，而不是只检查静态代码。

## 验证要求

- 后端 Java 行为变更至少运行相关单测；影响共享服务或接口契约时运行 `mvn -q test`。
- 前端 TypeScript 或页面行为变更至少运行 `cd src/main/frontend && yarn type-check`，必要时运行 `yarn build`。
- URL、安全、清洗、校验类改动必须补覆盖异常输入的测试，不只测正常路径。
- 提交前按范围运行：

```shell
scripts/check-admin-guardrails.sh
mvn -q -DskipTests compile
cd src/main/frontend && yarn type-check
cd src/main/frontend && yarn build
git diff --check
```
