# MCP 内容工具扩展契约

## 范围与数据归属

在现有 `/mcp` 上提供 9 个工具：`search_articles`、`read_article`、`get_article`、`list_categories`、`list_tags`、`create_article`、`update_article`、`publish_article`、`upload_attachment`。名称仍使用站点标题；服务描述介绍 MCP 能力并随令牌所属账号语言翻译，不复用站点 SEO 描述。2025-11-25 返回 `serverInfo.description`，旧版本通过 `instructions` 提供说明。

access 负责 MCP 协议、参数和授权适配；content 负责文章写入和分类标签读取，复用文章权限、版本、审计与发布服务；assets 通过启用模块提供附件存储。内置 AI 助手继续使用原有两个读取工具。没有数据库迁移，不修改既有 HTTP API 的字段或行为。

## 参数与副作用

- `get_article(id)`：返回可编辑元数据、当前状态与版本；正文通过 `read_article` 分页读取。
- `list_categories` / `list_tags`：按 ID 分页，`offset` 默认 0，`limit` 默认 50、上限 100；不返回全站文章统计。
- `create_article(title, typeId, status, markdown?, content?, ...)`：明确选择 `draft`、`private` 或 `published`；沿用文章字段 `alias`、`digest`、`keywords`、`thumbnail`、`canComment`、`recommended`、`editorType`。可直接发布，不增加人工审批门槛。默认保留后台编辑器的 AI 上下文。
- `update_article(id, version, ...)`：只更新提供的字段，省略字段保持原值；版本必须与当前版本一致，冲突后重新读取再决定修改。仅提供 Markdown 时重新生成 HTML；仅提供 HTML 时清空旧 Markdown 并切换编辑格式，未提供正文时保持现有正文。运行环境不含 Markdown 渲染器时，需同时提供对应的 HTML。
- `publish_article(id, version)`：保留内容并改为公开发布，独立检查文章更新与发布权限。
- `upload_attachment(filename, data)`：`data` 为标准 Base64，单个附件最多 4 MiB；只接收客户端提供的文件字节，不读取服务端路径或抓取 URL。复用账号上传目录与上传插件，返回 URL。assets 禁用时不提供该工具。

工具业务错误返回 `isError=true`，包含本地化 `error`；权限和版本冲突等后台业务错误同时包含稳定的 `code`。

文章写入结果包含 ID、版本、状态和静态刷新状态。数据库写入成功而刷新失败时仍返回保存结果及警告，避免客户端误以为文章未保存而重复创建。创建不保证幂等，客户端不应在响应丢失后盲目重试。

文章创建、更新与发布的操作日志从本次请求的 `User-Agent` 记录浏览器或客户端名称及版本，支持 `客户端名称/版本` 格式。未提供或无法识别时记录为 `MCP`；未提供版本时不推测版本。接口保持无会话，不跨请求保存 `initialize.clientInfo`，也不将 MCP 协议版本当作客户端版本。已有日志不会回填。

## 权限与兼容

`tools/list` 按当前账号与令牌权限过滤；调用时重新认证并校验，隐藏工具不代替服务端鉴权。普通个人令牌沿用账号 action；OAuth MCP 增加 `articles:write`、`articles:publish`、`assets:write`、`taxonomy:read`，读取和文章归属范围保持现有语义。发布及修改线上文章需要发布授权；编辑他人文章仍需 `articles:all` 且账号自身可访问。投稿者只能创建和编辑自己的草稿。

旧 OAuth 授权和旧 `zrmcp_` 令牌不会自动获得写权限；继承账号权限的通用个人令牌可使用账号已具备的工具。授权界面展示新增分类标签读取 scope；不新增删除、站点配置或任意执行工具。

## 验证切片

1. 协议版本、服务描述与所有工具/参数的中英文说明；Native DTO 注册。
2. OAuth 授权、个人令牌、旧只读令牌、角色与文章范围、撤销、账号变化、模块禁用。
3. 真实数据库创建/部分更新/发布、版本冲突、审计与刷新失败；H2 与 SQLite。
4. 附件字节、大小、Base64、文件名、账号目录和 context path；前端授权说明与类型检查。
