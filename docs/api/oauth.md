# 外部应用授权协议

此版本提供 OAuth 授权与只读 MCP 知识库，不提供外部文章写入工具。
`/api/oauth/me` 和只读知识库 `/mcp` 接受 OAuth Bearer；现有 `/api/admin/*` 仍使用后台会话。
写入相关 scope 是后续资源适配器的权限契约，不能凭已获取令牌绕过后台会话访问文章接口。

## 部署与客户端

授权服务使用配置的博客 Host 与服务 context path，不使用请求的 Host 推导 issuer。
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

首版使用管理员预登记的 public client，认证方式为 `none`，无 client secret。
每个应用最多登记 10 个精确回调地址，不支持通配符、fragment、动态注册和客户端元数据文档。
授权数据写入需要 JDBC 事务；目前支持 MySQL、H2、SQLite。D1/Web API 适配器不具备事务接口，
相关管理操作明确拒绝；OAuth 标准端点返回 503 `temporarily_unavailable`，不会降级为非原子令牌消费。

## 授权与令牌

1. 应用生成随机 `state` 与 43–128 字符的 PKCE `code_verifier`，计算 S256 challenge。
2. 授权地址传入 `response_type=code`、`client_id`、精确的 `redirect_uri`、空格分隔 `scope`、
   `code_challenge_method=S256`、`code_challenge`、`resource` 和 `state`。
   `resource` 必须为本服务的 `{issuer}/api/oauth` 或只读知识库 `{issuer}/mcp`，令牌不能跨资源使用。
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

个人资料的「外部应用」页签还支持创建仅供 MCP 使用的个人访问令牌，适用于手动填写 Bearer Token 的客户端。创建时选择名称、7/30/90 天有效期和只读范围（默认本人公开文章）；完整令牌只返回一次，数据库只保存摘要。令牌绑定创建账号和认证版本，账号停用或认证版本变更后立即失效，允许单独撤销。它不使用 OAuth 授权码或刷新流程，不能用于 `/api/oauth/me` 或后台 API。标准 OAuth 客户端继续使用上面的授权流程。内部接口与验证见 [个人令牌契约](../mcp-personal-tokens.md)。
撤销 endpoint 接受 form 字段 `token`、`client_id`；未知 token 返回 200 `{}`，不泄露凭证是否存在。

错误使用标准 HTTP 状态与 `{ "error": "invalid_grant" }` 一类 JSON，和后台业务 envelope 区分。
Bearer 验证失败返回 401；scope 不足返回 403；响应包含资源元数据发现地址。
响应禁止缓存，授权页禁止 iframe 嵌入，不记录明文凭证。

实现依据：[RFC 6749](https://www.rfc-editor.org/rfc/rfc6749)、[PKCE](https://www.rfc-editor.org/rfc/rfc7636)、
[撤销](https://www.rfc-editor.org/rfc/rfc7009)、[资源指示符](https://www.rfc-editor.org/rfc/rfc8707)、
[服务发现](https://www.rfc-editor.org/rfc/rfc8414)、[issuer 响应](https://www.rfc-editor.org/rfc/rfc9207)、
[资源元数据](https://www.rfc-editor.org/rfc/rfc9728)。这是明确限定的协议实现，不宣称认证或完整覆盖所有 OAuth 扩展。

MCP 资源只接受读取相关 scope 与 offline_access，写 scope 会返回 invalid_scope。工具与客户端配置见 [知识库契约](../mcp-knowledge-base.md)。
