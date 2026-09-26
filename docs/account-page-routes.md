# 账号网页路由

本次只整理网页 URL，后台 API、OAuth discovery / authorize / token / revoke 和 `/mcp` 地址保持不变。旧网页入口移除，不提供重定向或 `?tab=` 兼容。

| 网页 | 用途 | 页面初始数据 API |
| --- | --- | --- |
| `/admin/user` | 个人资料 | `/api/admin/user` |
| `/admin/user/preferences` | 偏好设置 | `/api/admin/user/preferences` |
| `/admin/user/security` | 账号安全 | `/api/admin/account-security` |
| `/admin/user/applications` | 个人令牌和外部应用授权 | `/api/admin/oauth` |
| `/admin/user/applications/authorize` | OAuth 授权确认 | `/api/admin/oauth/authorize` |
| `/admin/website/members` | 成员管理 | `/api/admin/members` |

前端导航和页面数据加载共用 `account-page-routes.ts`；后端 SSR 通过 `AdminAccountPages` 映射到原 API Controller，并继续检查该 Controller 的 action。成员管理独立要求 `member.manage`，不能按普通站点设置页面放行，也不能被 `/user` 的个人权限规则覆盖。

页面标题沿用中英文资源；个人页面通过独立路径切换，支持刷新、前进和后退。静态页面生成使用新路径，成员、应用和授权确认页面不加入 Service Worker 预缓存，浏览器页面数据只保存在当前会话内存中。

成员、外部应用和偏好设置沿用公共页面加载流程：对应 API 直接返回页面数据，SSR 使用同一映射填充首屏；再次进入时先显示当前会话的缓存，再请求接口并用新数据更新页面。成员和外部应用操作后的重新查询也回写同一页面缓存。外部应用缓存只保存列表与连接信息，创建时一次性返回的令牌或客户端密钥不进入页面缓存。

偏好设置刷新时同步已保存基准，保留正在编辑的草稿；保存成功清理其他页面缓存，并写回最新偏好快照。旧版本误存的个人资料数据不会作为偏好设置缓存使用。二级 Tab 的 fragment 和构建版本参数不改变页面缓存键，切换 Tab 不产生额外请求。

权限说明共用页内抽屉：成员管理默认打开角色权限，外部应用默认打开应用授权范围；关闭后保留当前页面和表单状态。打开时请求 `/api/admin/access`，继续由 `permission.read` 校验。移除 `/admin/user/permissions` 网页路由、静态页面和全局搜索入口，不做兼容跳转。

## 设置页界面

站点设置与个人设置共用 `SettingsLayout`：桌面左侧分类导航、右侧内容区域，窄屏使用分类选择器。
个人导航分为「个人账号」「偏好设置」「外部应用」，组内使用二级 Tab；内容使用相同标题、间距和表单宽度。

| 导航组 | 二级 Tab | URL |
| --- | --- | --- |
| 个人账号 | 个人信息 / 账户安全 | `/admin/user` / `/admin/user/security` |
| 偏好设置 | 界面显示 / 文章编辑 / AI 助手 | `/admin/user/preferences#appearance` / `#writing` / `#assistant` |
| 外部应用 | 个人访问令牌 / 我的授权 / 站点应用登记 | `/admin/user/applications#tokens` / `#grants` / `#clients` |

站点应用登记 Tab 仅对应用管理接口返回的管理员可见。页内 Tab 使用 URL fragment，支持直接打开、刷新和浏览器前进/后退；静态模式的 fragment 位于 `.html` 和版本查询参数之后。空或无效 fragment 回落到首个可见 Tab。
fragment 不参与页面数据请求、缓存键或权限判断，切换 Tab 不重新加载页面数据。偏好设置的三个 Tab 共用一份表单草稿和保存栏，切换时保留即时预览及未保存内容，离开偏好设置页面仍恢复已保存设置。API、SSR 页面映射、OAuth 授权确认路径和敏感页缓存规则沿用现有约定。

外部应用中的授权服务器、资源、MCP 地址及令牌创建结果共用地址解析规则：服务端返回的完整 HTTP(S) 地址原样保留，避免改变 OAuth issuer/resource；相对或缺失地址使用当前 `backendServerUrl` 补全，保留后端 context path 并避免重复拼接。静态后台配置独立后端时使用后端地址，而非静态页面域名。显示与复制使用同一结果，应用登记的回调地址保持原值。

可保存表单共用 `SettingsSubmitBar`，沿用各自的保存接口、权限与即时预览/撤销行为。
成员管理位于站点设置的「站点」分类，使用同一布局；仅有 `member.manage` 权限的账号可见。头像菜单只保留个人入口。旧 `/admin/user/members` 路由移除，不做兼容跳转；成员 API 仍为 `/api/admin/members`。
