# 外部应用授权协议

OAuth 支持只读 MCP 和按账号权限访问后台 API。Java 客户端优先通过浏览器完成授权，个人令牌适用于手动配置或脚本接入。

## Java 客户端浏览器登录

```sh
zrlogctl login --site https://blog.example/sub
zrlogctl --site https://blog.example/sub article list
zrlogctl logout --site https://blog.example/sub
```

服务按需登记固定 public client `zrlogctl`，无需手动添加站点应用。客户端打开系统浏览器，通过 S256 PKCE、state 和 issuer 校验完成授权码交换；只允许 `http://127.0.0.1:{port}/oauth/callback`，该内置客户端的端口可变，其他客户端仍精确匹配回调。禁用内置客户端后不会自动重新启用。

新 resource 为 `{issuer}/api/admin`，scope 使用现有 AccountAction ID（例如 `article.read taxonomy.read article.create article.publish`），或单独使用 `account:inherit` 显式继承账号当前权限；两者都可附加 `offline_access`。
请求继承权限时，用户可以在浏览器改为指定权限；受限请求不能选择继承或额外权限。所有后台接口继续以自身的 `@RequiresAction`、条件权限和数据归属检查为准，不新增一套 API 权限清单。该 resource 也可向通知接口发送具有 `notification.create` 权限的请求。

MCP 仍使用自己的 resource 和读取 scope，不因后台授权增加工具或扩大旧令牌的权限。新个人令牌的 MCP 读取范围遵守账号权限；已有 MCP 个人令牌保留原 scope 限制。

## 部署与客户端

OAuth 与 MCP 优先使用“设置 → 管理设置 → 后端服务地址”，保存到 `website.backend_server_url`，修改后立即生效，无需设置环境变量或重启。该配置属于整个站点的服务地址，管理设置仅提供编辑入口。
填写实际提供 API 的对外入口，例如 `https://xiaochun-admin.zrlog.com`，博客域名保持 `xiaochun.zrlog.com`。完整后端地址可包含 context path，不会重复追加；仅配置 origin 时追加服务 context path。
该字段通过已有 `SITE_CONFIGURE` 权限接口读取和修改，不加入 `PublicWebSiteInfo`、博客模板数据、未登录资源或静态后台页面；静态生成调用设置 API 时也不返回此字段。OAuth/MCP 发现协议仍需提供客户端可访问的 issuer/resource，因此应填写代理入口，而非内部源站地址。
未填写时，依次兼容 `ZRLOG_BACKEND_URL`、`DEFAULT_BACKEND_SERVER_URL`，最后回退到博客 Host 与服务 context path；清空字段恢复此行为，旧客户端省略字段不会清除已保存配置。不使用请求 Host、浏览器本地 `backendServerUrl` 或客户端传来的 resource 推导 issuer。
更改 issuer 后，原地址上的 OAuth 授权和个人令牌需重新建立。校验只接受 HTTPS（本机调试允许 HTTP），拒绝凭据、查询、fragment、无效端口、路径穿越和编码路径分隔符。
生产地址使用 HTTPS；localhost、127.0.0.1 和 IPv6 回环允许 HTTP。
例如部署在 `https://blog.example/sub`：

| 用途 | 地址与方法 |
| --- | --- |
| 授权服务发现 | `GET https://blog.example/.well-known/oauth-authorization-server/sub` |
| 资源发现 | `GET https://blog.example/.well-known/oauth-protected-resource/sub/api/oauth` |
| MCP 资源发现 | `GET https://blog.example/.well-known/oauth-protected-resource/sub/mcp` |
| MCP 工具调用 | `POST https://blog.example/sub/mcp` |
| 用户授权 | `GET https://blog.example/sub/oauth/authorize` |
| 换取或刷新令牌 | `POST https://blog.example/sub/oauth/token` |
| 撤销令牌 | `POST https://blog.example/sub/oauth/revoke` |
| 验证身份与当前 scope | `GET https://blog.example/sub/api/oauth/me` |

除内置 zrlogctl 外，使用管理员预登记的 public client，认证方式为 `none`，无 client secret。
每个应用最多登记 10 个精确回调地址，不支持通配符、fragment、动态注册和客户端元数据文档。
支持 MySQL、H2、SQLite 和 D1/Web API；凭证消费使用事务或带条件的原子更新。

## 授权与令牌

1. 应用生成随机 `state` 与 43–128 字符的 PKCE `code_verifier`，计算 S256 challenge。
2. 授权地址传入 `response_type=code`、`client_id`、精确的 `redirect_uri`、空格分隔 `scope`、
   `code_challenge_method=S256`、`code_challenge`、`resource` 和 `state`。
   `resource` 必须为本服务的 `{issuer}/api/admin`、`{issuer}/api/oauth` 或只读知识库 `{issuer}/mcp`，令牌不能跨资源使用。
3. 用户登录并选择授权范围。连接默认仅本人、默认只勾选读取公开文章；草稿、私密、全站、写入、发布、删除和长期连接需明确选择。
   授权页请求绑定当前账号、会话和一次性 CSRF 值，10 分钟后失效。
