# 后台 API 约定

## 路径与 Context Path

OpenAPI `paths` 只记录应用内路径，例如 `/api/admin/template/upload`，不包含部署时的 context path。调用方应在基础地址中统一添加 context path；本地开发后端默认基础地址为 `http://localhost:17080/sub`。

后台页面使用 `/admin`，JSON API 使用 `/api/admin`。公开 API、Webhook 和插件接口应使用各自的安全边界，不默认继承后台 API 约定。

## HTTP 方法与参数

- 新增或修改接口必须显式声明 HTTP 方法，不能依赖框架默认值。
- 查询条件使用 query 参数；结构化写入使用 JSON request body；文件上传使用 `multipart/form-data`。
- 修改、删除、上传和触发任务等有副作用的操作使用 `POST`，除非已有兼容契约要求其他方法。
- 参数来源必须在 OpenAPI 中明确，不能只依赖前端调用示例推断。

## 鉴权

后台 API 支持以下任一管理员凭证，Header 的优先级高于 Cookie：

- Header：`X-ZrLog-Admin-Token`
- Cookie：`admin-token`

凭证值属于敏感信息，文档、日志、测试输出和 AI 上下文都不得包含真实值。

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

后台路由初始化数据使用 `AdminPageDataResponse<T>`。它会附带 `pageBuildId`、`documentTitle`、`systemNotification` 和 `messageCenter` 等页面元数据，不应用于普通动作、轮询、公开 API 或 SSE 事件。

## 错误与兼容

- 参数缺失、鉴权失败和未处理异常由统一错误处理转换为 JSON 错误响应。
- 新错误应优先使用稳定错误码；兼容旧接口时至少保持 `error` 和 `message` 语义。
- 不删除或改名已有 JSON 字段，除非已确认全部消费方和迁移策略。
- 新增可选字段通常是兼容变更；修改类型、必填性或枚举范围属于协议变更。

## 副作用

OpenAPI 操作必须在描述或 `x-zrlog-side-effects` 中记录调用方无法从 Schema 看出的行为，包括：

- 数据库写入与审计记录
- 文件创建、替换或删除
- 缓存刷新和静态站更新
- 异步任务、锁和并发限制
- 幂等性、覆盖行为和回滚边界

涉及破坏性操作时，接口文档必须说明确认参数及失败后的数据状态。
