# 账号个人设置

## 数据归属与兼容

`user.preferences` 是可空 TEXT，保存 JSON 个人覆盖值。SQL 28 增加字段，新安装同步建列；旧账号为空时继续使用站点默认值，不复制全局配置到每个账号。

支持 `language`（zh_CN/en_US）、`appearance`（theme/darkMode/compactMode/colorPrimary）、`articlePageSize`（1–100）、`editor`（自动保存、链接预览、自动发布检查、摘要长度、封面比例）、`session.timeoutMinutes` 和 `articleList`（默认排序、状态筛选、显示列）。控制台布局保存为独立的 `dashboard` 分区，继续使用现有控制台配置界面和权限。

身份、角色、enabled、authVersion、OAuth 凭据仍归原字段/表。偏好写入不改变认证版本，不刷新公共静态页面，不修改站点配置。AI 密钥、AI 对话记录、站点首次配置清单不属于个人偏好；内置助手的知识范围归 assistant 分区。

## 接口契约（后台内部接口）

- GET `/api/admin/user/preferences`：使用 `AdminPageDataResponse` 页面响应，`data` 为 `{overrides, defaults, effective}`，供公共页面加载及 SSR 使用；仅含本期可编辑偏好，不返回原始 user 行和 dashboard 插件运行数据。
- POST `/api/admin/user/updatePreferences`：JSON 对象，前端传 `?section=appearance|writing|assistant|session`，服务端仅替换选中页的覆盖值；缺失/null 表示继承，空对象恢复该页默认值。不带 section 的旧客户端继续替换旧字段，但保留请求未包含的新增 session/articleList 和 editor 字段。未知字段、类型错误、越界值拒绝。dashboard 只能通过现有配置接口更新。
- 两个接口都绑定 `account.self`，身份只来自已验证会话，不接受 userId。底层按 userId 参数化写入，并以旧 JSON 值做条件更新重试，避免不同分区并发写入互相覆盖；同一分区最后写入生效。
- 条件更新冲突时采用带随机抖动的指数退避，每次重读最新 JSON 后合并，重试时间预算为 5 秒，兼容 JDBC 与 D1/Web API 多实例写入。持续冲突仍报错；数据库错误直接上抛，线程中断停止重试并保留中断状态。预算限制冲突重试，单次数据库调用仍由数据库超时控制。
- 读取损坏的个人 JSON 时回退默认值；单个分区不合法时仅该分区回退；写入恢复有效 JSON。未知的持久化分区保留，便于后续扩展。

### 并发更新修复验证（2026-09-26）

原实现连续重试 8 次，可能在另一请求的一批更新完成前耗尽次数。新增 Web API 回归在读取和写入之间提交 12 次竞争更新，已在修复前稳定复现异常；修复后覆盖个人设置和控制台布局双向合并、未知分区保留、持续冲突超时、线程中断及数据库错误直接上抛。

`./mvnw -q -o -Dmaven.repo.local=/tmp/zrlog-accounts-m2 test`：584 项全部通过。原 H2/SQLite 并发用例另行重复 50 轮，共 100 项全部通过；`scripts/check-admin-guardrails.sh` 和 `git diff --check` 通过。本次不改变接口字段、页面或数据库结构，无新增 native/Gson DTO。

## 生效链路

站点管理页面始终编辑站点默认值。登录后的后台 HTML 和个人设置接口提供个人有效外观；公共资源和公共静态 HTML 继续只含站点默认值，静态后台在验证账号后加载个人设置。后台服务端消息使用该请求账号的语言。文章列表默认页大小和编辑器自动保存从同一偏好服务解析；异步编辑器任务在回到请求线程后应用覆盖值。

偏好设置的界面显示、文章编辑、AI 助手使用独立页面路径和左侧分组导航。各页只显示和保存自己的字段，恢复默认仅清除该页覆盖值；控件显示有效值，未改动项继续跟随站点。再次进入先显示该页缓存，后台刷新保留本页草稿并同步其他页的最新设置；离开页面结束未保存的预览。修改时预览外观/语言，保存后清理当前会话页面缓存并写回新的偏好快照。后台首页现有布局设置自动保存到当前账号，未保存者仍读取原全局布局作为默认。

## 验证切片

1. 安装/升级：H2、SQLite 列兼容，旧账号数据、认证版本不变。
2. 服务：两账号隔离、默认继承/重置、严格校验、分区保留与并发条件更新。
3. 消费端：分页/自动保存/布局、服务端语言、SSR 与静态后台加载个人外观。
4. 页面：中英文表单、保存失败保留输入、恢复默认、离线禁止写入；后端测试、前端类型/测试/构建和工程护栏。

