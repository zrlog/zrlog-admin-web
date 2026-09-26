# 外部应用授权与个人令牌契约

## 范围

覆盖 `zrlog-admin-web`、`zrlog-client-java` 与共享的 `zrlog-base` 账号权限定义。
主要接入方式是 `zrlogctl login --site …`：打开浏览器登录并确认授权，客户端通过 Authorization Code + S256 PKCE 获取、保存并自动刷新令牌，无需手工登记应用或复制令牌。
个人令牌保留为无人值守脚本等场景的接入方式。

## 权限与兼容

- 两种明确模式：指定权限、继承账号权限；指定权限直接使用 `AccountAction` ID，与账号当前权限取交集
- 后台 Bearer 适配器复用 controller 的 `@RequiresAction`，不维护第二套接口白名单或权限表；附加条件（发布、管理员任命）、文章归属与私密内容规则保持有效
- MCP 仍只开放现有读取工具；令牌权限不能增加 MCP 工具。旧 OAuth scope/resource 和旧 `zrmcp_` 令牌不自动扩权
- 新 `zrpat_` 令牌绑定站点地址和 context path；权限模式编码在原有 `scope` 列中，数据库只存摘要，明文仅返回一次
- 老请求未传 permissionMode 时维持 MCP-only 语义；新请求显式选择 `custom` + permissions 或 `inherit`
- 继承模式使用账号当前权限；账号停用、认证版本变化、过期、撤销仍立即失效
- 受限凭证不能签发继承账号权限的令牌；签发指定权限凭证只可选择其有效权限
- Bearer 只从 Authorization 请求头读取；失败不回退到浏览器会话，普通 API 请求不刷新 Cookie

## OAuth / Java 客户端

- 固定 public client `zrlogctl` 首次登录按需登记；禁用后不得自动重新启用
- 仅该内置客户端允许 `http://127.0.0.1:{随机端口}/oauth/callback`，其他客户端仍精确匹配已登记回调
- 新 resource `{issuer}/api/admin` 使用账号 Action ID 或 `account:inherit`，可附加 `offline_access`；浏览器确认时可以收窄权限
- 客户端校验 state、issuer、回调路径、token_type；短期 access token、轮换 refresh token 均绑定站点
- 按站点保存受保护的本机凭证文件，串行刷新；logout 撤销授权后移除本机凭证
- 保留 --token-file、--token、ZRLOG_ACCESS_TOKEN 和原 ZRLOG_ADMIN_TOKEN；显式凭证优先于浏览器登录保存的凭证
- 原文章、分类、素材、主题命令复用 Bearer；增加 notification send
- 不改变客户端发布形态或自动更新机制
- 分域部署通过 `ZRLOG_BACKEND_URL` 固定后端公开地址，OAuth issuer、MCP resource、通知地址及页面显示共用该地址；兼容 `DEFAULT_BACKEND_SERVER_URL`，未配置时保留原单域部署行为

## 外部通知

`POST /api/webhook/message-center/notice` 使用统一的 `notification.create` 账号权限。
实验功能的“接收外部通知”是实际总开关，关闭后个人令牌、OAuth 与旧 Webhook 凭据均不能写通知。
接入地址由服务端返回，个人令牌页集中显示；旧 Webhook 页面仅保留兼容配置与撤销旧凭据，不提供新凭据签发 UI。
不实现出站 Webhook 或事件订阅。

## 验证

1. 共享账号权限的不可扩权限制、旧接口行为
2. 两种模式、账号权限交集、发布与管理员任命、凭证签发、角色变更/撤销/受众隔离
3. OAuth 浏览器完整登录、PKCE/state/issuer、拒绝授权、随机端口、刷新重放、禁用客户端
4. HTTP Bearer 到真实业务接口和通知开关；MCP 工具边界
5. 前端缓存、权限选择、一次性明文、中英文、桌面与移动显示
6. Java 配置优先级、凭证存储/刷新/退出、通知发送与原命令回归

2026-09-26 本地验证：共享库 451 项、后台 575 项、Java 客户端 64 项、前端 343 项测试通过；前端类型检查、生产构建、后台护栏与 OpenAPI 检查通过。
真实浏览器验证覆盖登录、明确选择继承权限、回调、刷新、草稿创建、发布回读、通知总开关与退出撤销；检查中文桌面/移动端以及英文深色页面。
独立后端域名回归覆盖发现、授权跳转、页面地址、context path 去重、错误 resource 和不支持的 MCP scope。
当前环境没有 native-image，未构建原生可执行文件；新增 DTO 已补充客户端 native 反射注册，后台沿用已有 DTO 注册。
