# 账号网页路由

本次只整理网页 URL，后台 API、OAuth discovery / authorize / token / revoke 和 `/mcp` 地址保持不变。旧网页入口移除，不提供重定向或 `?tab=` 兼容。

| 网页 | 用途 | 页面初始数据 API |
| --- | --- | --- |
| `/admin/user` | 个人资料 | `/api/admin/user` |
| `/admin/user/preferences` | 个人设置 | `/api/admin/user` |
| `/admin/user/security` | 账号安全 | `/api/admin/account-security` |
| `/admin/user/applications` | 个人令牌和外部应用授权 | `/api/admin/user`，页内继续请求 `/api/admin/oauth` |
| `/admin/user/applications/authorize` | OAuth 授权确认 | `/api/admin/oauth/authorize` |
| `/admin/user/members` | 成员管理 | `/api/admin/members` |
| `/admin/user/permissions` | 权限说明 | `/api/admin/access` |

前端导航和页面数据加载共用 `user-page-routes.ts`；后端 SSR 通过 `AdminUserPages` 映射到原 API Controller，并继续检查该 Controller 的 action。成员管理仍要求 `member.manage`，不得被 `/user` 的个人权限规则覆盖。

页面标题沿用中英文资源；个人页签通过独立路径切换，支持刷新、前进和后退。静态页面生成使用新路径，成员、权限、应用和授权确认页面不加入 Service Worker 预缓存，浏览器页面数据只保存在当前会话内存中。
