# 账号网页路由

本次只整理网页 URL，后台 API、OAuth discovery / authorize / token / revoke 和 `/mcp` 地址保持不变。旧网页入口移除，不提供重定向或 `?tab=` 兼容。

| 网页 | 用途 | 页面初始数据 API |
| --- | --- | --- |
| `/admin/user` | 个人资料 | `/api/admin/user` |
| `/admin/user/preferences` | 个人设置 | `/api/admin/user` |
| `/admin/user/security` | 账号安全 | `/api/admin/account-security` |
| `/admin/user/applications` | 个人令牌和外部应用授权 | `/api/admin/user`，页内继续请求 `/api/admin/oauth` |
| `/admin/user/applications/authorize` | OAuth 授权确认 | `/api/admin/oauth/authorize` |
| `/admin/website/members` | 成员管理 | `/api/admin/members` |
| `/admin/user/permissions` | 权限说明 | `/api/admin/access` |

前端导航和页面数据加载共用 `account-page-routes.ts`；后端 SSR 通过 `AdminAccountPages` 映射到原 API Controller，并继续检查该 Controller 的 action。成员管理独立要求 `member.manage`，不能按普通站点设置页面放行，也不能被 `/user` 的个人权限规则覆盖。

页面标题沿用中英文资源；个人页面通过独立路径切换，支持刷新、前进和后退。静态页面生成使用新路径，成员、权限、应用和授权确认页面不加入 Service Worker 预缓存，浏览器页面数据只保存在当前会话内存中。

## 设置页界面

站点设置与个人设置共用 `SettingsLayout`：桌面左侧分类导航、右侧内容区域，窄屏使用分类选择器。
个人导航包含个人资料、个人设置、账号安全和外部应用；内容使用相同标题、间距和表单宽度。
可保存表单共用 `SettingsSubmitBar`，沿用各自的保存接口、权限与即时预览/撤销行为。
成员管理位于站点设置的「站点」分类，使用同一布局；仅有 `member.manage` 权限的账号可见。头像菜单只保留个人入口。旧 `/admin/user/members` 路由移除，不做兼容跳转；成员 API 仍为 `/api/admin/members`。
