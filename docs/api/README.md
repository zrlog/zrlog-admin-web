# 后台 API 文档

这里是 `zrlog-admin-web` 后台 API 的统一文档入口，供开发者、调用方和 AI Agent 共同使用。

## 文档来源

- [`openapi.yaml`](openapi.yaml)：机器可读的 OpenAPI 3.1 契约，是路径、方法、参数和响应结构的文档来源。
- [`conventions.md`](conventions.md)：鉴权、context path、响应包装、错误和副作用等跨接口约定。
- Controller、DTO 和测试：接口实现来源。文档与实现不一致时，应在同一改动中修正，不保留两套语义。

人类可浏览的聚合页面由 `zrlog-www` 提供：`https://www.zrlog.com/docs/api?source=admin-web`。该页面读取本仓库契约，不拥有或改写接口定义。

当前采用渐进式覆盖，首个完整样板是 `POST /api/admin/template/upload`。遗留接口可以逐步补齐，但新增或修改接口时必须同步维护 OpenAPI 契约。

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

## 维护接口

新增或修改 API 时同时完成以下事项：

1. 在 `AdminRouters` 和 Controller 中确认真实路径与方法；变更接口必须显式声明 HTTP 方法。
2. 使用 typed request/response DTO，不用临时 `Map` 代替稳定协议。
3. 在 `openapi.yaml` 中维护参数、请求体、响应 Schema、鉴权、失败语义和示例。
4. 记录写库、覆盖文件、缓存刷新、静态站更新和审计等副作用。
5. 为每个操作填写稳定且唯一的 `operationId`、`x-zrlog-controller` 和 `x-zrlog-response-kind`。
6. DTO 有变化时同步维护 Native Image 注册和协议测试。
7. 运行文档校验和相关代码测试。

```shell
cd src/main/frontend
yarn api-docs:check
```

OpenAPI 稳定并达到足够覆盖率之前，不从契约自动生成客户端，也不提供运行时 Swagger UI 或 MCP 服务。
