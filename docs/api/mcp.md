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

文章操作日志与后台共用规则：创建成功记录 `CREATE_ARTICLE`；保存为公开文章（含重新发布）记录 `PUBLISH_ARTICLE`，直接创建并公开发布时同时记录两种事件。草稿和私密文章的普通更新不记录操作日志，权限拒绝和版本冲突也不记录成功事件。

操作日志将 `browser` 记为 `MCP`，并复用 `agentName`、`agentVersion` 保存本次请求 `User-Agent` 中的客户端名称和版本。例如 `User-Agent: codex/0.42.0` 会显示为 `MCP · codex 0.42.0`；`zrlogctl/<CLI版本> <agent名称>[/<agent版本>]` 则优先保留明确声明的 agent 名称和版本。未提供或无法识别时只标记 `MCP`；仅提供名称时版本留空。

这些字段是客户端声明的来源信息，不参与认证和权限判断。接口保持无会话，不跨请求保存 `initialize.clientInfo`，也不将 MCP 协议版本当作客户端版本。若 User-Agent 仅声明 HTTP 客户端库，则记录该库的名称和版本，不推测背后的模型或 agent。已有日志不会回填。

## 权限与兼容

`tools/list` 按当前账号与令牌权限过滤；调用时重新认证并校验，隐藏工具不代替服务端鉴权。普通个人令牌沿用账号 action；OAuth MCP 增加 `articles:write`、`articles:publish`、`assets:write`、`taxonomy:read`，读取和文章归属范围保持现有语义。发布及修改线上文章需要发布授权；编辑他人文章仍需 `articles:all` 且账号自身可访问。投稿者只能创建和编辑自己的草稿。

旧 OAuth 授权和旧 `zrmcp_` 令牌不会自动获得写权限；继承账号权限的通用个人令牌可使用账号已具备的工具。授权界面展示新增分类标签读取 scope；不新增删除、站点配置或任意执行工具。