## 本次验证记录

- base：`mvn -o -Dmaven.repo.local=/tmp/zrlog-accounts-m2 test`，447 项通过（含 H2 / SQLite 旧库升级）。
- install：同一临时 Maven 仓库运行 `mvn install`，175 项，4 项环境相关跳过；新安装 preferences 为空。
- admin：`mvn -o -Dmaven.repo.local=/tmp/zrlog-accounts-m2 '-Dtest=*,!MemoryApplicationTest' test`，503 项通过。MemoryApplicationTest 会重置正在使用的预览数据，因此通过独立 18083 内存安装和 HTTP 检查覆盖启动链路。
- frontend：`yarn type-check`、`CI=true yarn test --watchAll=false --runInBand`（251 项）、`yarn build`，以及改动文件 ESLint 通过。
- 工程护栏、权限接口中英文说明检查、各仓库 `git diff --check` 通过。
- 独立 HTTP 检查覆盖作者自行设置、跨账号隔离、无权限字段拒绝、后台 HTML/公共资源隔离、分页、编辑器、布局分区、重置，以及原会话和 OAuth access token 保持有效。
- 浏览器工具连接失败，未取得桌面/移动端截图；组件测试覆盖表单继承值、false 覆盖、保存失败保留输入、恢复默认、中英文及离线禁写。未运行 native-image 编译或真实 MySQL 数据库测试。

## 站点表单边界补充

站点后台设置表单仅使用接口返回的站点配置初始化和提交，不能以当前界面的 AppState 回填（当前界面可能已应用个人设置）。编辑站点默认外观时不修改当前界面状态；保存后按既有刷新流程重新解析有效偏好。回归覆盖个人外观与站点默认不同、只修改会话时长仍保留站点外观，以及深色控件跟随表单主题。

此补充通过 6 项前端专项测试、TypeScript 检查、改动文件 ESLint 和生产构建；预览 18083 已更新静态资源，保留现有内存数据。

## 个人设置交互修订

界面名称统一为「个人设置」。个人设置与站点后台设置共用外观字段组件及响应式横向表单：语言/主题下拉、深色/紧凑开关、预设调色板。控件显示有效值，未编辑的字段继续继承站点默认值；仅用户改动的字段加入覆盖值。

外观与语言在编辑时即时预览；保存成功后更新回退基准，保存失败保留输入。恢复默认先预览，点击保存才落库；撤销或离开个人设置页签恢复已保存外观。固定配色主题隐藏不生效的主色控件。用户资料页签切换会卸载个人设置，确保预览清理执行。

## AI 助手设置与默认知识库

个人设置增加 `assistant.knowledgeScope`，单个下拉框选择 `off`、`own_public`（默认）、`own_all`、`accessible_public`、`accessible_all`。all 包含草稿和私密文章，界面明确标注；普通账号只展示 off/own 范围。此设置仅决定本人内置助手使用的知识范围，不授予账号权限，也不影响外部 MCP 的 OAuth 授权。即使直接提交 accessible 范围，服务端也始终以账号权限交集查询。

普通助手对话默认挂载只读知识工具，不再区分写作/知识库模式。关闭知识库后仍可普通对话，供应商请求不包含工具。普通对话与结构化写作技能共用文章消息状态并自动保存，按账号和文章隔离，刷新或重进文章后恢复。导出和清空操作当前账号的已保存对话。

`POST /api/admin/article/ai` 继续用现有 SSE 事件，配置由服务端读取当前账号设置；旧请求 options 不再决定权限。请求开始时快照范围，后续模型轮次/工具调用/最终返回重新核对账号与设置，设置变化终止当前请求。客户端不缓存知识权限。外部 MCP 契约不变。

## 交互修订验证

- `mvn -o -Dmaven.repo.local=/tmp/zrlog-accounts-m2 '-Dtest=*,!MemoryApplicationTest' test`：507 项通过；补充保留站点写作提示词后，`-Dtest=AIKnowledgeServiceTest test` 的 8 项再次通过。
- `yarn type-check`、`CI=true yarn test --watchAll=false --runInBand`：40 组 / 259 项；生产 `yarn build`、改动文件 ESLint、工程护栏、`git diff --check` 通过。
- 真实 18084 隔离 HTTP 验收覆盖默认工具挂载、单一范围保存、私密内容主动选入、关闭后的普通对话、请求体不能扩大范围、账号权限交集、外部 OAuth MCP 不受内置设置影响；原个人设置/会话/OAuth/SSR 隔离检查继续通过。AI 使用本地模拟供应商，不调用真实模型。
- 普通对话继续使用站点配置的写作提示词。个人设置的外观预览不会被延迟返回的后台初始化请求覆盖。
- 浏览器连接仍报 `nodeRepl.fetch request failed`，未完成真实浏览器视觉检查；组件交互验证覆盖即时切换语言、开关预览、撤销/离开回退、重置后保存、单一知识范围选择及离线行为。
- 18084 验收后重启为干净预览，原 18080–18083 服务和数据保留；未提交代码。

