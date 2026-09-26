# 账号网页路由

网页入口与页面数据 API 按下表对应。OAuth discovery / authorize / token / revoke 和 `/mcp` 地址保持不变。旧聚合网页入口移除，不提供 fragment 或 `?tab=` 切换。

| 网页 | 用途 | 页面初始数据 API |
| --- | --- | --- |
| `/admin/user` | 个人信息 | `/api/admin/user` |
| `/admin/user/security` | 账户安全 | `/api/admin/account-security` |
| `/admin/user/preferences/appearance` | 界面显示 | `/api/admin/user/preferences` |
| `/admin/user/preferences/writing` | 文章编辑 | `/api/admin/user/preferences` |
| `/admin/user/preferences/assistant` | AI 助手 | `/api/admin/user/preferences` |
| `/admin/user/applications/tokens` | 个人访问令牌 | `/api/admin/oauth` |
| `/admin/user/applications/grants` | 我的授权 | `/api/admin/oauth` |
| `/admin/user/applications/clients` | 站点应用登记 | `/api/admin/oauth/clients` |
| `/admin/user/applications/authorize` | OAuth 授权确认 | `/api/admin/oauth/authorize` |
| `/admin/website/members` | 成员管理 | `/api/admin/members` |

前端导航和页面数据加载共用 `account-page-routes.ts`；后端 SSR 通过 `AdminAccountPages` 映射到原 API Controller，并继续检查该 Controller 的 action。成员管理独立要求 `member.manage`，不能按普通站点设置页面放行，也不能被 `/user` 的个人权限规则覆盖。

页面标题沿用中英文资源；个人页面通过独立路径切换，支持刷新、前进和后退。静态页面生成使用新路径，成员、应用和授权确认页面不加入 Service Worker 预缓存，浏览器页面数据只保存在当前会话内存中。

成员、外部应用和偏好设置沿用公共页面加载流程：对应 API 直接返回页面数据，SSR 使用同一映射填充首屏；再次进入时先显示当前会话的缓存，再请求接口并用新数据更新页面。成员和外部应用操作后的重新查询也回写同一页面缓存。外部应用缓存只保存列表与连接信息，创建时一次性返回的令牌或客户端密钥不进入页面缓存。

偏好设置每页独立加载、显示和保存，只更新当前页的字段；恢复默认也仅作用于当前页。接口刷新时保留当前页未保存的修改，并接收其他页的最新设置。保存成功清理其他页面缓存，写回本页最新快照；离开页面会卸载表单并结束未保存的预览，再次进入显示已保存数据。每个完整 pathname 有独立缓存键，旧聚合页缓存不会混入新页面。

权限说明共用页内抽屉：成员管理默认打开角色权限，外部应用默认打开应用授权范围；关闭后保留当前页面和表单状态。打开时请求 `/api/admin/access`，继续由 `permission.read` 校验。移除 `/admin/user/permissions` 网页路由、静态页面和全局搜索入口，不做兼容跳转。

## 设置页界面

站点设置与个人设置共用 `SettingsLayout`：桌面左侧分类导航、右侧内容区域，窄屏使用分类选择器。
个人导航与站点设置保持相同的左侧分组样式，分为「个人账号」「偏好设置」「外部应用」，每组下面直接列出具体页面入口。内容区不再使用横向 Tab，统一标题、间距和表单宽度；窄屏下拉框保留相同分组和入口。

| 左侧导航组 | 独立页面 |
| --- | --- |
| 个人账号 | 个人信息、账户安全 |
| 偏好设置 | 界面显示、文章编辑、AI 助手 |
| 外部应用 | 个人访问令牌、我的授权、站点应用登记 |

所有左侧入口使用表中独立 URL，不使用 fragment 切换内容；动态和 `.html` 静态入口都支持直接打开、刷新、前进和后退。旧 `/admin/user/preferences`、`/admin/user/applications` 聚合页移除。站点应用登记入口按 `oauth.client.manage` 权限显示，该页面 API 和 SSR 也单独校验此权限。所有新路径同步维护中英文标题、静态页面清单和 Service Worker 缓存排除规则。

外部应用中的授权服务器、资源、MCP 地址及令牌创建结果共用地址解析规则：服务端返回的完整 HTTP(S) 地址原样保留，避免改变 OAuth issuer/resource；相对或缺失地址使用当前 `backendServerUrl` 补全，保留后端 context path 并避免重复拼接。静态后台配置独立后端时使用后端地址，而非静态页面域名。显示与复制使用同一结果，应用登记的回调地址保持原值。

可保存表单共用 `SettingsSubmitBar`，沿用各自的保存接口、权限与即时预览/撤销行为。
成员管理位于站点设置的「站点」分类，使用同一布局；仅有 `member.manage` 权限的账号可见。头像菜单只保留个人入口。旧 `/admin/user/members` 路由移除，不做兼容跳转；成员 API 仍为 `/api/admin/members`。
