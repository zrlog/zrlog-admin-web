# 文章发布 SSE

`POST /api/admin/article/create` 与 `POST /api/admin/article/update` 支持流式公开发布。请求体仍是原有 JSON，在 `rubbish=false`、`privacy=false` 时设置 `transparentPublish=true`，并发送 `Accept: text/event-stream, application/json`。权限、文章归属与版本校验沿用普通保存接口，OAuth/PAT 仍通过 `Authorization: Bearer` 传入。

服务端通过请求体决定是否使用 SSE；草稿、私密文章和 `transparentPublish=false` 返回普通 JSON。调用方按实际响应 `Content-Type` 解析，鉴权或请求校验失败也可能返回 JSON 错误。旧版本返回 JSON 时，只能确认保存结果，不能据此确认静态同步完成。

SSE 响应为 `text/event-stream;charset=UTF-8`，每个事件由 `event:` 和 JSON `data:` 行组成，以空行结束。客户端应支持分块 UTF-8、LF/CRLF、多行 data 和注释心跳，并忽略未来新增的未知事件。

| 事件 | 数据与含义 |
| --- | --- |
| `publish-start` | `{ "message": "..." }`，保存已完成，开始后续发布流程 |
| `article` | `ArticleSaveResponse`，完整保存响应，文章位于 `data.article`；仍需检查 `error` |
| `static-sync-start`、`static-progress`、`static-sync-complete` | 同步快照：`total`、`handled`、`handing`、`pending`、`retrying`、`siteTypes` |
| `static-sync-skipped` | 同步快照，静态站插件未启用 |
| `publish-check-start`、`publish-check-complete` | 可选的发布检查进度，完成数据可包含检查结果及 AI 消息 |
| `publish-check-error` | `{ "message": "..." }`，发布检查失败提示，允许继续到发布完成 |
| `publish-complete` | `{ "message": "..." }`，发布流程的成功终止事件 |
| `publish-error`、`static-error`、`sse-error` | `{ "message": "..." }`，发布流程失败；不得继续报告成功 |

成功路径先收到 `article`，再收到 `publish-complete`。`article`、`static-sync-complete` 和流关闭都不能单独作为发布完成依据。客户端收到完整的 `publish-complete` 事件后可以关闭连接，无需等待服务器关闭 HTTP 响应；需要内容校验时再回读文章。

文章写入与静态同步不是一个事务。收到 `article` 后再发生同步错误或断流，文章已保存；在 `article` 之前断流也不能断定没有写入。客户端必须设置包含整个流读取过程的超时，出现超时、断流或错误时先检查远端文章状态，不能自动重连并重放创建/更新请求。SSE 的 `retry` 或 `id` 字段不构成写入请求的重试或续传契约。

`zrlogctl` 对公开发布和已发布文章的修订使用此协议，收到完成事件后继续校验内容与版本。进度写入 stderr，最终结果写入 stdout；JSON 输出模式将进度编码为每行一个 `{ "event": "...", "data": ... }` 对象。
