# 后台 API 文档

这里是 `zrlog-admin-web` 后台 API 的统一文档入口，供接口调用方使用。

## 文档来源

- [`zrlog-api/admin-web.yaml`](https://github.com/zrlog/zrlog-api/blob/main/admin-web.yaml)：统一维护的 OpenAPI 3.1
  契约。本文档中的 [`openapi.yaml`](openapi.yaml) 是生成快照，不独立修改。
- [`conventions.md`](conventions.md)：鉴权、context path、响应包装、错误和副作用等跨接口约定。
- [`oauth.md`](oauth.md)：外部应用授权协议、scope 和部署范围。
- [`article-publish-sse.md`](article-publish-sse.md)：文章发布进度、完成事件与断流处理。

人类可浏览的聚合页面由 `zrlog-www` 提供：`https://www.zrlog.com/docs/api?source=admin-web`。该页面读取本仓库契约，不拥有或改写接口定义。

`zrlogctl api` 可在运行时直接消费本文件，通过 `operationId` 查询和调用受支持接口，不生成 SDK。客户端随版本打包契约快照，也可通过
`--spec docs/api/openapi.yaml` 读取本地版本。发布 SSE 在响应的 `text/event-stream` 媒体类型上用
`x-zrlog-stream.completionEvent` 和 `errorEvents` 声明完成与失败事件，通用调用器据此判断流是否完整；扩展只描述已有协议，不改变服务端行为。

本文档只维护业务边界稳定、对自动化调用有独立价值且愿意承担兼容责任的关键接口，不是后台路由清单。未列出的后台路由仍可能存在，但属于
admin UI 内部实现，不承诺对第三方调用保持兼容。

当前覆盖后台业务操作：文章列表、详情、创建与更新，分类列表、创建与更新，导航增删改查，通用附件上传、主题上传、外部通知，以及
ZrLog 升级检查、版本通知和升级执行，另提供助手任务的外部触发。登录与刷新令牌仍按 [OAuth](oauth.md) 协议处理。博客前台公开 `/api` 文档由
`zrlog-blog-web` 独立维护。

ZrLog 升级接口要求 `system.manage`。`checkZrLogUpgrade` 返回在线升级能力和备份状态；`executeZrLogUpgrade` 必须提交
`upgradeRiskAccepted=true`，SSE 以 `response` 为最终处理结果、`sse-error` 为失败事件。断流和超时不得自动重试升级；
`data.finish=true` 后仍需另行验证重启后的版本和可用性。`zrlogctl upgrade check` / `upgrade apply --accept-risk` 复用此契约。

助手的外部定时调用使用 `POST /api/admin/assistant/trigger`（`triggerAssistantTasks`），只接收执行设置生成的专用 Bearer 令牌，
不接受管理员登录令牌，也不接受任务参数或执行授权。预算在后台执行设置中配置，默认 60 秒；FaaS 需要外部定时调用。
`executed` 包含失败和待处理结果，应同时检查 `failed`、`needsAttention` 和 `budgetExhausted`，再到助手执行历史中查看详情。
断流或超时不代表任务未执行；结果待确认的任务需要检查后再启用。插件触发只保存一条助手父任务，本次插件执行按子任务展示。

文章和分类读取沿用现有页面数据响应，只承诺契约中声明的核心字段；附带的编辑器配置、AI 上下文、列设置和页面元数据不纳入稳定接口。文章列表省略
`status`、`size`、`sort` 时使用账号偏好；遍历全部可见文章需显式传空 `status`、固定分页大小和排序。列表不提供更新版本，更新前使用
`getArticle` 读取完整快照。

## 查找接口

先在 `openapi.yaml` 中按路径或 `operationId` 搜索，再沿 `x-zrlog-controller` 定位实现：

```shell
rg -n '/api/admin/template/upload|operationId: uploadTemplate' docs/api/openapi.yaml
```

`x-zrlog-response-kind` 用于说明响应边界：

- `page-hydration`：后台页面初始化数据，使用 `AdminPageDataResponse<T>`。
- `action`：普通操作、轮询和常规数据接口，使用 `ApiStandardResponse<T>` 或其专用子类。
- `stream`：SSE 或其他流式响应，必须单独记录事件协议。
- `download`：文件或导出响应，不使用 JSON 响应包装。

MCP 使用独立的[内容工具协议](mcp.md)，不从后台 OpenAPI 自动生成工具。