## 思考展示与检索触发修订验证

- 思考展示改动运行后台完整回归（排除 MemoryApplicationTest），510 项通过；前端 40 组 / 261 项通过。
- 随后收紧按需检索提示词及等待文案，AIKnowledgeServiceTest 11 项、助手组件专项 20 项、TypeScript、ESLint、生产构建和工程护栏通过。
- 真实 HTTP + 本地模拟供应商验证：挂载 tools 且 tool_choice=auto 时可直接回答，不产生 tool 事件；实际 reasoning 文本独立传输，权限与范围控制、外部 MCP 继续通过。
- 供应商调用目前按轮返回，思考文本按轮呈现，不是逐 token 流式；Qwen 为兼容这条非流式工具链仍关闭 thinking。没有进行真实模型的检索判断效果评估，浏览器连接仍不可用。

## 恢复持久对话（修正临时保存回退）

普通对话和写作技能重新使用同一条持久记录链路。对话属于「账号 + 文章」，复用 website KV 的新键 `ai_chat_message_u{userId}_{articleId}`，不新增表，也不放入 user.preferences。旧 `ai_chat_message_{articleId}` 记录只作为兼容读取来源；新账号记录不存在时读取旧内容，首次保存复制到自己的记录，不改写旧记录。清空写入空数组，避免旧内容再次出现。

`POST /api/admin/article/ai` 增加 articleId（0 表示当前账号新草稿），服务端从该账号持久记录构造上下文。客户端 history 保留兼容校验，但不再作为已保存历史的权威来源。请求开始、模型轮次和保存前检查账号及文章访问权限。最终答案、思考文本、引用来源和稳定 messageId 在返回 done 前保存；写入失败不能报告成功。

前端普通对话回到现有文章消息状态和按会话隔离的缓存，多个助手入口共享同一份记录；重载从后端恢复。保留写作技能、上下文、导出、清空、草稿首次保存迁移。后台异步任务显式携带发起账号的记录上下文，不能写回旧共享键。文章删除清理该文章的旧记录与各账号记录。

验证覆盖：刷新/新请求恢复、跨账号与跨文章隔离、旧记录兼容、空记录不复活、草稿迁移、来源/思考保存、权限变更与写入失败、前端普通对话不再在组件卸载时丢失。


持久化修复验证：后端完整回归（排除会重置现有预览数据的 MemoryApplicationTest）516 项、前端 40 组 / 263 项通过；TypeScript、改动文件 ESLint、生产构建、native DTO/i18n 工程护栏与 diff 检查通过。真实 HTTP + 本地模拟供应商覆盖重新登录恢复、草稿首次保存迁移、服务端历史上下文、来源和多轮思考保存、导出、跨账号/文章隔离、清空后重载与未授权文章拒绝。预览独立使用 18085，已有 18080–18084 数据未改动；未提交代码。

## 账号设置扩展与会话兼容

本次继续使用 `user.preferences` JSON 与 `website` 站点默认值，不新增表、列或批量回填。新增 `session.timeoutMinutes`（6–99999 分钟）、`editor.linkPreviewEnabled`、`editor.publishCheckEnabled`、`editor.autoDigestLength`（-1–99999，-1 全文、0 不生成）、`editor.coverAspectRatio`（沿用站点枚举）。`articleList` 支持 sort（id/click/commentSize/releaseTime/lastUpdateDate + ASC/DESC）、status（全部/草稿/私密/已发布）、columns（可选显示列，标题和操作始终显示）。URL 中显式的排序和状态筛选优先于个人默认；清除状态筛选保留空参数，避免再次应用个人默认。列表没有旧站点设置的项目沿用内置默认。账号有效值覆盖站点默认，未设置/null 继承；权限和 AI 可用性仍由原服务控制。

实现范围：共享 JSON 存储与会话签发；个人设置契约与分区并发保存；编辑器、摘要生成、发布检查和 AI 封面消费；前端表单、继承/重置及回归。已有外观、分页、自动保存、布局、助手范围继续复用原字段。站点公共配置与共享 AI 凭据保留站点归属。

