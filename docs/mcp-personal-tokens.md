# 个人 MCP 访问令牌

## 契约与数据归属

个人设置左侧「外部应用」分组使用独立页面：`/admin/user/applications/tokens` 管理个人访问令牌，`/admin/user/applications/grants` 管理我的 OAuth 授权，`/admin/user/applications/clients` 管理站点应用。移除账号菜单中的独立外部应用入口，授权确认页面使用 `/admin/user/applications/authorize`。管理员额外可见的站点应用明确标注为站点范围，登记不授予个人数据访问。后台登录账号创建和撤销自己的令牌，不接受调用方指定所属账号。令牌只用于本站 `/mcp` 的只读知识库，不可用于 `/api/oauth/me`、后台会话或刷新令牌接口；原 OAuth 授权、PKCE 和刷新流程保持不变。

新增 schema 29 的 `user_access_token` 表，保存独立管理 ID、userId、名称、SHA-256 tokenHash、scope、MCP resource、账号 authVersion、创建/到期时间、撤销状态。避免把凭据放入全局配置、user.preferences 或伪装成 OAuth 客户端。明文格式 `zrmcp_` + 256 位随机值，只在创建响应返回一次；列表、SSR、审计均不包含明文或摘要。

权限继续使用 AccountAccess 与 KnowledgeService。每次 MCP 请求和工具访问都验证令牌有效期、撤销状态、resource、账号启用状态及 authVersion，再取账号当前 scope 与令牌 scope 交集。本人公开读取默认勾选；草稿、私密和账号允许的全站范围单独选择。仅允许 read/read_drafts/read_private/all，不接受写权限或 offline_access。

内部 UI 接口：
- `POST /api/admin/oauth/createPersonalToken`：`{name, scopes, expiresInDays}`，名称 1–128 字符，有效期仅 7/30/90 天，默认 30 天；返回 `{token, info}`。
- `POST /api/admin/oauth/revokePersonalToken`：`{id}`，只撤销当前账号所属令牌，重复/不存在返回相同成功结果。
- `GET /api/admin/oauth` 和页面初始化：增加 personalTokens 元数据列表、personalTokenScopes 可选权限、当前账号标识；不得返回 token/hash。

以上内部接口沿用 OAUTH_GRANT_MANAGE action 和同源校验；POST 创建响应禁止缓存。创建/撤销记录安全审计，不记录 token。浏览器明文只保留在创建结果抽屉内，关闭或离页清理；提供 MCP 地址和令牌复制说明。长期 OAuth 客户端继续优先用 OAuth，手动令牌不支持自动刷新。

## 验证切片

1. H2/SQLite 迁移与新安装，保留账号和已有 OAuth 授权。
2. 按账号列举/撤销、不可扩权、私密/草稿范围、跨资源拒绝、过期/撤销/停用/认证版本失效、数据库不存明文。
3. 中英文 UI、默认权限、创建仅一次显示、关闭后清理、错误状态、范围和期限提交。
4. 实际 HTTP 创建后调用 MCP、撤销后拒绝，以及已有 OAuth 连接回归；类型检查、构建、native/i18n 护栏。

## 验证结果

- base 449 项、install 175 项（4 项环境跳过）、后台 526 项、前端 41 组 / 270 项通过。安装后再跑迁移专项 28 项，H2/SQLite 均通过。后台排除会重置已有预览数据的 MemoryApplicationTest。
- TypeScript、ESLint、生产构建、native JSON 注册和中英文接口描述护栏、diff 检查通过。
- 真实 HTTP 覆盖个人令牌创建、只读与私密/草稿范围、列表/SSR 不返回令牌、跨资源拒绝、账户隔离、撤销、角色变更失效、GET/跨 Origin 请求拒绝；原 OAuth PKCE、MCP scope、受众和撤销回归通过。
- 浏览器连接返回 nodeRepl.fetch request failed，未完成真实浏览器视觉验收；组件测试覆盖创建、一次显示、关闭清理、失败保留、撤销、个人页加载、离线。
- 独立预览端口 18086，已有预览服务及数据保留。未提交代码，未操作用户真实数据库。部署时按既有升级流程执行 schema 29。
