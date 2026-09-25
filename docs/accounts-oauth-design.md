# 多账号、权限与 OAuth 授权

## 范围和数据归属

一个站点支持多个账号。首版提供 owner、admin、editor、author、contributor 五种角色。
后台会话和 OAuth 均使用实时账号权限；外部应用的 scope 只能收窄权限。
文章 userId 保留作者归属，修改者记录在版本和审计中。停用账号保留其内容。
本次实现成员管理、统一授权、OAuth 授权码 + S256 PKCE、刷新和撤销；MCP 工具后续接入。

## 可验证切片

1. 数据库 27：账号角色、启用状态、认证版本，以及应用、授权请求、授权和令牌持久化。
   既有最早账号迁移为 owner，其余既有管理员为 admin；新建账号必须显式选择角色。
2. 后台权限：默认拒绝未授权能力，文章列表/统计/详情/修改/版本按所有权过滤。
   站点设置、文件管理和插件管理属于管理员；编辑管理全站公开文章和草稿（其他作者的私密文章不可见），作者管理自己的内容，投稿者保存草稿。
3. 成员界面：列出、创建、调整角色、停用；owner 受保护，账号停用/密码重置撤销认证。
4. OAuth：管理员预注册 public client，精确登记回调；授权码 + PKCE；持久化单次授权码、
   短期 opaque access token、轮换 refresh token，重放撤销授权。凭证仅保存 SHA-256 摘要。
5. 授权界面：登录、展示请求权限、同意/拒绝、列出并撤销自己的授权。
6. 契约和验证：数据库迁移、跨账号拒绝、发布权限、停用即时失效、OAuth 重放/回调/
   PKCE/scope/resource/过期/撤销，以及前端类型检查、构建、桌面和移动端验证。

## 协议契约

- OAuth issuer 使用已配置的博客地址及 context path，拒绝不安全的远程 HTTP。
- `/.well-known/oauth-authorization-server`（子路径部署按 RFC 8414 发布 discovery）。
- `/oauth/authorize` GET：response_type=code、client_id、redirect_uri、scope、state、
  code_challenge、code_challenge_method=S256、resource。
- `/oauth/token` POST form：authorization_code / refresh_token；client_id 必填，公共客户端不持有 secret。
- `/oauth/revoke` POST form：撤销 token 所属授权；不存在的 token 同样返回成功。
- `/api/oauth/me` GET：Bearer 验证入口，返回账号身份、应用和当前有效 scope；
  同时发布受保护资源元数据。后续 MCP 注册独立 resource，不能复用其他受众令牌。
- `/api/admin/members/*`：成员管理。
- `/api/admin/oauth/*`：应用预注册、授权页数据、同意/拒绝以及自己的授权撤销。

Scope：articles:read、articles:read_drafts、articles:read_private、articles:all、articles:write、articles:publish、
articles:delete、assets:write。角色和文章归属仍由业务权限服务逐次检查。
`offline_access` 是刷新授权请求，不是数据访问权限。

## 兼容范围

现有后台登录和 API 响应结构保留。OAuth 标准端点使用标准 JSON/HTTP 错误，不使用后台响应包装。
首版客户端身份采用预注册；不宣称支持动态注册或客户端元数据文档。
账号权限检查必须覆盖历史后台入口；仅添加菜单隐藏不构成权限实现。
新建和修改公开文章、将公开文章转为草稿、回滚公开版本都需要发布能力。
首版不实现组织、多站点租户、逐篇分享、公开注册和 MCP 工具。

连接默认只有本人范围；请求并授予 articles:all 后才扩大为账号允许的全站范围。
草稿读取和私密读取独立授权；管理员的全站能力不会自动传递给外部应用。

## Action 规范

`AccountAction` 是固定角色/动作清单的唯一来源，`@RequiresAction` 逐方法绑定路由。
新受保护路由必须声明 Action，缺失默认拒绝，路由契约测试检查遗漏。
资源范围由 AccountAccess / ArticleAccess 以及文章查询条件统一校验。
`/api/admin/access` 返回角色矩阵、Action、绑定路由、OAuth scope；后台权限说明页只读展示。
成员页分配固定角色，外部应用页选择连接范围；没有自定义角色或权限编辑器。
Owner 独占任免管理员和转移所有权；管理员不能修改所有者或其他管理员。
编辑管理公开/草稿及自己的私密文章；投稿者只能编辑自己的草稿。

