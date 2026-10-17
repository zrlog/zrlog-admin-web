# MCP 内容工具

站点通过 `/mcp` 提供 OAuth 保护的 Streamable HTTP 接口。连接和授权方式见[外部应用授权](oauth.md)。个人令牌作为 Bearer 凭据使用，权限受所属账号和令牌范围限制。

可用工具包括 `search_articles`、`read_article`、`get_article`、`list_categories`、`list_tags`、`create_article`、`update_article`、`publish_article` 和 `upload_attachment`。通过 `tools/list` 获取当前授权下的工具、参数 Schema 和本地化说明；不提供删除、站点配置或任意执行工具。

## 参数与副作用

- `get_article(id)`：返回可编辑元数据、当前状态与版本；正文通过 `read_article` 分页读取。
- `list_categories` / `list_tags`：按 ID 分页，`offset` 默认 0，`limit` 默认 50、上限 100；不返回全站文章统计。
- `create_article(title, typeId, status, markdown?, content?, ...)`：明确选择 `draft`、`private` 或 `published`；沿用文章字段 `alias`、`digest`、`keywords`、`thumbnail`、`canComment`、`recommended`、`editorType`。外部 MCP 可直接调用发布，确认策略由客户端管理。默认保留后台编辑器的 AI 上下文。
- `update_article(id, version, ...)`：只更新提供的字段，省略字段保持原值；版本必须与当前版本一致，冲突后重新读取再决定修改。仅提供 Markdown 时重新生成 HTML；仅提供 HTML 时清空旧 Markdown 并切换编辑格式，未提供正文时保持现有正文。运行环境不含 Markdown 渲染器时，需同时提供对应的 HTML。
- `publish_article(id, version)`：保留内容并改为公开发布，独立检查文章更新与发布权限。
- `upload_attachment(filename, data)`：`data` 为标准 Base64，单个附件最多 4 MiB；只接收客户端提供的文件字节，不读取服务端路径或抓取 URL。复用账号上传目录与上传插件，返回 URL。assets 禁用时不提供该工具。

工具业务错误返回 `isError=true`，包含本地化 `error`；权限和版本冲突等后台业务错误同时包含稳定的 `code`。

文章写入结果包含 ID、版本、状态和静态刷新状态。数据库写入成功而刷新失败时仍返回保存结果及警告，避免客户端误以为文章未保存而重复创建。创建不保证幂等，客户端不应在响应丢失后盲目重试。

文章创建、更新与发布的操作日志从本次请求的 `User-Agent` 记录浏览器或客户端名称及版本，支持 `客户端名称/版本` 格式。未提供或无法识别时记录为 `MCP`；未提供版本时不推测版本。接口保持无会话，不跨请求保存 `initialize.clientInfo`，也不将 MCP 协议版本当作客户端版本。已有日志不会回填。

## 权限与兼容

`tools/list` 按当前账号与令牌权限过滤；调用时重新认证并校验，隐藏工具不代替服务端鉴权。普通个人令牌沿用账号 action；OAuth MCP 增加 `articles:write`、`articles:publish`、`assets:write`、`taxonomy:read`，读取和文章归属范围保持现有语义。发布及修改线上文章需要发布授权；编辑他人文章仍需 `articles:all` 且账号自身可访问。投稿者只能创建和编辑自己的草稿。

旧 OAuth 授权和旧 `zrmcp_` 令牌不会自动获得写权限；继承账号权限的通用个人令牌可使用账号已具备的工具。授权界面展示新增分类标签读取 scope；不新增删除、站点配置或任意执行工具。