保存继续使用原 POST 接口，新增可选 `section` 查询参数（appearance/writing/assistant/session），仅替换该页分区，其他分区从数据库最新值保留；未带 section 的旧客户端保持原有可编辑字段替换语义，但省略新增字段时保留其值，避免旧客户端清除新增个人设置。分区重置删除该页覆盖值。存储采用原条件更新与重试策略，共享于账号偏好和登录模块。

登录有效期沿用现有请求续期行为。新登录读取个人时长并写入令牌到期时间，Cookie 与服务端使用同一到期时间；续期沿用该会话的时长快照。修改账号/站点时长从下次登录生效。无到期时间的旧令牌继续以旧站点时长验证，首次续期转换为带到期时间的令牌。OAuth/PAT 生命周期不变，普通偏好保存不修改 authVersion。

统一存储由 base 的 `UserPreferenceStore` 提供：按 userId 读取 `user.preferences`，读取→分区合并→带旧 JSON 条件的 UPDATE，冲突重读后重试。后台页面、默认值合并与校验继续归 `UserPreferenceService`，登录模块通过同一个存储读取会话分区，不引入 admin 反向依赖。账号标识来自验证后的登录态，前端不能指定 userId。

```json
{
  "session": {"timeoutMinutes": 60},
  "editor": {"autoSaveInterval": 5, "linkPreviewEnabled": true, "publishCheckEnabled": false, "autoDigestLength": 200, "coverAspectRatio": "1:1"},
  "articleList": {"sort": "lastUpdateDate,DESC", "status": "draft", "columns": ["typeName", "lastUpdateDate"]}
}
```

摘要长度的站点默认与个人覆盖均保留 -1/0 的既有生成语义，避免设置页把它们归一化成 200。后台登录时长缺省值统一按分钟处理（1440），修复 DTO 将毫秒默认值直接当分钟的问题。

### 扩展验证（2026-10-05）

- base：`JAVA_HOME=/tmp/zrlog-test-support-jdk/jdk-21.0.12.1+1 ./mvnw -q -o -Dmaven.repo.local=/tmp/zrlog-accounts-m2 test install`，475 项通过；存储读取边界调整后另跑 token 及依赖模块测试与 install。覆盖账号 JSON 读取、非法值回退、新旧令牌过期及续期时长快照。
- admin：相同 JDK/本地 Maven 仓库运行 `./mvnw -q -o -Dmaven.repo.local=/tmp/zrlog-accounts-m2 test`，753 项通过（含在临时目录运行的 MemoryApplicationTest）。新增 H2/SQLite 账号隔离、旧客户端兼容、分区重置、并发更新、摘要生成、编辑器设置、AI 封面比例和列表显式查询覆盖测试。
- 前端：`CI=true yarn test --watchAll=false --runInBand`，57 组 / 505 项通过；`yarn type-check`、改动文件 ESLint、`yarn build` 通过。覆盖独立登录设置页面、站点继承值、false/0 覆盖、重置、失败保留输入及桌面/移动导航。
- `scripts/check-admin-guardrails.sh`、后端 `-DskipTests compile` 和两个仓库的 `git diff --check` 通过。新增嵌套 DTO 已注册 Native/Gson，现有 token 类型注册自动覆盖新增字段；未运行 Native Image 构建。
- 浏览器工具返回空浏览器列表，本次未取得桌面/移动截图，也未执行人工浏览器交互验收。没有发布或提交代码。

### 真实 HTTP 补充验证（2026-10-05）

从当前测试 classpath 启动独立 MemoryApplication，运行目录为 `/tmp/zrlog-account-preferences-preview-18186`，入口为 `http://localhost:18186/sub/admin/login`。通过真实安装链路创建临时 H2 数据库及两个测试账号，不使用仓库的 `.zrlog-memory`。

`python3 /tmp/zrlog-account-preferences-preview-18186/verify_http.py` 的 25 项检查全部通过：空账号继承站点值、账号隔离、跨会话恢复、旧页面保存保留其他页新值、不同分区并发写入、旧客户端兼容、非法参数拒绝、单页重置、站点默认不变，以及编辑器、列表显式查询和摘要 0/-1 的实际消费。四个个人设置 HTML 路由均返回成功。

Cookie 验证覆盖 1440→60→90 分钟的设置变化：已有会话继续按各自登录时的时长续期，新登录读取最新个人值；清除个人会话设置后，新登录重新使用站点的 1440 分钟。结果保存在临时目录的 `http-results.json` 和 `http-verification.log`。此轮未发现需要修改业务代码的问题；HTML 路由检查不代替浏览器视觉验收，浏览器截图与 Native Image 构建仍未执行。
