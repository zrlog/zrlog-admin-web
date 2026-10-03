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

- 后台 API 文档统一从 [`api/README.md`](api/README.md) 进入。`api/openapi.yaml` 只维护已发布或明确支持的关键接口，不要求枚举全部后台路由；文档中的接口发生变化时必须同步维护方法、鉴权、参数来源、响应类型和副作用。
- 新后台路由默认视为 admin UI 内部实现。只有业务边界稳定、对自动化调用有独立价值、兼容责任明确并经过显式评审后，才进入 OpenAPI 受支持范围。
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

- UI 任务从 [Ops UI 统一入口](../../zrlog-ops/docs/ui-design-guide.md) 按适用范围加载专项规范；主题分派与 API 用法见 [本地实现](frontend-theme.md)，不在此复制视觉路线与隔离规则。
- 前端可见文案统一维护在 `zrlog-admin-web-ui/src/main/frontend/src/i18n/admin.ts`，详见 [i18n 规则](i18n.md)。
- 后台主题样式、圆角、链接颜色和公共 shell 行为必须复用现有主题和组件约定，详见 [前端主题规则](frontend-theme.md)。
- 前端不应通过“显示能跑”的局部字符串拼接掩盖接口契约问题。跨前后端数据形状变化时，应先修 DTO 和调用类型。
- 修改页面行为后，优先用真实页面或现有脚本验证用户路径，而不是只检查静态代码。

## 静态刷新与 SSE

- Controller 写入会影响后台或博客静态页面的数据时，使用 `@RefreshCache` 声明需要更新的 `StaticSiteType`，不要在业务 Controller 中重复实现 SSE emitter。
- `@RefreshCache` 的 SSE 分支必须使用可传播异常的同步缓存刷新入口：刷新失败发送 `static-error`，且不得继续发送 `refresh-complete`。普通 JSON 分支保留既有的容错与异步语义。
- 与 `@RefreshCache` 写接口配对的前端请求统一使用 `postRefreshCacheSse`。只读查询、预览和仅生成 options/challenge 的请求继续使用普通 JSON 请求。
- `response` 事件只表示业务方法已经返回，后续静态生成仍可能进行。已经完成数据库写入的 CRUD 操作应保持默认行为：收到 `response` 后更新业务界面，同时在后台继续消费静态进度与错误，不能把后续静态失败误报成业务写入失败。
- 对静态资源是否完成敏感的调用应设置 `requiredCompletionEvent: "refresh-complete"`，用于识别业务响应后、资源完成事件前的异常断流；断流只影响资源刷新阶段的提示。
- `waitForComplete: true` 会等待整个 SSE 流结束，仅用于“流内后续步骤本身就是当前业务的一部分”的操作；不要用它补偿已经提交的数据库事务与静态资源刷新之间的非原子性。
- SSE 调用仍需检查 `ApiStandardResponse.error`。不支持 SSE 或服务端返回 JSON 时由公共 helper 降级，页面不应另写一套请求分支。
- multipart 等公共 helper 尚未支持的请求不得用临时 JSON 或手写流协议绕过；先扩展并验证公共 helper，再迁移调用点。

## AI 保存通知与编辑器版本

AI 工具保存文章后，`article-updated` 携带 `articleId`、已保存的 `version` 和可选的 `created`。通知只触发读取与同步，不再次提交文章，也不直接应用模型返回的正文。状态归属如下：

| 状态 | 归属与语义 |
| --- | --- |
| 工具写入结果 | 服务端 AI run 记录 `articleUpdates`、工具结果和 `nextTool`。这是保存结果与恢复执行的依据；执行结果不确定时沿用 `uncertain`，不得因为前端读取失败重做写入。 |
| 最新通知版本 | save coordinator 的 `latestArticleUpdateRef` 保存当前文章最高的刷新目标。收到通知不代表本地已接收内容；相同版本的在途读取合并，较低通知忽略。 |
| 已接收服务端版本 | save coordinator 的 `versionRef` 保存已接受的文章版本，也是后续保存的并发校验版本。同一文章内单调递增；成功接受版本 V 后，V 及更早的通知不再应用。 |
| 本地未保存编辑 | draft-sync 的 `revision` 和 dirty/conflict 状态仅跟踪本地修改，不等同于服务端 `version`。有冲突时编辑基线与本地副本保留，不能用“AI 已保存”清空。 |

执行边界：

- 通知的文章 ID 必须为正整数，版本必须为非负整数；创建后的初始版本 `0` 有效。校验后才允许给未保存草稿绑定新 ID。
- 拉取结果必须属于当前文章，版本不低于最新通知目标，且高于已经接收的服务端版本。响应落地时再次校验，避免读取期间的新通知或人工保存被旧响应覆盖；旧页面缓存和冲突读取也不能降低版本。
- 只有接受快照后才推进已接收版本。读取失败或数据尚未达到通知版本时保留刷新目标，不标为已应用；同一通知再次到达可重试读取。
- 无本地修改时接收服务端内容；本地编辑内容已与服务端一致时确认同步并移除本地草稿缓存；仍有差异时保留本地副本并进入现有合并流程。内容相同不能绕过版本校验。
- 按 `articleId + version` 确认同步，不给整条 AI 消息添加笼统的 `applied` 布尔值：一个 run 或消息可能产生多个文章版本。写作技能卡片的“应用到编辑器”仍受 `contextRevision` 校验，只产生本地编辑，不代表服务端保存完成。

同一轮写作技能的多项应用：

- 保留服务端 `skillContract.contextRevision` 作为生成时的原始内容指纹，用稳定的 `messageId`（`runId:skill:call:tool`）区分轮次，不按相同指纹合并不同 run。
- 编辑器宿主在当前会话记录同一 run 最近一次成功应用后的内容指纹，两个助手入口共享该记录。当前草稿仍匹配时，同轮其他卡片可继续应用；手动修改、其他 run 的应用或服务端内容变化不能借用这条记录。
- 每次点击读取 draft-sync 的即时草稿，并只在 patch 被接受后推进记录；重复应用相同内容不新增本地编辑或保存。仅服务端版本、ID 等同步元数据变化不使同轮结果过期；保存请求继续使用 coordinator 的单调版本校验。
- 记录仅用于当前编辑会话的应用校验，不是服务端保存确认；刷新页面后重新按原始指纹校验。封面接口只准备文件和更新候选结果 URL，上传完成后再次校验上下文及文章归属，再由统一入口应用一次。

## 验证要求

- 后端 Java 行为变更至少运行相关单测；影响共享服务或接口契约时运行 `mvn -q test`。
- 前端 TypeScript 或页面行为变更至少运行 `cd zrlog-admin-web-ui/src/main/frontend && yarn type-check`，必要时运行 `yarn build`。
- URL、安全、清洗、校验类改动必须补覆盖异常输入的测试，不只测正常路径。
- 提交前按范围运行：

```shell
scripts/check-admin-guardrails.sh
mvn -q -DskipTests compile
cd zrlog-admin-web-ui/src/main/frontend && yarn type-check
cd zrlog-admin-web-ui/src/main/frontend && yarn build
git diff --check
```