## 管理界面标准

- **成员管理 `/admin/user/members`**：账号、邮箱、固定角色、状态；新建和调整在抽屉中完成。
  新角色默认 contributor。不能删除账号；停用保留作者归属。管理员不能调整自己、所有者或其他管理员。
  所有权转移独立入口，必须校验当前密码及已启用的 MFA，原子交接唯一 owner。
- **权限说明 `/admin/user/permissions`**：所有登录用户可查看。展示固定角色矩阵、数据范围、Action ID、关联路由及 scope。
  路由以本地化用途描述为主，下一行显示实际路径。描述标识绑定 Controller 方法，文案统一维护在前端 i18n。
  宽屏显示五角色对照，窄屏显示当前所选角色。这里不提供权限开关。
- **外部应用 `/admin/user/applications`**：用户管理自己的授权；管理员额外管理预登记应用。
  `/admin/user/applications/authorize` 单独展示应用、回调、本人/全站范围和 scope，默认只选择公开读取。
- 菜单、搜索入口和操作按钮使用后端返回的 actions；手工请求仍受服务端检查。
  成员管理位于账号下拉菜单且仅管理员可见；外部应用移到个人资料页签，网页入口统一为 `/admin/user/...`，不保留旧路由。成员和外部应用页面标题栏右侧保留「权限说明」按钮。
  浏览器文章缓存、搜索历史按会话隔离；授权页面数据只保存在内存。

## 迁移与当前边界

27.sql 增加 user.role / enabled / authVersion 和三个 OAuth 表。保留原 userId 和文章归属，
最早账号成为 owner，其余既有账号为 admin；未指定角色的新账号默认 contributor。
这里没有用户—角色关联表、权限表或可编辑 RBAC 配置。新安装直接写入一个 owner。

JDBC 使用数据库事务；D1/Web API 使用单条条件写入原子消费授权码与刷新令牌，
并以账号版本、授权状态和 scope 控制访问。D1 不申请 JDBC 连接，也不发送 BEGIN/COMMIT。
签发中途失败不会恢复已消费凭据，需要重新授权；完整约束与并发验证见 [D1 账号存储](d1-account-storage.md)。

附件中原有公开上传路径仍是公开资源；文章设为私密不会把已经公开的文件地址变为私密。
普通成员上传被限制到各自目录；临时附件按账号隔离。独立私密附件库属于后续功能。

本次不调用用户真实数据库或执行在线升级。下次部署新版本时，现有升级机制才会运行 schema 27。

## 验收记录（2026-09-24）

- 基础库 `mvn test`：445 项通过；安装工程 `mvn install`：175 项，4 项跳过，无失败。
- 后台 `mvn test`：485 项通过，覆盖固定角色、路由声明、跨账号对象访问、停用/降权、OAuth 并发兑换和重放。
  OAuth 和升级迁移在 H2、SQLite 上运行；未连接真实 MySQL 实例。
- 前端 `yarn type-check`、`yarn build` 通过；相关 5 个测试套件、38 项测试通过，
  包括桌面/窄屏权限矩阵、管理员不可编辑同级管理员，以及授权默认本人公开读取。
- 隔离 MemoryApplication 的 `/sub` 部署通过真实 HTTP 验证：成员创建、作者越权拒绝、
  私有文章列表/详情/历史版本/SSR 隔离、降权与停用后的旧会话失效、投稿者只能存草稿。
  应用登记、发现文档、PKCE、同意时收窄 scope、令牌验证及撤销通过。
  四个新增管理/授权页面均返回 200，并包含对应服务端数据。
- API 文档检查、后台工程护栏、native JSON 注册检查及 `git diff --check` 通过。
- 浏览器自动化连接不可用，尚未完成真实浏览器截图验收；组件交互和服务端页面验证已完成。

浏览器缓存改为按登录会话隔离。旧版缓存未删除，但不会自动导入当前会话；
升级前应保存尚未提交的浏览器本地草稿。

### 2026-09-25 界面调整

接口用途说明覆盖 135 个 Controller 方法，共 134 条中英文文案，路由别名复用方法描述。
相关后端 23 项测试、页面 5 项测试、前端类型检查、构建及描述完整性护栏通过。
成员管理与外部应用入口移至账号菜单，权限说明作为这两个页面内的帮助入口。
