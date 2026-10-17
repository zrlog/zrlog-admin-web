# 后台 API 约定

本约定适用于 `openapi.yaml` 中记录的受支持后台接口。未记录的 `/api/admin` 路由属于后台 UI 内部实现，不应仅根据 Controller 或前端调用推断为稳定公共接口。

## 路径与 Context Path

OpenAPI `paths` 只记录应用内路径，例如 `/api/admin/template/upload`，不包含部署时的 context path。调用方应在基础地址中统一添加 context path；本地开发后端默认基础地址为 `http://localhost:17080/sub`。

后台页面使用 `/admin`，JSON API 使用 `/api/admin`。公开 API、Webhook 和插件接口应使用各自的安全边界，不默认继承后台 API 约定。

## HTTP 方法与参数

HTTP 方法、参数来源和副作用以 `openapi.yaml` 中各操作的定义为准。查询条件使用 query 参数，结构化写入使用 JSON request body，文件上传使用 `multipart/form-data`。

## 鉴权

后台 API 支持以下账号凭证，Bearer 优先；Bearer 无效时不回退到其他凭证：

- Header：`Authorization: Bearer <token>`，支持新个人访问令牌与 resource 为 `{issuer}/api/admin` 的 OAuth 令牌

- Header：`X-ZrLog-Admin-Token`
- Cookie：`admin-token`

凭证值属于敏感信息，文档、日志、测试输出和 AI 上下文都不得包含真实值。

角色、启用状态和认证版本来自数据库，停用、改密和角色调整使旧会话失效。

Bearer 通过同一个 Action 定义和接口声明授权；指定权限取与账号权限的交集，继承模式使用账号当前权限。
旧 MCP 令牌和其他 resource 的 OAuth 令牌不能调用后台 API。
协议和授权范围见 [OAuth](oauth.md)。

## JSON 响应

普通操作使用 `ApiStandardResponse<T>` 或其专用子类：

```json
{
  "error": 0,
  "message": "",
  "data": {}
}
```

- `error = 0` 表示业务成功，非零表示业务失败。
- `message` 是可展示的后端消息，但调用方不能依赖文案判断错误类型。
- `data` 的结构由具体接口定义；失败时可能不存在。
- 业务失败可能通过成功的 HTTP 响应返回，调用方必须同时检查 `error`。

后台路由初始化数据使用 `AdminPageDataResponse<T>`。它会附带 `pageBuildId`、`documentTitle`、`systemNotification` 和 `messageCenter` 等页面元数据，这些字段属于后台 UI 内部实现。

## 错误与兼容

- 参数缺失、鉴权失败和未处理异常由统一错误处理转换为 JSON 错误响应。
- 调用方通过 `error` 判断业务失败，不依赖本地化 `message` 匹配错误类型。
- 响应可能新增可选字段，调用方应允许并忽略未知字段。

## 副作用

操作描述或 `x-zrlog-side-effects` 说明调用方需要关注的行为，包括：

- 数据库写入与审计记录
- 文件创建、替换或删除
- 缓存刷新和静态站更新
- 异步任务、锁和并发限制
- 幂等性、覆盖行为和回滚边界

涉及破坏性操作时，调用方按契约传入确认参数，并检查失败后的数据状态。
