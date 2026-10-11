# 文章重排字段

沿用 log.arrange_plugin 及现有博客重排协议，补齐编辑器和文章 API 对该字段的控制。页面布局和业务交互由配置的插件负责，后台仅管理插件标识。

## 接口与兼容

- 创建、更新和读取使用现有文章接口，字段名为 arrange_plugin。
- 创建时省略或 null 使用默认排版；更新时省略或 null 保留原值；空字符串清除设置。读取快照将未设置规范化为空字符串。
- 非空标识最多 64 字符，以 ASCII 字母或数字开头，其余允许字母、数字、点、下划线与连字符。不接受模板路径、URL 或 HTML。不要求插件当前在线，避免临时停机影响编辑。
- 沿用账号权限、发布控制、版本号及冲突检测。字段加入自动保存、独立草稿创建、三方合并、修订比较和回滚。旧修订不会补出过去未记录的字段变更。
- 不新增数据库字段、内容类型、API 路由或页面表，不改变文章分类与列表收录逻辑。

## 验证

真实 DAO 覆盖创建/读取、设置/清空、省略/null 保留、无效标识、过期版本和修订回滚。前端覆盖排版字段与正文并行编辑时的合并与冲突，运行类型检查、生产构建和真实编辑器保存/刷新检查。契约在 zrlog-api/admin-web.yaml 维护并同步快照。

## 本地验证记录（2026-10-11）

- 在 `~/git/zrlog/zrlog-admin-web` 直接开发，API 源文件在同级 `zrlog-api`；契约 PR：https://github.com/zrlog/zrlog-api/pull/1。
- Maven 离线完整测试通过：758 项，无失败、错误或跳过；本地 install 编译和打包通过。
- 前端 `yarn type-check`、`yarn build` 通过；文章三方合并与 draft-sync 两个测试文件共 30 项通过。
- `scripts/check-admin-guardrails.sh`、`node scripts/check-api-docs.mjs`、契约索引及消费快照检查、`git diff --check` 通过。
- 本地真实安装流程初始化内存后台，页面地址 `http://127.0.0.1:18093/sub/admin/article-edit?id=1`，Chrome 154.0.8037.97。确认输入后自动保存、刷新读回、清空后读回默认值，以及字段关联的非法输入提示。
- 检查 1440×1000 和 390×900；中文 Desk、英文默认主题浅色/深色均有截图，未见新控件溢出。英文长标签使用上下布局；设置开关各占一行，避免窄抽屉中重叠。Escape 能关闭抽屉。此记录不声明完整 WCAG 验收。
- 截图、浏览器脚本和报告保留在忽略目录 `target/arrange-review/`；运行数据位于 `target/arrange-review-runtime/`，未使用开发者的 `.zrlog-memory`。
- 请求和响应沿用已注册的 `CreateArticleRequest`、`UpdateArticleRequest`、`LoadEditArticleResponse`；无需新增 Native DTO 注册。后台/UI JAR 均未包含 MemoryApplication 或 memory-install 配置。本轮未构建 Native Image。

同步 API 快照同时带入此前未同步的导航契约；现有导航控制器补齐 GET 读取、POST 写入声明，与当前前端调用一致。尚未部署后台，插件页面重排需在 retro-software 中接入并联合验证。