4. 服务仅向已登记回调返回 `code`（有效期 2 分钟）、原始 `state` 与 `iss`。
   用户拒绝时返回 `error=access_denied`、`state` 与 `iss`。
   应用必须验证 state 和 issuer。
5. token endpoint 接受 `application/x-www-form-urlencoded`，不得把 token 参数放在 URL 中。
   请求字段为 `grant_type=authorization_code`、`client_id`、`code`、`redirect_uri`、`code_verifier`、`resource`。
   返回 `access_token`、`token_type=Bearer`、`expires_in=600`、实际 `scope`。
   授予 `offline_access` 时同时返回有效期 30 天的 `refresh_token`。
6. 刷新使用 `grant_type=refresh_token`、`client_id`、`refresh_token`、`resource`，可用 `scope` 缩小本次 access token 的范围。
   refresh token 每次轮换，旧令牌不能再用。客户端应串行刷新并妥善保存新值。

授权码重放或 refresh token 重放会撤销整个授权。并发使用同一凭证最多成功消费一次，
重放后相关 access token 也失效。凭证在数据库中仅保存 SHA-256 摘要。
access token 不允许出现在 query 或 cookie；`/api/oauth/me` 要求 `Authorization: Bearer ...` 和 `articles:read`。

## Scope 与数据范围

| Scope | 含义 |
| --- | --- |
| `articles:read` | 读取公开文章 |
| `articles:read_drafts` | 在 read 基础上额外读取草稿 |
| `articles:read_private` | 在 read 基础上额外读取私密文章 |
| `articles:all` | 从本人范围扩大为该账号允许的全站范围 |
| `articles:write` | 创建或编辑文章；不包含发布能力 |
| `articles:publish` | 发布、撤回或修改线上文章；写入仍需 write |
| `articles:delete` | 删除范围内文章 |
| `assets:write` | 上传素材 |
| `offline_access` | 允许刷新令牌 |

最终权限 = 账号当前固定角色允许的 Action ∩ 连接授权的 scope ∩ 资源归属与可见性。
`articles:all` 不能让作者看到其他作者的后台文章；`read_private` 不能让编辑看到他人的私密文章。
管理员必须同时授权 all、read、read_private 才能通过 MCP 读取他人的私密文章；草稿还需 read_drafts。

管理员角色变更、停用账号、改密、重置密码和所有权转移会使旧会话/授权失效。
普通用户只能查看和撤销自己的授权。管理员可以停用应用并撤销该应用的全部授权。

个人设置左侧“外部应用”包含独立的个人令牌、我的授权、站点应用页面。个人令牌使用 7/30/90 天有效期，明文只显示一次，不支持刷新。创建请求显式传入 `permissionMode=custom` 与 AccountAction ID 列表，或 `permissionMode=inherit`；旧请求未传模式时仍创建 MCP-only 令牌。详见 [统一授权契约](../general-personal-tokens.md)。
撤销 endpoint 接受 form 字段 `token`、`client_id`；未知 token 返回 200 `{}`，不泄露凭证是否存在。

错误使用标准 HTTP 状态与 `{ "error": "invalid_grant" }` 一类 JSON，和后台业务 envelope 区分。
Bearer 验证失败返回 401；scope 不足返回 403；响应包含资源元数据发现地址。
响应禁止缓存，授权页禁止 iframe 嵌入，不记录明文凭证。

实现依据：[RFC 6749](https://www.rfc-editor.org/rfc/rfc6749)、[PKCE](https://www.rfc-editor.org/rfc/rfc7636)、
[撤销](https://www.rfc-editor.org/rfc/rfc7009)、[资源指示符](https://www.rfc-editor.org/rfc/rfc8707)、
[服务发现](https://www.rfc-editor.org/rfc/rfc8414)、[issuer 响应](https://www.rfc-editor.org/rfc/rfc9207)、
[资源元数据](https://www.rfc-editor.org/rfc/rfc9728)。这是明确限定的协议实现，不宣称认证或完整覆盖所有 OAuth 扩展。

MCP 资源只接受读取相关 scope 与 offline_access，写 scope 会返回 invalid_scope。工具与客户端配置见 [知识库契约](../mcp-knowledge-base.md)。

连接 ChatGPT 等 MCP 客户端时填写完整的 `{后端地址}/mcp`，授权与 token 请求的 resource 也使用这个地址。客户端应从 MCP 资源发现文档读取可选 scope；授权服务器的通用 scopes_supported 同时包含其他资源支持的写操作，不能全部用于 MCP。错误 `invalid_target` 表示 resource 与服务配置不匹配，`invalid_scope` 表示请求了该资源不支持的权限。

反向代理需要保留未认证响应的 `401`、JSON body 和 `WWW-Authenticate`。如果 AWS 网关把后者改名为 `x-amzn-remapped-www-authenticate`，应在对外代理恢复为标准 `WWW-Authenticate`，否则客户端可能无法发现 MCP 资源元数据；仅暴露 `Access-Control-Expose-Headers` 不能恢复被改名的响应头。
